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
    val durationMillis: Long
)
