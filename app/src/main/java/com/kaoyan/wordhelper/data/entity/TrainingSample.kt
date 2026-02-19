package com.kaoyan.wordhelper.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "training_samples")
data class TrainingSample(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "features_json")
    val featuresJson: String,
    val outcome: Int,
    val timestamp: Long,
    @ColumnInfo(name = "prediction_error")
    val predictionError: Float = 0f
)
