package com.example.sync

import com.example.domain.ProgressCounters

data class SparkProgressWrite(val progress: CloudWordProgress, val counterWriter: String)

/** One changed counter per document lets Firestore Rules validate the full delta without loops. */
fun nextSparkProgressWrite(local: CloudWordProgress, remote: CloudWordProgress?): SparkProgressWrite {
    val merged = mergeProgress(local, remote)
    val previous = remote?.effectiveCounters().orEmpty()
    val desired = merged.effectiveCounters()
    if (desired.size > 512) throw ProgressUploadRejected()
    val writer = desired.keys.sorted().firstOrNull { desired[it] != previous[it] }
    val counters = if (writer == null) previous else previous + (writer to requireNotNull(desired[writer]))
    return SparkProgressWrite(merged.copy(
        correctCount = ProgressCounters.total(counters.values.map { it.correct }),
        wrongCount = ProgressCounters.total(counters.values.map { it.wrong }),
        lapseCount = ProgressCounters.total(counters.values.map { it.lapses }), counters = counters), writer ?: "unchanged")
}
