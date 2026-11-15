package com.example.sync

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.DocumentSnapshot
import java.io.IOException

data class CloudProfile(val generation: Long, val version: Long, val migrationCompleted: Boolean)
data class UploadResult(val beforeVersion: Long, val profile: CloudProfile, val progress: List<CloudWordProgress>)
class GenerationChanged : IOException("Прогрес скинуто на іншому пристрої. Повторюємо синхронізацію.")
class ProgressUploadRejected : IOException("Прогрес перевищує підтримуваний обсяг. Локальні відповіді збережено; зверніться до підтримки.")

interface ProgressCloudStore {
    suspend fun profile(uid: String): CloudProfile
    suspend fun pull(uid: String, generation: Long): CloudProgressBatch
    suspend fun upload(uid: String, generation: Long, local: List<CloudWordProgress>): UploadResult
    suspend fun reset(uid: String, requestId: String): CloudProfile
}

/** Transport only. Every write reads the current generation and document versions in a transaction. */
class FirestoreProgressStore(private val firestore: FirebaseFirestore,
                             private val authUid: () -> String?) : ProgressCloudStore {
    private fun user(uid: String) = firestore.collection("users").document(uid)
    private fun progress(uid: String, generation: Long) = user(uid).collection("epochs")
        .document(generation.toString()).collection("wordProgress")
    private fun checkAccount(uid: String) { check(authUid() == uid) { "Google-акаунт змінився." } }
    private fun DocumentSnapshot.profile() = CloudProfile(
        generation = requireNotNull(getLong("generation")), version = requireNotNull(getLong("version")),
        migrationCompleted = getBoolean("migrationCompleted") == true)

    override suspend fun profile(uid: String): CloudProfile {
        val initial = firestore.runTransaction { tx ->
            checkAccount(uid)
            val ref = user(uid)
            val snapshot = tx.get(ref)
            if (snapshot.getLong("schemaVersion") == 2L) snapshot.profile()
            else {
                check(!snapshot.exists() || snapshot.getLong("schemaVersion") == 1L) { "Непідтримувана версія профілю." }
                tx.set(ref, mapOf("schemaVersion" to 2, "generation" to 0L, "version" to 0L,
                    "migrationCompleted" to false, "lastResetId" to "",
                    "createdAt" to (snapshot.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                    "updatedAt" to FieldValue.serverTimestamp()))
                CloudProfile(0, 0, false)
            }
        }.awaitRequest()
        if (initial.migrationCompleted) return initial
        // v2 profile fences off v1 writers before we read the legacy collection.
        val legacy = if (initial.generation == 0L) user(uid).collection("wordProgress").get(Source.SERVER).awaitRequest()
            .documents.mapNotNull { it.toCloudWordProgress() } else emptyList()
        for (chunk in legacy.chunked(20)) {
            var result = uploadBatch(uid, 0, chunk, migration = true)
            while (result.profile.generation == 0L && !result.profile.migrationCompleted &&
                result.progress.any { remote -> mergeProgress(chunk.first { it.wordKey == remote.wordKey }, remote) != remote }) {
                result = uploadBatch(uid, 0, chunk, migration = true)
            }
            if (result.profile.generation != 0L || result.profile.migrationCompleted) break
        }
        return firestore.runTransaction { tx ->
            checkAccount(uid)
            val root = tx.get(user(uid)).profile()
            tx.update(user(uid), "migrationCompleted", true)
            root.copy(migrationCompleted = true)
        }.awaitRequest()
    }

    override suspend fun pull(uid: String, generation: Long): CloudProgressBatch {
        checkAccount(uid)
        return parseCloudProgressBatch(progress(uid, generation).get(Source.SERVER).awaitRequest().documents
            .map { it.id to it.data.orEmpty() })
    }

    override suspend fun upload(uid: String, generation: Long, local: List<CloudWordProgress>): UploadResult =
        uploadBatch(uid, generation, local)

    private suspend fun uploadBatch(uid: String, generation: Long, local: List<CloudWordProgress>, migration: Boolean = false): UploadResult {
        checkAccount(uid)
        require(local.size in 1..20)
        require(local.map { it.wordKey }.toSet().size == local.size)
        val response = firestore.runTransaction { tx ->
            checkAccount(uid)
            val root = tx.get(user(uid)).profile()
            if (migration && (root.generation != 0L || root.migrationCompleted))
                return@runTransaction UploadResult(root.version, root, emptyList())
            if (root.generation != generation) throw GenerationChanged()
            val refs = local.map { progress(uid, generation).document(it.wordKey) }
            // Read all snapshots before the first write; retries merge against the new remote version.
            val previous = refs.map { tx.get(it).toCloudWordProgress() }
            val writes = local.mapIndexed { n, value -> nextSparkProgressWrite(value, previous[n]) }
            val changed = writes.indices.filter { writes[it].progress != previous[it] }
            val next = if (changed.isEmpty()) root else root.copy(version = root.version + 1)
            changed.forEach { n -> tx.set(refs[n], writes[n].progress.toFirestoreMap() +
                ("counterWriter" to writes[n].counterWriter)) }
            if (changed.isNotEmpty()) tx.update(user(uid), mapOf("version" to next.version,
                "updatedAt" to FieldValue.serverTimestamp()))
            UploadResult(root.version, next, writes.map { it.progress })
        }.awaitRequest()
        checkAccount(uid)
        return response
    }

    override suspend fun reset(uid: String, requestId: String): CloudProfile = firestore.runTransaction { tx ->
        checkAccount(uid)
        val receiptRef = user(uid).collection("resetRequests").document(requestId)
        val receipt = tx.get(receiptRef)
        val snapshot = tx.get(user(uid))
        val root = snapshot.profile()
        if (receipt.exists()) root
        else {
            val next = root.copy(generation = root.generation + 1, version = root.version + 1)
            tx.update(user(uid), mapOf("generation" to next.generation, "version" to next.version,
                "lastResetId" to requestId, "updatedAt" to FieldValue.serverTimestamp()))
            tx.set(receiptRef, mapOf("generation" to next.generation, "createdAt" to FieldValue.serverTimestamp()))
            next
        }
    }.awaitRequest()
}
