package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalSyncDao {
    @Query("SELECT * FROM active_profile WHERE singleton = 1")
    suspend fun active(): ActiveProfile?
    @Query("SELECT profileId FROM active_profile WHERE singleton = 1")
    fun observeActiveId(): Flow<String?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun activate(profile: ActiveProfile)
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun profile(id: String): LocalProfile?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: LocalProfile)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: PendingProgress)
    @Query("SELECT * FROM sync_outbox WHERE profileId = :profileId ORDER BY wordId LIMIT :limit")
    suspend fun pending(profileId: String, limit: Int = 20): List<PendingProgress>
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE profileId = :profileId")
    suspend fun pendingCount(profileId: String): Int
    @Query("SELECT * FROM sync_outbox WHERE profileId = :profileId AND wordId = :wordId")
    suspend fun pendingWord(profileId: String, wordId: Int): PendingProgress?
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE profileId = (SELECT profileId FROM active_profile WHERE singleton = 1)")
    fun observePendingCount(): Flow<Int>
    @Query("SELECT pendingResetId FROM profiles WHERE id = (SELECT profileId FROM active_profile WHERE singleton = 1)")
    fun observePendingReset(): Flow<String?>
    @Query("DELETE FROM sync_outbox WHERE profileId = :profileId AND wordId = :wordId AND revision = :revision")
    suspend fun acknowledge(profileId: String, wordId: Int, revision: Long)
    @Query("DELETE FROM sync_outbox WHERE profileId = :profileId")
    suspend fun clearOutbox(profileId: String)
    @Query("SELECT * FROM learning_sessions WHERE profileId = :profileId")
    suspend fun session(profileId: String): StoredSession?
    @Query("SELECT * FROM learning_sessions WHERE profileId = (SELECT profileId FROM active_profile WHERE singleton = 1)")
    fun observeSession(): Flow<StoredSession?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: StoredSession)
    @Query("DELETE FROM learning_sessions WHERE profileId = :profileId")
    suspend fun clearSession(profileId: String)
    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: String)
    @Query("SELECT digest FROM dictionary_metadata WHERE singleton = 1")
    suspend fun catalogDigest(): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCatalogMetadata(metadata: DictionaryMetadata)
}
