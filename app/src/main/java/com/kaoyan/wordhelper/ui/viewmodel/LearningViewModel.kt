package com.kaoyan.wordhelper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaoyan.wordhelper.KaoyanWordApp
import com.kaoyan.wordhelper.data.entity.Book
import com.kaoyan.wordhelper.data.model.AIContentType
import com.kaoyan.wordhelper.data.entity.Progress
import com.kaoyan.wordhelper.data.entity.Word
import com.kaoyan.wordhelper.data.model.PronunciationSource
import com.kaoyan.wordhelper.data.model.SpellingOutcome
import com.kaoyan.wordhelper.data.model.StudyRating
import com.kaoyan.wordhelper.data.repository.AddToNewWordsSource
import com.kaoyan.wordhelper.data.repository.TodayNewWordsPlan
import com.kaoyan.wordhelper.ml.core.AdaptiveResponseThreshold
import com.kaoyan.wordhelper.ml.integration.MLEnhancedScheduler
import com.kaoyan.wordhelper.ml.training.OnlineTrainer
import com.kaoyan.wordhelper.util.DateUtils
import com.kaoyan.wordhelper.util.ImmediateRetryQueuePlanner
import com.kaoyan.wordhelper.util.RetryRandomSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

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

private data class QueueLoadParams(
    val book: Book?,
    val newWordsLimit: Int,
    val shuffleNewWords: Boolean,
    val plannedModeEnabled: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class LearningViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as KaoyanWordApp).repository
    private val settingsRepository = (application as KaoyanWordApp).settingsRepository
    private val aiConfigRepository = (application as KaoyanWordApp).aiConfigRepository
    private val aiRepository = (application as KaoyanWordApp).aiRepository
    private val mlScheduler: MLEnhancedScheduler = (application as KaoyanWordApp).mlScheduler
    private val mlOnlineTrainer: OnlineTrainer? = run {
        val app = application as KaoyanWordApp
        OnlineTrainer(app.mlPredictor, app.database.trainingSampleDao(), app.mlModelPersistence)
    }

    private val immediateRetryQueue = ArrayDeque<Word>()
    private val sessionSkippedWordIds = LinkedHashSet<Long>()
    private val pendingTooEasyUndo = mutableMapOf<Long, TooEasyUndoPayload>()
    private var swipeSnackbarIdCounter = 0L
    private var undoTokenCounter = 0L
    private var queueBookId: Long? = null
    private var pendingFailedWordId: Long? = null
    private var answeredInSession = 0
    private var currentWordPresentedAtMs = 0L
    private var recognitionAutoPronounceEventCounter = 0L
    internal var retryQueueRandomSource: RetryRandomSource = RetryRandomSource.Default
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
    private val _recognitionAutoPronounceEvents = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val recognitionAutoPronounceEvents: SharedFlow<Long> = _recognitionAutoPronounceEvents.asSharedFlow()

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
    val algorithmV4Enabled: StateFlow<Boolean> = settingsRepository.settingsFlow
        .map { it.algorithmV4Enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val reviewPressureReliefEnabled: StateFlow<Boolean> = settingsRepository.settingsFlow
        .map { it.reviewPressureReliefEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val reviewPressureDailyCap: StateFlow<Int> = settingsRepository.settingsFlow
        .map { it.reviewPressureDailyCap.coerceIn(10, 500) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 120)
    val pronunciationEnabled: StateFlow<Boolean> = settingsRepository.settingsFlow
        .map { it.pronunciationEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val pronunciationSource: StateFlow<PronunciationSource> = settingsRepository.settingsFlow
        .map { it.pronunciationSource }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PronunciationSource.FREE_DICTIONARY
        )
    val recognitionAutoPronounceEnabled: StateFlow<Boolean> = settingsRepository.settingsFlow
        .map { it.recognitionAutoPronounceEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val shuffleNewWordsEnabled: StateFlow<Boolean> = settingsRepository.settingsFlow
        .map { it.newWordsShuffleEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val plannedNewWordsEnabled: StateFlow<Boolean> = settingsRepository.settingsFlow
        .map { it.plannedNewWordsEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val todayNewWordsPlan: StateFlow<TodayNewWordsPlan> = settingsRepository.todayNewWordsPlanFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayNewWordsPlan())
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

    private var cachedResponseThresholdMs: Long = RESPONSE_TIME_DOWNGRADE_THRESHOLD_MS

    init {
        refreshAiAvailability()
        // 加载ML响应时间阈值
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                if (settings.mlAdaptiveEnabled) {
                    val modelState = withContext(Dispatchers.IO) {
                        (getApplication<KaoyanWordApp>()).mlModelPersistence.getModelState()
                    }
                    cachedResponseThresholdMs = AdaptiveResponseThreshold.computeThreshold(
                        avgResponseTime = modelState?.avgResponseTime ?: 0f,
                        stdResponseTime = modelState?.stdResponseTime ?: 0f,
                        mlEnabled = true
                    )
                } else {
                    cachedResponseThresholdMs = RESPONSE_TIME_DOWNGRADE_THRESHOLD_MS
                }
            }
        }
        viewModelScope.launch {
            combine(
                activeBook,
                newWordsLimit,
                shuffleNewWordsEnabled,
                todayNewWordsPlan,
                plannedNewWordsEnabled
            ) { book, limit, shuffle, _, plannedEnabled ->
                QueueLoadParams(
                    book = book,
                    newWordsLimit = limit,
                    shuffleNewWords = shuffle,
                    plannedModeEnabled = plannedEnabled
                )
            }.collect { params ->
                if (pendingFailedWordId != null) return@collect
                loadQueueForBook(
                    book = params.book,
                    newWordsLimit = params.newWordsLimit,
                    shuffleNewWords = params.shuffleNewWords,
                    plannedModeEnabled = params.plannedModeEnabled
                )
            }
        }
        viewModelScope.launch {
            repository.getNewWordIdsFlow().collect {
                if (pendingFailedWordId != null) return@collect
                val book = activeBook.value
                if (book?.type == Book.TYPE_NEW_WORDS) {
                    loadQueueForBook(book, newWordsLimit.value, shuffleNewWordsEnabled.value, plannedNewWordsEnabled.value)
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
                        loadQueueForBook(book, newWordsLimit.value, shuffleNewWordsEnabled.value, plannedNewWordsEnabled.value)
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
        val previousWordId = word.id
        val responseTimeMs = buildCurrentResponseTimeMs()
        val effectiveRating = resolveRecognitionRating(rating, responseTimeMs)
        val currentSessionPosition = answeredInSession
        val currentSessionTotal = _totalCount.value.coerceAtLeast(1)
        launchSubmit {
            val currentMlEnabled = resolveMlEnabledForSubmission()
            pendingFailedWordId = null
            removeFromImmediateQueue(word.id)
            removeSessionSkippedWord(word.id)

            // ML训练：记录特征和结果
            if (currentMlEnabled && mlOnlineTrainer != null) {
                val progress = withContext(Dispatchers.IO) {
                    repository.getProgress(word.id, book.id)
                }
                val features = mlScheduler.extractFeatures(
                    progress = progress,
                    sessionPosition = currentSessionPosition,
                    sessionTotal = currentSessionTotal
                )
                val isCorrect = effectiveRating != StudyRating.AGAIN
                withContext(Dispatchers.IO) {
                    mlOnlineTrainer.onReviewCompleted(word.id, features, isCorrect)
                }
            }

            withContext(Dispatchers.IO) {
                repository.applyStudyResult(
                    wordId = word.id,
                    bookId = book.id,
                    rating = effectiveRating,
                    algorithmV4Enabled = algorithmV4Enabled.value,
                    mlScheduler = if (currentMlEnabled) mlScheduler else null,
                    mlEnabled = currentMlEnabled,
                    sessionPosition = currentSessionPosition,
                    sessionTotal = currentSessionTotal,
                    responseTimeMs = responseTimeMs
                )
            }
            if (effectiveRating == StudyRating.AGAIN) {
                enqueueImmediateRetry(word)
                _memoryAidSuggestionWordId.value = word.id
            } else {
                _memoryAidSuggestionWordId.value = null
                markAnswered()
            }
            loadQueueForBook(book, newWordsLimit.value, shuffleNewWordsEnabled.value, plannedNewWordsEnabled.value)
            emitRecognitionAutoPronounceEventIfNeeded(previousWordId)
        }
    }

    fun submitAnswerAndRemoveFromNewWords(rating: StudyRating) {
        val word = _currentWord.value ?: return
        val book = activeBook.value ?: return
        val previousWordId = word.id
        val responseTimeMs = buildCurrentResponseTimeMs()
        val effectiveRating = resolveRecognitionRating(rating, responseTimeMs)
        val currentSessionPosition = answeredInSession
        val currentSessionTotal = _totalCount.value.coerceAtLeast(1)
        launchSubmit {
            val currentMlEnabled = resolveMlEnabledForSubmission()
            pendingFailedWordId = null
            _memoryAidSuggestionWordId.value = null
            removeFromImmediateQueue(word.id)
            removeSessionSkippedWord(word.id)

            if (currentMlEnabled && mlOnlineTrainer != null) {
                val progress = withContext(Dispatchers.IO) {
                    repository.getProgress(word.id, book.id)
                }
                val features = mlScheduler.extractFeatures(
                    progress = progress,
                    sessionPosition = currentSessionPosition,
                    sessionTotal = currentSessionTotal
                )
                val isCorrect = effectiveRating != StudyRating.AGAIN
                withContext(Dispatchers.IO) {
                    mlOnlineTrainer.onReviewCompleted(word.id, features, isCorrect)
                }
            }

            withContext(Dispatchers.IO) {
                repository.removeFromNewWords(word.id)
                repository.applyStudyResult(
                    wordId = word.id,
                    bookId = book.id,
                    rating = effectiveRating,
                    algorithmV4Enabled = algorithmV4Enabled.value,
                    mlScheduler = if (currentMlEnabled) mlScheduler else null,
                    mlEnabled = currentMlEnabled,
                    sessionPosition = currentSessionPosition,
                    sessionTotal = currentSessionTotal,
                    responseTimeMs = responseTimeMs
                )
            }
            markAnswered()
            loadQueueForBook(book, newWordsLimit.value, shuffleNewWordsEnabled.value, plannedNewWordsEnabled.value)
            emitRecognitionAutoPronounceEventIfNeeded(previousWordId)
        }
    }

    fun submitSpellingOutcome(
        outcome: SpellingOutcome,
        attemptCount: Int,
        durationMillis: Long
    ) {
        val word = _currentWord.value ?: return
        val book = activeBook.value ?: return
        val currentSessionPosition = answeredInSession
        val currentSessionTotal = _totalCount.value.coerceAtLeast(1)
        launchSubmit {
            val currentMlEnabled = resolveMlEnabledForSubmission()
            if (outcome == SpellingOutcome.FAILED) {
                pendingFailedWordId = word.id
                _memoryAidSuggestionWordId.value = word.id
            } else {
                pendingFailedWordId = null
                _memoryAidSuggestionWordId.value = null
            }
            removeFromImmediateQueue(word.id)
            removeSessionSkippedWord(word.id)

            // ML训练
            if (currentMlEnabled && mlOnlineTrainer != null) {
                val progress = withContext(Dispatchers.IO) {
                    repository.getProgress(word.id, book.id)
                }
                val features = mlScheduler.extractFeatures(
                    progress = progress,
                    sessionPosition = currentSessionPosition,
                    sessionTotal = currentSessionTotal
                )
                val isCorrect = outcome != SpellingOutcome.FAILED
                withContext(Dispatchers.IO) {
                    mlOnlineTrainer.onReviewCompleted(word.id, features, isCorrect)
                }
            }

            withContext(Dispatchers.IO) {
                repository.applySpellingOutcome(
                    wordId = word.id,
                    bookId = book.id,
                    outcome = outcome,
                    attemptCount = attemptCount,
                    durationMillis = durationMillis,
                    algorithmV4Enabled = algorithmV4Enabled.value,
                    mlScheduler = if (currentMlEnabled) mlScheduler else null,
                    mlEnabled = currentMlEnabled,
                    sessionPosition = currentSessionPosition,
                    sessionTotal = currentSessionTotal
                )
            }
            if (outcome == SpellingOutcome.FAILED) {
                return@launchSubmit
            }
            pendingFailedWordId = null
            markAnswered()
            loadQueueForBook(book, newWordsLimit.value, shuffleNewWordsEnabled.value, plannedNewWordsEnabled.value)
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
            loadQueueForBook(book, newWordsLimit.value, shuffleNewWordsEnabled.value, plannedNewWordsEnabled.value)
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
            loadQueueForBook(book, newWordsLimit.value, shuffleNewWordsEnabled.value, plannedNewWordsEnabled.value)
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
            loadQueueForBook(book, newWordsLimit.value, shuffleNewWordsEnabled.value, plannedNewWordsEnabled.value)
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
                loadQueueForBook(book, newWordsLimit.value, shuffleNewWordsEnabled.value, plannedNewWordsEnabled.value)
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
            loadQueueForBook(book, newWordsLimit.value, shuffleNewWordsEnabled.value, plannedNewWordsEnabled.value)
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
            loadQueueForBook(book, newWordsLimit.value, shuffleNewWordsEnabled.value, plannedNewWordsEnabled.value)
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

    private suspend fun loadQueueForBook(
        book: Book?,
        newWordsLimit: Int,
        shuffleNewWords: Boolean,
        plannedModeEnabled: Boolean
    ) {
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
        val queueSnapshot = withContext(Dispatchers.IO) {
            repository.getStudyQueueSnapshot(
                book = book,
                newWordLimit = newWordsLimit,
                shuffleNewWords = shuffleNewWords,
                plannedNewWordIds = resolvePlannedNewWordIds(book, plannedModeEnabled),
                plannedModeEnabled = plannedModeEnabled
            )
        }
        _dueReviewCount.value = queueSnapshot.dueCount
        val mergedQueue = mergeWithImmediateRetryQueue(queueSnapshot.queue)
            .filterNot { it.id in sessionSkippedWordIds }
        updateQueue(mergedQueue)
    }

    private fun resolvePlannedNewWordIds(book: Book, plannedModeEnabled: Boolean): List<Long> {
        if (!plannedModeEnabled) return emptyList()
        if (book.type == Book.TYPE_NEW_WORDS) return emptyList()
        val plan = todayNewWordsPlan.value
        val learningDate = DateUtils.currentLearningDate().format(DATE_FORMATTER)
        if (plan.learningDate != learningDate) return emptyList()
        if (plan.bookId != book.id) return emptyList()
        return plan.wordIds
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
        currentWordPresentedAtMs = if (nextWord != null) System.currentTimeMillis() else 0L
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

    private fun emitRecognitionAutoPronounceEventIfNeeded(previousWordId: Long) {
        val nextWordId = _currentWord.value?.id ?: return
        if (nextWordId == previousWordId) return
        recognitionAutoPronounceEventCounter += 1
        _recognitionAutoPronounceEvents.tryEmit(recognitionAutoPronounceEventCounter)
    }

    private fun buildCurrentResponseTimeMs(now: Long = System.currentTimeMillis()): Long {
        val startAt = currentWordPresentedAtMs
        if (startAt <= 0L) return 0L
        return (now - startAt).coerceAtLeast(0L)
    }

    private fun resolveRecognitionRating(
        rating: StudyRating,
        responseTimeMs: Long
    ): StudyRating {
        if (!algorithmV4Enabled.value) return rating
        if (responseTimeMs <= cachedResponseThresholdMs) return rating
        return when (rating) {
            StudyRating.GOOD -> StudyRating.HARD
            StudyRating.HARD -> StudyRating.AGAIN
            StudyRating.AGAIN -> StudyRating.AGAIN
        }
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

    private suspend fun resolveMlEnabledForSubmission(): Boolean {
        return withContext(Dispatchers.IO) {
            val enabled = settingsRepository.settingsFlow.first().mlAdaptiveEnabled
            if (enabled) {
                getApplication<KaoyanWordApp>().ensureMLEngineInitialized()
            }
            enabled
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
        val policy = if (algorithmV4Enabled.value) {
            ImmediateRetryQueuePlanner.V4Policy
        } else {
            ImmediateRetryQueuePlanner.LegacyPolicy
        }

        immediateRetryQueue.forEach { retryWord ->
            val insertIndex = ImmediateRetryQueuePlanner.resolveInsertIndex(
                queueSize = merged.size,
                policy = policy,
                randomSource = retryQueueRandomSource
            )
            merged.add(insertIndex, retryWord)
        }
        return merged
    }

    companion object {
        private const val DEFAULT_NEW_WORDS_LIMIT = 20
        private const val RESPONSE_TIME_DOWNGRADE_THRESHOLD_MS = 6_000L
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
