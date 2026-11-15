package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.example.ads.AdManager
import com.example.auth.AuthUiState
import com.example.auth.GoogleSignInCoordinator
import com.example.auth.GoogleSignInResult
import com.example.billing.BillingManager
import com.example.data.AppPreferences
import com.example.data.ThemeMode
import com.example.reminders.ReviewReminderNotifications
import com.example.reminders.ReviewReminderScheduler
import com.example.telemetry.FirebaseTelemetry
import com.example.ui.MainAppContent
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.AppViewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {

    private var notificationRequestInFlight = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationRequestInFlight = false
        lifecycleScope.launch {
            if (granted && preferences.isReviewRemindersEnabled()) {
                activateReviewReminders(resetSnapshot = true)
            } else {
                ReviewReminderScheduler.cancel(this@MainActivity)
                refreshReviewNotificationAvailability()
            }
        }
    }

    private val container get() = (application as NaholosyApplication).container
    private val preferences get() = container.preferences
    private val telemetry get() = container.telemetry
    private val googleSignInCoordinator by lazy { GoogleSignInCoordinator(applicationContext) }
    val adManager get() = container.adManager
    val billingManager get() = container.billingManager

    var canPostReviewReminders by mutableStateOf(false)
        private set

    private val viewModel: AppViewModel by viewModels {
        AppViewModelFactory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationRequestInFlight = savedInstanceState?.getBoolean("notification_request_in_flight") ?: false
        enableEdgeToEdge()
        telemetry.appOpened()

        // Initialize ad and billing SDKs
        container.adConsent.gather(this)
        billingManager.initialize()
        ReviewReminderNotifications.createChannel(this)
        refreshReviewNotificationAvailability()
        lifecycleScope.launch {
            if (preferences.isReviewRemindersEnabled() && canPostReviewReminders) {
                ReviewReminderScheduler.ensureScheduled(this@MainActivity)
            } else {
                ReviewReminderScheduler.cancel(this@MainActivity)
            }
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val isDarkTheme = themeMode.resolve(isSystemInDarkTheme())

            // App theme can differ from the device theme. Update both bars together.
            androidx.compose.runtime.SideEffect {
                val style = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    // API 24–25 cannot display dark navigation icons on a light background.
                    0x801B1B1B.toInt()
                ) { isDarkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
            }
            
            MyApplicationTheme(darkTheme = isDarkTheme) {
                MainAppContent(viewModel = viewModel, activity = this)
            }
        }

        handleLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        billingManager.refresh()
        viewModel.dashboard.refreshNow()
        lifecycleScope.launch {
            container.ready.await()
            container.signedInUid()?.let(container.scheduler::schedule)
        }
        val couldPostBeforeResume = canPostReviewReminders
        refreshReviewNotificationAvailability()

        if (!couldPostBeforeResume && canPostReviewReminders) {
            lifecycleScope.launch {
                if (preferences.isReviewRemindersEnabled()) {
                    ReviewReminderScheduler.ensureScheduled(this@MainActivity)
                    ReviewReminderScheduler.enqueueImmediateCheck(this@MainActivity)
                }
            }
        } else if (!canPostReviewReminders) {
            ReviewReminderScheduler.cancel(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("notification_request_in_flight", notificationRequestInFlight)
        super.onSaveInstanceState(outState)
    }

    fun startGoogleSignIn() {
        when (viewModel.authState.value) {
            AuthUiState.SigningIn, is AuthUiState.SignedIn -> return
            AuthUiState.SignedOut, is AuthUiState.Error -> Unit
        }

        val serverClientId = getGeneratedWebClientId()
        if (serverClientId == null) {
            viewModel.reportGoogleSignInError(
                "У Firebase-конфігурації немає Web OAuth client. Оновіть google-services.json."
            )
            return
        }

        viewModel.beginGoogleSignIn()
        lifecycleScope.launch {
            try {
                when (
                    val result = googleSignInCoordinator.requestGoogleIdToken(
                        activity = this@MainActivity,
                        serverClientId = serverClientId
                    )
                ) {
                    is GoogleSignInResult.Success ->
                        viewModel.signInWithGoogleIdToken(result.idToken)
                    GoogleSignInResult.Cancelled ->
                        viewModel.cancelGoogleSignIn()
                    is GoogleSignInResult.Failure ->
                        viewModel.reportGoogleSignInError(result.message)
                }
            } catch (exception: CancellationException) {
                viewModel.cancelGoogleSignIn()
                throw exception
            } catch (exception: Exception) {
                telemetry.recordException(exception)
                viewModel.reportGoogleSignInError(
                    "Не вдалося завершити вхід Google. Спробуйте ще раз."
                )
            }
        }
    }

    fun signOutFromGoogle() {
        viewModel.signOut()
        lifecycleScope.launch {
            try {
                googleSignInCoordinator.clearCredentialState()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // Firebase is already signed out. Record only the provider-state cleanup failure.
                telemetry.recordException(exception)
            }
        }
    }

    fun showAdPrivacyOptions() = container.adConsent.showPrivacyOptions(this)
    private var deletionConfirmationInFlight = false
    fun confirmAccountDeletion() {
        if (deletionConfirmationInFlight || container.accountDeletion.state.value.busy) return
        val clientId = getGeneratedWebClientId()
        if (clientId == null) {
            viewModel.settings.reportDeletionMessage("Видалення недоступне: відсутня Google-конфігурація.")
            return
        }
        deletionConfirmationInFlight = true
        container.scope.launch {
            try {
                when (val result = googleSignInCoordinator.requestGoogleIdToken(this@MainActivity, clientId)) {
                    is GoogleSignInResult.Success -> {
                        viewModel.settings.deleteWithGoogleToken(result.idToken)
                        if (container.accountDeletion.pending.value == null && viewModel.authState.value is AuthUiState.SignedOut)
                            googleSignInCoordinator.clearCredentialState()
                    }
                    GoogleSignInResult.Cancelled -> viewModel.settings.reportDeletionMessage("Google-вхід скасовано. Раніше прийнятий запит на видалення це не скасовує.")
                    is GoogleSignInResult.Failure -> viewModel.settings.reportDeletionMessage(result.message)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { viewModel.settings.reportDeletionMessage("Не вдалося підтвердити видалення. Спробуйте ще раз.") }
            finally { deletionConfirmationInFlight = false }
        }
    }
    fun retryAdConsent() = container.adConsent.gather(this)

    fun setReviewRemindersEnabled(enabled: Boolean) {
        lifecycleScope.launch {
            updateReviewReminders(enabled)
        }
    }

    fun requestReviewReminderPermissionAfterOnboarding() {
        lifecycleScope.launch {
            if (preferences.hasRequestedNotificationPermission()) return@launch
            updateReviewReminders(enabled = true)
        }
    }

    fun dismissReviewReminderPrimer() {
        setReviewRemindersEnabled(false)
    }

    fun openReviewReminderSettings() {
        ReviewReminderNotifications.openSystemSettings(this)
    }

    fun openReviewReminderBackgroundSettings() {
        ReviewReminderNotifications.openBackgroundSettings(this)
    }

    fun startReviewSession() {
        ReviewReminderNotifications.cancel(this)
        viewModel.startReview()
    }

    fun resumeLearningSession() {
        ReviewReminderNotifications.cancel(this)
        viewModel.resumeSession()
    }

    fun finishLearningSession() {
        ReviewReminderNotifications.cancel(this)
        viewModel.finishSession()
        if (canPostReviewReminders) {
            ReviewReminderScheduler.enqueueImmediateCheck(this)
        }
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.action != ReviewReminderNotifications.ACTION_OPEN_REVIEW) return
        intent.action = null
        startReviewSession()
    }

    private suspend fun activateReviewReminders(resetSnapshot: Boolean) {
        if (!preferences.isReviewRemindersEnabled()) return
        if (resetSnapshot) preferences.clearReviewReminderSnapshot()
        ReviewReminderNotifications.createChannel(this)
        refreshReviewNotificationAvailability()
        if (!canPostReviewReminders) return

        ReviewReminderScheduler.ensureScheduled(this)
        ReviewReminderScheduler.enqueueImmediateCheck(this)
    }

    private suspend fun updateReviewReminders(enabled: Boolean) {
        preferences.markNotificationPermissionRequested()
        preferences.setReviewRemindersEnabled(enabled)

        if (!enabled) {
            ReviewReminderScheduler.cancel(this)
            ReviewReminderNotifications.cancel(this)
            preferences.clearReviewReminderSnapshot()
            refreshReviewNotificationAvailability()
            return
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            lifecycle.withResumed {
                if (!notificationRequestInFlight) {
                    notificationRequestInFlight = true
                    try {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } catch (error: Exception) {
                        notificationRequestInFlight = false
                        throw error
                    }
                }
            }
        } else if (ReviewReminderNotifications.canPost(this)) {
            activateReviewReminders(resetSnapshot = true)
        } else {
            ReviewReminderScheduler.cancel(this)
            refreshReviewNotificationAvailability()
            openReviewReminderSettings()
        }
    }

    private fun refreshReviewNotificationAvailability() {
        canPostReviewReminders = ReviewReminderNotifications.canPost(this)
    }

    private fun getGeneratedWebClientId(): String? {
        val resourceId = resources.getIdentifier(
            "default_web_client_id",
            "string",
            packageName
        )
        return resourceId
            .takeIf { it != 0 }
            ?.let(resources::getString)
            ?.takeIf(String::isNotBlank)
    }
}

private fun ThemeMode.resolve(systemIsDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemIsDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
