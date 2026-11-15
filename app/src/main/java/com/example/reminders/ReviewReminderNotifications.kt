package com.example.reminders

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object ReviewReminderNotifications {
    const val ACTION_OPEN_REVIEW = "com.intervalninaholosy.nmt.action.OPEN_REVIEW"
    const val CHANNEL_ID = "review_reminders_alerts_v2"

    private const val LEGACY_CHANNEL_ID = "review_reminders"
    private const val NOTIFICATION_ID = 2001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.review_reminders_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.review_reminders_channel_description)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }

        manager.createNotificationChannel(channel)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }

    fun canPost(context: Context): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)
        return channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(context: Context, decision: ReviewReminderDecision): Boolean {
        if (decision == ReviewReminderDecision.None) return false
        createChannel(context)
        if (!canPost(context)) return false

        val openReviewIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_REVIEW
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openReviewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, message, totalDueCount) = when (decision) {
            ReviewReminderDecision.None -> return false
            is ReviewReminderDecision.Standard -> Triple(
                context.getString(R.string.review_reminder_title),
                context.resources.getQuantityString(
                    R.plurals.review_reminder_body,
                    decision.totalDueCount,
                    decision.totalDueCount
                ),
                decision.totalDueCount
            )
            is ReviewReminderDecision.NewWords -> Triple(
                context.resources.getQuantityString(
                    R.plurals.review_reminder_new_title,
                    decision.newWordCount,
                    decision.newWordCount
                ),
                context.resources.getQuantityString(
                    R.plurals.review_reminder_new_body,
                    decision.totalDueCount,
                    decision.totalDueCount
                ),
                decision.totalDueCount
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_review_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setNumber(totalDueCount)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)

        BitmapFactory.decodeResource(context.resources, R.drawable.ic_notification_large)
            ?.let(builder::setLargeIcon)

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun openSystemSettings(context: Context) {
        // An app-wide denial cannot be repaired from the channel-only settings screen.
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val channelIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            }
        } else {
            appNotificationSettingsIntent(context)
        }

        try {
            context.startActivity(channelIntent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(appNotificationSettingsIntent(context))
        }
    }

    fun openBackgroundSettings(context: Context) {
        if (Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) {
            val miuiAutostartIntent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
            }
            try {
                context.startActivity(miuiAutostartIntent)
                return
            } catch (_: ActivityNotFoundException) {
                // Some MIUI/HyperOS versions do not expose this screen publicly.
            }
        }

        val appDetailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )

        try {
            context.startActivity(appDetailsIntent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun appNotificationSettingsIntent(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            )
        }
}
