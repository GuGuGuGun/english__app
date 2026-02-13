package com.kaoyan.wordhelper.data.repository

import androidx.room.withTransaction
import com.kaoyan.wordhelper.data.database.AppDatabase
import com.kaoyan.wordhelper.data.entity.Book
import com.kaoyan.wordhelper.data.entity.BookWordContent
import com.kaoyan.wordhelper.data.entity.DailyStats
import com.kaoyan.wordhelper.data.entity.EarlyReviewRef
import com.kaoyan.wordhelper.data.entity.NewWordRef
import com.kaoyan.wordhelper.data.entity.Progress
import com.kaoyan.wordhelper.data.entity.StudyLog
import com.kaoyan.wordhelper.data.entity.Word
import com.kaoyan.wordhelper.data.entity.WordEntity
import com.kaoyan.wordhelper.data.model.DailyStatsAggregate
import com.kaoyan.wordhelper.data.model.PresetBookCatalog
import com.kaoyan.wordhelper.data.model.SpellingOutcome
import com.kaoyan.wordhelper.data.model.StudyRating
import com.kaoyan.wordhelper.data.model.WordDraft
import com.kaoyan.wordhelper.util.DateUtils
import com.kaoyan.wordhelper.util.Sm2Scheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

class WordRepository(private val database: AppDatabase) {
    private val bookDao = database.bookDao()
    private val wordDao = database.wordDao()
    private val progressDao = database.progressDao()
    private val newWordRefDao = database.newWordRefDao()
    private val studyLogDao = database.studyLogDao()
    private val dailyStatsDao = database.dailyStatsDao()
    private val earlyReviewDao = database.earlyReviewDao()

    // ---- Book ----

    fun getAllBooks(): Flow<List<Book>> = bookDao.getAllBooks()

    suspend fun getBookById(bookId: Long): Book? = bookDao.getBookById(bookId)

    suspend fun getActiveBook(): Book? = bookDao.getActiveBook()

    fun getActiveBookFlow(): Flow<Book?> = bookDao.getActiveBookFlow()

    suspend fun switchBook(newBookId: Long) {
        bookDao.deactivateAll()
        bookDao.activate(newBookId)
    }

    suspend fun insertBook(book: Book): Long = bookDao.insert(book)

    suspend fun deleteBook(book: Book) = bookDao.delete(book)

    suspend fun deleteBookWithData(book: Book): Boolean {
        if (book.type == Book.TYPE_NEW_WORDS) {
            return false
        }
        database.withTransaction {
            val wasActive = book.isActive
            earlyReviewDao.deleteByBook(book.id)
            progressDao.deleteByBook(book.id)
            wordDao.deleteByBook(book.id)
            wordDao.deleteOrphanWords()
            bookDao.delete(book)

            if (wasActive) {
                val remaining = bookDao.getAllBooksList().filter { it.id != book.id }
                val fallback = remaining.firstOrNull { it.type != Book.TYPE_NEW_WORDS } ?: remaining.firstOrNull()
                if (fallback != null) {
                    bookDao.deactivateAll()
                    bookDao.activate(fallback.id)
                }
            }
        }
        return true
    }

    suspend fun importBook(name: String, drafts: List<WordDraft>): Long {
        return database.withTransaction {
            val bookId = bookDao.insert(
                Book(name = name, type = Book.TYPE_IMPORTED, totalCount = 0, isActive = false)
            )
            upsertDraftsForBook(bookId, drafts)
            bookDao.updateTotalCount(bookId, wordDao.getWordCount(bookId))
            bookId
        }
    }

    suspend fun ensurePresetBooks() {
        database.withTransaction {
            val books = bookDao.getAllBooksList().toMutableList()
            if (books.none { it.type == Book.TYPE_NEW_WORDS }) {
                val newWordsId = bookDao.insert(
                    Book(
                        name = PresetBookCatalog.NEW_WORDS_BOOK_NAME,
                        type = Book.TYPE_NEW_WORDS,
                        totalCount = 0,
                        isActive = false
                    )
                )
                bookDao.getBookById(newWordsId)?.let { books.add(it) }
            }

            val presetNames = PresetBookCatalog.presets.map { it.name }.toSet()
            val stalePresets = books.filter { it.type == Book.TYPE_PRESET && it.name !in presetNames }
            if (stalePresets.isNotEmpty()) {
                stalePresets.forEach { preset ->
                    earlyReviewDao.deleteByBook(preset.id)
                    progressDao.deleteByBook(preset.id)
                    wordDao.deleteByBook(preset.id)
                    bookDao.delete(preset)
                }
                wordDao.deleteOrphanWords()
                val staleIds = stalePresets.map { it.id }.toSet()
                books.removeAll { it.id in staleIds }
            }

            val duplicatePresets = books
                .filter { it.type == Book.TYPE_PRESET && it.name in presetNames }
                .groupBy { it.name }
                .values
                .flatMap { sameNameBooks ->
                    sameNameBooks
                        .sortedBy { it.id }
                        .drop(1)
                }
            if (duplicatePresets.isNotEmpty()) {
                duplicatePresets.forEach { preset ->
                    earlyReviewDao.deleteByBook(preset.id)
                    progressDao.deleteByBook(preset.id)
                    wordDao.deleteByBook(preset.id)
                    bookDao.delete(preset)
                }
                wordDao.deleteOrphanWords()
                val duplicateIds = duplicatePresets.map { it.id }.toSet()
                books.removeAll { it.id in duplicateIds }
            }

            var hasActiveBook = books.any { it.isActive }
            PresetBookCatalog.presets.forEachIndexed { index, preset ->
                val existing = books.firstOrNull { it.type == Book.TYPE_PRESET && it.name == preset.name }
                    ?: bookDao.findByNameAndType(preset.name, Book.TYPE_PRESET)
                val targetBook = if (existing == null) {
                    val createdId = bookDao.insert(
                        Book(
                            name = preset.name,
                            type = Book.TYPE_PRESET,
                            totalCount = 0,
                            isActive = !hasActiveBook && index == 0
                        )
                    )
                    val created = bookDao.getBookById(createdId)
                    if (created != null) {
                        books.add(created)
                        if (created.isActive) {
                            hasActiveBook = true
                        }
                    }
                    created
                } else {
                    existing
                }
                if (targetBook != null) {
                    syncPresetWords(targetBook.id, preset.drafts)
                }
            }

            if (!hasActiveBook) {
                val fallbackBook = books.firstOrNull { it.type == Book.TYPE_PRESET } ?: books.firstOrNull()
                if (fallbackBook != null) {
                    bookDao.deactivateAll()
                    bookDao.activate(fallbackBook.id)
                }
            }
            updateNewWordsBookCount()
        }
    }

    // ---- Word ----

    fun getWordsByBook(bookId: Long): Flow<List<Word>> = wordDao.getWordsByBook(bookId)

    suspend fun getWordsByBookList(bookId: Long): List<Word> = wordDao.getWordsByBookList(bookId)

    suspend fun getWordsForExport(book: Book): List<Word> {
        return if (book.type == Book.TYPE_NEW_WORDS) {
            wordDao.getNewWordsList()
        } else {
            wordDao.getWordsByBookList(book.id)
        }
    }

    suspend fun getNewWordsList(): List<Word> = wordDao.getNewWordsList()

    suspend fun getWordById(wordId: Long): Word? = wordDao.getWordById(wordId)

    suspend fun searchWords(query: String, currentBookId: Long? = null): List<Word> {
        return wordDao.searchWords(query, currentBookId ?: 0L)
    }

    suspend fun insertWords(words: List<Word>) {
        if (words.isEmpty()) return
        val draftsByBook = words.groupBy { it.bookId }
        draftsByBook.forEach { (bookId, list) ->
            if (bookId <= 0L) return@forEach
            val drafts = list.map { word ->
                WordDraft(
                    word = word.word,
                    phonetic = word.phonetic,
                    meaning = word.meaning,
                    example = word.example
                )
            }
            upsertDraftsForBook(bookId, drafts)
            bookDao.updateTotalCount(bookId, wordDao.getWordCount(bookId))
        }
    }

    suspend fun getWordCount(bookId: Long): Int = wordDao.getWordCount(bookId)

    // ---- Progress ----

    suspend fun getProgress(wordId: Long, bookId: Long): Progress? {
        return progressDao.getGlobalProgress(wordId)
    }

    fun getLearnedCount(bookId: Long): Flow<Int> = progressDao.getLearnedCount(bookId)

    fun getMasteredCount(bookId: Long): Flow<Int> = progressDao.getMasteredCount(bookId)

    suspend fun upsertProgress(progress: Progress) {
        val existing = progressDao.getProgress(progress.wordId, progress.bookId)
        if (existing != null) {
            progressDao.update(progress.copy(id = existing.id))
        } else {
            progressDao.insert(progress)
        }
    }

    suspend fun getDueWords(bookId: Long): List<Progress> {
        return progressDao.getProgressByBook(bookId)
            .filter { it.status != Progress.STATUS_MASTERED && it.nextReviewTime > 0L && DateUtils.isDue(it.nextReviewTime) }
            .sortedBy { it.nextReviewTime }
    }

    suspend fun disperseReviewPressure(bookId: Long, dailyCap: Int): Int {
        val safeDailyCap = dailyCap.coerceAtLeast(1)
        return database.withTransaction {
            val dueWords = progressDao.getProgressByBook(bookId)
                .filter { it.status != Progress.STATUS_MASTERED && it.nextReviewTime > 0L && DateUtils.isDue(it.nextReviewTime) }
                .sortedBy { it.nextReviewTime }
            if (dueWords.size <= safeDailyCap) return@withTransaction 0

            val overflow = dueWords.drop(safeDailyCap)
            val now = System.currentTimeMillis()
            val daySlots = ceil(overflow.size.toDouble() / safeDailyCap.toDouble())
                .toInt()
                .coerceAtLeast(1)

            overflow.forEachIndexed { index, progress ->
                val dayOffset = (index % daySlots) + 1
                val nextReviewTime = Sm2Scheduler.nextReviewTimeByDays(dayOffset, now)
                progressDao.update(progress.copy(nextReviewTime = nextReviewTime))
                earlyReviewDao.deleteByWordAndBook(progress.wordId, bookId)
            }
            overflow.size
        }
    }

    fun getProgressByBookFlow(bookId: Long): Flow<List<Progress>> = progressDao.getProgressByBookFlow(bookId)

    suspend fun getProgressByBook(bookId: Long): List<Progress> = progressDao.getProgressByBook(bookId)

    suspend fun applyStudyResult(wordId: Long, bookId: Long, rating: StudyRating) {
        database.withTransaction {
            val existing = progressDao.getGlobalProgress(wordId)
            val schedule = Sm2Scheduler.schedule(existing, rating)
            val isNewWord = existing == null
            val reviewCount = (existing?.reviewCount ?: 0) + 1
            val linkedBookIds = (wordDao.getBookIdsByWordId(wordId) + bookId).distinct()
            linkedBookIds.forEach { linkedBookId ->
                val existingByBook = progressDao.getProgress(wordId, linkedBookId)
                val progress = Progress(
                    id = existingByBook?.id ?: 0,
                    wordId = wordId,
                    bookId = linkedBookId,
                    status = schedule.status,
                    repetitions = schedule.repetitions,
                    intervalDays = schedule.intervalDays,
                    nextReviewTime = schedule.nextReviewTime,
                    easeFactor = schedule.easeFactor,
                    reviewCount = reviewCount,
                    spellCorrectCount = existing?.spellCorrectCount ?: 0,
                    spellWrongCount = existing?.spellWrongCount ?: 0
                )
                if (existingByBook != null) {
                    progressDao.update(progress.copy(id = existingByBook.id))
                } else {
                    progressDao.insert(progress)
                }
            }
            earlyReviewDao.deleteByWord(wordId)
            recordStudyInternal()
            updateDailyStatsInternal(
                newWordsDelta = if (isNewWord) 1 else 0,
                reviewWordsDelta = if (isNewWord) 0 else 1
            )
        }
    }

    suspend fun applySpellingOutcome(
        wordId: Long,
        bookId: Long,
        outcome: SpellingOutcome,
        attemptCount: Int,
        durationMillis: Long = 0L
    ) {
        database.withTransaction {
            val existing = progressDao.getGlobalProgress(wordId)
            val schedule = Sm2Scheduler.scheduleSpelling(existing, outcome)
            val isNewWord = existing == null
            val reviewCount = (existing?.reviewCount ?: 0) + 1
            val spellCorrectCount = (existing?.spellCorrectCount ?: 0) + outcome.spellCorrectDelta
            val spellWrongCount = (existing?.spellWrongCount ?: 0) + outcome.spellWrongDelta
            val linkedBookIds = (wordDao.getBookIdsByWordId(wordId) + bookId).distinct()
            linkedBookIds.forEach { linkedBookId ->
                val existingByBook = progressDao.getProgress(wordId, linkedBookId)
                val progress = Progress(
                    id = existingByBook?.id ?: 0,
                    wordId = wordId,
                    bookId = linkedBookId,
                    status = schedule.status,
                    repetitions = schedule.repetitions,
                    intervalDays = schedule.intervalDays,
                    nextReviewTime = schedule.nextReviewTime,
                    easeFactor = schedule.easeFactor,
                    reviewCount = reviewCount,
                    spellCorrectCount = spellCorrectCount,
                    spellWrongCount = spellWrongCount
                )
                if (existingByBook != null) {
                    progressDao.update(progress.copy(id = existingByBook.id))
                } else {
                    progressDao.insert(progress)
                }
            }
            earlyReviewDao.deleteByWord(wordId)
            recordStudyInternal()
            updateDailyStatsInternal(
                newWordsDelta = if (isNewWord) 1 else 0,
                reviewWordsDelta = if (isNewWord) 0 else 1,
                spellPracticeDelta = attemptCount.coerceAtLeast(1),
                durationMillisDelta = durationMillis.coerceAtLeast(0L)
            )
        }
    }

    suspend fun getStudyQueue(book: Book, newWordLimit: Int = DEFAULT_NEW_WORDS_LIMIT): List<Word> {
        val sourceWords = if (book.type == Book.TYPE_NEW_WORDS) {
            wordDao.getNewWordsList()
        } else {
            wordDao.getWordsByBookList(book.id)
        }
        if (sourceWords.isEmpty()) return emptyList()

        val progressList = progressDao.getProgressByWordIds(sourceWords.map { it.id })
            .groupBy { it.wordId }
            .mapNotNull { (_, grouped) ->
                grouped.maxWithOrNull(
                    compareBy<Progress> { it.reviewCount }
                        .thenBy { it.nextReviewTime }
                        .thenBy { it.id }
                )
            }
        val dueProgress = progressList
            .filter { it.status != Progress.STATUS_MASTERED && it.nextReviewTime > 0 && DateUtils.isDue(it.nextReviewTime) }
            .sortedBy { it.nextReviewTime }
        val dueOrder = dueProgress.mapIndexed { index, progress -> progress.wordId to index }.toMap()

        val dueWords = sourceWords
            .filter { dueOrder.containsKey(it.id) }
            .sortedBy { dueOrder.getValue(it.id) }

        val earlyReviewRefs = earlyReviewDao.getByBook(book.id)
        val earlyOrder = earlyReviewRefs.mapIndexed { index, ref -> ref.wordId to index }.toMap()
        val earlyReviewWords = sourceWords
            .filter { earlyOrder.containsKey(it.id) }
            .sortedBy { earlyOrder.getValue(it.id) }
            .filterNot { dueOrder.containsKey(it.id) }

        val progressIds = progressList.map { it.wordId }.toSet()
        val excludedIds = dueWords.map { it.id }.toSet() + earlyReviewWords.map { it.id }.toSet()
        val newWords = sourceWords
            .filter { it.id !in progressIds && it.id !in excludedIds }
            .take(newWordLimit)

        return dueWords + earlyReviewWords + newWords
    }

    fun getEarlyReviewCountFlow(bookId: Long): Flow<Int> = earlyReviewDao.getCountFlow(bookId)

    suspend fun getEarlyReviewWordIds(bookId: Long): Set<Long> {
        return earlyReviewDao.getByBook(bookId).map { it.wordId }.toSet()
    }

    suspend fun replaceEarlyReviewWords(bookId: Long, wordIds: Collection<Long>) {
        database.withTransaction {
            earlyReviewDao.deleteByBook(bookId)
            if (wordIds.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val refs = wordIds.distinct().mapIndexed { index, wordId ->
                    EarlyReviewRef(
                        wordId = wordId,
                        bookId = bookId,
                        addTime = now + index
                    )
                }
                earlyReviewDao.insertAll(refs)
            }
        }
    }

    // ---- NewWordRef ----

    fun getAllNewWordRefs(): Flow<List<NewWordRef>> = newWordRefDao.getAll()

    fun getNewWordCount(): Flow<Int> = newWordRefDao.getCount()

    fun getNewWordIdsFlow(): Flow<Set<Long>> {
        return newWordRefDao.getAll().map { refs -> refs.map { it.wordId }.toSet() }
    }

    suspend fun addToNewWords(wordId: Long): Boolean {
        getOrCreateNewWordsBook()
        if (newWordRefDao.exists(wordId)) return false
        newWordRefDao.insert(NewWordRef(wordId = wordId))
        updateNewWordsBookCount()
        return true
    }

    suspend fun removeFromNewWords(wordId: Long) {
        newWordRefDao.deleteByWordId(wordId)
        updateNewWordsBookCount()
    }

    suspend fun isInNewWords(wordId: Long): Boolean = newWordRefDao.exists(wordId)

    suspend fun clearNewWords() {
        val book = getOrCreateNewWordsBook()
        newWordRefDao.deleteAll()
        progressDao.deleteByBook(book.id)
        updateNewWordsBookCount()
    }

    // ---- Study Log ----

    fun getStudyLogs(): Flow<List<StudyLog>> = studyLogDao.getAll()

    fun getDailyStats(startDate: LocalDate, endDate: LocalDate): Flow<List<DailyStats>> {
        val start = startDate.format(DATE_FORMATTER)
        val end = endDate.format(DATE_FORMATTER)
        return dailyStatsDao.getByDateRange(start, end)
    }

    fun getDailyStatsAggregated(startDate: LocalDate, endDate: LocalDate): Flow<List<DailyStatsAggregate>> {
        val start = startDate.format(DATE_FORMATTER)
        val end = endDate.format(DATE_FORMATTER)
        return dailyStatsDao.getAggregatedByDateRange(start, end)
    }

    suspend fun recordSpellingResult(wordId: Long, bookId: Long, isCorrect: Boolean) {
        database.withTransaction {
            val existing = progressDao.getGlobalProgress(wordId)
            val spellCorrect = (existing?.spellCorrectCount ?: 0) + if (isCorrect) 1 else 0
            val spellWrong = (existing?.spellWrongCount ?: 0) + if (isCorrect) 0 else 1
            val linkedBookIds = (wordDao.getBookIdsByWordId(wordId) + bookId).distinct()
            linkedBookIds.forEach { linkedBookId ->
                val existingByBook = progressDao.getProgress(wordId, linkedBookId)
                val updated = if (existingByBook == null) {
                    Progress(
                        wordId = wordId,
                        bookId = linkedBookId,
                        spellCorrectCount = spellCorrect,
                        spellWrongCount = spellWrong
                    )
                } else {
                    existingByBook.copy(
                        spellCorrectCount = spellCorrect,
                        spellWrongCount = spellWrong
                    )
                }
                if (existingByBook == null) {
                    progressDao.insert(updated)
                } else {
                    progressDao.update(updated.copy(id = existingByBook.id))
                }
            }
            updateDailyStatsInternal(spellPracticeDelta = 1)
        }
    }

    suspend fun getTodayNewWordsCount(): Int {
        val today = currentLearningDate().format(DATE_FORMATTER)
        return dailyStatsDao.getByDate(today)?.newWordsCount ?: 0
    }

    suspend fun estimateRemainingNewWordsToday(book: Book, dailyLimit: Int): Int {
        if (book.type == Book.TYPE_NEW_WORDS) return Int.MAX_VALUE
        val todayLearned = getTodayNewWordsCount()
        val remainingByDailyLimit = (dailyLimit - todayLearned).coerceAtLeast(0)
        if (remainingByDailyLimit == 0) return 0
        val sourceWords = wordDao.getWordsByBookList(book.id)
        if (sourceWords.isEmpty()) return 0
        val globalProgressIds = progressDao.getProgressByWordIds(sourceWords.map { it.id })
            .map { it.wordId }
            .toSet()
        val remainingInBook = sourceWords.count { it.id !in globalProgressIds }
        return minOf(remainingByDailyLimit, remainingInBook)
    }

    // ---- Utility ----

    suspend fun getNextNewWord(bookId: Long): Word? = wordDao.getNextNewWord(bookId)

    suspend fun getOrCreateNewWordsBook(): Book {
        return bookDao.getNewWordsBook() ?: run {
            val id = bookDao.insert(
                Book(
                    name = PresetBookCatalog.NEW_WORDS_BOOK_NAME,
                    type = Book.TYPE_NEW_WORDS,
                    totalCount = 0,
                    isActive = false
                )
            )
            bookDao.getBookById(id)!!
        }
    }

    suspend fun updateBookTotalCount(bookId: Long) {
        val count = wordDao.getWordCount(bookId)
        bookDao.updateTotalCount(bookId, count)
    }

    private suspend fun syncPresetWords(bookId: Long, drafts: List<WordDraft>) {
        if (drafts.isEmpty()) {
            bookDao.updateTotalCount(bookId, 0)
            return
        }
        val existingWordKeys = wordDao.getWordsByBookList(bookId)
            .map { normalizeWordKey(it.word) }
            .toSet()
        val toInsert = drafts
            .filter { normalizeWordKey(it.word) !in existingWordKeys }
        upsertDraftsForBook(bookId, toInsert)
        bookDao.updateTotalCount(bookId, wordDao.getWordCount(bookId))
    }

    private fun normalizeWordKey(raw: String): String {
        return raw.trim().lowercase()
    }

    private suspend fun upsertDraftsForBook(bookId: Long, drafts: List<WordDraft>) {
        if (drafts.isEmpty()) return
        val contents = ArrayList<BookWordContent>(drafts.size)
        drafts.forEach { draft ->
            val rawWord = draft.word.trim()
            if (rawWord.isBlank()) return@forEach
            val wordId = getOrCreateWordId(rawWord, draft.phonetic.trim())
            contents.add(
                BookWordContent(
                    wordId = wordId,
                    bookId = bookId,
                    meaning = draft.meaning,
                    example = draft.example
                )
            )
        }
        if (contents.isNotEmpty()) {
            wordDao.upsertBookWordContents(contents)
        }
    }

    private suspend fun getOrCreateWordId(rawWord: String, rawPhonetic: String): Long {
        val key = normalizeWordKey(rawWord)
        check(key.isNotBlank()) { "word key must not be blank" }
        val existing = wordDao.getWordEntityByKey(key)
        if (existing != null) {
            if (existing.phonetic.isBlank() && rawPhonetic.isNotBlank()) {
                wordDao.updatePhonetic(existing.id, rawPhonetic)
            }
            return existing.id
        }

        val insertedId = wordDao.insertWordEntity(
            WordEntity(
                word = rawWord,
                wordKey = key,
                phonetic = rawPhonetic
            )
        )
        if (insertedId > 0) {
            return insertedId
        }
        return wordDao.getWordIdByKey(key)
            ?: error("failed to resolve word id for key: $key")
    }

    private suspend fun updateNewWordsBookCount() {
        val book = getOrCreateNewWordsBook()
        val count = newWordRefDao.getCountOnce()
        bookDao.updateTotalCount(book.id, count)
    }

    private suspend fun recordStudyInternal() {
        val today = currentLearningDate().format(DATE_FORMATTER)
        val now = System.currentTimeMillis()
        val existing = studyLogDao.getByDate(today)
        val updated = if (existing == null) {
            StudyLog(date = today, count = 1, updateTime = now)
        } else {
            existing.copy(count = existing.count + 1, updateTime = now)
        }
        studyLogDao.insert(updated)
    }

    private suspend fun updateDailyStatsInternal(
        newWordsDelta: Int = 0,
        reviewWordsDelta: Int = 0,
        spellPracticeDelta: Int = 0,
        durationMillisDelta: Long = 0L
    ) {
        val today = currentLearningDate().format(DATE_FORMATTER)
        val existing = dailyStatsDao.getOrCreate(today)
        val updated = existing.copy(
            newWordsCount = (existing.newWordsCount + newWordsDelta).coerceAtLeast(0),
            reviewWordsCount = (existing.reviewWordsCount + reviewWordsDelta).coerceAtLeast(0),
            spellPracticeCount = (existing.spellPracticeCount + spellPracticeDelta).coerceAtLeast(0),
            durationMillis = (existing.durationMillis + durationMillisDelta).coerceAtLeast(0L)
        )
        dailyStatsDao.update(updated)
    }

    private fun currentLearningDate(now: Long = System.currentTimeMillis()): LocalDate {
        val zonedNow = Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault())
        val date = zonedNow.toLocalDate()
        return if (zonedNow.hour < DAY_REFRESH_HOUR) date.minusDays(1) else date
    }

    companion object {
        private const val DEFAULT_NEW_WORDS_LIMIT = 20
        private const val DAY_REFRESH_HOUR = 4
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
