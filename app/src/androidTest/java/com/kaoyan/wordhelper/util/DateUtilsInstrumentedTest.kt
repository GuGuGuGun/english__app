package com.kaoyan.wordhelper.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DateUtilsInstrumentedTest {

    @Test
    fun formatDateTimeUsesZone() {
        val timestamp = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli()
        val utc = DateUtils.formatDateTime(timestamp, ZoneId.of("UTC"))
        val shanghai = DateUtils.formatDateTime(timestamp, ZoneId.of("Asia/Shanghai"))
        assertEquals("2024-01-01 00:00", utc)
        assertEquals("2024-01-01 08:00", shanghai)
    }

    @Test
    fun reviewTagUsesLocalDate() {
        val now = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli()
        val tomorrow = Instant.parse("2024-01-02T00:00:00Z").toEpochMilli()
        val tag = DateUtils.reviewTag(tomorrow, now, ZoneId.of("UTC"))
        assertEquals("明天", tag)
    }

    @Test
    fun reviewTagUsesFourAmRefreshBoundary() {
        val now = Instant.parse("2024-01-01T01:00:00Z").toEpochMilli()
        val refreshAtFour = Instant.parse("2024-01-01T04:00:00Z").toEpochMilli()
        val tag = DateUtils.reviewTag(refreshAtFour, now, ZoneId.of("UTC"))
        assertEquals("明天", tag)
    }

    @Test
    fun formatReviewDayShowsDayBasedLabel() {
        val now = Instant.parse("2024-01-01T12:00:00Z").toEpochMilli()
        val twoDaysLater = Instant.parse("2024-01-03T04:00:00Z").toEpochMilli()
        val text = DateUtils.formatReviewDay(twoDaysLater, now, ZoneId.of("UTC"))
        assertEquals("2天后", text)
    }

    @Test
    fun isDueUsesFourAmRefreshBoundary() {
        val now = Instant.parse("2024-01-01T03:00:00Z").toEpochMilli()
        val reviewAtFour = Instant.parse("2024-01-01T04:00:00Z").toEpochMilli()
        val due = DateUtils.isDue(reviewAtFour, now, ZoneId.of("UTC"))
        assertEquals(false, due)
    }
}
