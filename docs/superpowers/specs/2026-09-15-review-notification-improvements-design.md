# Review notification improvements design

## Context

The app currently posts one local reminder notification from a 20-minute periodic WorkManager job. The reminder uses a default-importance channel, stores only the last notification time, and does not reconcile the in-app switch with Android's app/channel settings. It therefore does not intentionally produce a heads-up alert, cannot distinguish newly due words from previously announced words, and can leave a stale notification visible after the user begins reviewing through the app.

This change remains entirely local. It does not add Firebase Cloud Messaging, a backend, remote push, exact alarms, or marketing notifications.

## Notification behavior

The app will use a new high-importance channel so existing installations receive the new heads-up behavior despite Android notification-channel immutability. The legacy channel will no longer receive notifications. The notification will use:

- `IMPORTANCE_HIGH` on Android 8 and newer and `PRIORITY_HIGH` on older supported versions;
- default sound and vibration, subject to the user's system settings and Do Not Disturb rules;
- `VISIBILITY_PUBLIC`, allowing the full reminder to appear on the lock screen when the user's lock-screen settings permit it;
- the existing monochrome vector as the required small status-bar icon;
- a resized copy of the supplied full-color app artwork as the large notification icon;
- the existing tap action, which opens the app, clears the notification, and starts a review session.

The app will not use a full-screen intent. Heads-up and lock-screen visibility remain under Android and user control.

## Due-word decision model

The database query will return the IDs of all learning words whose due time is at least five minutes in the past. The worker compares this set with the set stored after the previous successfully posted notification.

The decision order is:

1. If reminders are disabled, cancel outstanding work and notification state.
2. If no words are due, cancel the visible notification and reset the last-notified time and ID set.
3. If no previous successful notification snapshot exists, post the standard reminder with the total due count.
4. Otherwise calculate `newIds = currentDueIds - lastNotifiedDueIds`.
5. If `newIds` is not empty, post a "new words" reminder immediately on that worker run, even if the three-hour repeat cooldown has not elapsed. The title states the number of newly due words and the body states the current total.
6. If no IDs are new, post the standard reminder only when three hours have elapsed since the previous successful notification.
7. After a successful post, store the current due-ID set and notification timestamp. A failed or blocked post does not advance either value.

Because identity rather than only count is compared, replacing five reviewed words with five newly due words still produces a five-new-words alert even though the total remains unchanged. The worker keeps using one notification ID, so the newest information replaces the previous card rather than creating a stack.

## Scheduling

When the user successfully enables reminders, the app resets prior notification tracking, ensures the unique 20-minute periodic worker exists, and enqueues a unique one-time check immediately. The immediate job is still subject to normal WorkManager scheduling, but it does not intentionally wait for the next periodic interval.

When reminders are disabled, the app cancels both unique jobs, removes the visible notification, and clears tracking state. On app startup, periodic work is scheduled only when the saved reminder preference is enabled.

Starting review from either the dashboard or a notification immediately dismisses the visible reminder. Finishing a learning/review session enqueues a one-time refresh so an obsolete notification is removed when nothing remains due and the stored state reflects the latest due words.

## Permission and settings synchronization

The saved preference represents whether the user wants reminders. A separate observable activity state represents whether Android currently allows the app and the new channel to post them. The settings switch displays the effective state: desired and system-allowed.

The activity refreshes system availability after channel creation, after the Android 13+ permission result, and in `onResume`, covering a return from system settings. If the preference is enabled but Android blocks notifications, the settings screen explains the mismatch and offers a button that opens the new channel's system settings. Re-enabling the channel restores the effective switch without losing the user's saved preference.

## Components and data

- `ReviewReminderPolicy` returns an explicit sealed decision: none, standard reminder, or new-words reminder.
- `WordDao` supplies due word IDs in addition to the existing count API.
- `AppPreferences` persists the last successfully notified due-ID set and provides an atomic reset helper with the timestamp.
- `ReviewReminderWorker` obtains the ID set, applies the policy, selects notification copy, and persists state only after `notify()` succeeds.
- `ReviewReminderNotifications` owns channel migration, system availability checks, large/small icons, public lock-screen visibility, and posting/cancellation.
- `ReviewReminderScheduler` owns periodic, immediate, and cancellation operations using unique work names.
- `MainActivity` coordinates permission results, effective availability, enable/disable side effects, notification-settings navigation, and session-triggered cancellation/refresh.
- `SettingsScreen` renders the effective switch, blocked-state explanation, and system-settings action.

## Error handling

Notification posting returns success or failure. A permission race or `SecurityException` leaves the last-successful state unchanged so a later worker can retry. Database or DataStore failures return `Result.retry()` through the worker's existing retry path. Opening system settings is guarded so a missing settings activity cannot crash the app.

## Verification

Unit tests will cover:

- no due words;
- first notification;
- unchanged words within and after the three-hour cooldown;
- new IDs bypassing the cooldown;
- equal total counts with different IDs;
- timestamp rollback;
- correct new-word count.

Static review will verify the manifest permission, channel migration, notification icons, lock-screen visibility, activity lifecycle synchronization, and all review entry/exit paths. Per the user's standing instruction, no Gradle build, test execution, emulator run, or commit is part of this change unless separately requested.

## Scope boundaries

The change does not guarantee exact 20-minute delivery, bypass Do Not Disturb, override user lock-screen privacy, create full-screen alarm UI, send remote push messages, or add unrelated notification categories.
