package com.kaoyan.wordhelper.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tb_progress",
    indices = [
        Index(value = ["word_id", "book_id"], unique = true),
        Index(value = ["next_review_time"])
    ]
)
data class Progress(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "word_id")
    val wordId: Long,
    @ColumnInfo(name = "book_id")
    val bookId: Long,
    val status: Int = STATUS_NEW,
    val repetitions: Int = 0,
    @ColumnInfo(name = "interval_days")
    val intervalDays: Int = 0,
    @ColumnInfo(name = "next_review_time")
    val nextReviewTime: Long = 0L,
    @ColumnInfo(name = "ease_factor")
    val easeFactor: Float = 2.5f,
    @ColumnInfo(name = "review_count")
    val reviewCount: Int = 0,
    @ColumnInfo(name = "spell_correct_count")
    val spellCorrectCount: Int = 0,
    @ColumnInfo(name = "spell_wrong_count")
    val spellWrongCount: Int = 0,
    @ColumnInfo(name = "marked_easy_count")
    val markedEasyCount: Int = 0,
    @ColumnInfo(name = "last_easy_time")
    val lastEasyTime: Long = 0L,
    @ColumnInfo(name = "consecutive_correct")
    val consecutiveCorrect: Int = 0,
    @ColumnInfo(name = "avg_response_time_ms")
    val avgResponseTimeMs: Float = 0f,
    @ColumnInfo(name = "last_review_time")
    val lastReviewTime: Long = 0L
) {
    companion object {
        const val STATUS_NEW = 0
        const val STATUS_LEARNING = 1
        const val STATUS_MASTERED = 2
    }
}
