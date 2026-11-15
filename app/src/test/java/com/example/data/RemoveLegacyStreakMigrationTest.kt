package com.example.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoveLegacyStreakMigrationTest {
    @Test
    fun removesRetiredDataWithoutChangingSessionAccountOrSettings() = runTest {
        val streak = intPreferencesKey("current_streak")
        val lastActive = stringPreferencesKey("last_active_date")
        val session = stringPreferencesKey("active_session")
        val owner = stringPreferencesKey("sync_owner_uid")
        val premium = booleanPreferencesKey("is_premium")
        val original = preferencesOf(
            streak to 12,
            lastActive to "2026-10-20",
            session to "saved-session",
            owner to "account-a",
            premium to true
        )

        assertTrue(RemoveLegacyStreakMigration.shouldMigrate(original))
        val migrated = RemoveLegacyStreakMigration.migrate(original)

        assertNull(migrated[streak])
        assertNull(migrated[lastActive])
        assertEquals("saved-session", migrated[session])
        assertEquals("account-a", migrated[owner])
        assertEquals(true, migrated[premium])
        assertFalse(RemoveLegacyStreakMigration.shouldMigrate(migrated))
        assertEquals(migrated, RemoveLegacyStreakMigration.migrate(migrated))
    }
}
