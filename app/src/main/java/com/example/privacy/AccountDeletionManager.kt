package com.example.privacy

import com.example.data.AppPreferences
import com.example.sync.awaitRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class PendingAccountDeletion(val uid: String, val receipt: String, val nextEpoch: Long = 0,
    val cloudCleared: Boolean = false, val authDeleted: Boolean = false)
data class AccountDeletionState(val busy: Boolean = false, val message: String? = null)

/** Spark has no server deletion runner. Persist checkpoints and resume while the app is open. */
class AccountDeletionManager(
    private val auth: FirebaseAuth?, private val firestore: FirebaseFirestore?,
    private val preferences: AppPreferences, private val scope: CoroutineScope,
    private val onPending: suspend (String) -> Unit, private val onCompleted: suspend (String) -> Unit
) {
    private val mutablePending = MutableStateFlow<PendingAccountDeletion?>(null)
    val pending = mutablePending.asStateFlow()
    private val mutableState = MutableStateFlow(AccountDeletionState())
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    suspend fun restoreIntent() { mutablePending.value = preferences.pendingAccountDeletion.first() }
    fun recover() { scope.launch { checkPending() } }
    fun report(message: String) { mutableState.value = AccountDeletionState(message = message) }
    private suspend fun save(request: PendingAccountDeletion) {
        preferences.setPendingAccountDeletion(request)
        mutablePending.value = request
    }
    suspend fun submit(googleToken: String) = mutex.withLock {
        mutableState.value = AccountDeletionState(busy = true)
        try {
            val user = requireNotNull(auth?.currentUser)
            val uid = user.uid
            user.reauthenticate(GoogleAuthProvider.getCredential(googleToken, null)).awaitRequest()
            check(auth.currentUser?.uid == uid)
            user.getIdToken(true).awaitRequest()
            val request = mutablePending.value?.also { require(it.uid == uid) }
                ?: PendingAccountDeletion(uid, UUID.randomUUID().toString())
            save(request)
            onPending(uid)
            continueDeletion(request)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { interrupted() }
    }
    suspend fun checkPending() = mutex.withLock {
        val request = mutablePending.value ?: return@withLock
        mutableState.value = AccountDeletionState(busy = true)
        try { continueDeletion(request) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { interrupted() }
    }
    private fun interrupted() {
        mutableState.value = AccountDeletionState(message = if (mutablePending.value == null)
            "Не вдалося підтвердити видалення. Виберіть той самий Google-акаунт і перевірте інтернет."
            else "Видалення ще не завершено. Залиште застосунок відкритим і натисніть «Продовжити видалення». За потреби повторно підтвердьте Google-вхід. Якщо вхід недоступний — скористайтеся зверненням до підтримки нижче.")
    }
    private suspend fun continueDeletion(initial: PendingAccountDeletion) {
        var request = initial
        if (request.authDeleted) { finish(request); return }
        val user = requireNotNull(auth?.currentUser) { "Потрібна підтримка або повторний вхід." }
        check(user.uid == request.uid)
        val db = requireNotNull(firestore)
        val root = db.collection("users").document(request.uid)
        if (!request.cloudCleared) {
            val fence = db.collection("accountDeletionJobs").document(request.uid)
            val generation = db.runTransaction { tx ->
                check(auth.currentUser?.uid == request.uid)
                val existing = tx.get(fence)
                if (existing.exists()) requireNotNull(existing.getLong("generation"))
                else {
                    val last = tx.get(root).getLong("generation") ?: 0L
                    tx.set(fence, mapOf("generation" to last, "createdAt" to FieldValue.serverTimestamp()))
                    last
                }
            }.awaitRequest()
            val store = object : DeletionDataStore {
                override suspend fun deletePage(collectionPath: String): Boolean {
                    check(auth.currentUser?.uid == request.uid)
                    val docs = db.collection(collectionPath).limit(100).get(Source.SERVER).awaitRequest()
                    if (docs.isEmpty) return true
                    db.batch().apply { docs.documents.forEach { delete(it.reference) } }.commit().awaitRequest()
                    return false
                }
                override suspend fun deleteDocument(documentPath: String) {
                    check(auth.currentUser?.uid == request.uid)
                    db.document(documentPath).delete().awaitRequest()
                }
            }
            val cleared = clearSparkAccountData(request.uid, request.nextEpoch, generation, store, { next ->
                request = request.copy(nextEpoch = next)
                save(request)
            })
            if (!cleared) { continuationMessage(); return }
            request = request.copy(cloudCleared = true)
            save(request)
        }
        check(auth.currentUser?.uid == request.uid)
        try { user.delete().awaitRequest() }
        catch (failure: FirebaseAuthInvalidUserException) {
            if (failure.errorCode != "ERROR_USER_NOT_FOUND") throw failure
        }
        request = request.copy(authDeleted = true)
        save(request)
        finish(request)
    }
    private fun continuationMessage() {
        mutableState.value = AccountDeletionState(message = "Частину даних уже видалено. Натисніть «Продовжити видалення», щоб завершити. Не видаляйте застосунок до завершення.")
    }
    private suspend fun finish(request: PendingAccountDeletion) {
        onCompleted(request.uid)
        preferences.setPendingAccountDeletion(null)
        mutablePending.value = null
        mutableState.value = AccountDeletionState(message = "Акаунт і навчальні дані видалено. Чинний Premium можна відновити через той самий акаунт Google Play. Тимчасовий технічний запис захищає від повторного запису зі старих пристроїв; подробиці — у політиці конфіденційності.")
    }
}
