package com.kaoyan.wordhelper.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class PressureLevel {
    LOW,
    MEDIUM,
    HIGH
}

data class DayForecast(
    val date: Long,
    val reviewCount: Int,
    val newWordQuota: Int
) {
    val totalCount: Int
        get() = reviewCount + newWordQuota

    val pressureLevel: PressureLevel
        get() = when {
            totalCount < MEDIUM_PRESSURE_THRESHOLD -> PressureLevel.LOW
            totalCount <= HIGH_PRESSURE_THRESHOLD -> PressureLevel.MEDIUM
            else -> PressureLevel.HIGH
        }

    companion object {
        const val MEDIUM_PRESSURE_THRESHOLD = 50
        const val HIGH_PRESSURE_THRESHOLD = 100
    }
}

class ForecastCalculator(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun calculateNext7DaysPressure(
        reviewTimes: List<Long>,
        now: Long = System.currentTimeMillis(),
        dailyNewWordLimit: Int,
        todayRemainingNewWords: Int
    ): List<DayForecast> {
        val safeDailyLimit = dailyNewWordLimit.coerceAtLeast(0)
        val safeTodayRemaining = todayRemainingNewWords.coerceIn(0, safeDailyLimit)
        val startDate = toLocalDate(now)
        val reviewCountByDate = reviewTimes
            .asSequence()
            .filter { it > 0L }
            .map(::toLocalDate)
            .groupingBy { it }
            .eachCount()

        return (0L..6L).map { dayOffset ->
            val date = startDate.plusDays(dayOffset)
            val reviewCount = reviewCountByDate[date] ?: 0
            DayForecast(
                date = toStartOfDayMillis(date),
                reviewCount = reviewCount,
                newWordQuota = if (dayOffset == 0L) safeTodayRemaining else safeDailyLimit
            )
        }
    }

    fun toStartOfDayMillis(timestamp: Long): Long {
        return toStartOfDayMillis(toLocalDate(timestamp))
    }

    private fun toStartOfDayMillis(date: LocalDate): Long {
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private fun toLocalDate(timestamp: Long): LocalDate {
        return Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
    }
}
