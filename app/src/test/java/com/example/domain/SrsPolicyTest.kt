package com.example.domain

import com.example.data.WordState
import org.junit.Assert.*
import org.junit.Test

class SrsPolicyTest {
    @Test fun earlyAnswerPreservesScheduleAndMistakeResetsIt() {
        val first = SrsPolicy.answer(null, 1, true, 1_000, "a", "user")
        val early = SrsPolicy.answer(first, 1, true, 2_000, "a", "user")
        assertEquals(1, early.box)
        assertEquals(first.dueAt, early.dueAt)
        assertEquals(2, early.correctCount)
        val mistaken = SrsPolicy.answer(early.copy(box = 5), 1, false, 3_000, "a", "user")
        assertEquals(1, mistaken.box)
        assertEquals(3_000 + requireNotNull(SrsPolicy.intervals[1]), mistaken.dueAt)
        assertEquals(1, mistaken.lapseCount)
    }
    @Test fun allStagesAdvanceAtDueTimeAndFinalSuccessCompletesLearning() {
        var state = SrsPolicy.answer(null, 1, true, 1_000, "a", "user")
        for (box in 2..7) {
            state = SrsPolicy.answer(state, 1, true, requireNotNull(state.dueAt), "a", "user")
            assertEquals(box, state.box)
            assertEquals("learning", state.status)
        }
        val finished = SrsPolicy.answer(state, 1, true, requireNotNull(state.dueAt), "a", "user")
        assertEquals("learned", finished.status)
        assertNull(finished.dueAt)
        assertEquals(8, finished.correctCount)
    }
    @Test fun legacyCountsArePreservedWithoutBeingAssignedToEveryNewDevice() {
        val old = WordState(wordId = 1, dueAt = 0, correctCount = 5, wrongCount = 2, lapseCount = 2)
        val onA = SrsPolicy.answer(old, 1, true, 1_000, "a", "user")
        val onB = SrsPolicy.answer(old, 1, true, 1_000, "b", "user")
        val merged = ProgressCounters.merge(
            ProgressCounters.decode(onA.countersJson, 0, 0, 0), ProgressCounters.decode(onB.countersJson, 0, 0, 0))
        assertEquals(7, merged.values.sumOf { it.correct })
        assertEquals(2, merged.values.sumOf { it.wrong })
    }
}
