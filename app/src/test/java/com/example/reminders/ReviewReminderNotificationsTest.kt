package com.example.reminders

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ReviewReminderNotificationsTest {
    private lateinit var app: Application
    private lateinit var manager: NotificationManager

    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        manager = app.getSystemService(NotificationManager::class.java)
        shadowOf(manager).setNotificationsEnabled(true)
    }

    @Test fun missingRuntimePermissionPreventsPostingAndOpensAppSettings() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReviewReminderNotifications.createChannel(app)
        assertFalse(ReviewReminderNotifications.canPost(app))
        assertFalse(ReviewReminderNotifications.show(app, ReviewReminderDecision.Standard(3)))
        assertEquals(0, shadowOf(manager).allNotifications.size)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, openedSettingsAction())
    }

    @Test fun permissionAndEnabledChannelAllowARealNotification() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReviewReminderNotifications.createChannel(app)
        assertTrue(ReviewReminderNotifications.canPost(app))
        assertTrue(ReviewReminderNotifications.show(app, ReviewReminderDecision.Standard(3)))
        assertEquals(1, shadowOf(manager).allNotifications.size)
        val intent = shadowOf(shadowOf(manager).allNotifications.single().contentIntent).savedIntent
        assertEquals(ReviewReminderNotifications.ACTION_OPEN_REVIEW, intent.action)
        ReviewReminderNotifications.cancel(app)
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test fun blockedChannelStaysBlockedAndOpensChannelSettings() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        manager.createNotificationChannel(NotificationChannel(
            ReviewReminderNotifications.CHANNEL_ID, "Review", NotificationManager.IMPORTANCE_NONE))
        ReviewReminderNotifications.createChannel(app)
        assertFalse(ReviewReminderNotifications.canPost(app))
        assertFalse(ReviewReminderNotifications.show(app, ReviewReminderDecision.Standard(3)))
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, openedSettingsAction())
    }

    @Test fun appWideBlockOpensAppSettingsEvenWithGrantedRuntimePermission() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ReviewReminderNotifications.createChannel(app)
        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(ReviewReminderNotifications.canPost(app))
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, openedSettingsAction())
    }

    @Test @Config(sdk = [28]) fun preAndroid13NeedsNoRuntimeNotificationPermission() {
        ReviewReminderNotifications.createChannel(app)
        assertTrue(ReviewReminderNotifications.canPost(app))
        assertTrue(ReviewReminderNotifications.show(app, ReviewReminderDecision.Standard(1)))
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, openedSettingsAction())
    }

    private fun openedSettingsAction(): String? {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        return try {
            ReviewReminderNotifications.openSystemSettings(controller.get())
            val intent = shadowOf(controller.get()).nextStartedActivity
            assertEquals(app.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
            if (intent.action == Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS) {
                assertEquals(ReviewReminderNotifications.CHANNEL_ID, intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
            }
            intent.action
        } finally { controller.pause().stop().destroy() }
    }
}
