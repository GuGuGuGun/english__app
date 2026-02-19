package com.kaoyan.wordhelper.ui.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaoyan.wordhelper.KaoyanWordApp
import com.kaoyan.wordhelper.data.entity.Book
import com.kaoyan.wordhelper.data.entity.Progress
import com.kaoyan.wordhelper.data.entity.Word
import com.kaoyan.wordhelper.data.model.WordDraft
import com.kaoyan.wordhelper.data.repository.SettingsRepository
import com.kaoyan.wordhelper.util.DateUtils
import com.kaoyan.wordhelper.util.WordFileParser
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

data class BookUiModel(
    val book: Book,
    val learned: Int,
    val mastered: Int,
    val total: Int,
    val dueCount: Int,
    val earlyReviewCount: Int,
    val estimatedFinishDays: Int?
)

data class ImportPreview(
    val displayName: String,
    val drafts: List<WordDraft>,
    val previewLines: List<String>
)

data class EarlyReviewCandidate(
    val wordId: Long,
    val word: String,
    val phonetic: String,
    val meaning: String,
    val statusLabel: String,
    val nextReviewText: String
)

data class EarlyReviewUiState(
    val bookId: Long? = null,
    val loading: Boolean = false,
    val candidates: List<EarlyReviewCandidate> = emptyList(),
    val selectedWordIds: Set<Long> = emptySet()
)

@OptIn(ExperimentalCoroutinesApi::class)
class BookManageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as KaoyanWordApp).repository
    private val settingsRepository: SettingsRepository = (application as KaoyanWordApp).settingsRepository

    private val _importPreview = MutableStateFlow<ImportPreview?>(null)
    val importPreview: StateFlow<ImportPreview?> = _importPreview.asStateFlow()

    private val _importBookName = MutableStateFlow("")
    val importBookName: StateFlow<String> = _importBookName.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _earlyReviewUiState = MutableStateFlow(EarlyReviewUiState())
    val earlyReviewUiState: StateFlow<EarlyReviewUiState> = _earlyReviewUiState.asStateFlow()

    private val dailyNewWordsLimit: StateFlow<Int> = settingsRepository.settingsFlow
        .map { it.newWordsLimit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 20)

    val plannedNewWordsEnabled: StateFlow<Boolean> = settingsRepository.settingsFlow
        .map { it.plannedNewWordsEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val books: StateFlow<List<BookUiModel>> = combine(repository.getAllBooks(), dailyNewWordsLimit) { books, dailyLimit ->
        books to dailyLimit
    }
        .flatMapLatest { (books, dailyLimit) ->
            if (books.isEmpty()) {
                flowOf(emptyList())
            } else {
                val bookFlows = books.map { book ->
                    combine(
                        repository.getLearnedCount(book.id),
                        repository.getMasteredCount(book.id),
                        repository.getProgressByBookFlow(book.id),
                        repository.getEarlyReviewCountFlow(book.id)
                    ) { learned, mastered, progressList, earlyReviewCount ->
                        BookUiModel(
                            book = book,
                            learned = learned,
                            mastered = mastered,
                            total = book.totalCount,
                            dueCount = computeDueCount(progressList),
                            earlyReviewCount = earlyReviewCount,
                            estimatedFinishDays = computeEstimatedFinishDays(book, learned, book.totalCount, dailyLimit)
                        )
                    }
                }
                combine(bookFlows) { it.toList() }
            }
        }
        .map { items ->
            items.sortedWith(
                compareByDescending<BookUiModel> { it.book.type == Book.TYPE_NEW_WORDS }
                    .thenBy { it.book.type }
                    .thenBy { it.book.id }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun switchBook(bookId: Long) {
        viewModelScope.launch {
            repository.switchBook(bookId)
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            val deleted = repository.deleteBookWithData(book)
            _message.value = if (deleted) {
                "已删除词书：${book.name}"
            } else {
                "生词本不可删除"
            }
        }
    }

    fun clearNewWords() {
        viewModelScope.launch {
            repository.clearNewWords()
        }
    }

    fun onImportFileSelected(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            _isImporting.value = true
            val resolver = getApplication<Application>().contentResolver
            val displayName = resolveDisplayName(resolver, uri)
            val text = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }
            if (text.isNullOrBlank()) {
                _message.value = "文件内容为空或无法读取"
                _isImporting.value = false
                return@launch
            }
            val drafts = WordFileParser.parse(text)
            if (drafts.isEmpty()) {
                _message.value = "未解析到有效词条"
                _isImporting.value = false
                return@launch
            }
            val previewLines = drafts.take(5).map { draft ->
                buildString {
                    append(draft.word)
                    if (draft.phonetic.isNotBlank()) {
                        append("  ")
                        append(draft.phonetic)
                    }
                    if (draft.meaning.isNotBlank()) {
                        append("  ")
                        append(draft.meaning)
                    }
                }
            }
            _importBookName.value = suggestBookName(displayName)
            _importPreview.value = ImportPreview(displayName = displayName, drafts = drafts, previewLines = previewLines)
            _isImporting.value = false
        }
    }

    fun updateImportBookName(name: String) {
        _importBookName.value = name
    }

    fun dismissImportPreview() {
        _importPreview.value = null
    }

    fun confirmImport() {
        val preview = _importPreview.value ?: return
        val name = _importBookName.value.trim()
        if (name.isBlank()) {
            _message.value = "请输入词书名称"
            return
        }
        viewModelScope.launch {
            _isImporting.value = true
            withContext(Dispatchers.IO) {
                repository.importBook(name, preview.drafts)
            }
            _importPreview.value = null
            _message.value = "已导入 ${preview.drafts.size} 条词条"
            _isImporting.value = false
        }
    }

    fun suggestExportFileName(bookName: String): String {
        val normalized = bookName
            .trim()
            .ifBlank { "word_book" }
            .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
            .trim('_')
            .ifBlank { "word_book" }
        val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        return "${normalized}_$date.txt"
    }

    fun exportBook(book: Book, uri: Uri?) {
        if (uri == null) {
            _message.value = "已取消导出"
            return
        }
        viewModelScope.launch {
            _isImporting.value = true
            val resolver = getApplication<Application>().contentResolver
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val words = repository.getWordsForExport(book)
                    val content = buildExportContent(book.name, words)
                    resolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                        writer.write(content)
                    } ?: error("无法写入目标文件")
                    words.size
                }
            }
            _isImporting.value = false
            _message.value = if (result.isSuccess) {
                "导出完成：${result.getOrNull() ?: 0} 条词条"
            } else {
                "导出失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun openEarlyReviewSelector(bookId: Long) {
        viewModelScope.launch {
            _earlyReviewUiState.value = EarlyReviewUiState(bookId = bookId, loading = true)
            val words = withContext(Dispatchers.IO) { repository.getWordsByBookList(bookId) }
            val progressMap = withContext(Dispatchers.IO) {
                repository.getProgressByBook(bookId).associateBy { it.wordId }
            }
            val selectedIds = withContext(Dispatchers.IO) { repository.getEarlyReviewWordIds(bookId) }

            val candidates = words.mapNotNull { word ->
                val progress = progressMap[word.id] ?: return@mapNotNull null
                EarlyReviewCandidate(
                    wordId = word.id,
                    word = word.word,
                    phonetic = word.phonetic,
                    meaning = word.meaning,
                    statusLabel = progressStatusText(progress.status),
                    nextReviewText = DateUtils.formatReviewDay(progress.nextReviewTime)
                )
            }
            _earlyReviewUiState.value = EarlyReviewUiState(
                bookId = bookId,
                loading = false,
                candidates = candidates,
                selectedWordIds = selectedIds.intersect(candidates.map { it.wordId }.toSet())
            )
        }
    }

    fun closeEarlyReviewSelector() {
        _earlyReviewUiState.value = EarlyReviewUiState()
    }

    fun toggleEarlyReviewSelection(wordId: Long) {
        val state = _earlyReviewUiState.value
        val selected = state.selectedWordIds.toMutableSet()
        if (!selected.add(wordId)) {
            selected.remove(wordId)
        }
        _earlyReviewUiState.value = state.copy(selectedWordIds = selected)
    }

    fun selectAllEarlyReview() {
        val state = _earlyReviewUiState.value
        _earlyReviewUiState.value = state.copy(
            selectedWordIds = state.candidates.map { it.wordId }.toSet()
        )
    }

    fun clearAllEarlyReviewSelection() {
        val state = _earlyReviewUiState.value
        _earlyReviewUiState.value = state.copy(selectedWordIds = emptySet())
    }

    fun confirmEarlyReviewSelection() {
        val state = _earlyReviewUiState.value
        val bookId = state.bookId ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.replaceEarlyReviewWords(bookId, state.selectedWordIds)
            }
            _message.value = "已设置提前复习 ${state.selectedWordIds.size} 词"
            _earlyReviewUiState.value = state.copy(loading = false)
        }
    }

    private fun resolveDisplayName(resolver: ContentResolver, uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) {
                return cursor.getString(index)
            }
        }
        return uri.lastPathSegment ?: "导入词书"
    }

    private fun suggestBookName(displayName: String): String {
        val trimmed = displayName.trim()
        val name = trimmed.substringBeforeLast('.', trimmed)
        return if (name.isBlank()) "导入词书" else name
    }

    private fun buildExportContent(bookName: String, words: List<Word>): String {
        val exportTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val header = listOf(
            "# Kaoyan Word Book Export",
            "# book_name: $bookName",
            "# export_time: $exportTime",
            "# format: word<TAB>phonetic<TAB>meaning<TAB>example"
        )
        val rows = words.map { word ->
            listOf(
                escapeExportField(word.word),
                escapeExportField(word.phonetic),
                escapeExportField(word.meaning),
                escapeExportField(word.example)
            ).joinToString("\t")
        }
        return (header + rows).joinToString(separator = "\n")
    }

    private fun escapeExportField(raw: String): String {
        return raw
            .replace('\t', ' ')
            .replace('\r', ' ')
            .replace('\n', ' ')
            .trim()
    }

    private fun computeDueCount(progressList: List<Progress>): Int {
        return progressList.count { progress ->
            progress.status != Progress.STATUS_MASTERED &&
                progress.nextReviewTime > 0L &&
                DateUtils.isDue(progress.nextReviewTime)
        }
    }

    private fun computeEstimatedFinishDays(
        book: Book,
        learned: Int,
        total: Int,
        dailyLimit: Int
    ): Int? {
        if (book.type == Book.TYPE_NEW_WORDS) return null
        val safeTotal = total.coerceAtLeast(0)
        val remainingNew = (safeTotal - learned.coerceAtLeast(0)).coerceAtLeast(0)
        if (remainingNew == 0) return 0
        val safeDaily = dailyLimit.coerceAtLeast(1)
        return ceil(remainingNew.toDouble() / safeDaily.toDouble()).toInt()
    }

    private fun progressStatusText(status: Int): String {
        return when (status) {
            Progress.STATUS_MASTERED -> "已掌握"
            Progress.STATUS_LEARNING -> "学习中"
            Progress.STATUS_NEW -> "新词"
            else -> "未学习"
        }
    }
}
