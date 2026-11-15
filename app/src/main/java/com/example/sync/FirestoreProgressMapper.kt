package com.example.sync

import com.example.domain.DeviceCounter
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

fun CloudWordProgress.toFirestoreMap(): Map<String, Any?> = mapOf(
    "schemaVersion" to 2, "wordKey" to wordKey, "box" to box, "status" to status,
    "dueAt" to dueAt?.let { Timestamp(Date(it)) }, "correctCount" to correctCount,
    "wrongCount" to wrongCount, "lapseCount" to lapseCount,
    "lastReviewedAt" to Timestamp(Date(lastReviewedAt)), "updatedAt" to Timestamp(Date(updatedAt)),
    "revision" to revision, "deviceId" to deviceId,
    "counters" to effectiveCounters().mapValues { (_, value) -> mapOf(
        "correct" to value.correct, "wrong" to value.wrong, "lapses" to value.lapses) }
)

fun DocumentSnapshot.toCloudWordProgress(): CloudWordProgress? {
    if (!exists()) return null
    return try { parseCloudWordProgress(id, requireNotNull(data)) }
    catch (_: IllegalArgumentException) { throw CorruptCloudProgress() }
}

fun parseCloudWordProgress(id: String, data: Map<String, Any?>): CloudWordProgress {
    fun whole(value: Any?): Long {
        require(value is Long || value is Int) { "Очікується ціле число." }
        return (value as Number).toLong()
    }
    val schema = whole(data["schemaVersion"])
    require(schema in listOf(1L, 2L)) { "Непідтримувана версія хмарного прогресу." }
    fun integer(name: String) = whole(data[name]).also { require(it in 0..Int.MAX_VALUE.toLong()) }.toInt()
    fun timestamp(name: String) = requireNotNull(data[name] as? Timestamp).toDate().time
    val rawCounters = if (schema == 2L) requireNotNull(data["counters"] as? Map<*, *>)
        else data["counters"] as? Map<*, *> ?: emptyMap<Any, Any>()
    val counters = rawCounters.map { (key, raw) ->
        require(key is String && key.isNotBlank() && key.length <= 128)
        val value = requireNotNull(raw as? Map<*, *>)
        require(value.keys == setOf("correct", "wrong", "lapses"))
        fun count(name: String) = whole(value[name])
            .also { require(it in 0..Int.MAX_VALUE.toLong()) }.toInt()
        key to DeviceCounter(count("correct"), count("wrong"), count("lapses"))
    }.toMap()
    require(counters.size <= 512)
    val progress = CloudWordProgress(wordKey = requireNotNull(data["wordKey"] as? String), box = integer("box"),
        status = requireNotNull(data["status"] as? String), dueAt = data["dueAt"]?.let { timestamp("dueAt") },
        correctCount = integer("correctCount"), wrongCount = integer("wrongCount"), lapseCount = integer("lapseCount"),
        lastReviewedAt = timestamp("lastReviewedAt"), updatedAt = timestamp("updatedAt"),
        revision = whole(data["revision"]), deviceId = requireNotNull(data["deviceId"] as? String), counters = counters)
    require(progress.wordKey == id && progress.box in 1..7 && progress.revision >= 0 &&
        progress.deviceId.isNotBlank() && progress.deviceId.length <= 128)
    require((progress.status == "learning" && progress.dueAt != null) ||
        (progress.status == "learned" && progress.box == 7 && progress.dueAt == null))
    if (schema == 2L) {
        require(counters.values.sumOf { it.correct.toLong() } == progress.correctCount.toLong())
        require(counters.values.sumOf { it.wrong.toLong() } == progress.wrongCount.toLong())
        require(counters.values.sumOf { it.lapses.toLong() } == progress.lapseCount.toLong())
    }
    return progress.copy(counters = progress.effectiveCounters())
}

data class CloudProgressBatch(val valid: List<CloudWordProgress>, val rejectedCount: Int)

/** Only malformed document data is isolated; transport/cancellation errors are never swallowed. */
fun parseCloudProgressBatch(documents: List<Pair<String, Map<String, Any?>>>): CloudProgressBatch {
    var rejected = 0
    val valid = documents.mapNotNull { (id, data) ->
        try { parseCloudWordProgress(id, data) }
        catch (_: IllegalArgumentException) { rejected++; null }
    }
    return CloudProgressBatch(valid, rejected)
}

class CorruptCloudProgress : java.io.IOException(
    "У хмарному прогресі є некоректні записи. Локальні відповіді збережено, синхронізацію призупинено. Зверніться до підтримки через налаштування."
)
