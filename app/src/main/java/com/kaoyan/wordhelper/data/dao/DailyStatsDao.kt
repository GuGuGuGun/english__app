package com.kaoyan.wordhelper.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.kaoyan.wordhelper.data.entity.DailyStats
import com.kaoyan.wordhelper.data.model.DailyStatsAggregate
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStatsDao {

    @Query(
        "SELECT * FROM tb_daily_stats WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC"
    )
    fun getByDateRange(startDate: String, endDate: String): Flow<List<DailyStats>>

    @Query(
        """
        SELECT date,
               SUM(new_words_count) AS new_words_count,
               SUM(review_words_count) AS review_words_count,
               SUM(spell_practice_count) AS spell_practice_count,
               SUM(duration_millis) AS duration_millis,
               SUM(gesture_easy_count) AS gesture_easy_count,
               SUM(gesture_notebook_count) AS gesture_notebook_count,
               SUM(fuzzy_words_count) AS fuzzy_words_count,
               SUM(recognized_words_count) AS recognized_words_count
        FROM tb_daily_stats
        WHERE date BETWEEN :startDate AND :endDate
        GROUP BY date
        ORDER BY date ASC
        """
    )
    fun getAggregatedByDateRange(startDate: String, endDate: String): Flow<List<DailyStatsAggregate>>

    @Query("SELECT * FROM tb_daily_stats WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyStats?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(stats: DailyStats): Long

    @Update
    suspend fun update(stats: DailyStats)

    @Transaction
    suspend fun getOrCreate(date: String): DailyStats {
        val existing = getByDate(date)
        if (existing != null) return existing
        insert(DailyStats(date = date))
        return getByDate(date)!!
    }
}
