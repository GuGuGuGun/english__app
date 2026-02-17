package com.kaoyan.wordhelper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaoyan.wordhelper.KaoyanWordApp
import com.kaoyan.wordhelper.data.entity.Book
import com.kaoyan.wordhelper.data.entity.Progress
import com.kaoyan.wordhelper.data.entity.Word
import com.kaoyan.wordhelper.data.model.AIContentType
import com.kaoyan.wordhelper.data.model.PronunciationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchWordItem(
    val word: Word,
    val isInNewWords: Boolean,
    val progress: Progress?
)

data class SentenceAnalysis(
    val mainClause: String,
    val grammarBreakdown: String,
    val chineseTranslation: String
)

data class SearchSentenceAiState(
    val isEnabled: Boolean = false,
    val isConfigured: Boolean = false,
    val isSentenceMode: Boolean = false,
    val isLoading: Boolean = false,
    val sourceText: String = "",
    val analysis: SentenceAnalysis? = null,
    val error: String? = null
) {
    val isAvailable: Boolean
        get() = isEnabled && isConfigured
}

data class SearchWordAiState(
    val wordId: Long? = null,
    val isEnabled: Boolean = false,
    val isConfigured: Boolean = false,
    val isLoading: Boolean = false,
    val content: String = "",
    val error: String? = null,
    val translationLoading: Boolean = false,
    val translationContent: String = "",
    val translationError: String? = null
) {
    val isAvailable: Boolean
        get() = isEnabled && isConfigured
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KaoyanWordApp
    private val repository = app.repository
    private val settingsRepository = app.settingsRepository
    private val aiConfigRepository = app.aiConfigRepository
    private val aiRepository = app.aiRepository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _sentenceAiState = MutableStateFlow(SearchSentenceAiState())
    val sentenceAiState: StateFlow<SearchSentenceAiState> = _sentenceAiState.asStateFlow()
    private val _wordAiState = MutableStateFlow(SearchWordAiState())
    val wordAiState: StateFlow<SearchWordAiState> = _wordAiState.asStateFlow()
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
    private var lastAnalyzedSentence: String? = null

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

    init {
        observeSentenceMode()
        refreshSentenceAiAvailability()
    }

    fun updateQuery(text: String) {
        _query.value = text
    }

    fun refreshSentenceAiAvailability() {
        viewModelScope.launch {
            val config = aiConfigRepository.getConfig()
            _sentenceAiState.value = _sentenceAiState.value.copy(
                isEnabled = config.enabled,
                isConfigured = config.isConfigured()
            )
            _wordAiState.value = _wordAiState.value.copy(
                isEnabled = config.enabled,
                isConfigured = config.isConfigured()
            )
        }
    }

    fun clearWordAiState() {
        _wordAiState.value = _wordAiState.value.copy(
            wordId = null,
            isLoading = false,
            content = "",
            error = null,
            translationLoading = false,
            translationContent = "",
            translationError = null
        )
    }

    fun requestWordMemoryAid(word: Word, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val config = aiConfigRepository.getConfig()
            val enabled = config.enabled
            val configured = config.isConfigured()
            _wordAiState.value = _wordAiState.value.copy(
                wordId = word.id,
                isEnabled = enabled,
                isConfigured = configured
            )
            if (!enabled || !configured) {
                _wordAiState.value = _wordAiState.value.copy(
                    wordId = word.id,
                    isLoading = false,
                    content = "",
                    error = "请先在 AI 实验室启用并配置 API Key"
                )
                return@launch
            }

            _wordAiState.value = _wordAiState.value.copy(
                wordId = word.id,
                isLoading = true,
                error = null
            )
            val result = withContext(Dispatchers.IO) {
                aiRepository.getAIContent(
                    wordId = word.id,
                    queryContent = word.word,
                    type = AIContentType.MEMORY_AID,
                    forceRefresh = forceRefresh
                )
            }
            result.fold(
                onSuccess = { content ->
                    _wordAiState.value = _wordAiState.value.copy(
                        wordId = word.id,
                        isLoading = false,
                        content = content,
                        error = null
                    )
                },
                onFailure = { throwable ->
                    _wordAiState.value = _wordAiState.value.copy(
                        wordId = word.id,
                        isLoading = false,
                        content = "",
                        error = throwable.message ?: "AI 助记生成失败"
                    )
                }
            )
        }
    }

    fun requestWordChineseTranslation(word: Word, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val config = aiConfigRepository.getConfig()
            val enabled = config.enabled
            val configured = config.isConfigured()
            _wordAiState.value = _wordAiState.value.copy(
                wordId = word.id,
                isEnabled = enabled,
                isConfigured = configured
            )
            if (!enabled || !configured) {
                _wordAiState.value = _wordAiState.value.copy(
                    wordId = word.id,
                    translationLoading = false,
                    translationContent = "",
                    translationError = "请先在 AI 实验室启用并配置 API Key"
                )
                return@launch
            }

            _wordAiState.value = _wordAiState.value.copy(
                wordId = word.id,
                translationLoading = true,
                translationError = null
            )
            val result = withContext(Dispatchers.IO) {
                aiRepository.getAIContent(
                    wordId = word.id,
                    queryContent = word.word,
                    type = AIContentType.WORD_TRANSLATION,
                    forceRefresh = forceRefresh
                )
            }
            result.fold(
                onSuccess = { content ->
                    _wordAiState.value = _wordAiState.value.copy(
                        wordId = word.id,
                        translationLoading = false,
                        translationContent = content,
                        translationError = null
                    )
                },
                onFailure = { throwable ->
                    _wordAiState.value = _wordAiState.value.copy(
                        wordId = word.id,
                        translationLoading = false,
                        translationContent = "",
                        translationError = throwable.message ?: "中文翻译生成失败"
                    )
                }
            )
        }
    }

    fun retrySentenceAnalysis(forceRefresh: Boolean = false) {
        val sentence = _query.value.trim()
        if (sentence.length <= SENTENCE_MODE_THRESHOLD) return
        viewModelScope.launch {
            analyzeSentence(sentence = sentence, forceRefresh = forceRefresh)
        }
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

    private fun observeSentenceMode() {
        viewModelScope.launch {
            _query
                .debounce(350)
                .map { it.trim() }
                .distinctUntilChanged()
                .collectLatest { normalized ->
                    if (normalized.length <= SENTENCE_MODE_THRESHOLD) {
                        lastAnalyzedSentence = null
                        _sentenceAiState.value = _sentenceAiState.value.copy(
                            isSentenceMode = false,
                            isLoading = false,
                            sourceText = "",
                            analysis = null,
                            error = null
                        )
                        return@collectLatest
                    }
                    analyzeSentence(sentence = normalized, forceRefresh = false)
                }
        }
    }

    private suspend fun analyzeSentence(sentence: String, forceRefresh: Boolean) {
        val config = aiConfigRepository.getConfig()
        val enabled = config.enabled
        val configured = config.isConfigured()
        _sentenceAiState.value = _sentenceAiState.value.copy(
            isSentenceMode = true,
            sourceText = sentence,
            isEnabled = enabled,
            isConfigured = configured
        )
        if (!enabled || !configured) {
            _sentenceAiState.value = _sentenceAiState.value.copy(
                isLoading = false,
                analysis = null,
                error = "请先在 AI 实验室启用并配置 API Key"
            )
            return
        }

        val state = _sentenceAiState.value
        if (!forceRefresh &&
            lastAnalyzedSentence == sentence &&
            state.analysis != null &&
            state.error.isNullOrBlank() &&
            !state.isLoading
        ) {
            return
        }

        _sentenceAiState.value = state.copy(
            isSentenceMode = true,
            sourceText = sentence,
            isEnabled = enabled,
            isConfigured = configured,
            isLoading = true,
            error = null
        )

        val result = withContext(Dispatchers.IO) {
            aiRepository.analyzeSentence(sentence = sentence, forceRefresh = forceRefresh)
        }
        result.fold(
            onSuccess = { content ->
                lastAnalyzedSentence = sentence
                _sentenceAiState.value = _sentenceAiState.value.copy(
                    isLoading = false,
                    analysis = parseSentenceAnalysis(content),
                    error = null
                )
            },
            onFailure = { throwable ->
                _sentenceAiState.value = _sentenceAiState.value.copy(
                    isLoading = false,
                    analysis = null,
                    error = throwable.message ?: "句子解析失败"
                )
            }
        )
    }

    private fun parseSentenceAnalysis(rawContent: String): SentenceAnalysis {
        val normalized = rawContent.replace("\r\n", "\n").trim()
        if (normalized.isBlank()) {
            return SentenceAnalysis(
                mainClause = "未识别到句子主干，请重试。",
                grammarBreakdown = "未识别到语法成分，请重试。",
                chineseTranslation = "未识别到中文翻译，请重试。"
            )
        }

        val headings = SENTENCE_SECTION_PATTERN.findAll(normalized).toList()
        if (headings.isNotEmpty()) {
            var mainClause = ""
            var grammarBreakdown = ""
            var chineseTranslation = ""
            headings.forEachIndexed { index, match ->
                val label = match.groupValues[1]
                val start = match.range.last + 1
                val end = if (index < headings.lastIndex) {
                    headings[index + 1].range.first
                } else {
                    normalized.length
                }
                val sectionContent = normalized.substring(start, end)
                    .trim()
                    .trimStart('-', '•', '*', ' ')
                    .trim()
                when {
                    label.contains("主干") && mainClause.isBlank() -> {
                        mainClause = sectionContent
                    }

                    label.contains("翻译") && chineseTranslation.isBlank() -> {
                        chineseTranslation = sectionContent
                    }

                    grammarBreakdown.isBlank() -> {
                        grammarBreakdown = sectionContent
                    }
                }
            }
            if (mainClause.isBlank()) {
                mainClause = "未识别到句子主干，请重试。"
            }
            if (grammarBreakdown.isBlank()) {
                grammarBreakdown = normalized
            }
            if (chineseTranslation.isBlank()) {
                chineseTranslation = extractLikelyTranslation(normalized)
                    .ifBlank { "未识别到中文翻译，请重试。" }
            }
            return SentenceAnalysis(
                mainClause = mainClause,
                grammarBreakdown = grammarBreakdown,
                chineseTranslation = chineseTranslation
            )
        }

        val blocks = normalized
            .split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val mainClause = blocks.firstOrNull().orEmpty().ifBlank { "未识别到句子主干，请重试。" }
        val grammarBreakdown = when {
            blocks.size >= 3 -> blocks.subList(1, blocks.lastIndex).joinToString("\n\n")
            blocks.size == 2 -> blocks[1]
            else -> normalized
        }.ifBlank { normalized }
        val chineseTranslation = extractLikelyTranslation(normalized).ifBlank {
            blocks.lastOrNull().orEmpty().ifBlank { "未识别到中文翻译，请重试。" }
        }
        return SentenceAnalysis(
            mainClause = mainClause,
            grammarBreakdown = grammarBreakdown,
            chineseTranslation = chineseTranslation
        )
    }

    private fun extractLikelyTranslation(content: String): String {
        return content
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .lastOrNull { line -> line.any { it in '\u4e00'..'\u9fff' } }
            .orEmpty()
    }

    companion object {
        const val SENTENCE_MODE_THRESHOLD = 20
        private val SENTENCE_SECTION_PATTERN = Regex(
            """(?m)^\s*(?:#{1,3}\s*)?[【\[]?(句子主干|主干|语法成分标注|语法成分|语法拆解|语法分析|中文翻译|翻译)[】\]]?\s*[:：]?\s*$"""
        )
    }
}
