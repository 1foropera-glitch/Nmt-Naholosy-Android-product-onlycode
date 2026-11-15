package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.util.isDifficultWord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SessionViewModel(private val app: AppContainer, private val navigator: AppNavigator,
                       private val settings: SettingsViewModel) : ViewModel(), AutoCloseable {
    val reduceMotion = settings.reduceMotion
    val soundEnabled = settings.soundEnabled
    val hapticsEnabled = settings.hapticsEnabled
    val isPremium = settings.isPremium
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val activeSession = combine(app.sessions.session, app.database.wordDao().observeCatalogIncludingRetired(), app.profiles.activeId) { stored, words, profile ->
        val record = stored?.second
        if (record == null || profile == null || stored?.first != profile) null else {
            val byId = words.associateBy { it.id }
            // Preserve positions if a word was retired during an upgrade.
            val restored = record.wordIds.mapNotNull(byId::get)
            if (restored.size != record.wordIds.size) null else ActiveSession(mode = record.mode, words = restored,
                currentIndex = record.currentIndex, correctCount = record.correctCount, wrongCount = record.wrongCount,
                selectedAnswer = record.selectedAnswer, isCorrectAnswer = record.isCorrectAnswer,
                isEarlyReview = record.isEarlyReview, id = record.id, profileId = profile)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun command(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { app.ready.await(); action() } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) { app.telemetry.recordException(error); message.value = error.message ?: "Не вдалося зберегти відповідь." } finally { busy.value = false }
        }
    }
    fun navigateTo(screen: Screen) = navigator.navigate(screen)
    fun startPractice(amount: Int) = start("practice", amount)
    fun startReview() = start("review", 36)
    fun startDifficultPractice() = start("difficult", 12)
    private fun start(mode: String, amount: Int) = command {
        activeSession.value?.let { resumeSession(); return@command }
        val profile = app.profiles.activeProfile().id
        var early = false
        val words = when (mode) {
            "review" -> app.wordRepository.getDueWords(amount).ifEmpty {
                early = true; app.wordRepository.getAllLearningWords(amount)
            }
            "difficult" -> {
                val ids = app.wordRepository.getAllWordStatesSnapshot().filter(::isDifficultWord).map { it.wordId }.toSet()
                app.wordRepository.getAllWordsSnapshot().filter { it.id in ids }.take(amount)
            }
            else -> app.wordRepository.getNewRandomWords(amount).ifEmpty {
                app.wordRepository.getAllWordsSnapshot().shuffled().take(amount)
            }
        }
        if (app.profiles.activeProfile().id != profile) return@command
        val record = app.sessions.start(profile, mode, words, early) ?: run { message.value = "Немає слів для цього тренування."; return@command }
        when (record.mode) {
            "review" -> { app.telemetry.reviewStarted(record.wordIds.size, record.isEarlyReview); navigateTo(Screen.ReviewSession) }
            "difficult" -> navigateTo(Screen.DifficultSession)
            else -> { app.telemetry.practiceStarted(record.wordIds.size); navigateTo(Screen.PracticeSession(record.wordIds.size)) }
        }
    }
    fun submitAnswer(answer: String) {
        val current = activeSession.value ?: return
        command {
            if (app.sessions.answer(current.profileId, current.id, current.currentIndex, answer)) {
                app.signedInUid()?.let(app.scheduler::schedule)
            }
        }
    }
    fun nextQuestion() {
        val current = activeSession.value ?: return
        command { app.sessions.next(current.profileId, current.id, current.currentIndex) }
    }
    fun pauseSession() = navigator.home()
    fun finishSession() = discard(true)
    fun discardSession() = discard(false)
    private fun discard(completed: Boolean) {
        val current = activeSession.value ?: return
        command {
            app.sessions.discard(current.profileId, current.id)
            if (completed && current.isFinished) app.telemetry.sessionCompleted(current.mode, current.correctCount, current.words.size)
            navigator.home()
        }
    }
    fun resumeSession() {
        val current = activeSession.value ?: return
        navigateTo(when (current.mode) {
            "review" -> Screen.ReviewSession
            "difficult" -> Screen.DifficultSession
            else -> Screen.PracticeSession(current.words.size)
        })
    }
    override fun close() { viewModelScope.cancel() }
}
