package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: AppContainer) : ViewModel(), AutoCloseable {
    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
    val themeMode = app.preferences.themeMode.state(ThemeMode.SYSTEM)
    val reduceMotion = app.preferences.reduceMotion.state(false)
    val soundEnabled = app.preferences.soundEnabled.state(true)
    val hapticsEnabled = app.preferences.hapticsEnabled.state(true)
    val reviewRemindersEnabled = app.preferences.reviewRemindersEnabled.state(false)
    val isPremium = app.premiumState.map { it == true }.state(false)
    val diagnosticsEnabled = app.preferences.diagnosticsEnabled.state(false)
    val adConsentState = app.adConsent.state
    val hasSeenOnboarding = app.preferences.hasSeenOnboarding.state(false)
    val reminderPrompt = combine(app.preferences.hasSeenOnboarding,
        app.preferences.hasRequestedNotificationPermission, app.preferences.reviewRemindersEnabled
    ) { seen, handled, enabled -> com.example.reminders.ReminderPromptState(seen, handled, enabled) }
        .state(com.example.reminders.ReminderPromptState())
    val authState = app.authRepository.authState
    val syncState = app.syncRepository.state
    val pendingCount = app.profiles.pendingCount.state(0)
    val pendingReset = app.profiles.pendingReset.map { it != null }.state(false)
    val quietHoursEnabled = app.preferences.quietHoursEnabled.state(true)
    val billingState = app.billingManager.state
    val message = MutableStateFlow<String?>(null)
    val accountDeletionState = app.accountDeletion.state
    val pendingAccountDeletion = app.accountDeletion.pending
    suspend fun deleteWithGoogleToken(token: String) = app.accountDeletion.submit(token)
    fun reportDeletionMessage(text: String) = app.accountDeletion.report(text)
    fun checkAccountDeletion() = app.accountDeletion.recover()
    init {
        viewModelScope.launch {
            app.accountDeletion.state.map { it.message }.filterNotNull().distinctUntilChanged().collect { message.value = it }
        }
    }

    private fun update(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { app.telemetry.recordException(error); message.value = "Не вдалося зберегти зміни. Спробуйте ще раз." }
        }
    }
    fun setThemeMode(mode: ThemeMode) = update { app.preferences.setThemeMode(mode) }
    fun setReduceMotion(value: Boolean) = update { app.preferences.setReduceMotion(value) }
    fun setSoundEnabled(value: Boolean) = update { app.preferences.setSoundEnabled(value) }
    fun setHapticsEnabled(value: Boolean) = update { app.preferences.setHapticsEnabled(value) }
    fun setDiagnosticsEnabled(value: Boolean) = update { app.preferences.setDiagnosticsEnabled(value) }
    fun setQuietHoursEnabled(value: Boolean) = update { app.preferences.setQuietHoursEnabled(value) }
    fun restorePurchases() = app.billingManager.restorePurchases()
    fun setOnboardingSeen() = update { app.preferences.setOnboardingSeen() }
    fun syncProgressNow() = update {
        app.ready.await()
        app.refreshAccount()
        app.signedInUid()?.let { uid -> app.scheduler.schedule(uid); app.syncRepository.syncNow(uid) }
    }
    override fun close() { viewModelScope.cancel() }
}
