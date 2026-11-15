package com.example.viewmodel

import com.example.data.Word

sealed class Screen {
    object Onboarding : Screen()
    object Dashboard : Screen()
    object Dictionary : Screen()
    object PracticeSetup : Screen()
    data class PracticeSession(val amount: Int) : Screen()
    object ReviewSession : Screen()
    object ProgressStats : Screen()
    object DifficultWords : Screen()
    object DifficultSession : Screen()
    object Settings : Screen()
}

data class ActiveSession(
    val mode: String, // "practice" or "review"
    val words: List<Word>,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val selectedAnswer: String? = null,
    val isCorrectAnswer: Boolean? = null,
    val isEarlyReview: Boolean = false,
    val id: String = "",
    val profileId: String = "guest"
) {
    val currentWord: Word? = words.getOrNull(currentIndex)
    val isFinished: Boolean = currentIndex >= words.size && words.isNotEmpty()
}

