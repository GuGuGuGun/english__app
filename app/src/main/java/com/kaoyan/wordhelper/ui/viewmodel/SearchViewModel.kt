package com.kaoyan.wordhelper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaoyan.wordhelper.KaoyanWordApp
import com.kaoyan.wordhelper.data.entity.Book
import com.kaoyan.wordhelper.data.entity.Progress
import com.kaoyan.wordhelper.data.entity.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchWordItem(
    val word: Word,
    val isInNewWords: Boolean,
    val progress: Progress?
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as KaoyanWordApp).repository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val activeBookFlow: Flow<Book?> = repository.getActiveBookFlow()

    val results: StateFlow<List<SearchWordItem>> = combine(
        _query.debounce(200),
        activeBookFlow
    ) { text, activeBook ->
        text to activeBook
    }
        .mapLatest { (text, activeBook) ->
            val trimmed = text.trim()
            if (trimmed.isBlank()) {
                emptyList()
            } else {
                withContext(Dispatchers.IO) {
                    val scopedBookId = if (activeBook?.type == Book.TYPE_NEW_WORDS) null else activeBook?.id
                    repository.searchWords(trimmed, scopedBookId).map { word ->
                        val progressBookId = activeBook?.id ?: word.bookId
                        word to repository.getProgress(word.id, progressBookId)
                    }
                }
            }
        }
        .combine(repository.getNewWordIdsFlow()) { words, newWordIds ->
            words.map { (word, progress) ->
                SearchWordItem(
                    word = word,
                    isInNewWords = newWordIds.contains(word.id),
                    progress = progress
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateQuery(text: String) {
        _query.value = text
    }

    fun toggleNewWord(word: Word, isInNewWords: Boolean) {
        viewModelScope.launch {
            if (isInNewWords) {
                repository.removeFromNewWords(word.id)
            } else {
                repository.addToNewWords(word.id)
            }
        }
    }
}
