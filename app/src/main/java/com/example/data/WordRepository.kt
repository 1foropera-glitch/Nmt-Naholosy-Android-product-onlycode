package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class WordRepository(private val db: AppDatabase, private val catalog: DictionaryCatalog) {
    private val dao = db.wordDao()
    val allWords: Flow<List<Word>> = dao.getAllWords()
    val allWordStates: Flow<List<WordState>> = dao.getAllWordStates()

    suspend fun initializeCatalog() = catalog.update(db)
    fun searchWords(query: String) = dao.searchWords("%${query.trim().lowercase()}%")
    fun getWordsByLetter(letter: String) = dao.getWordsByLetter(letter.uppercase())
    suspend fun getWordById(id: Int) = dao.getWordById(id)
    suspend fun getAllWordsSnapshot() = dao.getAllWordsSnapshot()
    suspend fun getAllWordStatesSnapshot() = dao.getAllWordStatesSnapshot()
    suspend fun getNewRandomWords(limit: Int) = dao.getNewRandomWords(limit)
    suspend fun getDueWords(limit: Int) = dao.getDueWords(System.currentTimeMillis(), limit)
    suspend fun getAllLearningWords(limit: Int) = dao.getAllLearningWords(limit)
}
