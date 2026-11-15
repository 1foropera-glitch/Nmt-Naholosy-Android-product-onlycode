package com.example.data

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.util.UUID

@JsonClass(generateAdapter = true)
data class SessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val mode: String,
    val wordIds: List<Int>,
    val currentIndex: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val selectedAnswer: String? = null,
    val isCorrectAnswer: Boolean? = null,
    val isEarlyReview: Boolean = false
) {
    companion object {
        val adapter = Moshi.Builder().build().adapter(SessionRecord::class.java)
        fun fromLegacy(old: PersistedSession): SessionRecord {
            val index = old.currentIndex.coerceIn(0, old.wordIds.size)
            val correct = old.correctCount.coerceIn(0, old.wordIds.size)
            return SessionRecord(mode = old.mode, wordIds = old.wordIds, currentIndex = index,
                correctCount = correct, wrongCount = old.wrongCount.coerceIn(0, old.wordIds.size - correct),
                selectedAnswer = old.selectedAnswer.takeIf { index < old.wordIds.size },
                isCorrectAnswer = old.isCorrectAnswer.takeIf { index < old.wordIds.size }, isEarlyReview = old.isEarlyReview)
        }
    }
}
