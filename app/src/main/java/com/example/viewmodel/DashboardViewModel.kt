package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.util.isDifficultWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(private val app: AppContainer, private val navigator: AppNavigator,
                         private val session: SessionViewModel, settings: SettingsViewModel) : ViewModel(), AutoCloseable {
    val activeSession = session.activeSession
    val reduceMotion = settings.reduceMotion
    val reminderPrompt = settings.reminderPrompt
    val pendingReset = settings.pendingReset
    fun retryReset() = app.signedInUid()?.let { uid ->
        app.scheduler.schedule(uid)
        viewModelScope.launch { app.syncRepository.syncNow(uid) }
    }
    val allWordStates = app.wordRepository.allWordStates.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val totalWordsCount = app.wordRepository.allWords.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val difficultWords = combine(app.wordRepository.allWords, allWordStates) { words, states ->
        val byId = words.associateBy { it.id }
        states.filter(::isDifficultWord).sortedWith(compareByDescending<com.example.data.WordState> { it.lapseCount }.thenBy { it.box })
            .mapNotNull { state -> byId[state.wordId]?.let { it to state } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val clock = MutableStateFlow(System.currentTimeMillis())
    init { viewModelScope.launch { while (true) { delay(30_000); refreshNow() } } }
    fun refreshNow() { clock.value = System.currentTimeMillis() }
    val dueReviewsCount = combine(allWordStates, clock) { states, now ->
        states.count { it.status == "learning" && it.dueAt?.let { due -> due <= now } == true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val srsStats = allWordStates.map { states ->
        (1..7).associate { box -> "box_$box" to states.count { it.status == "learning" && it.box == box } } +
            ("learned" to states.count { it.status == "learned" })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    fun navigateTo(screen: Screen) = navigator.navigate(screen)
    fun resumeSession() = session.resumeSession()
    fun startReview() = session.startReview()
    fun startDifficultPractice() = session.startDifficultPractice()
    fun clearAllProgress() {
        viewModelScope.launch {
            try {
                val profile = app.profiles.requestReset(app.signedInUid() ?: "guest")
                app.clearReminders()
                if (profile.id != "guest") {
                    app.scheduler.schedule(profile.id)
                    app.syncRepository.syncNow(profile.id)
                }
                app.telemetry.progressReset()
            } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { app.telemetry.recordException(error); session.message.value = "Не вдалося скинути прогрес. Спробуйте ще раз." }
        }
    }
    override fun close() { viewModelScope.cancel() }
}
