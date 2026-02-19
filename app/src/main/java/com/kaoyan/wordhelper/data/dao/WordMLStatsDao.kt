package com.kaoyan.wordhelper.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kaoyan.wordhelper.data.entity.WordMLStats

@Dao
interface WordMLStatsDao {

    @Query("SELECT * FROM word_ml_stats WHERE word_id = :wordId")
    suspend fun get(wordId: Long): WordMLStats?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stats: WordMLStats)

    @Update
    suspend fun update(stats: WordMLStats)

    @Query("SELECT AVG(predicted_difficulty) FROM word_ml_stats")
    suspend fun getAverageDifficulty(): Float?

    @Query("SELECT AVG(avg_forget_prob) FROM word_ml_stats WHERE review_count > 0")
    suspend fun getAverageForgetProb(): Float?
}
