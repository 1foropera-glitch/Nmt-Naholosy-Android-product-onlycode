package com.example.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.domain.DeviceCounter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PartialCloudRecoveryTest {
    @Test fun validRecordsSurviveCorruptionWithoutAdvancingCheckpointOrUploading() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val word = Word(1, "вОдний", "водний", "водний", "1", "В", null, "test")
            db.wordDao().insertWords(listOf(word))
            db.syncDao().saveProfile(LocalProfile("user", cloudBound = true, cloudVersion = -1))
            db.syncDao().activate(ActiveProfile(profileId = "user"))
            val progress = CloudWordProgress(word.cloudKey(), 1, "learning", 1000L, 2, 0, 0,
                100L, 100L, 2L, "device", mapOf("device" to DeviceCounter(2, 0, 0)))
            var corrupted = true
            var uploaded = false
            val store = object : ProgressCloudStore {
                override suspend fun profile(uid: String) = CloudProfile(0, 1, true)
                override suspend fun pull(uid: String, generation: Long) = CloudProgressBatch(listOf(progress), if (corrupted) 1 else 0)
                override suspend fun upload(uid: String, generation: Long, local: List<CloudWordProgress>): UploadResult {
                    uploaded = true
                    error("Unexpected upload")
                }
                override suspend fun reset(uid: String, requestId: String): CloudProfile = error("Unexpected reset")
            }
            val preferences = AppPreferences(context)
            val repository = ProgressSyncRepository(store, db, ProfileRepository(db, preferences), preferences)
            repository.onAccountChanged("user")
            assertFalse(repository.syncNow("user"))
            assertEquals(2, db.wordDao().getWordState(1, "user")?.correctCount)
            assertEquals(-1L, db.syncDao().profile("user")?.cloudVersion)
            assertFalse((repository.state.value as ProgressSyncState.Error).retryable)
            assertFalse(uploaded)
            // Once the server record is repaired, a manual retry may complete normally.
            corrupted = false
            assertTrue(repository.syncNow("user"))
            assertEquals(1L, db.syncDao().profile("user")?.cloudVersion)
        } finally { db.close() }
    }
}
