package com.example.sync

import com.example.data.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProgressSyncModelsTest {
    @Test
    fun cloudKey_isStableAndIncludesStressPosition() {
        val first = word(normalized = "вигода", stressPositions = "1")
        val same = word(normalized = "вигода", stressPositions = "1")
        val differentStress = word(normalized = "вигода", stressPositions = "3")

        assertEquals(first.cloudKey(), same.cloudKey())
        assertNotEquals(first.cloudKey(), differentStress.cloudKey())
        assertEquals(67, first.cloudKey().length)
    }

    @Test
    fun mergeProgress_keepsNewerSchedulingState() {
        val local = progress(revision = 4, updatedAt = 400, box = 4, deviceId = "local")
        val cloud = progress(revision = 3, updatedAt = 900, box = 3, deviceId = "cloud")

        val merged = mergeProgress(local, cloud, now = 1_000, localDeviceId = "local")

        assertEquals(4, merged.box)
        assertEquals(4, merged.revision)
        assertEquals("local", merged.deviceId)
    }

    @Test
    fun mergeProgress_neverReducesCounters() {
        val local = progress(
            revision = 4,
            updatedAt = 400,
            correctCount = 5,
            wrongCount = 1,
            lapseCount = 1,
            deviceId = "local"
        )
        val cloud = progress(
            revision = 5,
            updatedAt = 500,
            correctCount = 3,
            wrongCount = 4,
            lapseCount = 4,
            deviceId = "cloud"
        )

        val merged = mergeProgress(local, cloud, now = 1_000, localDeviceId = "local")

        assertEquals(5, merged.correctCount)
        assertEquals(4, merged.wrongCount)
        assertEquals(4, merged.lapseCount)
        assertEquals(5, merged.revision)
        assertEquals(500L, merged.updatedAt)
    }

    private fun word(normalized: String, stressPositions: String) = Word(
        stressed = normalized,
        plain = normalized,
        normalized = normalized,
        stressPositions = stressPositions,
        letter = "В",
        note = null,
        source = "test"
    )

    private fun progress(
        revision: Long,
        updatedAt: Long,
        box: Int = 2,
        correctCount: Int = 2,
        wrongCount: Int = 1,
        lapseCount: Int = 1,
        deviceId: String
    ) = CloudWordProgress(
        wordKey = "v1_key",
        box = box,
        status = "learning",
        dueAt = 2_000,
        correctCount = correctCount,
        wrongCount = wrongCount,
        lapseCount = lapseCount,
        lastReviewedAt = updatedAt,
        updatedAt = updatedAt,
        revision = revision,
        deviceId = deviceId
    )
}
