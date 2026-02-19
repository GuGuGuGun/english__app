package com.kaoyan.wordhelper.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.kaoyan.wordhelper.data.entity.TrainingSample
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingSampleDao {

    @Insert
    suspend fun insert(sample: TrainingSample): Long

    @Insert
    suspend fun insertAll(samples: List<TrainingSample>)

    @Query("SELECT * FROM training_samples ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<TrainingSample>

    @Query("SELECT * FROM training_samples ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentSamples(limit: Int): Flow<List<TrainingSample>>

    @Query("SELECT * FROM training_samples ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getRecentBatch(limit: Int, offset: Int): List<TrainingSample>

    @Query("SELECT COUNT(*) FROM training_samples")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM training_samples")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM training_samples WHERE id NOT IN (SELECT id FROM training_samples ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun trimOldSamples(keepCount: Int)

    @Query("DELETE FROM training_samples")
    suspend fun deleteAll()
}
