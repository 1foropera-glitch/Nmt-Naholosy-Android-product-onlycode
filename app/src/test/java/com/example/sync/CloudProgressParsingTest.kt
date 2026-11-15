package com.example.sync

import com.example.domain.DeviceCounter
import org.junit.Assert.*
import org.junit.Test

class CloudProgressParsingTest {
    private val progress = CloudWordProgress("word", 1, "learning", 1000L,
        2, 1, 0, 100L, 100L, 3, "device", mapOf("device" to DeviceCounter(2, 1, 0)))

    @Test fun validProgressRoundTripsWithoutLosingCounters() {
        assertEquals(progress, parseCloudWordProgress("word", progress.toFirestoreMap()))
    }

    @Test fun malformedDocumentDoesNotHideValidNeighbours() {
        val valid = progress.toFirestoreMap()
        val batch = parseCloudProgressBatch(listOf("word" to valid,
            "bad" to (valid + ("counters" to mapOf("device" to "invalid"))),
            "bad-version" to (valid + ("schemaVersion" to 7L))))
        assertEquals(listOf(progress), batch.valid)
        assertEquals(2, batch.rejectedCount)
    }

    @Test fun rejectsFractionalNegativeMismatchedAndNonMapCounters() {
        val invalid = listOf(
            mapOf("device" to mapOf("correct" to 2.5, "wrong" to 1, "lapses" to 0)),
            mapOf("device" to mapOf("correct" to -1, "wrong" to 1, "lapses" to 0)),
            mapOf("device" to mapOf("correct" to 99, "wrong" to 1, "lapses" to 0)),
            "invalid", null)
        invalid.forEach { counters ->
            assertThrows(IllegalArgumentException::class.java) {
                parseCloudWordProgress("word", progress.toFirestoreMap() + ("counters" to counters))
            }
        }
    }

    @Test fun legacyProgressWithoutCountersRemainsReadable() {
        val legacy = progress.toFirestoreMap() - "counters" + ("schemaVersion" to 1L)
        val parsed = parseCloudWordProgress("word", legacy)
        assertEquals(2, parsed.correctCount)
        assertEquals(2, parsed.counters.values.sumOf { it.correct })
    }
}
