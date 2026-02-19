package com.kaoyan.wordhelper.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "word_ml_stats")
data class WordMLStats(
    @PrimaryKey
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "predicted_difficulty")
    val predictedDifficulty: Float = 0.5f,
    @ColumnInfo(name = "personal_ef")
    val personalEf: Float = 2.5f,
    @ColumnInfo(name = "avg_forget_prob")
    val avgForgetProb: Float = 0.5f,
    @ColumnInfo(name = "review_count")
    val reviewCount: Int = 0
)
