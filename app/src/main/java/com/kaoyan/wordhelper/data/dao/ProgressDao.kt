package com.kaoyan.wordhelper.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kaoyan.wordhelper.data.entity.Progress
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Query("SELECT * FROM tb_progress WHERE word_id = :wordId AND book_id = :bookId")
    suspend fun getProgress(wordId: Long, bookId: Long): Progress?

    @Query(
        """SELECT * FROM tb_progress
           WHERE word_id = :wordId
           ORDER BY review_count DESC, next_review_time DESC, id DESC
           LIMIT 1"""
    )
    suspend fun getGlobalProgress(wordId: Long): Progress?

    @Query("SELECT * FROM tb_progress WHERE word_id IN (:wordIds)")
    suspend fun getProgressByWordIds(wordIds: List<Long>): List<Progress>

    @Query("SELECT COUNT(*) FROM tb_progress WHERE book_id = :bookId AND status >= 1")
    fun getLearnedCount(bookId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM tb_progress WHERE book_id = :bookId AND status = 2")
    fun getMasteredCount(bookId: Long): Flow<Int>

    @Query(
        """SELECT * FROM tb_progress WHERE book_id = :bookId 
           AND status != 2 AND next_review_time > 0 AND next_review_time <= :now 
           ORDER BY next_review_time ASC"""
    )
    suspend fun getDueWords(bookId: Long, now: Long = System.currentTimeMillis()): List<Progress>

    @Query("SELECT * FROM tb_progress WHERE book_id = :bookId")
    suspend fun getProgressByBook(bookId: Long): List<Progress>

    @Query("SELECT * FROM tb_progress WHERE book_id = :bookId")
    fun getProgressByBookFlow(bookId: Long): Flow<List<Progress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: Progress): Long

    @Update
    suspend fun update(progress: Progress)

    @Query("DELETE FROM tb_progress WHERE book_id = :bookId")
    suspend fun deleteByBook(bookId: Long)
}
