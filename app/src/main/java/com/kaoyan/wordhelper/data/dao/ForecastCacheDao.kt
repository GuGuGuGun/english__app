package com.kaoyan.wordhelper.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kaoyan.wordhelper.data.entity.ForecastCache

@Dao
interface ForecastCacheDao {

    @Query(
        """SELECT * FROM tb_forecast_cache
           WHERE date BETWEEN :startDate AND :endDate
           ORDER BY date ASC"""
    )
    suspend fun getRange(startDate: Long, endDate: Long): List<ForecastCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(caches: List<ForecastCache>)

    @Query("DELETE FROM tb_forecast_cache")
    suspend fun clearAll()
}
