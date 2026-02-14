package com.kaoyan.wordhelper.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tb_ai_cache",
    indices = [
        Index(value = ["word_id", "type"]),
        Index(value = ["query_content", "type"])
    ]
)
data class AICache(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "word_id")
    val wordId: Long? = null,
    @ColumnInfo(name = "query_content")
    val queryContent: String,
    val type: String,
    @ColumnInfo(name = "ai_content")
    val aiContent: String,
    @ColumnInfo(name = "model_name")
    val modelName: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
