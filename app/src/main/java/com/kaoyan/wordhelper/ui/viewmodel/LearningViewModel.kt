package com.kaoyan.wordhelper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaoyan.wordhelper.KaoyanWordApp
import com.kaoyan.wordhelper.data.entity.Book
import com.kaoyan.wordhelper.data.model.AIContentType
import com.kaoyan.wordhelper.data.entity.Progress
import com.kaoyan.wordhelper.data.entity.Word
import com.kaoyan.wordhelper.data.model.SpellingOutcome
import com.kaoyan.wordhelper.data.model.StudyRating
import com.kaoyan.wordhelper.data.repository.AddToNewWordsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class LearningAiState(
    val isEnabled: Boolean = false,
    val isConfigured: Boolean = false,
    val isLoading: Boolean = false,
    val activeType: AIContentType? = null,
    val content: String = "",
    val error: String? = null
) {
    val isAvailable: Boolean
        get() = isEnabled && isConfigured
}

data class SwipeSnackbarEvent(
    val id: Long,
    val message: String,
    val actionLabel: String? = null,
    val undoToken: Long? = null
)

private data class TooEasyUndoPayload(
    val wordId: Long,
    val snapshots: List<Progress>
)

@OptIn(ExperimentalCoroutinesApi::class)
class LearningViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as KaoyanWordApp).repository
    private val settingsRepository = (application as KaoyanWordApp).settingsRepository
    private val aiConfigRepository = (application as KaoyanWordApp).aiConfigRepository
    private val aiRepository = (application as KaoyanWordApp).aiRepository

    private val immediateRetryQueue = ArrayDeque<Word>()
    private val sessionSkippedWordIds = LinkedHashSet<Long>()
    private val pendingTooEasyUndo = mutableMapOf<Long, TooEasyUndoPayload>()
    private var swipeSnackbarIdCounter = 0L
    private var undoTokenCounter = 0L
    private var queueBookId: Long? = null
    private var pendingFailedWordId: Long? = null
    private var answeredInSession = 0
    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _dueReviewCount = MutableStateFlow(0)
    val dueReviewCount: StateFlow<Int> = _dueReviewCount.asStateFlow()
    private val _aiState = MutableStateFlow(LearningAiState())
    val aiState: StateFlow<LearningAiState> = _aiState.asStateFlow()
    private val _cachedExampleContent = MutableStateFlow("")
    val cachedExampleContent: StateFlow<String> = _cachedExampleContent.asStateFlow()
    private val _cachedMemoryAidContent = MutableStateFlow("")
    val cachedMemoryAidContent: StateFlow<String> = _cachedMemoryAidContent.asStateFlow()
    private val _memoryAidSuggestionWordId = MutableStateFlow<Long?>(null)
    val memoryAidSuggestionWordId: StateFlow<Long?> = _memoryAidSuggestionWordId.asStateFlow()
    private val _swipeSnackbarEvent = MutableStateFlow<SwipeSnackbarEvent?>(null)
    val swipeSnackbarEvent: StateFlow<SwipeSnackbarEvent?> = _swipeSnackbarEvent.asStateFlow()

    val showSwipeGuide: StateFlow<Boolean> = settingsRepository.settingsFlow
        .map { !it.swipeGestureGuideShown }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val books: StateFlow<List<Book>> = repository.getAllBooks()
        .map { list ->
            list.sortedWith(
                compareByDescending<Book> { it.type == Book.TYPE_NEW_WORDS }
                    .thenBy { it.type }
                    .thenBy { it.id }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeBook: StateFlow<Book?> = repository.getActiveBookFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _currentWord = MutableStateFlow<Word?>(null)
    val currentWord: StateFlow<Word?> = _currentWord.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    val isNewWordsBook: StateFlow<Boolean> = activeBook
        .map { it?.type == Book.TYPE_NEW_WORDS }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isInNewWords: StateFlow<Boolean> = repository.getNewWordIdsFlow()
        .combine(_currentWord) { ids, word -> word?.let { ids.contains(it.id) } ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val currentProgress: StateFlow<Progress?> = combine(activeBook, _currentWord) { book, word -> book to word }
        .mapLatest { (book, word) ->
            if (book == null || word == null) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    repository.getProgress(word.id, book.id)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val newWordsLimit: StateFlow<Int> = settingsRepository.settingsFlow
        .map { it.newWordsLimit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_NEW_WORDS_LIMIT)
    val reviewPressureReliefEnabled: StateFlow<Boolean> = settingsRepository.settingsFlow
        .map { it.reviewPressureReliefEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val reviewPressureDailyCap: StateFlow<Int> = settingsRepository.settingsFlow
        .map { it.reviewPressureDailyCap.coerceIn(10, 500) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 120)
    val canRelieveReviewPressure: StateFlow<Boolean> = combine(
        activeBook,
        reviewPressureReliefEnabled,
        reviewPressureDailyCap,
        _dueReviewCount
    ) { book, enabled, cap, dueCount ->
        enabled &&
            book != null &&
            book.type != Book.TYPE_NEW_WORDS &&
            dueCount > cap
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        refreshAiAvailability()
        viewModelScope.launch {
            combine(activeBook, newWordsLimit) { book, limit -> book to limit }
                .collect { (book, limit) ->
                    if (pendingFailedWordId != null) return@collect
                    loadQueueForBook(book, limit)
                }
        }
        viewModelScope.launch {
            repository.getNewWordIdsFlow().collect {
                if (pendingFailedWordId != null) return@collect
                val book = activeBook.value
                if (book?.type == Book.TYPE_NEW_WORDS) {
                    loadQueueForBook(book, newWordsLimit.value)
                }
            }
        }
        viewModelScope.launch {
            activeBook
                .flatMapLatest { book ->
                    if (book == null) {
                        flowOf(0)
                    } else {
                        repository.getEarlyReviewCountFlow(book.id)
                    }
                }
                .collect {
                    if (pendingFailedWordId != null) return@collect
                    val book = activeBook.value
                    if (book != null) {
                        loadQueueForBook(book, newWordsLimit.value)
                    }
                }
        }
    }

    fun switchBook(bookId: Long) {
        immediateRetryQueue.clear()
        sessionSkippedWordIds.clear()
        pendingTooEasyUndo.clear()
        queueBookId = null
        pendingFailedWordId = null
        answeredInSession = 0
        viewModelScope.launch {
            repository.switchBook(bookId)
        }
    }


    fun toggleNewWord() {
        val word = _currentWord.value ?: return
        viewModelScope.launch {
            val exists = repository.isInNewWords(word.id)
            if (exists) {
                repository.removeFromNewWords(word.id)
            } else {
                repository.addToNewWords(word.id, AddToNewWordsSource.BUTTON)
            }
        }
    }

    fun submitAnswer(rating: StudyRating) {
        val word = _currentWord.value ?: return
        val book = activeBook.value ?: return
        launchSubmit {
            pendingFailedWordId = null
            removeFromImmediateQueue(word.id)
            removeSessionSkippedWord(word.id)
            withContext(Dispatchers.IO) {
                repository.applyStudyResult(word.id, book.id, rating)
            }
            if (rating == StudyRating.AGAIN) {
                enqueueImmediateRetry(word)
                _memoryAidSuggestionWordId.value = word.id
            } else {
                _memoryAidSuggestionWordId.value = null
                markAnswered()
            }
            loadQueueForBook(book, newWordsLimit.value)
        }
    }

    fun submitAnswerAndRemoveFromNewWords(rating: StudyRating) {
        val word = _currentWord.value ?: return
        val book = activeBook.value ?: return
        launchSubmit {
            pendingFailedWordId = null
            _memoryAidSuggestionWordId.value = null
            removeFromImmediateQueue(word.id)
            removeSessionSkippedWord(word.id)
            withContext(Dispatchers.IO) {
                repository.removeFromNewWords(word.id)
                repository.applyStudyResult(word.id, book.id, rating)
            }
            markAnswered()
            loadQueueForBook(book, newWordsLimit.value)
        }
    }

    fun submitSpellingOutcome(
        outcome: SpellingOutcome,
        attemptCount: Int,
        durationMillis: Long
    ) {
        val word = _currentWord.value ?: return
        val book = activeBook.value ?: return
        launchSubmit {
            if (outcome == SpellingOutcome.FAILED) {
                pendingFailedWordId = word.id
                _memoryAidSuggestionWordId.value = word.id
            } else {
                pendingFailedWordId = null
                _memoryAidSuggestionWordId.value = null
            }
            removeFromImmediateQueue(word.id)
            removeSessionSkippedWord(word.id)
            withContext(Dispatchers.IO) {
                repository.applySpellingOutcome(
                    wordId = word.id,
                    bookId = book.id,
                    outcome = outcome,
                    attemptCount = attemptCount,
                    durationMillis = durationMillis
                )
            }
            if (outcome == SpellingOutcome.FAILED) {
                return@launchSubmit
            }
            pendingFailedWordId = null
            markAnswered()
            loadQueueForBook(book, newWordsLimit.value)
        }
    }

    fun continueAfterSpellingFailure() {
        val currentWord = _currentWord.value ?: return
        val book = activeBook.value ?: return
        if (_isSubmitting.value) return
        if (pendingFailedWordId != currentWord.id) return
        pendingFailedWordId = null
        viewModelScope.launch {
            enqueueImmediateRetry(currentWord)
            markAnswered()
            loadQueueForBook(book, newWordsLimit.value)
        }
    }

    fun onSwipeTooEasy() {
        val word = _currentWord.value ?: return
        val book = activeBook.value ?: return
        launchSubmit {
            pendingFailedWordId = null
            _memoryAidSuggestionWordId.value = null
            removeFromImmediateQueue(word.id)
            removeSessionSkippedWord(word.id)
            val snapshots = withContext(Dispatchers.IO) {
                repository.getProgressSnapshotsForWord(word.id)
            }
            val marked = withContext(Dispatchers.IO) {
                repository.markWordAsTooEasy(word.id, book.id)
            }
            if (!marked) {
                emitSwipeSnackbar("标记失败，请重试")
                return@launchSubmit
            }
            markAnswered()
            val undoToken = registerTooEasyUndo(word.id, snapshots)
            emitSwipeSnackbar(
                message = "已标记为太简单，30天后复习",
                actionLabel = "撤销",
                undoToken = undoToken
            )
            loadQueueForBook(book, newWordsLimit.value)
        }
    }

    fun onSwipeAddToNotebook() {
        val word = _currentWord.value ?: return
        val book = activeBook.value ?: return
        launchSubmit {
            pendingFailedWordId = null
            _memoryAidSuggestionWordId.value = null
            removeFromImmediateQueue(word.id)
            removeSessionSkippedWord(word.id)
            val inserted = withContext(Dispatchers.IO) {
                repository.addToNewWords(word.id, AddToNewWordsSource.GESTURE)
            }
            skipWordInSession(word.id)
            markAnswered()
            emitSwipeSnackbar(
                message = if (inserted) "已加入生词本" else "已在生词本中"
            )
            loadQueueForBook(book, newWordsLimit.value)
        }
    }

    fun undoSwipeTooEasy(undoToken: Long) {
        val payload = pendingTooEasyUndo[undoToken] ?: return
        launchSubmit {
            withContext(Dispatchers.IO) {
                repository.restoreProgressSnapshots(
                    wordId = payload.wordId,
                    snapshots = payload.snapshots,
                    rollbackGestureEasy = true
                )
            }
            pendingTooEasyUndo.remove(undoToken)
            answeredInSession = (answeredInSession - 1).coerceAtLeast(0)
            val book = activeBook.value
            if (book != null) {
                loadQueueForBook(book, newWordsLimit.value)
            }
            emitSwipeSnackbar("已撤销“太简单”")
        }
    }

    fun dismissSwipeUndo(undoToken: Long) {
        pendingTooEasyUndo.remove(undoToken)
    }

    fun consumeSwipeSnackbarEvent(eventId: Long) {
        if (_swipeSnackbarEvent.value?.id == eventId) {
            _swipeSnackbarEvent.value = null
        }
    }

    fun dismissSwipeGuide() {
        viewModelScope.launch {
            settingsRepository.updateSwipeGestureGuideShown(true)
        }
    }

    fun refreshQueue() {
        val book = activeBook.value ?: return
        viewModelScope.launch {
            pendingFailedWordId = null
            sessionSkippedWordIds.clear()
            answeredInSession = 0
            loadQueueForBook(book, newWordsLimit.value)
        }
    }

    fun disperseReviewPressure() {
        val book = activeBook.value ?: return
        if (book.type == Book.TYPE_NEW_WORDS) return
        if (!reviewPressureReliefEnabled.value) return
        if (_isSubmitting.value) return
        val dailyCap = reviewPressureDailyCap.value.coerceAtLeast(1)
        launchSubmit {
            val movedCount = withContext(Dispatchers.IO) {
                repository.disperseReviewPressure(book.id, dailyCap)
            }
            _message.value = if (movedCount > 0) {
                "已分散 $movedCount 个待复习单词到后续天"
            } else {
                "当前待复习数量未超过上限"
            }
            pendingFailedWordId = null
            loadQueueForBook(book, newWordsLimit.value)
        }
    }

    fun refreshAiAvailability() {
        viewModelScope.launch {
            val config = aiConfigRepository.getConfig()
            _aiState.value = _aiState.value.copy(
                isEnabled = config.enabled,
                isConfigured = config.isConfigured()
            )
        }
    }

    fun loadCachedAiForCurrentWord() {
        val word = _currentWord.value ?: run {
            _cachedExampleContent.value = ""
            _cachedMemoryAidContent.value = ""
            return
        }
        viewModelScope.launch {
            val cachedExample = withContext(Dispatchers.IO) {
                aiRepository.getCachedExample(word.id, word.word)
            }.orEmpty()
            val cachedMemoryAid = withContext(Dispatchers.IO) {
                aiRepository.getCachedMemoryAid(word.id, word.word)
            }.orEmpty()
            _cachedExampleContent.value = cachedExample
            _cachedMemoryAidContent.value = cachedMemoryAid
        }
    }

    fun requestAiExample(forceRefresh: Boolean = false) {
        val word = _currentWord.value ?: return
        viewModelScope.launch {
            val config = aiConfigRepository.getConfig()
            val enabled = config.enabled
            val configured = config.isConfigured()
            _aiState.value = _aiState.value.copy(
                isEnabled = enabled,
                isConfigured = configured
            )
            if (!enabled || !configured) {
                _aiState.value = _aiState.value.copy(
                    activeType = AIContentType.EXAMPLE,
                    error = "请先在 AI 实验室启用并配置 API Key",
                    content = "",
                    isLoading = false
                )
                return@launch
            }

            _aiState.value = _aiState.value.copy(
                activeType = AIContentType.EXAMPLE,
                isLoading = true,
                error = null
            )
            val result = withContext(Dispatchers.IO) {
                aiRepository.generateExample(
                    wordId = word.id,
                    word = word.word,
                    forceRefresh = forceRefresh
                )
            }
            result.fold(
                onSuccess = { content ->
                    _cachedExampleContent.value = content
                    _aiState.value = _aiState.value.copy(
                        activeType = AIContentType.EXAMPLE,
                        content = content,
                        error = null,
                        isLoading = false
                    )
                },
                onFailure = { throwable ->
                    _aiState.value = _aiState.value.copy(
                        activeType = AIContentType.EXAMPLE,
                        content = "",
                        error = throwable.message ?: "例句生成失败",
                        isLoading = false
                    )
                }
            )
        }
    }

    fun requestAiMemoryAid(forceRefresh: Boolean = false) {
        val word = _currentWord.value ?: return
        if (_memoryAidSuggestionWordId.value == word.id) {
            _memoryAidSuggestionWordId.value = null
        }
        viewModelScope.launch {
            val config = aiConfigRepository.getConfig()
            val enabled = config.enabled
            val configured = config.isConfigured()
            _aiState.value = _aiState.value.copy(
                isEnabled = enabled,
                isConfigured = configured
            )
            if (!enabled || !configured) {
                _aiState.value = _aiState.value.copy(
                    activeType = AIContentType.MEMORY_AID,
                    error = "请先在 AI 实验室启用并配置 API Key",
                    content = "",
                    isLoading = false
                )
                return@launch
            }

            _aiState.value = _aiState.value.copy(
                activeType = AIContentType.MEMORY_AID,
                isLoading = true,
                error = null
            )
            val result = withContext(Dispatchers.IO) {
                aiRepository.generateMemoryAid(
                    wordId = word.id,
                    word = word.word,
                    forceRefresh = forceRefresh
                )
            }
            result.fold(
                onSuccess = { content ->
                    _cachedMemoryAidContent.value = content
                    _aiState.value = _aiState.value.copy(
                        activeType = AIContentType.MEMORY_AID,
                        content = content,
                        error = null,
                        isLoading = false
                    )
                },
                onFailure = { throwable ->
                    _aiState.value = _aiState.value.copy(
                        activeType = AIContentType.MEMORY_AID,
                        content = "",
                        error = throwable.message ?: "助记生成失败",
                        isLoading = false
                    )
                }
            )
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private suspend fun loadQueueForBook(book: Book?, newWordsLimit: Int) {
        if (book == null) {
            immediateRetryQueue.clear()
            sessionSkippedWordIds.clear()
            pendingTooEasyUndo.clear()
            queueBookId = null
            pendingFailedWordId = null
            answeredInSession = 0
            _dueReviewCount.value = 0
            updateQueue(emptyList())
            return
        }
        if (queueBookId != book.id) {
            immediateRetryQueue.clear()
            sessionSkippedWordIds.clear()
            pendingTooEasyUndo.clear()
            queueBookId = book.id
            pendingFailedWordId = null
            answeredInSession = 0
        }
        val (queue, dueCount) = withContext(Dispatchers.IO) {
            val dueCount = repository.getDueWords(book.id).size
            val effectiveNewWordLimit = repository.estimateRemainingNewWordsToday(book, newWordsLimit)
            repository.getStudyQueue(book, effectiveNewWordLimit) to dueCount
        }
        _dueReviewCount.value = dueCount
        val mergedQueue = mergeWithImmediateRetryQueue(queue)
            .filterNot { it.id in sessionSkippedWordIds }
        updateQueue(mergedQueue)
    }

    private fun updateQueue(list: List<Word>) {
        val oldWordId = _currentWord.value?.id
        val remaining = list.size
        val total = answeredInSession + remaining
        _totalCount.value = total
        _currentIndex.value = when {
            total <= 0 -> 0
            remaining == 0 -> total
            else -> (answeredInSession + 1).coerceAtMost(total)
        }
        val nextWord = list.firstOrNull()
        _currentWord.value = nextWord
        if (oldWordId != nextWord?.id) {
            _cachedExampleContent.value = ""
            _cachedMemoryAidContent.value = ""
            _aiState.value = _aiState.value.copy(
                isLoading = false,
                activeType = null,
                content = "",
                error = null
            )
        }
    }

    private fun markAnswered() {
        answeredInSession += 1
    }

    private fun skipWordInSession(wordId: Long) {
        sessionSkippedWordIds.add(wordId)
    }

    private fun removeSessionSkippedWord(wordId: Long) {
        sessionSkippedWordIds.remove(wordId)
    }

    private fun emitSwipeSnackbar(
        message: String,
        actionLabel: String? = null,
        undoToken: Long? = null
    ) {
        swipeSnackbarIdCounter += 1
        _swipeSnackbarEvent.value = SwipeSnackbarEvent(
            id = swipeSnackbarIdCounter,
            message = message,
            actionLabel = actionLabel,
            undoToken = undoToken
        )
    }

    private fun registerTooEasyUndo(wordId: Long, snapshots: List<Progress>): Long {
        undoTokenCounter += 1
        val token = undoTokenCounter
        pendingTooEasyUndo[token] = TooEasyUndoPayload(
            wordId = wordId,
            snapshots = snapshots
        )
        return token
    }

    private fun launchSubmit(block: suspend () -> Unit) {
        if (_isSubmitting.value) return
        _isSubmitting.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    private fun enqueueImmediateRetry(word: Word) {
        removeFromImmediateQueue(word.id)
        immediateRetryQueue.addLast(word)
    }

    private fun removeFromImmediateQueue(wordId: Long) {
        if (immediateRetryQueue.isEmpty()) return
        val retained = immediateRetryQueue.filterNot { it.id == wordId }
        immediateRetryQueue.clear()
        immediateRetryQueue.addAll(retained)
    }

    private fun mergeWithImmediateRetryQueue(baseQueue: List<Word>): List<Word> {
        if (immediateRetryQueue.isEmpty()) return baseQueue
        val immediateIds = immediateRetryQueue.map { it.id }.toSet()
        val merged = baseQueue
            .filterNot { it.id in immediateIds }
            .toMutableList()

        immediateRetryQueue.forEach { retryWord ->
            val insertIndex = if (merged.size <= 5) {
                merged.size
            } else {
                val maxIndex = merged.lastIndex
                val minIndex = 2.coerceAtMost(maxIndex)
                Random.nextInt(from = minIndex, until = maxIndex + 1)
            }
            merged.add(insertIndex, retryWord)
        }
        return merged
    }

    companion object {
        private const val DEFAULT_NEW_WORDS_LIMIT = 20
    }
}
