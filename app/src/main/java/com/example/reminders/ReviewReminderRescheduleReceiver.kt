package com.example.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Restores word-review checks after a reboot or an app update. */
class ReviewReminderRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val preferences = AppPreferences(appContext)
                ReviewReminderNotifications.createChannel(appContext)

                if (
                    preferences.isReviewRemindersEnabled() &&
                    ReviewReminderNotifications.canPost(appContext)
                ) {
                    ReviewReminderNotifications.createChannel(appContext)
                    ReviewReminderScheduler.ensureScheduled(appContext)
                    ReviewReminderScheduler.enqueueImmediateCheck(appContext)
                } else {
                    ReviewReminderScheduler.cancel(appContext)
                }
            } catch (failure: Exception) {
                android.util.Log.w("ReviewReminders", "Could not restore reminders", failure)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
