package com.kaoyan.wordhelper.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kaoyan.wordhelper.data.entity.NewWordRef
import kotlinx.coroutines.flow.Flow

@Dao
interface NewWordRefDao {

    @Query("SELECT * FROM tb_new_word_ref ORDER BY add_time DESC")
    fun getAll(): Flow<List<NewWordRef>>

    @Query("SELECT EXISTS(SELECT 1 FROM tb_new_word_ref WHERE word_id = :wordId)")
    suspend fun exists(wordId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(ref: NewWordRef): Long

    @Query("DELETE FROM tb_new_word_ref WHERE word_id = :wordId")
    suspend fun deleteByWordId(wordId: Long)

    @Query("DELETE FROM tb_new_word_ref")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM tb_new_word_ref")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tb_new_word_ref")
    suspend fun getCountOnce(): Int
}
