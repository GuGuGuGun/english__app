package com.kaoyan.wordhelper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.kaoyan.wordhelper.KaoyanWordApp
import com.kaoyan.wordhelper.data.entity.Book
import com.kaoyan.wordhelper.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import kotlin.math.min

data class TodayNewWordCandidate(
    val wordId: Long,
    val word: String,
    val phonetic: String,
    val meaning: String
)

data class TodayNewWordsSelectUiState(
    val loading: Boolean = false,
    val books: List<Book> = emptyList(),
    val activeBookId: Long? = null,
    val selectedBookId: Long? = null,
    val dailyLimit: Int = 20,
    val query: String = "",
    val candidates: List<TodayNewWordCandidate> = emptyList(),
    val selectedWordIds: Set<Long> = emptySet()
) {
    val selectedCount: Int
        get() = selectedWordIds.size
}

private data class TodayNewWordsSelectBaseState(
    val loading: Boolean,
    val books: List<Book>,
    val activeBookId: Long?,
    val selectedBookId: Long?,
    val dailyLimit: Int
)

class TodayNewWordsSelectViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val repository = (application as KaoyanWordApp).repository
    private val settingsRepository = (application as KaoyanWordApp).settingsRepository

    private val initialBookId: Long? = savedStateHandle.get<Long>("bookId")

    private val _selectedBookId = MutableStateFlow(initialBookId)
    private val _query = MutableStateFlow("")
    private val _loading = MutableStateFlow(false)
    private val _allCandidates = MutableStateFlow<List<TodayNewWordCandidate>>(emptyList())
    private val _selectedWordIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _savedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val savedEvent = _savedEvent.asSharedFlow()

    private val books: StateFlow<List<Book>> = repository.getAllBooks()
        .map { items ->
            items.filter { it.type != Book.TYPE_NEW_WORDS }
                .sortedWith(compareBy<Book> { it.type }.thenBy { it.id })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val activeBookId: StateFlow<Long?> = repository.getActiveBookFlow()
        .map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val dailyLimit: StateFlow<Int> = settingsRepository.settingsFlow
        .map { it.newWordsLimit.coerceAtLeast(1) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 20)

    private val baseState: StateFlow<TodayNewWordsSelectBaseState> = combine(
        _loading,
        books,
        activeBookId,
        _selectedBookId,
        dailyLimit
    ) { loading, books, activeBookId, selectedBookId, dailyLimit ->
        TodayNewWordsSelectBaseState(
            loading = loading,
            books = books,
            activeBookId = activeBookId,
            selectedBookId = selectedBookId,
            dailyLimit = dailyLimit
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TodayNewWordsSelectBaseState(
            loading = false,
            books = emptyList(),
            activeBookId = null,
            selectedBookId = null,
            dailyLimit = 20
        )
    )

    val uiState: StateFlow<TodayNewWordsSelectUiState> = combine(
        baseState,
        _query,
        _allCandidates,
        _selectedWordIds
    ) { base, query, allCandidates, selectedWordIds ->
        val filtered = allCandidates.filter { candidate ->
            val keyword = query.trim()
            keyword.isBlank() ||
                candidate.word.contains(keyword, ignoreCase = true) ||
                candidate.meaning.contains(keyword, ignoreCase = true)
        }
        TodayNewWordsSelectUiState(
            loading = base.loading,
            books = base.books,
            activeBookId = base.activeBookId,
            selectedBookId = base.selectedBookId,
            dailyLimit = base.dailyLimit,
            query = query,
            candidates = filtered,
            selectedWordIds = selectedWordIds
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayNewWordsSelectUiState())

    init {
        viewModelScope.launch {
            if (_selectedBookId.value == null) {
                _selectedBookId.value = repository.getActiveBook()?.id
            }
        }

        viewModelScope.launch {
            combine(_selectedBookId, books) { selected, books ->
                when {
                    books.isEmpty() -> null
                    selected != null && books.any { it.id == selected } -> selected
                    else -> books.first().id
                }
            }
                .distinctUntilChanged()
                .collect { normalizedBookId ->
                    if (_selectedBookId.value != normalizedBookId) {
                        _selectedBookId.value = normalizedBookId
                    }
                    if (normalizedBookId == null) {
                        _allCandidates.value = emptyList()
                        _selectedWordIds.value = emptySet()
                    } else {
                        loadCandidates(normalizedBookId)
                    }
                }
        }
    }

    fun selectBook(bookId: Long) {
        _selectedBookId.value = bookId
    }

    fun updateQuery(query: String) {
        _query.value = query
    }

    fun toggleWord(wordId: Long) {
        val selected = _selectedWordIds.value.toMutableSet()
        if (!selected.add(wordId)) {
            selected.remove(wordId)
        }
        _selectedWordIds.value = selected
    }

    fun clearSelection() {
        _selectedWordIds.value = emptySet()
    }

    fun randomSelect() {
        val state = uiState.value
        val poolIds = state.candidates.map { it.wordId }
        if (poolIds.isEmpty()) {
            _selectedWordIds.value = emptySet()
            return
        }
        val targetCount = min(state.dailyLimit, poolIds.size)
        _selectedWordIds.value = poolIds.shuffled().take(targetCount).toSet()
    }

    fun savePlanAndSwitchBook() {
        val bookId = _selectedBookId.value ?: return
        val selectedIds = _selectedWordIds.value.toList()
        viewModelScope.launch {
            if (selectedIds.isEmpty()) {
                settingsRepository.clearTodayNewWordsPlan()
            } else {
                settingsRepository.saveTodayNewWordsPlan(bookId, selectedIds)
            }
            repository.switchBook(bookId)
            _message.value = if (selectedIds.isEmpty()) {
                "已清空今日新词计划"
            } else {
                val date = DateUtils.currentLearningDate().format(DATE_FORMATTER)
                "已设置$date 今日新词 ${selectedIds.size} 个，并切换到所选词书"
            }
            _savedEvent.tryEmit(Unit)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private suspend fun loadCandidates(bookId: Long) {
        _loading.value = true
        val words = withContext(Dispatchers.IO) { repository.getWordsByBookList(bookId) }
        val progressByWordId = withContext(Dispatchers.IO) {
            repository.getGlobalProgressMap(words.map { it.id })
        }
        val unlearned = words
            .filter { progressByWordId[it.id] == null }
            .map { word ->
                TodayNewWordCandidate(
                    wordId = word.id,
                    word = word.word,
                    phonetic = word.phonetic,
                    meaning = word.meaning
                )
            }
        _allCandidates.value = unlearned

        val plan = settingsRepository.todayNewWordsPlanFlow.first()
        val learningDate = DateUtils.currentLearningDate().format(DATE_FORMATTER)
        val candidateIds = unlearned.map { it.wordId }.toSet()
        _selectedWordIds.value = if (plan.learningDate == learningDate && plan.bookId == bookId) {
            plan.wordIds.toSet().intersect(candidateIds)
        } else {
            emptySet()
        }
        _loading.value = false
    }

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
