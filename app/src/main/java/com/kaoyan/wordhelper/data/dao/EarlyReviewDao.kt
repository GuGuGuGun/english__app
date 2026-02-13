package com.kaoyan.wordhelper.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kaoyan.wordhelper.data.entity.EarlyReviewRef
import kotlinx.coroutines.flow.Flow

@Dao
interface EarlyReviewDao {

    @Query("SELECT * FROM tb_early_review_ref WHERE book_id = :bookId ORDER BY add_time ASC")
    suspend fun getByBook(bookId: Long): List<EarlyReviewRef>

    @Query("SELECT * FROM tb_early_review_ref WHERE book_id = :bookId ORDER BY add_time ASC")
    fun getByBookFlow(bookId: Long): Flow<List<EarlyReviewRef>>

    @Query("SELECT COUNT(*) FROM tb_early_review_ref WHERE book_id = :bookId")
    fun getCountFlow(bookId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(refs: List<EarlyReviewRef>)

    @Query("DELETE FROM tb_early_review_ref WHERE word_id = :wordId AND book_id = :bookId")
    suspend fun deleteByWordAndBook(wordId: Long, bookId: Long)

    @Query("DELETE FROM tb_early_review_ref WHERE word_id = :wordId")
    suspend fun deleteByWord(wordId: Long)

    @Query("DELETE FROM tb_early_review_ref WHERE book_id = :bookId")
    suspend fun deleteByBook(bookId: Long)
}
