package com.kaoyan.wordhelper.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kaoyan.wordhelper.data.entity.StudyLog
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyLogDao {

    @Query("SELECT * FROM tb_study_log ORDER BY date DESC")
    fun getAll(): Flow<List<StudyLog>>

    @Query("SELECT * FROM tb_study_log WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): StudyLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: StudyLog)
}
