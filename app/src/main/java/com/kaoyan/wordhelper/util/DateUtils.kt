package com.kaoyan.wordhelper.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object DateUtils {

    private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private const val DAY_REFRESH_HOUR = 4

    fun formatDateTime(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        if (timestamp <= 0L) return "暂无"
        return Instant.ofEpochMilli(timestamp).atZone(zoneId).format(DATE_TIME_FORMATTER)
    }

    fun formatReviewDay(
        nextReviewTime: Long,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (nextReviewTime <= 0L) return "无"
        val diffDays = daysBetweenByRefreshDay(now, nextReviewTime, zoneId)
        return when {
            diffDays < 0 -> "已到期"
            diffDays == 0L -> "今日"
            diffDays == 1L -> "明天"
            else -> "${diffDays}天后"
        }
    }

    fun reviewTag(
        nextReviewTime: Long,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (nextReviewTime <= 0L) return "未安排"
        val diffDays = daysBetweenByRefreshDay(now, nextReviewTime, zoneId)
        return when {
            diffDays < 0 -> "已到期"
            diffDays == 0L -> "今日"
            diffDays == 1L -> "明天"
            else -> "${diffDays}天后"
        }
    }

    fun reviewDescription(
        nextReviewTime: Long,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (nextReviewTime <= 0L) return "暂无安排"
        val diffDays = daysBetweenByRefreshDay(now, nextReviewTime, zoneId)
        return when {
            diffDays < 0 -> "已到期，建议现在复习"
            diffDays == 0L -> "今日复习"
            diffDays == 1L -> "明天复习"
            else -> "${diffDays}天后复习"
        }
    }

    fun isDue(
        nextReviewTime: Long,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (nextReviewTime <= 0L) return false
        return daysBetweenByRefreshDay(now, nextReviewTime, zoneId) <= 0L
    }

    private fun daysBetweenByRefreshDay(now: Long, target: Long, zoneId: ZoneId): Long {
        val nowDate = toLearningDate(now, zoneId)
        val targetDate = toLearningDate(target, zoneId)
        return ChronoUnit.DAYS.between(nowDate, targetDate)
    }

    private fun toLearningDate(timestamp: Long, zoneId: ZoneId): LocalDate {
        val zoned = Instant.ofEpochMilli(timestamp).atZone(zoneId)
        val date = zoned.toLocalDate()
        return if (zoned.hour < DAY_REFRESH_HOUR) date.minusDays(1) else date
    }
}
