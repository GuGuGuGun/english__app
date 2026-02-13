package com.kaoyan.wordhelper.data.database

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kaoyan.wordhelper.data.dao.BookDao
import com.kaoyan.wordhelper.data.dao.DailyStatsDao
import com.kaoyan.wordhelper.data.dao.EarlyReviewDao
import com.kaoyan.wordhelper.data.dao.NewWordRefDao
import com.kaoyan.wordhelper.data.dao.ProgressDao
import com.kaoyan.wordhelper.data.dao.StudyLogDao
import com.kaoyan.wordhelper.data.dao.WordDao
import com.kaoyan.wordhelper.data.entity.Book
import com.kaoyan.wordhelper.data.entity.BookWordContent
import com.kaoyan.wordhelper.data.entity.DailyStats
import com.kaoyan.wordhelper.data.entity.EarlyReviewRef
import com.kaoyan.wordhelper.data.entity.NewWordRef
import com.kaoyan.wordhelper.data.entity.Progress
import com.kaoyan.wordhelper.data.entity.StudyLog
import com.kaoyan.wordhelper.data.model.PresetBookCatalog
import com.kaoyan.wordhelper.data.model.WordDraft
import com.kaoyan.wordhelper.data.entity.WordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Book::class,
        WordEntity::class,
        BookWordContent::class,
        Progress::class,
        NewWordRef::class,
        StudyLog::class,
        DailyStats::class,
        EarlyReviewRef::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun wordDao(): WordDao
    abstract fun progressDao(): ProgressDao
    abstract fun newWordRefDao(): NewWordRefDao
    abstract fun studyLogDao(): StudyLogDao
    abstract fun dailyStatsDao(): DailyStatsDao
    abstract fun earlyReviewDao(): EarlyReviewDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "kaoyan_words.db"
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6
                )
                .build()
        }

        @VisibleForTesting
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_progress ADD COLUMN repetitions INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tb_progress ADD COLUMN interval_days INTEGER NOT NULL DEFAULT 0")
            }
        }

        @VisibleForTesting
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_study_log (
                        date TEXT NOT NULL PRIMARY KEY,
                        count INTEGER NOT NULL,
                        update_time INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        @VisibleForTesting
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_progress ADD COLUMN review_count INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tb_progress ADD COLUMN spell_correct_count INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tb_progress ADD COLUMN spell_wrong_count INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_daily_stats (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        new_words_count INTEGER NOT NULL DEFAULT 0,
                        review_words_count INTEGER NOT NULL DEFAULT 0,
                        spell_practice_count INTEGER NOT NULL DEFAULT 0,
                        duration_millis INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tb_daily_stats_date ON tb_daily_stats(date)")
            }
        }

        @VisibleForTesting
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_early_review_ref (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word_id INTEGER NOT NULL,
                        book_id INTEGER NOT NULL,
                        add_time INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_tb_early_review_ref_word_id_book_id ON tb_early_review_ref(word_id, book_id)"
                )
            }
        }

        @VisibleForTesting
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tb_word RENAME TO tb_word_legacy")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_word (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word TEXT NOT NULL,
                        word_key TEXT NOT NULL,
                        phonetic TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tb_word_word_key ON tb_word(word_key)")

                db.execSQL(
                    """
                    INSERT INTO tb_word (id, word, word_key, phonetic)
                    SELECT grouped.new_id,
                           (
                               SELECT legacy.word
                               FROM tb_word_legacy legacy
                               WHERE LOWER(TRIM(legacy.word)) = grouped.word_key
                               ORDER BY legacy.id ASC
                               LIMIT 1
                           ) AS word,
                           grouped.word_key,
                           IFNULL(
                               (
                                   SELECT legacy.phonetic
                                   FROM tb_word_legacy legacy
                                   WHERE LOWER(TRIM(legacy.word)) = grouped.word_key
                                     AND TRIM(legacy.phonetic) <> ''
                                   ORDER BY legacy.id ASC
                                   LIMIT 1
                               ),
                               ''
                           ) AS phonetic
                    FROM (
                        SELECT MIN(id) AS new_id, LOWER(TRIM(word)) AS word_key
                        FROM tb_word_legacy
                        GROUP BY LOWER(TRIM(word))
                    ) AS grouped
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_book_word_content (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word_id INTEGER NOT NULL,
                        book_id INTEGER NOT NULL,
                        meaning TEXT NOT NULL,
                        example TEXT NOT NULL,
                        FOREIGN KEY(word_id) REFERENCES tb_word(id) ON DELETE CASCADE,
                        FOREIGN KEY(book_id) REFERENCES tb_book(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_tb_book_word_content_word_id_book_id ON tb_book_word_content(word_id, book_id)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tb_book_word_content_book_id ON tb_book_word_content(book_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tb_book_word_content_word_id ON tb_book_word_content(word_id)")

                db.execSQL(
                    """
                    INSERT INTO tb_book_word_content (word_id, book_id, meaning, example)
                    SELECT w.id,
                           legacy.book_id,
                           legacy.meaning,
                           legacy.example
                    FROM tb_word_legacy legacy
                    INNER JOIN tb_word w ON w.word_key = LOWER(TRIM(legacy.word))
                    WHERE legacy.id = (
                        SELECT legacy2.id
                        FROM tb_word_legacy legacy2
                        WHERE legacy2.book_id = legacy.book_id
                          AND LOWER(TRIM(legacy2.word)) = LOWER(TRIM(legacy.word))
                        ORDER BY legacy2.id DESC
                        LIMIT 1
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_word_id_map (
                        old_id INTEGER PRIMARY KEY NOT NULL,
                        new_id INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO tb_word_id_map (old_id, new_id)
                    SELECT legacy.id, w.id
                    FROM tb_word_legacy legacy
                    INNER JOIN tb_word w ON w.word_key = LOWER(TRIM(legacy.word))
                    """.trimIndent()
                )

                db.execSQL("ALTER TABLE tb_progress RENAME TO tb_progress_legacy")
                db.execSQL("DROP INDEX IF EXISTS index_tb_progress_word_id_book_id")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_progress (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word_id INTEGER NOT NULL,
                        book_id INTEGER NOT NULL,
                        status INTEGER NOT NULL,
                        repetitions INTEGER NOT NULL DEFAULT 0,
                        interval_days INTEGER NOT NULL DEFAULT 0,
                        next_review_time INTEGER NOT NULL,
                        ease_factor REAL NOT NULL,
                        review_count INTEGER NOT NULL DEFAULT 0,
                        spell_correct_count INTEGER NOT NULL DEFAULT 0,
                        spell_wrong_count INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO tb_progress (
                        id,
                        word_id,
                        book_id,
                        status,
                        repetitions,
                        interval_days,
                        next_review_time,
                        ease_factor,
                        review_count,
                        spell_correct_count,
                        spell_wrong_count
                    )
                    SELECT legacy.id,
                           mapped.new_id,
                           legacy.book_id,
                           legacy.status,
                           legacy.repetitions,
                           legacy.interval_days,
                           legacy.next_review_time,
                           legacy.ease_factor,
                           legacy.review_count,
                           legacy.spell_correct_count,
                           legacy.spell_wrong_count
                    FROM tb_progress_legacy legacy
                    INNER JOIN tb_word_id_map mapped ON mapped.old_id = legacy.word_id
                    WHERE legacy.id = (
                        SELECT legacy2.id
                        FROM tb_progress_legacy legacy2
                        INNER JOIN tb_word_id_map mapped2 ON mapped2.old_id = legacy2.word_id
                        WHERE mapped2.new_id = mapped.new_id
                          AND legacy2.book_id = legacy.book_id
                        ORDER BY legacy2.id DESC
                        LIMIT 1
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_tb_progress_word_id_book_id ON tb_progress(word_id, book_id)"
                )
                db.execSQL("DROP TABLE tb_progress_legacy")

                db.execSQL("ALTER TABLE tb_new_word_ref RENAME TO tb_new_word_ref_legacy")
                db.execSQL("DROP INDEX IF EXISTS index_tb_new_word_ref_word_id")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_new_word_ref (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word_id INTEGER NOT NULL,
                        add_time INTEGER NOT NULL,
                        note TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO tb_new_word_ref (id, word_id, add_time, note)
                    SELECT legacy.id,
                           mapped.new_id,
                           legacy.add_time,
                           legacy.note
                    FROM tb_new_word_ref_legacy legacy
                    INNER JOIN tb_word_id_map mapped ON mapped.old_id = legacy.word_id
                    WHERE legacy.id = (
                        SELECT legacy2.id
                        FROM tb_new_word_ref_legacy legacy2
                        INNER JOIN tb_word_id_map mapped2 ON mapped2.old_id = legacy2.word_id
                        WHERE mapped2.new_id = mapped.new_id
                        ORDER BY legacy2.id DESC
                        LIMIT 1
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tb_new_word_ref_word_id ON tb_new_word_ref(word_id)")
                db.execSQL("DROP TABLE tb_new_word_ref_legacy")

                db.execSQL("ALTER TABLE tb_early_review_ref RENAME TO tb_early_review_ref_legacy")
                db.execSQL("DROP INDEX IF EXISTS index_tb_early_review_ref_word_id_book_id")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_early_review_ref (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word_id INTEGER NOT NULL,
                        book_id INTEGER NOT NULL,
                        add_time INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO tb_early_review_ref (id, word_id, book_id, add_time)
                    SELECT legacy.id,
                           mapped.new_id,
                           legacy.book_id,
                           legacy.add_time
                    FROM tb_early_review_ref_legacy legacy
                    INNER JOIN tb_word_id_map mapped ON mapped.old_id = legacy.word_id
                    WHERE legacy.id = (
                        SELECT legacy2.id
                        FROM tb_early_review_ref_legacy legacy2
                        INNER JOIN tb_word_id_map mapped2 ON mapped2.old_id = legacy2.word_id
                        WHERE mapped2.new_id = mapped.new_id
                          AND legacy2.book_id = legacy.book_id
                        ORDER BY legacy2.id DESC
                        LIMIT 1
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_tb_early_review_ref_word_id_book_id ON tb_early_review_ref(word_id, book_id)"
                )
                db.execSQL("DROP TABLE tb_early_review_ref_legacy")

                db.execSQL("DROP TABLE tb_word_id_map")
                db.execSQL("DROP TABLE tb_word_legacy")
            }
        }

        private suspend fun prepopulate(database: AppDatabase) {
            val bookDao = database.bookDao()
            val wordDao = database.wordDao()

            bookDao.insert(
                Book(
                    name = PresetBookCatalog.NEW_WORDS_BOOK_NAME,
                    type = Book.TYPE_NEW_WORDS,
                    totalCount = 0,
                    isActive = false
                )
            )

            PresetBookCatalog.presets.forEachIndexed { index, preset ->
                val bookId = bookDao.insert(
                    Book(
                        name = preset.name,
                        type = Book.TYPE_PRESET,
                        totalCount = 0,
                        isActive = index == 0
                    )
                )
                upsertDraftsForBook(wordDao, bookId, preset.drafts)
                bookDao.updateTotalCount(bookId, wordDao.getWordCount(bookId))
            }
        }

        private suspend fun upsertDraftsForBook(wordDao: WordDao, bookId: Long, drafts: List<WordDraft>) {
            if (drafts.isEmpty()) return
            val contents = ArrayList<BookWordContent>(drafts.size)
            drafts.forEach { draft ->
                val rawWord = draft.word.trim()
                if (rawWord.isBlank()) return@forEach

                val phonetic = draft.phonetic.trim()
                val key = normalizeWordKey(rawWord)
                val existing = wordDao.getWordEntityByKey(key)
                val wordId = if (existing != null) {
                    if (existing.phonetic.isBlank() && phonetic.isNotBlank()) {
                        wordDao.updatePhonetic(existing.id, phonetic)
                    }
                    existing.id
                } else {
                    val insertedId = wordDao.insertWordEntity(
                        WordEntity(
                            word = rawWord,
                            wordKey = key,
                            phonetic = phonetic
                        )
                    )
                    if (insertedId > 0) {
                        insertedId
                    } else {
                        wordDao.getWordIdByKey(key) ?: return@forEach
                    }
                }

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

        private fun normalizeWordKey(raw: String): String {
            return raw.trim().lowercase()
        }
    }
}
