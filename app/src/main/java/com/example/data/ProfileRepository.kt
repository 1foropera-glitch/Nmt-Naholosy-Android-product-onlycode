package com.example.data

import androidx.room.withTransaction
import com.example.sync.mergeProgress
import com.example.sync.toCloudProgress
import com.squareup.moshi.Moshi
import java.util.UUID

class ProfileRepository(private val db: AppDatabase, private val preferences: AppPreferences) {
    val activeId = db.syncDao().observeActiveId()
    val pendingCount = db.syncDao().observePendingCount()
    val pendingReset = db.syncDao().observePendingReset()

    suspend fun initialize() {
        val legacyOwner = preferences.getSyncOwnerUid() ?: GUEST
        val legacySession = preferences.getActiveSession()
        db.withTransaction {
            val dao = db.syncDao()
            if (dao.active() == null) {
                dao.saveProfile(LocalProfile(legacyOwner))
                dao.saveProfile(LocalProfile(GUEST))
                db.wordDao().statesForProfile("legacy").forEach { old ->
                    db.wordDao().insertWordState(old.copy(profileId = legacyOwner))
                    dao.enqueue(PendingProgress(legacyOwner, old.wordId, old.revision))
                }
                db.wordDao().clearProfileProgress("legacy")
                if (legacySession != null) {
                    val migrated = SessionRecord.fromLegacy(legacySession)
                    dao.saveSession(StoredSession(legacyOwner, SessionRecord.adapter.toJson(migrated)))
                }
                // Never display a previous user's profile before Auth has selected it.
                dao.activate(ActiveProfile(profileId = GUEST))
            }
        }
        preferences.completeLegacyMigration()
    }

    suspend fun activate(uid: String?) = db.withTransaction {
        val target = uid ?: GUEST
        val dao = db.syncDao()
        if (dao.profile(target) == null) dao.saveProfile(LocalProfile(target))
        if (uid != null) {
            // Guest answers are adopted exactly once; per-installation counters preserve independent work.
            db.wordDao().statesForProfile(GUEST).forEach { state ->
                val word = db.wordDao().getWordById(state.wordId) ?: return@forEach
                val existing = db.wordDao().getWordState(state.wordId, target)
                val merged = mergeProgress(state.toCloudProgress(word), existing?.toCloudProgress(word))
                    .toWordState(state.wordId, target)
                db.wordDao().insertWordState(merged)
                dao.enqueue(PendingProgress(target, merged.wordId, merged.revision))
            }
            if (dao.session(target) == null) dao.session(GUEST)?.let {
                dao.saveSession(it.copy(profileId = target))
            }
            db.wordDao().clearProfileProgress(GUEST)
            dao.clearOutbox(GUEST)
            dao.clearSession(GUEST)
            dao.saveProfile(LocalProfile(GUEST))
        }
        dao.activate(ActiveProfile(profileId = target))
    }

    suspend fun activeProfile(): LocalProfile = db.withTransaction {
        val id = db.syncDao().active()?.profileId ?: GUEST
        db.syncDao().profile(id) ?: LocalProfile(id).also { db.syncDao().saveProfile(it) }
    }

    suspend fun requestReset(expectedProfile: String): LocalProfile = db.withTransaction {
        val profile = activeProfile()
        check(profile.id == expectedProfile) { "Акаунт змінився. Повторіть дію." }
        if (profile.id == GUEST) {
            clearLocal(profile.copy(generation = profile.generation + 1, writerId = UUID.randomUUID().toString()))
        } else {
            val reset = profile.copy(pendingResetId = profile.pendingResetId ?: UUID.randomUUID().toString())
            db.syncDao().saveProfile(reset)
            reset
        }
    }

    suspend fun clearLocal(profile: LocalProfile): LocalProfile {
        db.wordDao().clearProfileProgress(profile.id)
        db.syncDao().clearSession(profile.id)
        db.syncDao().clearOutbox(profile.id)
        db.syncDao().saveProfile(profile)
        return profile
    }

    suspend fun deleteLocalAccount(uid: String) = db.withTransaction {
        require(uid != GUEST)
        val dao = db.syncDao()
        db.wordDao().clearProfileProgress(uid)
        dao.clearSession(uid)
        dao.clearOutbox(uid)
        dao.deleteProfile(uid)
        if (dao.active()?.profileId == uid) {
            if (dao.profile(GUEST) == null) dao.saveProfile(LocalProfile(GUEST))
            dao.activate(ActiveProfile(profileId = GUEST))
        }
    }

    companion object { const val GUEST = "guest" }
}
