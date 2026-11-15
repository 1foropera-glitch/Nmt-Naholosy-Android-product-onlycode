package com.example.domain

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@JsonClass(generateAdapter = true)
data class DeviceCounter(val correct: Int = 0, val wrong: Int = 0, val lapses: Int = 0)

/** A grow-only counter per installation. Merging never creates a new answer. */
object ProgressCounters {
    fun total(values: Collection<Int>): Int = values.sumOf { it.toLong() }
        .also { require(it in 0..Int.MAX_VALUE.toLong()) { "Лічильник перевищив підтримуваний діапазон." } }.toInt()
    private val adapter = Moshi.Builder().build().adapter<Map<String, DeviceCounter>>(
        Types.newParameterizedType(Map::class.java, String::class.java, DeviceCounter::class.java)
    )

    fun decode(json: String, correct: Int, wrong: Int, lapses: Int): Map<String, DeviceCounter> {
        val counters = adapter.fromJson(json).orEmpty()
        require(counters.size <= 512 && counters.all { (key, value) ->
            key.isNotBlank() && key.length <= 128 && value.correct >= 0 && value.wrong >= 0 && value.lapses >= 0
        }) { "Некоректний формат лічильників прогресу." }
        require(correct >= 0 && wrong >= 0 && lapses >= 0)
        return if (counters.isEmpty() && correct.toLong() + wrong + lapses > 0) {
            mapOf("legacy" to DeviceCounter(correct, wrong, lapses))
        } else counters
    }

    fun encode(counters: Map<String, DeviceCounter>): String = adapter.toJson(counters.toSortedMap())

    fun merge(a: Map<String, DeviceCounter>, b: Map<String, DeviceCounter>): Map<String, DeviceCounter> =
        (a.keys + b.keys).associateWith { key ->
            val x = a[key] ?: DeviceCounter()
            val y = b[key] ?: DeviceCounter()
            DeviceCounter(maxOf(x.correct, y.correct), maxOf(x.wrong, y.wrong), maxOf(x.lapses, y.lapses))
        }
}
