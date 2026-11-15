package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SessionRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var sessions: SessionRepository
    private val word = Word(id = 1, stressed = "вОдний", plain = "водний", normalized = "водний",
        stressPositions = "1", letter = "В", note = null, source = "test")
    @Before fun setup() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        db.syncDao().saveProfile(LocalProfile("user"))
        db.syncDao().activate(ActiveProfile(profileId = "user"))
        db.wordDao().insertWords(listOf(word))
        sessions = SessionRepository(db, { "device-a" }, { "user" })
    }
    @After fun close() { db.close() }
    @Test fun pendingResetIsPersistentVisibleAndBlocksNewAnswers() = runTest {
        val session = requireNotNull(sessions.start("user", "practice", listOf(word)))
        val profiles = ProfileRepository(db, AppPreferences(ApplicationProvider.getApplicationContext()))
        val first = profiles.requestReset("user")
        val second = profiles.requestReset("user")
        assertNotNull(first.pendingResetId)
        assertEquals(first.pendingResetId, second.pendingResetId)
        assertEquals(first.pendingResetId, db.syncDao().observePendingReset().first())
        assertTrue(runCatching { sessions.answer("user", session.id, 0, word.stressed) }.exceptionOrNull() is IllegalArgumentException)
        assertNull(db.wordDao().getWordState(word.id, "user"))
    }
    @Test fun duplicateAnswerAfterRepositoryRecreationCannotIncrementProgressTwice() = runTest {
        val session = requireNotNull(sessions.start("user", "practice", listOf(word)))
        assertTrue(sessions.answer("user", session.id, 0, word.stressed))
        val recreated = SessionRepository(db, { "device-a" }, { "user" })
        assertFalse(recreated.answer("user", session.id, 0, word.stressed))
        assertEquals(1, db.wordDao().getWordState(1, "user")?.correctCount)
        assertEquals(1, db.syncDao().pendingCount("user"))
        val persisted = requireNotNull(db.syncDao().session("user"))
        assertEquals(word.stressed, SessionRecord.adapter.fromJson(persisted.json)?.selectedAnswer)
    }
    @Test fun invalidAnswerRollsBackAllLocalChangesAndStaleProfileCannotSubmit() = runTest {
        val session = requireNotNull(sessions.start("user", "practice", listOf(word)))
        runCatching { sessions.answer("user", session.id, 0, "invalid") }.also { assertTrue(it.isFailure) }
        assertNull(db.wordDao().getWordState(1, "user"))
        assertEquals(0, db.syncDao().pendingCount("user"))
        assertFalse(sessions.answer("other", session.id, 0, word.stressed))
    }
    @Test fun nextQuestionIsIdempotentAndCannotSkipUnansweredQuestion() = runTest {
        val session = requireNotNull(sessions.start("user", "review", listOf(word)))
        sessions.next("user", session.id, 0)
        assertEquals(0, SessionRecord.adapter.fromJson(requireNotNull(db.syncDao().session("user")).json)?.currentIndex)
        sessions.answer("user", session.id, 0, word.stressed)
        sessions.next("user", session.id, 0)
        sessions.next("user", session.id, 0)
        assertEquals(1, SessionRecord.adapter.fromJson(requireNotNull(db.syncDao().session("user")).json)?.currentIndex)
    }
}
