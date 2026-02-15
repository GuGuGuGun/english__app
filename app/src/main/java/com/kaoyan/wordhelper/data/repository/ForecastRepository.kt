package com.kaoyan.wordhelper.data.repository

import androidx.room.withTransaction
import com.kaoyan.wordhelper.data.database.AppDatabase
import com.kaoyan.wordhelper.data.entity.ForecastCache
import com.kaoyan.wordhelper.data.entity.Progress
import com.kaoyan.wordhelper.data.entity.Word
import com.kaoyan.wordhelper.util.DayForecast
import com.kaoyan.wordhelper.util.ForecastCalculator
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ForecastDataSource {
    CACHE,
    CALCULATED
}

data class ForecastLoadResult(
    val days: List<DayForecast>,
    val source: ForecastDataSource,
    val elapsedMillis: Long
)

class ForecastRepository(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val calculator: ForecastCalculator = ForecastCalculator()
) {
    private val progressDao = database.progressDao()
    private val wordDao = database.wordDao()
    private val dailyStatsDao = database.dailyStatsDao()
    private val forecastCacheDao = database.forecastCacheDao()

    suspend fun calculateNext7DaysPressure(
        now: Long = System.currentTimeMillis()
    ): List<DayForecast> {
        return loadNext7DaysPressure(now).days
    }

    suspend fun loadNext7DaysPressure(
        now: Long = System.currentTimeMillis()
    ): ForecastLoadResult {
        val startedAt = System.currentTimeMillis()
        val settings = settingsRepository.settingsFlow.first()
        val dailyLimit = settings.newWordsLimit.coerceAtLeast(0)
        val today = currentLearningDate(now).format(DATE_FORMATTER)
        val todayLearned = dailyStatsDao.getByDate(today)?.newWordsCount ?: 0
        val todayRemaining = (dailyLimit - todayLearned).coerceAtLeast(0)
        val start = calculator.toStartOfDayMillis(now)
        val end = start + ONE_DAY_MILLIS * 6

        val cached = forecastCacheDao.getRange(start, end)
        if (isCacheValid(cached, start, dailyLimit, todayRemaining)) {
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            return ForecastLoadResult(
                days = cached.map {
                    DayForecast(
                        date = it.date,
                        reviewCount = it.reviewCount,
                        newWordQuota = it.newWordQuota
                    )
                },
                source = ForecastDataSource.CACHE,
                elapsedMillis = elapsed
            )
        }

        val pendingProgress = getGlobalPendingProgress()
        val forecasts = calculator.calculateNext7DaysPressure(
            reviewTimes = pendingProgress.map { it.nextReviewTime },
            now = now,
            dailyNewWordLimit = dailyLimit,
            todayRemainingNewWords = todayRemaining
        )

        // Cache is optional but cheap to keep in sync for future UI reads.
        val caches = forecasts.map { day ->
            ForecastCache(
                date = day.date,
                reviewCount = day.reviewCount,
                newWordQuota = day.newWordQuota,
                isCalculated = true
            )
        }
        forecastCacheDao.upsertAll(caches)
        val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
        return ForecastLoadResult(
            days = forecasts,
            source = ForecastDataSource.CALCULATED,
            elapsedMillis = elapsed
        )
    }

    suspend fun getWordsForDate(date: Long, limit: Int = 5): List<Word> {
        val safeLimit = limit.coerceAtLeast(1)
        val start = calculator.toStartOfDayMillis(date)
        val end = start + ONE_DAY_MILLIS
        val progress = progressDao.getWordsForDate(start, end, safeLimit)
            .distinctBy { it.wordId }
        return progress.mapNotNull { item -> wordDao.getWordById(item.wordId) }
    }

    suspend fun pullForwardReviews(targetDate: Long, count: Int): Int {
        val safeCount = count.coerceAtLeast(0)
        if (safeCount == 0) return 0
        val start = calculator.toStartOfDayMillis(targetDate)
        val end = start + ONE_DAY_MILLIS
        val now = System.currentTimeMillis()
        return database.withTransaction {
            val dueWords = progressDao.getWordsForDate(start, end, safeCount)
            dueWords.forEach { progress ->
                progressDao.updateNextReviewTime(progress.id, now)
            }
            forecastCacheDao.clearAll()
            dueWords.size
        }
    }

    private fun isCacheValid(
        caches: List<ForecastCache>,
        startDate: Long,
        dailyLimit: Int,
        todayRemaining: Int
    ): Boolean {
        if (caches.size != 7) return false
        return caches.withIndex().all { (index, cache) ->
            val expectedDate = startDate + ONE_DAY_MILLIS * index
            val expectedQuota = if (index == 0) todayRemaining else dailyLimit
            cache.isCalculated &&
                cache.date == expectedDate &&
                cache.newWordQuota == expectedQuota
        }
    }

    private suspend fun getGlobalPendingProgress(): List<Progress> {
        return progressDao.getAllPendingProgress()
            .groupBy { it.wordId }
            .mapNotNull { (_, grouped) ->
                grouped.maxWithOrNull(
                    compareBy<Progress> { it.reviewCount }
                        .thenBy { it.nextReviewTime }
                        .thenBy { it.id }
                )
            }
    }

    private fun currentLearningDate(now: Long): LocalDate {
        val zonedNow = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        val date = zonedNow.toLocalDate()
        return if (zonedNow.hour < DAY_REFRESH_HOUR) date.minusDays(1) else date
    }

    companion object {
        private const val DAY_REFRESH_HOUR = 4
        private const val ONE_DAY_MILLIS = 24 * 60 * 60 * 1000L
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
