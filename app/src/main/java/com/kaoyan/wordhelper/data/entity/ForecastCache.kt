package com.kaoyan.wordhelper.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tb_forecast_cache")
data class ForecastCache(
    @PrimaryKey
    val date: Long,
    @ColumnInfo(name = "review_count")
    val reviewCount: Int = 0,
    @ColumnInfo(name = "new_word_quota")
    val newWordQuota: Int = 0,
    @ColumnInfo(name = "is_calculated")
    val isCalculated: Boolean = false
)
