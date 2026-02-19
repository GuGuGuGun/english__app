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
class MLMigrationTest {

    @Test
    fun migrate10To11_addsMLTablesAndColumns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DB)
        createVersion10Database(context)

        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12)
            .build()

        val db = roomDb.openHelper.writableDatabase

        // 验证 tb_progress 新增列
        db.query("PRAGMA table_info('tb_progress')").use { cursor ->
            var hasConsecutiveCorrect = false
            var hasAvgResponseTimeMs = false
            var hasLastReviewTime = false
            while (cursor.moveToNext()) {
                when (cursor.getString(1)) {
                    "consecutive_correct" -> hasConsecutiveCorrect = true
                    "avg_response_time_ms" -> hasAvgResponseTimeMs = true
                    "last_review_time" -> hasLastReviewTime = true
                }
            }
            assertTrue("应有 consecutive_correct 列", hasConsecutiveCorrect)
            assertTrue("应有 avg_response_time_ms 列", hasAvgResponseTimeMs)
            assertTrue("应有 last_review_time 列", hasLastReviewTime)
        }

        // 验证新增列默认值
        db.query(
            "SELECT consecutive_correct, avg_response_time_ms, last_review_time FROM tb_progress WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals(0f, cursor.getFloat(1), 0.01f)
            assertEquals(0L, cursor.getLong(2))
        }

        // 验证 ml_model_state 表存在
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='ml_model_state'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        // 验证 training_samples 表存在
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='training_samples'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        // 验证 word_ml_stats 表存在
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='word_ml_stats'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        // 验证原有数据不丢失
        db.query("SELECT ease_factor FROM tb_progress WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2.5f, cursor.getFloat(0), 0.01f)
        }

        // 验证可以插入 ml_model_state
        db.execSQL(
            """INSERT INTO ml_model_state (id, n_params_json, z_params_json, weights_json,
               version, sample_count, last_training_time, global_accuracy,
               user_base_retention, avg_response_time, std_response_time)
               VALUES (1, '[]', '[]', '[]', 0, 0, 0, 0, 0.85, 0, 0)"""
        )
        db.query("SELECT user_base_retention FROM ml_model_state WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0.85f, cursor.getFloat(0), 0.01f)
        }

        // 验证可以插入 training_samples
        db.execSQL(
            """INSERT INTO training_samples (word_id, features_json, outcome, timestamp, prediction_error)
               VALUES (1, '[0.5]', 1, 1000000, 0.1)"""
        )
        db.query("SELECT COUNT(*) FROM training_samples").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }

        // 验证可以插入 word_ml_stats
        db.execSQL(
            """INSERT INTO word_ml_stats (word_id, predicted_difficulty, personal_ef, avg_forget_prob, review_count)
               VALUES (1, 0.5, 2.5, 0.3, 5)"""
        )
        db.query("SELECT personal_ef FROM word_ml_stats WHERE word_id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2.5f, cursor.getFloat(0), 0.01f)
        }

        roomDb.close()
        context.deleteDatabase(TEST_DB)
    }

    private fun createVersion10Database(context: Context) {
        val callback = object : SupportSQLiteOpenHelper.Callback(10) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                // 最小化的 v10 schema，只包含 tb_progress 和必要的表
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
                        word_key TEXT NOT NULL DEFAULT '',
                        phonetic TEXT NOT NULL DEFAULT '',
                        book_id INTEGER NOT NULL,
                        FOREIGN KEY(book_id) REFERENCES tb_book(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_book_word_content (
                        word_id INTEGER NOT NULL,
                        book_id INTEGER NOT NULL,
                        meaning TEXT NOT NULL DEFAULT '',
                        example TEXT NOT NULL DEFAULT '',
                        phrases TEXT NOT NULL DEFAULT '',
                        synonyms TEXT NOT NULL DEFAULT '',
                        rel_words TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(word_id, book_id)
                    )
                    """.trimIndent()
                )
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
                        spell_wrong_count INTEGER NOT NULL DEFAULT 0,
                        marked_easy_count INTEGER NOT NULL DEFAULT 0,
                        last_easy_time INTEGER NOT NULL DEFAULT 0
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
                        note TEXT NOT NULL DEFAULT '',
                        source INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_study_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_daily_stats (
                        date TEXT NOT NULL PRIMARY KEY,
                        new_words_count INTEGER NOT NULL DEFAULT 0,
                        review_words_count INTEGER NOT NULL DEFAULT 0,
                        spell_practice_count INTEGER NOT NULL DEFAULT 0,
                        total_duration_millis INTEGER NOT NULL DEFAULT 0,
                        gesture_easy_count INTEGER NOT NULL DEFAULT 0,
                        gesture_notebook_count INTEGER NOT NULL DEFAULT 0,
                        fuzzy_words_count INTEGER NOT NULL DEFAULT 0,
                        recognized_words_count INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_early_review_ref (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word_id INTEGER NOT NULL,
                        book_id INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_ai_cache (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        word_id INTEGER NOT NULL,
                        word TEXT NOT NULL,
                        content_type TEXT NOT NULL,
                        content TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tb_forecast_cache (
                        id INTEGER NOT NULL PRIMARY KEY,
                        json_data TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                // 插入测试数据
                db.execSQL(
                    "INSERT INTO tb_book VALUES (1, '考研核心词汇', 0, 1, 1)"
                )
                db.execSQL(
                    "INSERT INTO tb_word (id, word, word_key, phonetic, book_id) VALUES (1, 'abandon', 'abandon', '', 1)"
                )
                db.execSQL(
                    "INSERT INTO tb_book_word_content VALUES (1, 1, '', '', '', '', '')"
                )
                db.execSQL(
                    "INSERT INTO tb_progress (id, word_id, book_id, status, next_review_time, ease_factor) VALUES (1, 1, 1, 1, 1700000000000, 2.5)"
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
        private const val TEST_DB = "ml-migration-test"
    }
}
