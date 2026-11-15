package com.example.sync

import androidx.room.withTransaction
import com.example.data.AppDatabase
import com.example.data.AppPreferences
import com.example.data.LocalProfile
import com.example.data.PendingProgress
import com.example.data.ProfileRepository
import com.example.telemetry.AppTelemetry
import com.example.telemetry.NoOpTelemetry
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProgressSyncRepository(
    private val store: ProgressCloudStore?,
    private val db: AppDatabase,
    private val profiles: ProfileRepository,
    private val preferences: AppPreferences,
    private val telemetry: AppTelemetry = NoOpTelemetry,
    private val onReset: suspend () -> Unit = {}
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<ProgressSyncState>(ProgressSyncState.SignedOut)
    val state: StateFlow<ProgressSyncState> = mutableState.asStateFlow()
    @Volatile private var activeUid: String? = null

    fun onAccountChanged(uid: String?) {
        activeUid = uid
        mutableState.value = if (uid == null) ProgressSyncState.SignedOut else ProgressSyncState.Idle
    }

    suspend fun syncNow(uid: String): Boolean = mutex.withLock {
        if (activeUid != uid || store == null) return@withLock false
        mutableState.value = ProgressSyncState.Syncing
        val start = System.nanoTime()
        try {
            performSync(uid, store)
            val count = db.syncDao().pendingCount(uid)
            if (activeUid == uid) mutableState.value = if (count == 0) {
                ProgressSyncState.Synced(System.currentTimeMillis(), db.wordDao().statesForProfile(uid).size)
            } else ProgressSyncState.Idle
            telemetry.syncFinished((System.nanoTime() - start) / 1_000_000, count, true)
            count == 0 && db.syncDao().profile(uid)?.pendingResetId == null
        } catch (cancelled: CancellationException) {
            if (activeUid == uid) mutableState.value = ProgressSyncState.Idle
            throw cancelled
        } catch (failure: Exception) {
            telemetry.recordException(failure)
            val corrupt = generateSequence<Throwable>(failure) { it.cause }.take(8).any { it is CorruptCloudProgress }
            val rejected = generateSequence<Throwable>(failure) { it.cause }.take(8).any { it is ProgressUploadRejected }
            if (activeUid == uid) mutableState.value = ProgressSyncState.Error(
                if (corrupt) requireNotNull(CorruptCloudProgress().message)
                else if (failure is IOException) failure.message ?: "Немає зв’язку з хмарою. Спробуємо ще раз."
                else "Не вдалося синхронізувати прогрес. Локальні відповіді збережено.",
                retryable = !corrupt && !rejected)
            telemetry.syncFinished((System.nanoTime() - start) / 1_000_000, db.syncDao().pendingCount(uid), false)
            false
        }
    }

    suspend fun clearProgress(uid: String): Boolean {
        if (activeUid != uid) return false
        profiles.requestReset(uid)
        return syncNow(uid)
    }

    private suspend fun performSync(uid: String, store: ProgressCloudStore) {
        var cloud = store.profile(uid)
        var local = requireNotNull(db.syncDao().profile(uid))
        local.pendingResetId?.let { request ->
            cloud = store.reset(uid, request)
            db.withTransaction {
                ensureActive(uid)
                val current = requireNotNull(db.syncDao().profile(uid))
                if (current.pendingResetId == request) profiles.clearLocal(
                    current.copy(generation = cloud.generation, cloudVersion = -1, pendingResetId = null, cloudBound = true))
            }
            preferences.clearReviewReminderSnapshot()
            onReset()
        }
        var downloadedReset = false
        db.withTransaction {
            ensureActive(uid)
            local = requireNotNull(db.syncDao().profile(uid))
            if (!local.cloudBound) {
                // First adoption of guest/v1 data belongs to the server's current generation.
                local = local.copy(generation = cloud.generation, cloudBound = true)
                db.syncDao().saveProfile(local)
            } else if (cloud.generation > local.generation) {
                local = profiles.clearLocal(local.copy(generation = cloud.generation, cloudVersion = -1))
                downloadedReset = true
            }
            check(cloud.generation == local.generation) { "Несумісне покоління прогресу." }
        }
        if (downloadedReset) { preferences.clearReviewReminderSnapshot(); onReset() }
        if (local.cloudVersion != cloud.version) {
            val downloaded = store.pull(uid, cloud.generation)
            db.withTransaction {
                ensureActive(uid, cloud.generation)
                applyProgress(uid, downloaded.valid)
                val current = requireNotNull(db.syncDao().profile(uid))
                if (downloaded.rejectedCount == 0) db.syncDao().saveProfile(current.copy(cloudVersion = cloud.version))
            }
            // Keep the old checkpoint and outbox until support repairs the malformed remote data.
            if (downloaded.rejectedCount > 0) throw CorruptCloudProgress()
        }
        // A bounded run avoids monopolising WorkManager. Remaining items cause retry.
        repeat(16) {
            ensureActive(uid, cloud.generation)
            val pending = db.syncDao().pending(uid)
            if (pending.isEmpty()) return
            val values = db.withTransaction {
                ensureActive(uid, cloud.generation)
                pending.mapNotNull { item ->
                    val word = db.wordDao().getWordById(item.wordId) ?: return@mapNotNull null
                    db.wordDao().getWordState(item.wordId, uid)?.toCloudProgress(word)
                }
            }
            if (values.isEmpty()) throw IOException("Черга містить невідомі слова. Потрібне оновлення словника.")
            val result = store.upload(uid, cloud.generation, values)
            db.withTransaction {
                ensureActive(uid, cloud.generation)
                applyProgress(uid, result.progress)
                val current = requireNotNull(db.syncDao().profile(uid))
                // Only advance the checkpoint if every intervening version is already known locally.
                if (current.cloudVersion == result.beforeVersion) {
                    db.syncDao().saveProfile(current.copy(cloudVersion = result.profile.version))
                }
            }
        }
    }

    /** Caller holds a Room transaction; reread current state so network snapshots cannot erase answers. */
    private suspend fun applyProgress(uid: String, remote: List<CloudWordProgress>) {
        val words = db.wordDao().getCatalogIncludingRetired().associateBy { it.cloudKey() }
        remote.forEach { cloud ->
            val word = words[cloud.wordKey] ?: return@forEach
            val current = db.wordDao().getWordState(word.id, uid)
            val merged = current?.let { mergeProgress(it.toCloudProgress(word), cloud) } ?: cloud
            val state = merged.toWordState(word.id, uid)
            db.wordDao().insertWordState(state)
            if (merged == cloud) {
                db.syncDao().pendingWord(uid, word.id)?.let {
                    db.syncDao().acknowledge(uid, word.id, it.revision)
                }
            } else db.syncDao().enqueue(PendingProgress(uid, word.id, state.revision))
        }
    }

    private suspend fun ensureActive(uid: String, generation: Long? = null) {
        if (activeUid != uid || db.syncDao().active()?.profileId != uid) throw IOException("Профіль змінився.")
        if (generation != null && db.syncDao().profile(uid)?.generation != generation) throw GenerationChanged()
    }
}
