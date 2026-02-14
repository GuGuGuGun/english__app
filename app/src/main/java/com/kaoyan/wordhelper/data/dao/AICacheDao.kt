package com.kaoyan.wordhelper.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kaoyan.wordhelper.data.entity.AICache

@Dao
interface AICacheDao {

    @Query(
        """SELECT * FROM tb_ai_cache
           WHERE word_id = :wordId AND type = :type
           ORDER BY created_at DESC
           LIMIT 1"""
    )
    suspend fun getByWordIdAndType(wordId: Long, type: String): AICache?

    @Query(
        """SELECT * FROM tb_ai_cache
           WHERE query_content = :queryContent AND type = :type
           ORDER BY created_at DESC
           LIMIT 1"""
    )
    suspend fun getByQueryAndType(queryContent: String, type: String): AICache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: AICache): Long

    @Query("DELETE FROM tb_ai_cache WHERE created_at < :beforeTime")
    suspend fun deleteOlderThan(beforeTime: Long): Int
}
