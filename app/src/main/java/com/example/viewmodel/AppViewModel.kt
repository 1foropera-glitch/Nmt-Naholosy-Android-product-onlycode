package com.example.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.AppContainer
import com.example.StartupState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Retained owner of feature ViewModels and navigation. Business rules live in the features/data layer. */
class AppViewModel(private val app: AppContainer, savedState: SavedStateHandle) : ViewModel() {
    private val navigator = AppNavigator(savedState)
    val settings = SettingsViewModel(app)
    val session = SessionViewModel(app, navigator, settings)
    val dashboard = DashboardViewModel(app, navigator, session, settings)
    val dictionary = DictionaryViewModel(app.wordRepository)
    val currentScreen = navigator.screen
    val startup = app.startup.asStateFlow()
    val themeMode = settings.themeMode
    val reduceMotion = settings.reduceMotion
    val hasSeenOnboarding = settings.hasSeenOnboarding
    val authState = settings.authState
    val message = combine(session.message, settings.message) { a, b -> a ?: b }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            currentScreen.collect { app.telemetry.screenViewed(it.javaClass.simpleName) }
        }
        viewModelScope.launch {
            app.ready.await()
            var previous = app.profiles.activeProfile().id
            app.profiles.activeId.filterNotNull().collect { id ->
                if (id != previous) { navigator.home(); previous = id }
            }
        }
    }
    fun retryStartup() = app.retryStartup()
    fun dismissMessage() { session.message.value = null; settings.message.value = null }
    fun navigateTo(screen: Screen) { navigator.navigate(screen) }
    fun back() = navigator.back()
    fun closeDifficultWords() = navigator.back()
    fun setOnboardingSeen() = settings.setOnboardingSeen()
    fun startReview() = session.startReview()
    fun resumeSession() = session.resumeSession()
    fun finishSession() = session.finishSession()
    fun beginGoogleSignIn() = app.authRepository.beginGoogleSignIn()
    fun cancelGoogleSignIn() = app.authRepository.cancelGoogleSignIn()
    fun reportGoogleSignInError(message: String) = app.authRepository.reportGoogleSignInError(message)
    suspend fun signInWithGoogleIdToken(token: String) = app.authRepository.signInWithGoogleIdToken(token)
    fun signOut() = app.authRepository.signOut()
    override fun onCleared() {
        dictionary.close(); dashboard.close(); session.close(); settings.close()
        super.onCleared()
    }
}

class AppViewModelFactory(private val app: AppContainer) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(AppViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return AppViewModel(app, extras.createSavedStateHandle()) as T
    }
}
