package com.example.reminders

import java.util.concurrent.TimeUnit

object ReviewReminderPolicy {
    val CHECK_INTERVAL_MILLIS: Long = TimeUnit.MINUTES.toMillis(20)
    val DUE_GRACE_PERIOD_MILLIS: Long = TimeUnit.MINUTES.toMillis(5)
    val REPEAT_COOLDOWN_MILLIS: Long = TimeUnit.HOURS.toMillis(3)

    fun eligibleBefore(now: Long): Long = now - DUE_GRACE_PERIOD_MILLIS
    fun isQuietHour(localHour: Int): Boolean {
        require(localHour in 0..23)
        return localHour < 7
    }

    fun decide(
        currentDueWordIds: Set<Int>,
        lastNotifiedDueWordIds: Set<Int>,
        lastNotificationAt: Long,
        now: Long
    ): ReviewReminderDecision {
        if (currentDueWordIds.isEmpty()) return ReviewReminderDecision.None

        if (lastNotifiedDueWordIds.isEmpty()) {
            return ReviewReminderDecision.Standard(currentDueWordIds.size)
        }

        val newWordCount = currentDueWordIds.count { it !in lastNotifiedDueWordIds }
        if (newWordCount > 0) {
            return ReviewReminderDecision.NewWords(
                newWordCount = newWordCount,
                totalDueCount = currentDueWordIds.size
            )
        }

        return if (
            lastNotificationAt <= 0L ||
            now < lastNotificationAt ||
            now - lastNotificationAt >= REPEAT_COOLDOWN_MILLIS
        ) {
            ReviewReminderDecision.Standard(currentDueWordIds.size)
        } else {
            ReviewReminderDecision.None
        }
    }
}

sealed interface ReviewReminderDecision {
    data object None : ReviewReminderDecision
    data class Standard(val totalDueCount: Int) : ReviewReminderDecision
    data class NewWords(
        val newWordCount: Int,
        val totalDueCount: Int
    ) : ReviewReminderDecision
}
