package com.kaoyan.wordhelper.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tb_new_word_ref",
    indices = [Index(value = ["word_id"], unique = true)]
)
data class NewWordRef(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "add_time")
    val addTime: Long = System.currentTimeMillis(),
    val note: String = ""
)
