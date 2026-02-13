package com.kaoyan.wordhelper.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tb_book")
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: Int,
    @ColumnInfo(name = "total_count")
    val totalCount: Int = 0,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = false
) {
    companion object {
        const val TYPE_PRESET = 0
        const val TYPE_IMPORTED = 1
        const val TYPE_NEW_WORDS = 2
    }
}
