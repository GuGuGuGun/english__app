package com.kaoyan.wordhelper.data.model

import androidx.room.ColumnInfo

data class DailyStatsAggregate(
    val date: String,
    @ColumnInfo(name = "new_words_count")
    val newWordsCount: Int,
    @ColumnInfo(name = "review_words_count")
    val reviewWordsCount: Int,
    @ColumnInfo(name = "spell_practice_count")
    val spellPracticeCount: Int,
    @ColumnInfo(name = "duration_millis")
    val durationMillis: Long,
    @ColumnInfo(name = "gesture_easy_count")
    val gestureEasyCount: Int,
    @ColumnInfo(name = "gesture_notebook_count")
    val gestureNotebookCount: Int
)
