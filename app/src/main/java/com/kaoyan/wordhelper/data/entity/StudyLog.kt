package com.kaoyan.wordhelper.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tb_study_log")
data class StudyLog(
    @PrimaryKey
    val date: String,
    val count: Int = 0,
    @ColumnInfo(name = "update_time")
    val updateTime: Long = System.currentTimeMillis()
)
