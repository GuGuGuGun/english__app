package com.kaoyan.wordhelper.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tb_daily_stats",
    indices = [Index(value = ["date"], unique = true)]
)
data class DailyStats(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    @ColumnInfo(name = "new_words_count")
    val newWordsCount: Int = 0,
    @ColumnInfo(name = "review_words_count")
    val reviewWordsCount: Int = 0,
    @ColumnInfo(name = "spell_practice_count")
    val spellPracticeCount: Int = 0,
    @ColumnInfo(name = "duration_millis")
    val durationMillis: Long = 0L,
    @ColumnInfo(name = "gesture_easy_count")
    val gestureEasyCount: Int = 0,
    @ColumnInfo(name = "gesture_notebook_count")
    val gestureNotebookCount: Int = 0
)
