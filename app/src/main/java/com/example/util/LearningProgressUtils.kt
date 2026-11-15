package com.example.util

import com.example.data.WordState
import java.util.Calendar
import kotlin.math.ceil

fun targetNmtYear(now: Calendar = Calendar.getInstance()): Int =
    targetNmtYear(
        year = now.get(Calendar.YEAR),
        month = now.get(Calendar.MONTH)
    )

fun targetNmtYear(year: Int, month: Int): Int =
    if (month >= Calendar.AUGUST) year + 1 else year

fun isDifficultWord(state: WordState): Boolean =
    state.lapseCount >= 4 && state.status == "learning" && state.box <= 3

fun sessionUpdatesSrs(mode: String): Boolean = mode != "difficult"

fun stagePassedCount(states: List<WordState>, stageBox: Int): Int = states.count { state ->
    state.status == "learned" || (state.status == "learning" && state.box > stageBox)
}

fun timeProgress(state: WordState?, now: Long): Float {
    val dueAt = state?.dueAt ?: return 0f
    if (dueAt <= now) return 1f
    val duration = dueAt - state.lastReviewedAt
    if (duration <= 0L) return 0f
    return ((now - state.lastReviewedAt).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}

fun formatReviewCountdown(dueAt: Long?, now: Long): String {
    if (dueAt == null) return "Розклад завершено"
    val remaining = dueAt - now
    if (remaining <= 0L) return "Готово до повторення"

    val minute = 60_000L
    val hour = 60L * minute
    val day = 24L * hour
    return when {
        remaining < minute -> "Повторити менш ніж за хвилину"
        remaining < hour -> {
            val value = ceil(remaining.toDouble() / minute).toInt()
            "Повторити через $value ${ukrainianForm(value, "хвилину", "хвилини", "хвилин")}" 
        }
        remaining < day -> {
            val value = ceil(remaining.toDouble() / hour).toInt()
            "Повторити через $value ${ukrainianForm(value, "годину", "години", "годин")}" 
        }
        else -> {
            val value = ceil(remaining.toDouble() / day).toInt()
            "Повторити через $value ${ukrainianForm(value, "день", "дні", "днів")}" 
        }
    }
}

fun wordsLabel(count: Int): String = "$count ${ukrainianForm(count, "слово", "слова", "слів")}" 

fun wordsNeedAttentionLabel(count: Int): String {
    val verb = if (count % 100 !in 11..19 && count % 10 == 1) "потребує" else "потребують"
    return "${wordsLabel(count)} $verb додаткової уваги"
}

fun ukrainianForm(count: Int, one: String, few: String, many: String): String {
    val lastTwo = count % 100
    val lastOne = count % 10
    return when {
        lastTwo in 11..19 -> many
        lastOne == 1 -> one
        lastOne in 2..4 -> few
        else -> many
    }
}
