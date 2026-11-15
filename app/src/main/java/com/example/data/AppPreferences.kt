package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.appPreferencesDataStore by preferencesDataStore(
    name = "app_preferences",
    produceMigrations = { listOf(RemoveLegacyStreakMigration) }
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStoredValue(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

data class PremiumCache(val active: Boolean, val ownerUid: String?)

@JsonClass(generateAdapter = true)
data class PersistedSession(
    val mode: String,
    val wordIds: List<Int>,
    val currentIndex: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val selectedAnswer: String?,
    val isCorrectAnswer: Boolean?,
    val isEarlyReview: Boolean
)

class AppPreferences(context: Context) {
    private val context = context.applicationContext
    private object Keys {
        val ThemeMode = stringPreferencesKey("theme_mode")
        val ReduceMotion = booleanPreferencesKey("reduce_motion")
        val SoundEnabled = booleanPreferencesKey("sound_enabled")
        val HapticsEnabled = booleanPreferencesKey("haptics_enabled")
        val ReviewRemindersEnabled = booleanPreferencesKey("review_reminders_enabled")
        val IsPremium = booleanPreferencesKey("is_premium")
        val PremiumOwnerUid = stringPreferencesKey("premium_owner_uid")
        val HasSeenOnboarding = booleanPreferencesKey("has_seen_onboarding")
        val SyncOwnerUid = stringPreferencesKey("sync_owner_uid")
        val LastReviewReminderAt = longPreferencesKey("last_review_reminder_at")
        val LastNotifiedDueWordIds = stringPreferencesKey("last_notified_due_word_ids")
        val ReviewSnapshotProfile = stringPreferencesKey("review_snapshot_profile")
        val HasRequestedNotificationPermission = booleanPreferencesKey("has_requested_notification_permission")
        val ActiveSession = stringPreferencesKey("active_session")
        val DiagnosticsEnabled = booleanPreferencesKey("diagnostics_enabled")
        val QuietHoursEnabled = booleanPreferencesKey("quiet_hours_enabled")
        val DeletionUid = stringPreferencesKey("account_deletion_uid")
        val DeletionNextEpoch = longPreferencesKey("account_deletion_next_epoch")
        val DeletionCloudCleared = booleanPreferencesKey("account_deletion_cloud_cleared")
        val DeletionAuthDeleted = booleanPreferencesKey("account_deletion_auth_deleted")
        val DeletionReceipt = stringPreferencesKey("account_deletion_receipt")
    }

    private val sessionAdapter = Moshi.Builder().build().adapter(PersistedSession::class.java)

    val pendingAccountDeletion: Flow<com.example.privacy.PendingAccountDeletion?> = context.appPreferencesDataStore.data.map {
        val uid = it[Keys.DeletionUid]
        val receipt = it[Keys.DeletionReceipt]
        if (uid != null && receipt != null) com.example.privacy.PendingAccountDeletion(uid, receipt,
            it[Keys.DeletionNextEpoch] ?: 0, it[Keys.DeletionCloudCleared] ?: false, it[Keys.DeletionAuthDeleted] ?: false) else null
    }
    suspend fun setPendingAccountDeletion(value: com.example.privacy.PendingAccountDeletion?) {
        context.appPreferencesDataStore.edit {
            if (value == null) {
                it.remove(Keys.DeletionUid); it.remove(Keys.DeletionReceipt); it.remove(Keys.DeletionNextEpoch)
                it.remove(Keys.DeletionCloudCleared); it.remove(Keys.DeletionAuthDeleted)
            }
            else {
                it[Keys.DeletionUid] = value.uid; it[Keys.DeletionReceipt] = value.receipt
                it[Keys.DeletionNextEpoch] = value.nextEpoch; it[Keys.DeletionCloudCleared] = value.cloudCleared
                it[Keys.DeletionAuthDeleted] = value.authDeleted
            }
        }
    }

    val premiumCache: Flow<PremiumCache> = context.appPreferencesDataStore.data.map {
        PremiumCache(it[Keys.IsPremium] ?: false, it[Keys.PremiumOwnerUid])
    }
    val diagnosticsEnabled: Flow<Boolean> = context.appPreferencesDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[Keys.DiagnosticsEnabled] ?: false }
    suspend fun setDiagnosticsEnabled(enabled: Boolean) {
        context.appPreferencesDataStore.edit { it[Keys.DiagnosticsEnabled] = enabled }
    }
    val quietHoursEnabled: Flow<Boolean> = context.appPreferencesDataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[Keys.QuietHoursEnabled] ?: true }
    suspend fun setQuietHoursEnabled(enabled: Boolean) {
        context.appPreferencesDataStore.edit { it[Keys.QuietHoursEnabled] = enabled }
    }

    val themeMode: Flow<ThemeMode> = context.appPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> ThemeMode.fromStoredValue(preferences[Keys.ThemeMode]) }

    val reduceMotion: Flow<Boolean> = context.appPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[Keys.ReduceMotion] ?: false }

    val soundEnabled: Flow<Boolean> = context.appPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[Keys.SoundEnabled] ?: true }

    val hapticsEnabled: Flow<Boolean> = context.appPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[Keys.HapticsEnabled] ?: true }

    val reviewRemindersEnabled: Flow<Boolean> = context.appPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[Keys.ReviewRemindersEnabled] ?: false }

    val isPremium: Flow<Boolean> = context.appPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[Keys.IsPremium] ?: false }

    val premiumOwnerUid: Flow<String?> = context.appPreferencesDataStore.data
        .catch { exception -> if (exception is IOException) emit(emptyPreferences()) else throw exception }
        .map { it[Keys.PremiumOwnerUid] }

    val hasSeenOnboarding: Flow<Boolean> = context.appPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[Keys.HasSeenOnboarding] ?: false }

    val hasRequestedNotificationPermission: Flow<Boolean> = context.appPreferencesDataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[Keys.HasRequestedNotificationPermission] ?: false }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[Keys.ThemeMode] = themeMode.name
        }
    }

    suspend fun setReduceMotion(enabled: Boolean) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[Keys.ReduceMotion] = enabled
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[Keys.SoundEnabled] = enabled
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[Keys.HapticsEnabled] = enabled
        }
    }

    suspend fun setReviewRemindersEnabled(enabled: Boolean) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[Keys.ReviewRemindersEnabled] = enabled
        }
    }

    suspend fun isReviewRemindersEnabled(): Boolean =
        context.appPreferencesDataStore.data.first()[Keys.ReviewRemindersEnabled] ?: false

    suspend fun setPremium(enabled: Boolean, ownerUid: String? = null) {
        require(!enabled || !ownerUid.isNullOrBlank())
        context.appPreferencesDataStore.edit { preferences ->
            preferences[Keys.IsPremium] = enabled
            if (ownerUid != null) preferences[Keys.PremiumOwnerUid] = ownerUid
            else preferences.remove(Keys.PremiumOwnerUid)
        }
    }

    suspend fun setOnboardingSeen() {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[Keys.HasSeenOnboarding] = true
        }
    }

    suspend fun getLastReviewReminderAt(): Long =
        context.appPreferencesDataStore.data.first()[Keys.LastReviewReminderAt] ?: 0L

    suspend fun setLastReviewReminderAt(timestamp: Long) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[Keys.LastReviewReminderAt] = timestamp
        }
    }

    suspend fun getLastNotifiedDueWordIds(): Set<Int> {
        val stored = context.appPreferencesDataStore.data.first()[Keys.LastNotifiedDueWordIds]
        return stored
            ?.split(',')
            ?.mapNotNull(String::toIntOrNull)
            ?.toSet()
            .orEmpty()
    }

    suspend fun getReviewSnapshotProfile(): String? = context.appPreferencesDataStore.data.first()[Keys.ReviewSnapshotProfile]

    suspend fun setReviewReminderSnapshot(timestamp: Long, dueWordIds: Set<Int>, profileToken: String) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[Keys.ReviewSnapshotProfile] = profileToken
            preferences[Keys.LastReviewReminderAt] = timestamp
            preferences[Keys.LastNotifiedDueWordIds] = dueWordIds.sorted().joinToString(",")
        }
    }

    suspend fun clearReviewReminderSnapshot() {
        context.appPreferencesDataStore.edit { preferences ->
            preferences.remove(Keys.ReviewSnapshotProfile)
            preferences[Keys.LastReviewReminderAt] = 0L
            preferences.remove(Keys.LastNotifiedDueWordIds)
        }
    }

    suspend fun markNotificationPermissionRequested() {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[Keys.HasRequestedNotificationPermission] = true
        }
    }

    suspend fun hasRequestedNotificationPermission(): Boolean =
        context.appPreferencesDataStore.data.first()[Keys.HasRequestedNotificationPermission] ?: false

    suspend fun saveActiveSession(session: PersistedSession) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[Keys.ActiveSession] = sessionAdapter.toJson(session)
        }
    }

    suspend fun getActiveSession(): PersistedSession? {
        val json = context.appPreferencesDataStore.data.first()[Keys.ActiveSession] ?: return null
        return runCatching { sessionAdapter.fromJson(json) }.getOrNull()
    }

    suspend fun clearActiveSession() {
        context.appPreferencesDataStore.edit { preferences ->
            preferences.remove(Keys.ActiveSession)
        }
    }

    suspend fun completeLegacyMigration() {
        context.appPreferencesDataStore.edit { preferences ->
            preferences.remove(Keys.ActiveSession)
            preferences.remove(Keys.SyncOwnerUid)
            preferences.remove(stringPreferencesKey("sync_install_id"))
        }
    }

    suspend fun getSyncOwnerUid(): String? =
        context.appPreferencesDataStore.data.first()[Keys.SyncOwnerUid]

    suspend fun setSyncOwnerUid(uid: String) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[Keys.SyncOwnerUid] = uid
        }
    }
}

private fun emptyPreferences(): Preferences = androidx.datastore.preferences.core.emptyPreferences()
