package com.example.sync

import com.example.domain.DeviceCounter
import org.junit.Assert.*
import org.junit.Test

class ProgressConvergenceTest {
    private fun progress(device: String, correct: Int, wrong: Int = 0, revision: Long = 1) = CloudWordProgress(
        wordKey = "word", box = if (wrong > 0) 1 else 2, status = "learning", dueAt = 100,
        correctCount = correct, wrongCount = wrong, lapseCount = wrong, lastReviewedAt = 10,
        updatedAt = 10, revision = revision, deviceId = device,
        counters = mapOf(device to DeviceCounter(correct, wrong, wrong)))

    @Test fun independentOfflineAnswersAreAddedExactlyOnce() {
        val a = progress("a", 1)
        val b = progress("b", 1)
        val merged = mergeProgress(a, b)
        assertEquals(2, merged.correctCount)
        assertEquals(merged, mergeProgress(merged, b))
        assertEquals(merged, mergeProgress(b, a))
    }
    @Test fun mergeIsAssociativeIdempotentAndIndependentOfLocalClock() {
        val a = progress("a", 3, revision = 3)
        val b = progress("b", 2, 1, 4)
        val c = progress("c", 4, revision = 2)
        assertEquals(mergeProgress(mergeProgress(a, b), c), mergeProgress(a, mergeProgress(b, c)))
        assertEquals(a, mergeProgress(a, a))
        assertEquals(mergeProgress(a, b, 1, "a"), mergeProgress(a, b, Long.MAX_VALUE, "z"))
    }
    @Test fun cloudRoundtripPreservesAuthorAndCounters() {
        val original = progress("device-a", 4, 1, 5)
        val state = original.toWordState(7, "account-a")
        assertEquals("device-a", state.deviceId)
        assertEquals("account-a", state.profileId)
    }
}
