package com.example.data

import androidx.room.withTransaction
import com.example.domain.SrsPolicy
import com.example.util.sessionUpdatesSrs
import kotlinx.coroutines.flow.map

/** Room is the only durable session state. Commands carry identity to reject stale UI events. */
class SessionRepository(private val db: AppDatabase, private val installId: () -> String,
                        private val accountProfile: () -> String) {
    val session = db.syncDao().observeSession().map { stored ->
        stored?.let { it.profileId to requireNotNull(SessionRecord.adapter.fromJson(it.json)) }
    }

    suspend fun start(expectedProfile: String, mode: String, words: List<Word>, early: Boolean = false): SessionRecord? = db.withTransaction {
        val profile = currentProfile()
        if (profile.id != expectedProfile) return@withTransaction null
        require(profile.pendingResetId == null) { "Скидання прогресу очікує з’єднання з хмарою." }
        read(profile.id)?.let { return@withTransaction it }
        if (words.isEmpty()) return@withTransaction null
        SessionRecord(mode = mode, wordIds = words.map { it.id }, isEarlyReview = early).also { save(profile.id, it) }
    }

    suspend fun answer(profileId: String, sessionId: String, index: Int, answer: String): Boolean = db.withTransaction {
        val profile = currentProfile()
        if (profile.id != profileId) return@withTransaction false
        require(profile.pendingResetId == null) { "Скидання прогресу очікує з’єднання з хмарою." }
        val session = read(profile.id) ?: return@withTransaction false
        if (session.id != sessionId || session.currentIndex != index || session.selectedAnswer != null) return@withTransaction false
        val wordId = session.wordIds.getOrNull(index) ?: return@withTransaction false
        val word = db.wordDao().getWordById(wordId) ?: return@withTransaction false
        require(answer in com.example.util.StressUtils.getAnswerVariants(word.plain, word.stressed))
        val correct = answer == word.stressed
        if (sessionUpdatesSrs(session.mode)) {
            val state = SrsPolicy.answer(db.wordDao().getWordState(word.id, profile.id), word.id,
                correct, System.currentTimeMillis(), "${installId()}_${profile.writerId}", profile.id)
            db.wordDao().insertWordState(state)
            db.syncDao().enqueue(PendingProgress(profile.id, word.id, state.revision))
        }
        save(profile.id, session.copy(selectedAnswer = answer, isCorrectAnswer = correct,
            correctCount = session.correctCount + if (correct) 1 else 0,
            wrongCount = session.wrongCount + if (correct) 0 else 1))
        true
    }

    suspend fun next(profileId: String, sessionId: String, index: Int) = db.withTransaction {
        val profile = currentProfile()
        if (profile.id != profileId) return@withTransaction
        require(profile.pendingResetId == null) { "Скидання прогресу очікує з’єднання з хмарою." }
        val session = read(profile.id) ?: return@withTransaction
        if (session.id == sessionId && session.currentIndex == index && session.selectedAnswer != null) {
            save(profile.id, session.copy(currentIndex = index + 1, selectedAnswer = null, isCorrectAnswer = null))
        }
    }

    suspend fun discard(profileId: String, sessionId: String) = db.withTransaction {
        if (currentProfile().id == profileId && read(profileId)?.id == sessionId) db.syncDao().clearSession(profileId)
    }

    private suspend fun currentProfile(): LocalProfile {
        val id = db.syncDao().active()?.profileId ?: error("Профіль ще завантажується.")
        check(id == accountProfile()) { "Профіль змінився. Спробуйте ще раз." }
        return requireNotNull(db.syncDao().profile(id))
    }
    private suspend fun read(id: String): SessionRecord? = db.syncDao().session(id)?.let { SessionRecord.adapter.fromJson(it.json) }
    private suspend fun save(id: String, record: SessionRecord) = db.syncDao().saveSession(StoredSession(id, SessionRecord.adapter.toJson(record)))
}
