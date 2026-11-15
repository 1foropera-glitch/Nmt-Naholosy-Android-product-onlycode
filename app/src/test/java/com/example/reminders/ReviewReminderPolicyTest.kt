package com.example.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReviewReminderPolicyTest {

    @Test
    fun `eligible cutoff applies five minute grace period`() {
        val now = TimeUnit.HOURS.toMillis(10)

        assertEquals(
            now - TimeUnit.MINUTES.toMillis(5),
            ReviewReminderPolicy.eligibleBefore(now)
        )
    }

    @Test
    fun `empty due list never notifies`() {
        assertEquals(
            ReviewReminderDecision.None,
            ReviewReminderPolicy.decide(
                currentDueWordIds = emptySet(),
                lastNotifiedDueWordIds = setOf(1, 2),
                lastNotificationAt = 0L,
                now = TimeUnit.HOURS.toMillis(1)
            )
        )
    }

    @Test
    fun `first non empty due list creates standard reminder`() {
        assertEquals(
            ReviewReminderDecision.Standard(totalDueCount = 3),
            ReviewReminderPolicy.decide(
                currentDueWordIds = setOf(1, 2, 3),
                lastNotifiedDueWordIds = emptySet(),
                lastNotificationAt = 0L,
                now = TimeUnit.HOURS.toMillis(1)
            )
        )
    }

    @Test
    fun `unchanged due list waits for three hour cooldown`() {
        val lastNotificationAt = TimeUnit.HOURS.toMillis(1)

        assertEquals(
            ReviewReminderDecision.None,
            ReviewReminderPolicy.decide(
                currentDueWordIds = setOf(1, 2, 3),
                lastNotifiedDueWordIds = setOf(1, 2, 3),
                lastNotificationAt = lastNotificationAt,
                now = lastNotificationAt + TimeUnit.HOURS.toMillis(3) - 1
            )
        )
    }

    @Test
    fun `unchanged due list notifies again when cooldown expires`() {
        val lastNotificationAt = TimeUnit.HOURS.toMillis(1)

        assertEquals(
            ReviewReminderDecision.Standard(totalDueCount = 3),
            ReviewReminderPolicy.decide(
                currentDueWordIds = setOf(1, 2, 3),
                lastNotifiedDueWordIds = setOf(1, 2, 3),
                lastNotificationAt = lastNotificationAt,
                now = lastNotificationAt + TimeUnit.HOURS.toMillis(3)
            )
        )
    }

    @Test
    fun `new words bypass cooldown even when total count is unchanged`() {
        val lastNotificationAt = TimeUnit.HOURS.toMillis(1)

        assertEquals(
            ReviewReminderDecision.NewWords(newWordCount = 2, totalDueCount = 3),
            ReviewReminderPolicy.decide(
                currentDueWordIds = setOf(2, 4, 5),
                lastNotifiedDueWordIds = setOf(1, 2, 3),
                lastNotificationAt = lastNotificationAt,
                now = lastNotificationAt + TimeUnit.MINUTES.toMillis(20)
            )
        )
    }

    @Test
    fun `clock moving backwards starts a fresh standard reminder window`() {
        assertEquals(
            ReviewReminderDecision.Standard(totalDueCount = 1),
            ReviewReminderPolicy.decide(
                currentDueWordIds = setOf(1),
                lastNotifiedDueWordIds = setOf(1),
                lastNotificationAt = TimeUnit.HOURS.toMillis(5),
                now = TimeUnit.HOURS.toMillis(4)
            )
        )
    }
}
