package com.example.sync

import com.example.domain.DeviceCounter
import org.junit.Assert.*
import org.junit.Test

class SparkProgressMergeTest {
    private fun value(c: Map<String, DeviceCounter>, revision: Long = 1) = CloudWordProgress("word", 1, "learning", 1000,
        c.values.sumOf { it.correct }, c.values.sumOf { it.wrong }, c.values.sumOf { it.lapses }, 100, 100, revision, "a", c)

    @Test fun partialUploadPreservesOutboxUntilEveryWriterHasArrived() {
        val local = value(mapOf("a" to DeviceCounter(2), "b" to DeviceCounter(3)))
        val first = nextSparkProgressWrite(local, null)
        assertEquals(1, first.progress.counters.size)
        assertNotEquals(first.progress, mergeProgress(local, first.progress))
        val second = nextSparkProgressWrite(local, first.progress)
        assertEquals(local, second.progress)
        assertEquals(local, nextSparkProgressWrite(local, second.progress).progress)
    }
    @Test fun concurrentDeviceAndRetryDoNotLoseOrDuplicateCounters() {
        val local = value(mapOf("a" to DeviceCounter(4)))
        val remote = value(mapOf("a" to DeviceCounter(2), "b" to DeviceCounter(5)), 3)
        val merged = nextSparkProgressWrite(local, remote).progress
        assertEquals(9, merged.correctCount)
        assertEquals(3L, merged.revision)
        assertEquals(merged, nextSparkProgressWrite(local, merged).progress)
    }
    @Test fun emptyCountersDoNotInventAContribution() {
        val empty = value(emptyMap())
        assertEquals(empty, nextSparkProgressWrite(empty, null).progress)
    }
}
