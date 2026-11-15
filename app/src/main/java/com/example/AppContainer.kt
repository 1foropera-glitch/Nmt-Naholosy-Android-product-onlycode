package com.example

import android.content.Context
import com.example.ads.AdManager
import com.example.auth.AuthUiState
import com.example.auth.AuthRepository
import com.example.auth.FirebaseAuthRepository
import com.example.auth.LocalOnlyAuthRepository
import com.example.billing.BillingManager
import com.example.data.*
import com.example.reminders.ReviewReminderNotifications
import com.example.sync.*
import com.example.telemetry.FirebaseTelemetry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface StartupState {
    data object Loading : StartupState
    data object Ready : StartupState
    data class Error(val message: String) : StartupState
}

/** Application-scoped composition root shared by the UI and workers. */
class AppContainer(context: Context) {
    private val context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database = AppDatabase.getDatabase(this.context)
    val preferences = AppPreferences(this.context)
    val telemetry = FirebaseTelemetry(this.context)
    private val firebase = runCatching { FirebaseApp.initializeApp(this.context) }.getOrNull()
    private val firebaseAuth = firebase?.let { FirebaseAuth.getInstance(it) }
    val authRepository: AuthRepository = firebaseAuth?.let { FirebaseAuthRepository(it) } ?: LocalOnlyAuthRepository()
    val identity by lazy { InstallIdentity(this.context) }
    val wordRepository = WordRepository(database, DictionaryCatalog(this.context))
    val profiles = ProfileRepository(database, preferences)
    val sessions = SessionRepository(database, { identity.id }, { signedInUid() ?: ProfileRepository.GUEST })
    val scheduler = ProgressSyncScheduler(this.context)
    val syncRepository = ProgressSyncRepository(firebase?.let {
        FirestoreProgressStore(FirebaseFirestore.getInstance(it)) { firebaseAuth?.currentUser?.uid }
    }, database, profiles, preferences, telemetry) { clearReminders() }
    val adConsent = com.example.privacy.AdConsentManager(this.context)
    val premiumState: StateFlow<Boolean?> = combine(preferences.premiumCache, authRepository.authState) { cache, auth ->
        when (auth) {
            AuthUiState.SigningIn -> null
            is AuthUiState.SignedIn -> if (cache.ownerUid == auth.account.uid) cache.active else null
            else -> false
        }
    }.catch { error -> telemetry.recordException(error); emit(null) }
        .stateIn(scope, SharingStarted.Eagerly, null)
    val adManager = AdManager(this.context, telemetry::adShown)
    val billingManager = BillingManager(this.context, preferences, telemetry, scope, com.example.billing.PurchaseVerifier(firebase) { it == signedInUid() })
    val startup = MutableStateFlow<StartupState>(StartupState.Loading)
    val ready = CompletableDeferred<Unit>()
    private val startupMutex = Mutex()
    private val accountMutex = Mutex()
    private var selectedUid: String? = null
    private var accountSelected = false
    val accountDeletion = com.example.privacy.AccountDeletionManager(firebaseAuth,
        firebase?.let { FirebaseFirestore.getInstance(it) },
        preferences, scope,
        onPending = { uid -> scheduler.cancel(uid); syncRepository.onAccountChanged(null); refreshAccount(); clearReminders() },
        onCompleted = { uid ->
            accountMutex.withLock {
                scheduler.cancel(uid)
                profiles.deleteLocalAccount(uid)
                if (preferences.premiumCache.first().ownerUid == uid) preferences.setPremium(false)
                if (firebaseAuth?.currentUser?.uid == uid) authRepository.signOut()
                accountSelected = false
                clearReminders()
            }
            refreshAccount()
        })

    fun signedInUid(): String? = (authRepository.authState.value as? AuthUiState.SignedIn)?.account?.uid
        ?.takeUnless { it == accountDeletion.pending.value?.uid }
    suspend fun clearReminders() {
        preferences.clearReviewReminderSnapshot()
        ReviewReminderNotifications.cancel(context)
    }
    suspend fun runForProfile(expected: LocalProfile, action: suspend () -> Unit): Boolean = accountMutex.withLock {
        val current = profiles.activeProfile()
        if (current.id != expected.id || current.generation != expected.generation || current.writerId != expected.writerId
            || current.pendingResetId != null || current.id != (signedInUid() ?: ProfileRepository.GUEST)) return@withLock false
        if (accountDeletion.pending.value?.uid == current.id) return@withLock false
        action()
        true
    }

    fun start() {
        firebase?.let { AppCheckInstaller.install(it) }
        retryStartup()
        scope.launch {
            preferences.diagnosticsEnabled.collect(telemetry::setCollectionEnabled)
        }
        scope.launch {
            combine(adConsent.state, premiumState) { consent, premium ->
                com.example.privacy.canRequestAdvertisements(consent.canRequestAds, premium)
            }.distinctUntilChanged().collect(adManager::setAllowed)
        }
        scope.launch {
            ready.await()
            authRepository.authState.map { (it as? AuthUiState.SignedIn)?.account?.uid }
                .distinctUntilChanged().collect { refreshAccount() }
        }
        scope.launch {
            ready.await()
            profiles.pendingCount.collect { count ->
                signedInUid()?.takeIf { count > 0 }?.let(scheduler::schedule)
            }
        }
    }

    fun retryStartup() {
        scope.launch {
            startupMutex.withLock {
                if (ready.isCompleted) return@withLock
                startup.value = StartupState.Loading
                try {
                    withContext(Dispatchers.IO) {
                        accountDeletion.restoreIntent()
                        identity.id
                        wordRepository.initializeCatalog()
                        profiles.initialize()
                    }
                    refreshAccount()
                    startup.value = StartupState.Ready
                    ready.complete(Unit)
                    accountDeletion.recover()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    telemetry.recordException(error)
                    startup.value = StartupState.Error("Не вдалося завантажити словник. Спробуйте ще раз.")
                }
            }
        }
    }

    suspend fun refreshAccount() = accountMutex.withLock {
        val uid = signedInUid()
        if (accountSelected && selectedUid == uid) return@withLock
        selectedUid?.let(scheduler::cancel)
        syncRepository.onAccountChanged(null)
        val previousProfile = profiles.activeProfile()
        profiles.activate(uid)
        val activeProfile = profiles.activeProfile()
        // Process startup also selects Auth's account. It is not necessarily an account change.
        // Preserve a posted reminder and its cooldown when reopening the same persisted profile.
        if (previousProfile.id != activeProfile.id ||
            previousProfile.generation != activeProfile.generation ||
            previousProfile.writerId != activeProfile.writerId) {
            clearReminders()
        }
        selectedUid = uid
        accountSelected = true
        syncRepository.onAccountChanged(uid)
        withContext(Dispatchers.Main.immediate) { billingManager.refresh() }
        uid?.let(scheduler::schedule)
    }
}
