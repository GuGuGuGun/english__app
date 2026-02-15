package com.kaoyan.wordhelper.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ForecastCalculatorTest {

    private val zoneId = ZoneId.of("UTC")
    private val calculator = ForecastCalculator(zoneId)

    @Test
    fun calculateNext7DaysPressure_groupsReviewCountsAndAppliesPressureLevels() {
        val now = LocalDateTime.of(2026, 2, 15, 10, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val day0 = calculator.toStartOfDayMillis(now)
        val day1 = day0 + DAY
        val day3 = day0 + DAY * 3
        val reviewTimes = buildList {
            add(day0 + 60_000)
            add(day0 + 120_000)
            add(day1 + 180_000)
            repeat(95) { add(day3 + 5_000L + it) }
        }

        val result = calculator.calculateNext7DaysPressure(
            reviewTimes = reviewTimes,
            now = now,
            dailyNewWordLimit = 20,
            todayRemainingNewWords = 7
        )

        assertEquals(7, result.size)
        assertEquals(2, result[0].reviewCount)
        assertEquals(7, result[0].newWordQuota)
        assertEquals(PressureLevel.LOW, result[0].pressureLevel)
        assertEquals(1, result[1].reviewCount)
        assertEquals(20, result[1].newWordQuota)
        assertEquals(PressureLevel.LOW, result[1].pressureLevel)
        assertEquals(95, result[3].reviewCount)
        assertEquals(20, result[3].newWordQuota)
        assertEquals(PressureLevel.HIGH, result[3].pressureLevel)
    }

    @Test
    fun calculateNext7DaysPressure_respectsDayBoundaryAndQuotaClamping() {
        val now = LocalDateTime.of(2026, 2, 15, 23, 30)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val day0 = calculator.toStartOfDayMillis(now)
        val day1 = day0 + DAY
        val reviewTimes = listOf(
            -1L,
            0L,
            day0 + DAY - 1,
            day1
        )

        val result = calculator.calculateNext7DaysPressure(
            reviewTimes = reviewTimes,
            now = now,
            dailyNewWordLimit = 30,
            todayRemainingNewWords = 40
        )

        assertEquals(1, result[0].reviewCount)
        assertEquals(1, result[1].reviewCount)
        assertEquals(30, result[0].newWordQuota)
        assertEquals(30, result[1].newWordQuota)
    }

    companion object {
        private const val DAY = 24 * 60 * 60 * 1000L
    }
}
