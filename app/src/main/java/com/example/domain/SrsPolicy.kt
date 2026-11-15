package com.example.domain

import com.example.data.ReviewStages
import com.example.data.WordState

/** Pure SRS transition. Time and installation identity are supplied by the caller. */
object SrsPolicy {
    val intervals = ReviewStages.associate { it.box to it.intervalMillis }

    fun answer(previous: WordState?, wordId: Int, correct: Boolean, now: Long,
               deviceId: String, profileId: String): WordState {
        val counters = ProgressCounters.decode(previous?.countersJson ?: "{}",
            previous?.correctCount ?: 0, previous?.wrongCount ?: 0, previous?.lapseCount ?: 0).toMutableMap()
        val own = counters[deviceId] ?: DeviceCounter()
        counters[deviceId] = own.copy(correct = own.correct + if (correct) 1 else 0,
            wrong = own.wrong + if (correct) 0 else 1,
            lapses = own.lapses + if (correct) 0 else 1)
        val early = previous?.dueAt?.let { it > now } == true
        val box = when {
            previous == null || !correct -> 1
            early -> previous.box
            else -> (previous.box + 1).coerceAtMost(7)
        }
        val learned = correct && previous != null && !early && previous.box >= 7
        val due = when {
            learned -> null
            correct && early -> previous?.dueAt
            else -> now + requireNotNull(intervals[box])
        }
        return WordState(wordId = wordId, profileId = profileId, box = box, dueAt = due,
            status = if (learned) "learned" else "learning",
            correctCount = ProgressCounters.total(counters.values.map { it.correct }), wrongCount = ProgressCounters.total(counters.values.map { it.wrong }),
            lapseCount = ProgressCounters.total(counters.values.map { it.lapses }), lastReviewedAt = now, updatedAt = now,
            revision = (previous?.revision ?: 0) + 1, deviceId = deviceId,
            countersJson = ProgressCounters.encode(counters))
    }
}
