package com.example.sync

import com.example.data.Word
import com.example.data.WordState
import com.example.domain.DeviceCounter
import com.example.domain.ProgressCounters
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

sealed interface ProgressSyncState {
    data object SignedOut : ProgressSyncState
    data object Idle : ProgressSyncState
    data object Syncing : ProgressSyncState
    data class Synced(val syncedAt: Long, val wordCount: Int) : ProgressSyncState
    data class Error(val message: String, val retryable: Boolean = true) : ProgressSyncState
    data object AccountMismatch : ProgressSyncState
}

data class CloudWordProgress(
    val wordKey: String,
    val box: Int,
    val status: String,
    val dueAt: Long?,
    val correctCount: Int,
    val wrongCount: Int,
    val lapseCount: Int,
    val lastReviewedAt: Long,
    val updatedAt: Long,
    val revision: Long,
    val deviceId: String,
    val counters: Map<String, DeviceCounter> = emptyMap()
) {
    fun toWordState(wordId: Int, profileId: String = "guest"): WordState = WordState(
        wordId = wordId,
        box = box,
        dueAt = dueAt,
        status = status,
        correctCount = correctCount,
        wrongCount = wrongCount,
        lapseCount = lapseCount,
        lastReviewedAt = lastReviewedAt,
        updatedAt = updatedAt,
        revision = revision,
        profileId = profileId,
        deviceId = deviceId,
        countersJson = ProgressCounters.encode(effectiveCounters())
    )
}

fun Word.cloudKey(): String {
    if (stableKey.isNotBlank()) return stableKey
    val canonicalValue = "$normalized|$stressPositions"
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(canonicalValue.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return "v1_$digest"
}

fun CloudWordProgress.effectiveCounters(): Map<String, DeviceCounter> =
    counters.ifEmpty {
        if (correctCount + wrongCount + lapseCount == 0) emptyMap()
        else mapOf("legacy" to DeviceCounter(correctCount, wrongCount, lapseCount))
    }

/** Deterministic scheduling register + per-installation counters; pure and idempotent. */
@Suppress("UNUSED_PARAMETER")
fun mergeProgress(local: CloudWordProgress, cloud: CloudWordProgress?, now: Long = 0,
                  localDeviceId: String = ""): CloudWordProgress {
    if (cloud == null) return local.copy(counters = local.effectiveCounters())
    require(local.wordKey == cloud.wordKey)
    val winner = listOf(local, cloud).maxWith(
        compareBy<CloudWordProgress> { it.revision }.thenBy { it.updatedAt }.thenBy { it.deviceId })
    val counters = ProgressCounters.merge(local.effectiveCounters(), cloud.effectiveCounters())
    return winner.copy(correctCount = ProgressCounters.total(counters.values.map { it.correct }),
        wrongCount = ProgressCounters.total(counters.values.map { it.wrong }), lapseCount = ProgressCounters.total(counters.values.map { it.lapses }),
        counters = counters)
}

fun WordState.toCloudProgress(word: Word): CloudWordProgress {
    val reviewedAt = lastReviewedAt.takeIf { it > 0 } ?: 1L
    return CloudWordProgress(wordKey = word.cloudKey(), box = box, status = status, dueAt = dueAt,
        correctCount = correctCount, wrongCount = wrongCount, lapseCount = lapseCount,
        lastReviewedAt = reviewedAt, updatedAt = updatedAt.takeIf { it > 0 } ?: reviewedAt,
        revision = revision, deviceId = deviceId,
        counters = ProgressCounters.decode(countersJson, correctCount, wrongCount, lapseCount))
}
