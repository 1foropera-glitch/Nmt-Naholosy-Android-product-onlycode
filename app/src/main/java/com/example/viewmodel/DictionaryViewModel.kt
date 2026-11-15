package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Word
import com.example.data.WordRepository
import com.example.data.WordState
import com.example.util.StressUtils
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*

class DictionaryViewModel(repository: WordRepository) : ViewModel(), AutoCloseable {
    val searchQuery = MutableStateFlow("")
    val selectedLetter = MutableStateFlow<String?>(null)
    val dictionaryWords = combine(repository.allWords, searchQuery, selectedLetter) { words, query, letter ->
        val normalized = StressUtils.normalizeWord(query)
        words.filter { (letter == null || it.letter.equals(letter, true)) &&
            (normalized.isBlank() || StressUtils.normalizeWord(it.plain).contains(normalized) || it.normalized.contains(normalized)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Word>())
    val allWordStates = repository.allWordStates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<WordState>())
    fun onSearchQueryChanged(query: String) { searchQuery.value = query }
    fun onLetterSelected(letter: String?) { selectedLetter.value = letter }
    override fun close() { viewModelScope.cancel() }
}
