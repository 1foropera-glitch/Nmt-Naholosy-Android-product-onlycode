package com.example.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.domain.DeviceCounter
import com.example.privacy.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AccountDeletionLocalTest {
    @Test fun deletingAnInactiveAccountPreservesOtherProfilesAndGuestData() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val profiles = ProfileRepository(db, AppPreferences(context))
            val word = Word(1, "вОдний", "водний", "водний", "1", "В", null, "test")
            db.wordDao().insertWords(listOf(word))
            for (uid in listOf("deleted", "other", "guest")) {
                db.syncDao().saveProfile(LocalProfile(uid))
                val progress = CloudWordProgress(word.cloudKey(), 1, "learning", 1000, 1, 0, 0,
                    100, 100, 1, "device", mapOf("device" to DeviceCounter(1, 0, 0)))
                db.wordDao().insertWordState(progress.toWordState(word.id, uid))
                db.syncDao().enqueue(PendingProgress(uid, word.id, 1))
                db.syncDao().saveSession(StoredSession(uid, "{}"))
            }
            db.syncDao().activate(ActiveProfile(profileId = "other"))
            profiles.deleteLocalAccount("deleted")
            assertNull(db.syncDao().profile("deleted"))
            assertNull(db.syncDao().session("deleted"))
            assertEquals(0, db.syncDao().pendingCount("deleted"))
            assertTrue(db.wordDao().statesForProfile("deleted").isEmpty())
            assertEquals("other", db.syncDao().active()?.profileId)
            for (uid in listOf("other", "guest")) {
                assertEquals(1, db.syncDao().pendingCount(uid))
                assertEquals(1, db.wordDao().statesForProfile(uid).size)
                assertNotNull(db.syncDao().session(uid))
            }
        } finally { db.close() }
    }

    @Test fun deletingTheActiveAccountSelectsGuestWithoutAdoptingDeletedProgress() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.syncDao().saveProfile(LocalProfile("deleted"))
            db.syncDao().activate(ActiveProfile(profileId = "deleted"))
            ProfileRepository(db, AppPreferences(context)).deleteLocalAccount("deleted")
            assertEquals("guest", db.syncDao().active()?.profileId)
            assertNotNull(db.syncDao().profile("guest"))
            assertNull(db.syncDao().profile("deleted"))
        } finally { db.close() }
    }

    @Test fun confirmedAuthDeletionSurvivesLocalCleanupFailureAndCanRecoverAfterRestart() = runTest {
        val prefs = AppPreferences(ApplicationProvider.getApplicationContext())
        val receipt = PendingAccountDeletion("deleted", "12345678-1234-4234-8234-123456789abc", cloudCleared = true, authDeleted = true)
        prefs.setPendingAccountDeletion(receipt)
        var cleanupCalls = 0
        var fail = true
        fun manager() = AccountDeletionManager(null, null, prefs, backgroundScope, {}, {
            cleanupCalls++
            if (fail) throw IOException("local storage unavailable")
        })
        try {
            val first = manager(); first.restoreIntent(); first.checkPending()
            assertEquals(receipt, prefs.pendingAccountDeletion.first())
            fail = false
            val restarted = manager(); restarted.restoreIntent(); restarted.checkPending()
            assertEquals(2, cleanupCalls)
            assertNull(prefs.pendingAccountDeletion.first())
            assertNull(restarted.pending.value)
        } finally { prefs.setPendingAccountDeletion(null) }
    }

    @Test fun missingAuthSessionNeverClaimsDeletionOrClearsTheDurableIntent() = runTest {
        val prefs = AppPreferences(ApplicationProvider.getApplicationContext())
        val receipt = PendingAccountDeletion("deleted", "12345678-1234-4234-8234-123456789abc")
        prefs.setPendingAccountDeletion(receipt)
        var erased = false
        try {
            val manager = AccountDeletionManager(null, null, prefs, backgroundScope, {}, { erased = true })
            manager.restoreIntent(); manager.checkPending()
            assertFalse(erased)
            assertEquals(receipt, prefs.pendingAccountDeletion.first())
            assertFalse(manager.state.value.busy)
            assertNotNull(manager.state.value.message)
        } finally { prefs.setPendingAccountDeletion(null) }
    }
}
