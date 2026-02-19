package com.kaoyan.wordhelper.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kaoyan.wordhelper.data.entity.MLModelState
import kotlinx.coroutines.flow.Flow

@Dao
interface MLModelStateDao {

    @Query("SELECT * FROM ml_model_state WHERE id = 1")
    suspend fun get(): MLModelState?

    @Query("SELECT * FROM ml_model_state WHERE id = 1")
    fun observe(): Flow<MLModelState?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: MLModelState)

    @Update
    suspend fun update(state: MLModelState)

    @Query("UPDATE ml_model_state SET sample_count = :sampleCount, last_training_time = :time WHERE id = 1")
    suspend fun updateSampleCount(sampleCount: Int, time: Long)

    @Query(
        """UPDATE ml_model_state
           SET avg_response_time = :avgTime, std_response_time = :stdTime
           WHERE id = 1"""
    )
    suspend fun updateResponseTimeStats(avgTime: Float, stdTime: Float)

    @Query(
        """UPDATE ml_model_state
           SET user_base_retention = :retention,
               global_accuracy = :accuracy,
               last_training_time = :time
           WHERE id = 1"""
    )
    suspend fun updateLearningMetrics(retention: Float, accuracy: Float, time: Long)
}
