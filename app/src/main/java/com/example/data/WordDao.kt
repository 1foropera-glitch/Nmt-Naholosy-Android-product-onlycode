package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT COUNT(*) FROM words WHERE isActive = 1")
    suspend fun getWordsCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<Word>)
    @Query("SELECT * FROM words WHERE isActive = 1 ORDER BY plain ASC")
    fun getAllWords(): Flow<List<Word>>
    @Query("SELECT * FROM words WHERE isActive = 1 AND (normalized LIKE :query OR plain LIKE :query) ORDER BY plain ASC")
    fun searchWords(query: String): Flow<List<Word>>
    @Query("SELECT * FROM words WHERE isActive = 1 AND letter = :letter ORDER BY plain ASC")
    fun getWordsByLetter(letter: String): Flow<List<Word>>
    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWordById(id: Int): Word?
    @Query("SELECT * FROM words WHERE isActive = 1 ORDER BY id")
    suspend fun getAllWordsSnapshot(): List<Word>
    @Query("SELECT * FROM words ORDER BY id")
    suspend fun getCatalogIncludingRetired(): List<Word>
    @Query("SELECT * FROM words ORDER BY id")
    fun observeCatalogIncludingRetired(): Flow<List<Word>>
    @Query("UPDATE words SET isActive = 0")
    suspend fun retireCatalog()
    @Query("""SELECT w.* FROM words w LEFT JOIN word_states s
        ON w.id = s.wordId AND s.profileId = (SELECT profileId FROM active_profile WHERE singleton = 1)
        WHERE w.isActive = 1 AND s.wordId IS NULL ORDER BY RANDOM() LIMIT :limit""")
    suspend fun getNewRandomWords(limit: Int): List<Word>
    @Query("""SELECT w.* FROM words w JOIN word_states s ON w.id = s.wordId
        WHERE w.isActive = 1 AND s.profileId = (SELECT profileId FROM active_profile WHERE singleton = 1)
        AND s.status = 'learning' AND s.dueAt <= :now ORDER BY s.dueAt LIMIT :limit""")
    suspend fun getDueWords(now: Long, limit: Int): List<Word>
    @Query("""SELECT wordId FROM word_states WHERE wordId IN (SELECT id FROM words WHERE isActive = 1) AND profileId = (SELECT profileId FROM active_profile WHERE singleton = 1)
        AND status = 'learning' AND dueAt IS NOT NULL AND dueAt <= :eligibleBefore""")
    suspend fun getDueReviewWordIds(eligibleBefore: Long): List<Int>
    @Query("""SELECT COUNT(*) FROM word_states WHERE wordId IN (SELECT id FROM words WHERE isActive = 1) AND profileId = (SELECT profileId FROM active_profile WHERE singleton = 1)
        AND status = 'learning' AND dueAt IS NOT NULL AND dueAt <= :eligibleBefore""")
    suspend fun getDueReviewCount(eligibleBefore: Long): Int
    @Query("""SELECT w.* FROM words w JOIN word_states s ON w.id = s.wordId
        WHERE w.isActive = 1 AND s.profileId = (SELECT profileId FROM active_profile WHERE singleton = 1)
        AND s.status = 'learning' ORDER BY s.dueAt LIMIT :limit""")
    suspend fun getAllLearningWords(limit: Int): List<Word>
    @Query("SELECT * FROM word_states WHERE profileId = :profileId AND wordId = :wordId")
    suspend fun getWordState(wordId: Int, profileId: String): WordState?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordState(state: WordState)
    @Query("SELECT * FROM word_states WHERE profileId = :profileId")
    suspend fun statesForProfile(profileId: String): List<WordState>
    @Query("SELECT * FROM word_states WHERE wordId IN (SELECT id FROM words WHERE isActive = 1) AND profileId = (SELECT profileId FROM active_profile WHERE singleton = 1)")
    suspend fun getAllWordStatesSnapshot(): List<WordState>
    @Query("SELECT * FROM word_states WHERE wordId IN (SELECT id FROM words WHERE isActive = 1) AND profileId = (SELECT profileId FROM active_profile WHERE singleton = 1)")
    fun getAllWordStates(): Flow<List<WordState>>
    @Query("DELETE FROM word_states WHERE profileId = :profileId")
    suspend fun clearProfileProgress(profileId: String)
}
