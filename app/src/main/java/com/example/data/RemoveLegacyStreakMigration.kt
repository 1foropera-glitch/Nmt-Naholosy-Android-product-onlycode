package com.example.data

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Removes retired streak data when an existing installation is upgraded. */
object RemoveLegacyStreakMigration : DataMigration<Preferences> {
    private val currentStreak = intPreferencesKey("current_streak")
    private val lastActiveDate = stringPreferencesKey("last_active_date")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData.contains(currentStreak) || currentData.contains(lastActiveDate)

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            remove(currentStreak)
            remove(lastActiveDate)
        }

    override suspend fun cleanUp() = Unit
}
