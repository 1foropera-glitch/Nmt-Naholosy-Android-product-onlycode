package com.example.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import androidx.room.withTransaction

class ReviewReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val container = (applicationContext as com.example.NaholosyApplication).container
            if (container.startup.first { it != com.example.StartupState.Loading } != com.example.StartupState.Ready) return Result.retry()
            container.refreshAccount()
            val now = System.currentTimeMillis()
            val (profile, dueWordIds) = container.database.withTransaction {
                container.profiles.activeProfile() to container.database.wordDao()
                    .getDueReviewWordIds(ReviewReminderPolicy.eligibleBefore(now)).toSet()
            }
            val profileToken = "${profile.id}:${profile.generation}:${profile.writerId}"
            val preferences = AppPreferences(applicationContext)

            if (!preferences.isReviewRemindersEnabled()) {
                ReviewReminderNotifications.cancel(applicationContext)
                return Result.success()
            }
            val localHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            if (preferences.quietHoursEnabled.first() && ReviewReminderPolicy.isQuietHour(localHour)) {
                ReviewReminderNotifications.cancel(applicationContext)
                return Result.success() // Keep the snapshot; the next daytime run reevaluates due words.
            }

            if (dueWordIds.isEmpty()) {
                preferences.clearReviewReminderSnapshot()
                ReviewReminderNotifications.cancel(applicationContext)
                return Result.success()
            }

            val sameProfile = preferences.getReviewSnapshotProfile() == profileToken
            val lastNotificationAt = if (sameProfile) preferences.getLastReviewReminderAt() else 0L
            val decision = ReviewReminderPolicy.decide(
                currentDueWordIds = dueWordIds,
                lastNotifiedDueWordIds = if (sameProfile) preferences.getLastNotifiedDueWordIds() else emptySet(),
                lastNotificationAt = lastNotificationAt,
                now = now
            )
            if (decision == ReviewReminderDecision.None) {
                return Result.success()
            }

            container.runForProfile(profile) {
                if (preferences.isReviewRemindersEnabled() && ReviewReminderNotifications.show(applicationContext, decision)) {
                    preferences.setReviewReminderSnapshot(now, dueWordIds, profileToken)
                }
            }
            Result.success()
        } catch (exception: CancellationException) {
            throw exception
        } catch (error: Exception) {
            (applicationContext as com.example.NaholosyApplication).container.telemetry.recordException(error)
            Result.retry()
        }
    }
}
