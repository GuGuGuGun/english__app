package com.kaoyan.wordhelper.data.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @Test
    fun migrate1To10_preservesProgressData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DB)
        createVersion1Database(context)

        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12
            )
            .build()

        val migratedDb = roomDb.openHelper.writableDatabase

        migratedDb.query("SELECT COUNT(*) FROM tb_progress").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        migratedDb.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='tb_early_review_ref'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        migratedDb.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='tb_book_word_content'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        migratedDb.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='tb_ai_cache'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        migratedDb.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='tb_forecast_cache'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        migratedDb.query(
            """
            SELECT repetitions, interval_days, review_count, spell_correct_count, spell_wrong_count
            FROM tb_progress WHERE id = 1
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals(0, cursor.getInt(3))
            assertEquals(0, cursor.getInt(4))
        }

        migratedDb.query(
            """
            SELECT marked_easy_count, last_easy_time
            FROM tb_progress WHERE id = 1
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals(0L, cursor.getLong(1))
        }

        migratedDb.query(
            """
            SELECT gesture_easy_count, gesture_notebook_count
            FROM tb_daily_stats
            WHERE date = '2026-02-15'
            LIMIT 1
            """.trimIndent()
        ).use { cursor ->
            // daily stats row may not exist in seeded v1 db; validate schema by querying pragma instead.
            if (cursor.moveToFirst()) {
                assertEquals(0, cursor.getInt(0))
                assertEquals(0, cursor.getInt(1))
            }
        }

        migratedDb.query("PRAGMA table_info('tb_daily_stats')").use { cursor ->
            var hasGestureEasy = false
            var hasGestureNotebook = false
            var hasFuzzyWords = false
            var hasRecognizedWords = false
            while (cursor.moveToNext()) {
                when (cursor.getString(1)) {
                    "gesture_easy_count" -> hasGestureEasy = true
                    "gesture_notebook_count" -> hasGestureNotebook = true
                    "fuzzy_words_count" -> hasFuzzyWords = true
                    "recognized_words_count" -> hasRecognizedWords = true
                }
            }
            assertTrue(hasGestureEasy)
            assertTrue(hasGestureNotebook)
            assertTrue(hasFuzzyWords)
            assertTrue(hasRecognizedWords)
        }

        migratedDb.query("SELECT word, word_key, phonetic FROM tb_word WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("abandon", cursor.getString(0))
            assertEquals("abandon", cursor.getString(1))
            assertEquals("", cursor.getString(2))
        }

        migratedDb.query(
            """
            SELECT word_id, book_id, meaning, example
            FROM tb_book_word_content
            WHERE word_id = 1 AND book_id = 1
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals("", cursor.getString(2))
            assertEquals("", cursor.getString(3))
        }

        migratedDb.query("PRAGMA table_info('tb_book_word_content')").use { cursor ->
            var hasPhrases = false
            var hasSynonyms = false
            var hasRelWords = false
            while (cursor.moveToNext()) {
                when (cursor.getString(1)) {
                    "phrases" -> hasPhrases = true
                    "synonyms" -> hasSynonyms = true
                    "rel_words" -> hasRelWords = true
                }
            }
            assertTrue(hasPhrases)
            assertTrue(hasSynonyms)
            assertTrue(hasRelWords)
        }

        roomDb.close()
        context.deleteDatabase(TEST_DB)
    }

    private fun createVersion1Database(context: Context) {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_book (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        type INTEGER NOT NULL,
                        total_count INTEGER NOT NULL,
                        is_active INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_word (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word TEXT NOT NULL,
                        phonetic TEXT NOT NULL,
                        meaning TEXT NOT NULL,
                        example TEXT NOT NULL,
                        book_id INTEGER NOT NULL,
                        FOREIGN KEY(book_id) REFERENCES tb_book(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tb_word_book_id ON tb_word(book_id)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_progress (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word_id INTEGER NOT NULL,
                        book_id INTEGER NOT NULL,
                        status INTEGER NOT NULL,
                        next_review_time INTEGER NOT NULL,
                        ease_factor REAL NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_tb_progress_word_id_book_id ON tb_progress(word_id, book_id)"
                )
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
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_tb_new_word_ref_word_id ON tb_new_word_ref(word_id)"
                )
                db.execSQL(
                    "INSERT INTO tb_book (id, name, type, total_count, is_active) VALUES (1, '考研核心词汇', 0, 1, 1)"
                )
                db.execSQL(
                    "INSERT INTO tb_word (id, word, phonetic, meaning, example, book_id) VALUES " +
                        "(1, 'abandon', '', '', '', 1)"
                )
                db.execSQL(
                    "INSERT INTO tb_progress (id, word_id, book_id, status, next_review_time, ease_factor) VALUES " +
                        "(1, 1, 1, 1, 1700000000000, 2.5)"
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(callback)
                .build()
        )
        helper.writableDatabase.close()
        helper.close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
