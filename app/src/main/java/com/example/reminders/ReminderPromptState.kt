package com.example.reminders

/** A handled prompt is not evidence that Android granted notification permission. */
data class ReminderPromptState(
    val onboardingSeen: Boolean = false,
    val promptHandled: Boolean = false,
    val remindersEnabled: Boolean = false
) {
    val needsPermissionRecovery: Boolean get() = promptHandled && remindersEnabled

    fun shouldShow(canPostNotifications: Boolean): Boolean =
        onboardingSeen && !canPostNotifications && (!promptHandled || remindersEnabled)
}
