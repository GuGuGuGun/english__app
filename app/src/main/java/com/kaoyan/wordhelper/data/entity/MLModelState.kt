package com.kaoyan.wordhelper.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ml_model_state")
data class MLModelState(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "n_params_json")
    val nParamsJson: String = "",
    @ColumnInfo(name = "z_params_json")
    val zParamsJson: String = "",
    @ColumnInfo(name = "weights_json")
    val weightsJson: String = "",
    val version: Int = 0,
    @ColumnInfo(name = "sample_count")
    val sampleCount: Int = 0,
    @ColumnInfo(name = "last_training_time")
    val lastTrainingTime: Long = 0L,
    @ColumnInfo(name = "global_accuracy")
    val globalAccuracy: Float = 0f,
    @ColumnInfo(name = "user_base_retention")
    val userBaseRetention: Float = 0.85f,
    @ColumnInfo(name = "avg_response_time")
    val avgResponseTime: Float = 0f,
    @ColumnInfo(name = "std_response_time")
    val stdResponseTime: Float = 0f
)
