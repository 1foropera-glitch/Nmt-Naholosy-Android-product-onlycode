package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.ActiveProfile
import com.example.data.AppDatabase
import com.example.data.LocalProfile
import com.example.data.SessionRecord
import com.example.data.SessionRepository
import com.example.data.Word
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Uses a separate disposable database; never modifies the learner's progress. */
@RunWith(AndroidJUnit4::class)
class SessionPersistenceDeviceTest {
    @Test fun committedAnswerSurvivesDatabaseReopenAndCannotBeCountedTwice() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "device-session-${UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open()
        try {
            db.syncDao().saveProfile(LocalProfile("test"))
            db.syncDao().activate(ActiveProfile(profileId = "test"))
            val word = Word(id = 1, stressed = "вОдний", plain = "водний", normalized = "водний",
                stressPositions = "1", letter = "В", note = null, source = "test")
            db.wordDao().insertWords(listOf(word))
            val repository = SessionRepository(db, { "device-test" }, { "test" })
            val session = requireNotNull(repository.start("test", "practice", listOf(word)))
            assertTrue(repository.answer("test", session.id, 0, word.stressed))
            db.close()
            db = open()
            val reopened = SessionRepository(db, { "device-test" }, { "test" })
            assertFalse(reopened.answer("test", session.id, 0, word.stressed))
            assertEquals(1, db.wordDao().getWordState(1, "test")?.correctCount)
            assertEquals(1, db.syncDao().pendingCount("test"))
            val record = requireNotNull(db.syncDao().session("test"))
            assertEquals(word.stressed, SessionRecord.adapter.fromJson(record.json)?.selectedAnswer)
            reopened.next("test", session.id, 0)
            reopened.next("test", session.id, 0)
            assertEquals(1, SessionRecord.adapter.fromJson(requireNotNull(db.syncDao().session("test")).json)?.currentIndex)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
