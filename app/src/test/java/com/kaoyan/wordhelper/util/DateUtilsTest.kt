package com.kaoyan.wordhelper.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DateUtilsTest {

    @Test
    fun learningDateRangeUsesRefreshBoundaryBeforeFourAm() {
        val now = java.time.Instant.parse("2024-01-02T02:00:00Z").toEpochMilli()
        val range = DateUtils.learningDateRange(days = 7, now = now, zoneId = ZoneId.of("UTC"))

        assertEquals(LocalDate.of(2023, 12, 26), range.first)
        assertEquals(LocalDate.of(2024, 1, 1), range.second)
    }

    @Test
    fun learningHeatmapRangeUsesLearningDateAsEnd() {
        val now = java.time.Instant.parse("2024-01-02T02:00:00Z").toEpochMilli()
        val range = DateUtils.learningHeatmapRange(now = now, zoneId = ZoneId.of("UTC"))

        assertEquals(LocalDate.of(2024, 1, 1), range.second)
        assertEquals(LocalDate.of(2023, 10, 16), range.first)
    }
}
