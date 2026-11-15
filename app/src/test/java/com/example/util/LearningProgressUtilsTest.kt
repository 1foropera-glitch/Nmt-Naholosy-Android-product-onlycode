package com.example.util

import com.example.data.WordState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class LearningProgressUtilsTest {
    @Test
    fun `nmt year changes on first of august`() {
        assertEquals(2026, targetNmtYear(2026, Calendar.JULY))
        assertEquals(2027, targetNmtYear(2026, Calendar.AUGUST))
    }

    @Test
    fun `difficult word requires four lapses and low box`() {
        assertFalse(isDifficultWord(state(lapses = 3, box = 1)))
        assertTrue(isDifficultWord(state(lapses = 4, box = 1)))
        assertFalse(isDifficultWord(state(lapses = 4, box = 4)))
    }

    @Test
    fun `countdown uses ukrainian time forms`() {
        val now = 1_000L
        assertEquals("Повторити через 1 хвилину", formatReviewCountdown(now + 60_000L, now))
        assertEquals("Повторити через 2 години", formatReviewCountdown(now + 2 * 3_600_000L, now))
        assertEquals("Повторити через 1 день", formatReviewCountdown(now + 86_400_000L, now))
        assertEquals("Повторити через 5 днів", formatReviewCountdown(now + 5 * 86_400_000L, now))
        assertEquals("Готово до повторення", formatReviewCountdown(now, now))
    }

    @Test
    fun `stage progress counts only later boxes and learned words`() {
        val states = listOf(
            state(lapses = 0, box = 1),
            state(lapses = 0, box = 2).copy(wordId = 2),
            state(lapses = 0, box = 4).copy(wordId = 3),
            state(lapses = 0, box = 7).copy(wordId = 4, status = "learned", dueAt = null)
        )

        assertEquals(3, stagePassedCount(states, stageBox = 1))
        assertEquals(2, stagePassedCount(states, stageBox = 3))
        assertEquals(1, stagePassedCount(states, stageBox = 7))
    }

    @Test
    fun `difficult practice never updates srs`() {
        assertFalse(sessionUpdatesSrs("difficult"))
        assertTrue(sessionUpdatesSrs("practice"))
        assertTrue(sessionUpdatesSrs("review"))
    }

    @Test
    fun `attention label agrees with word count`() {
        assertEquals("1 слово потребує додаткової уваги", wordsNeedAttentionLabel(1))
        assertEquals("2 слова потребують додаткової уваги", wordsNeedAttentionLabel(2))
        assertEquals("11 слів потребують додаткової уваги", wordsNeedAttentionLabel(11))
    }

    private fun state(lapses: Int, box: Int) = WordState(
        wordId = 1,
        box = box,
        dueAt = 2_000L,
        status = "learning",
        correctCount = 0,
        wrongCount = lapses,
        lapseCount = lapses,
        lastReviewedAt = 1_000L,
        updatedAt = 1_000L,
        revision = 1
    )
}
