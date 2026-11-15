package com.example.reminders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPromptStateTest {
    @Test fun freshUserIsOfferedRemindersAfterOnboarding() {
        assertFalse(ReminderPromptState().shouldShow(false))
        assertTrue(ReminderPromptState(onboardingSeen = true).shouldShow(false))
    }

    @Test fun deniedOrDismissedDialogDoesNotHideTheRecoveryPath() {
        val state = ReminderPromptState(onboardingSeen = true, promptHandled = true, remindersEnabled = true)
        assertTrue(state.shouldShow(canPostNotifications = false))
        assertTrue(state.needsPermissionRecovery)
    }

    @Test fun grantingPermissionHidesPromptAndRevokingShowsItAgain() {
        val state = ReminderPromptState(true, true, true)
        assertFalse(state.shouldShow(true))
        assertTrue(state.shouldShow(false))
    }

    @Test fun choosingNotNowDoesNotNagOnEveryVisit() {
        assertFalse(ReminderPromptState(true, true, false).shouldShow(false))
    }
}
