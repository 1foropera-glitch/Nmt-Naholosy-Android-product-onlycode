package com.example.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReviewReminderScheduler {
    private const val PERIODIC_WORK_NAME = "review-reminder-check"
    private const val IMMEDIATE_WORK_NAME = "review-reminder-check-now"

    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReviewReminderWorker>(
            ReviewReminderPolicy.CHECK_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS
        )
            .setInitialDelay(
                ReviewReminderPolicy.CHECK_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun enqueueImmediateCheck(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReviewReminderWorker>().build()
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).apply {
            cancelUniqueWork(PERIODIC_WORK_NAME)
            cancelUniqueWork(IMMEDIATE_WORK_NAME)
        }
    }
}
