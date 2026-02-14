package com.kaoyan.wordhelper.util

import com.kaoyan.wordhelper.data.entity.Progress
import com.kaoyan.wordhelper.data.model.SpellingOutcome
import com.kaoyan.wordhelper.data.model.StudyRating
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Sm2Scheduler {

    private const val MIN_EASE = 1.3f
    private const val MIN_EASE_SPELLING = 1.1f
    private const val MAX_EASE = 3.0f
    private const val TEN_MINUTES = 10 * 60 * 1000L
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    private const val DAY_REFRESH_HOUR = 4
    private const val MASTERED_INTERVAL_DAYS = 21
    private const val MAX_GROWTH_FACTOR = 2.5f
    private const val HARD_REDUCTION_FACTOR = 0.7f

    data class ScheduleResult(
        val repetitions: Int,
        val intervalDays: Int,
        val easeFactor: Float,
        val nextReviewTime: Long,
        val status: Int
    )

    fun schedule(current: Progress?, rating: StudyRating, now: Long = System.currentTimeMillis()): ScheduleResult {
        val quality = rating.quality
        var ease = current?.easeFactor ?: 2.5f
        val previousRepetitions = current?.repetitions ?: 0
        val previousIntervalDays = current?.intervalDays ?: 0
        val previousStatus = current?.status ?: Progress.STATUS_NEW
        val previousReviewCount = current?.reviewCount ?: 0

        ease = updateEase(ease, quality)

        // Forgot: reset interval to 1 day in DB, and rely on session immediate-retry queue
        // to reinforce once more in the current learning session.
        if (quality < 3) {
            return ScheduleResult(
                repetitions = 0,
                intervalDays = 1,
                easeFactor = ease,
                nextReviewTime = computeNextReviewTimeByDays(now, 1),
                status = Progress.STATUS_LEARNING
            )
        }

        // New word branch: keep early spacing dense and smooth.
        if (previousStatus == Progress.STATUS_NEW) {
            val intervalDays = if (quality >= 5) 1 else 0
            val nextReviewTime = if (intervalDays == 0) now + TEN_MINUTES else computeNextReviewTimeByDays(now, 1)
            return ScheduleResult(
                repetitions = if (quality >= 5) 1 else 0,
                intervalDays = intervalDays,
                easeFactor = ease,
                nextReviewTime = nextReviewTime,
                status = Progress.STATUS_LEARNING
            )
        }

        val intervalDays = if (quality == StudyRating.HARD.quality) {
            if (previousIntervalDays <= 1) {
                1
            } else {
                (previousIntervalDays * HARD_REDUCTION_FACTOR)
                    .roundToInt()
                    .coerceIn(1, previousIntervalDays)
            }
        } else {
            when (previousReviewCount) {
                0 -> 1
                1 -> 3
                else -> {
                    val grown = (previousIntervalDays * ease).roundToInt().coerceAtLeast(1)
                    val maxAllowed = (previousIntervalDays * MAX_GROWTH_FACTOR).roundToInt().coerceAtLeast(1)
                    min(grown, maxAllowed)
                }
            }
        }
        val repetitions = previousRepetitions + 1
        val status = if (intervalDays >= MASTERED_INTERVAL_DAYS && previousReviewCount >= 2) {
            Progress.STATUS_MASTERED
        } else {
            Progress.STATUS_LEARNING
        }
        val nextReviewTime = computeNextReviewTimeByDays(now, intervalDays)

        return ScheduleResult(
            repetitions = repetitions,
            intervalDays = intervalDays,
            easeFactor = ease,
            nextReviewTime = nextReviewTime,
            status = status
        )
    }

    fun scheduleSpelling(
        current: Progress?,
        outcome: SpellingOutcome,
        now: Long = System.currentTimeMillis()
    ): ScheduleResult {
        val previousStatus = current?.status ?: Progress.STATUS_NEW
        val previousReviewCount = current?.reviewCount ?: 0
        val previousIntervalDays = current?.intervalDays ?: 0
        val previousRepetitions = current?.repetitions ?: 0

        var ease = updateEase(current?.easeFactor ?: 2.5f, outcome.quality, MIN_EASE_SPELLING)
        val intervalDays = when (outcome) {
            SpellingOutcome.PERFECT -> {
                val standard = spellingStandardInterval(previousReviewCount, previousIntervalDays, ease)
                (standard * 1.1f).roundToInt().coerceAtLeast(1)
            }

            SpellingOutcome.HINTED -> {
                spellingStandardInterval(previousReviewCount, previousIntervalDays, ease)
            }

            SpellingOutcome.RETRY_SUCCESS -> 1

            SpellingOutcome.FAILED -> {
                ease = max(1.3f, ease - 0.2f)
                0
            }
        }

        val repetitions = when (outcome) {
            SpellingOutcome.FAILED -> 0
            SpellingOutcome.RETRY_SUCCESS -> max(1, previousRepetitions)
            else -> if (previousStatus == Progress.STATUS_NEW) 1 else previousRepetitions + 1
        }

        val status = if (
            outcome != SpellingOutcome.FAILED &&
            intervalDays >= MASTERED_INTERVAL_DAYS &&
            previousReviewCount >= 2
        ) {
            Progress.STATUS_MASTERED
        } else {
            Progress.STATUS_LEARNING
        }

        val nextReviewTime = if (intervalDays > 0) {
            computeNextReviewTimeByDays(now, intervalDays)
        } else {
            now + TEN_MINUTES
        }

        return ScheduleResult(
            repetitions = repetitions,
            intervalDays = intervalDays,
            easeFactor = ease,
            nextReviewTime = nextReviewTime,
            status = status
        )
    }

    private fun updateEase(currentEase: Float, quality: Int): Float {
        return updateEase(currentEase, quality, MIN_EASE)
    }

    private fun updateEase(currentEase: Float, quality: Int, minEase: Float): Float {
        val updated = currentEase - 0.8f + 0.28f * quality - 0.02f * quality * quality
        return updated.coerceIn(minEase, MAX_EASE)
    }

    private fun spellingStandardInterval(
        previousReviewCount: Int,
        previousIntervalDays: Int,
        ease: Float
    ): Int {
        return when {
            previousReviewCount <= 0 -> 1
            previousReviewCount == 1 -> 3
            else -> {
                val base = previousIntervalDays.coerceAtLeast(1)
                val grown = (base * ease).roundToInt().coerceAtLeast(1)
                val maxAllowed = (base * MAX_GROWTH_FACTOR).roundToInt().coerceAtLeast(1)
                min(grown, maxAllowed)
            }
        }
    }

    fun estimateLastReviewTime(progress: Progress?): Long? {
        if (progress == null || progress.reviewCount <= 0 || progress.nextReviewTime <= 0L) {
            return null
        }
        return if (progress.intervalDays > 0) {
            Instant.ofEpochMilli(progress.nextReviewTime)
                .atZone(ZoneId.systemDefault())
                .minusDays(progress.intervalDays.toLong())
                .toInstant()
                .toEpochMilli()
                .coerceAtLeast(0L)
        } else {
            (progress.nextReviewTime - TEN_MINUTES).coerceAtLeast(0L)
        }
    }

    fun nextReviewTimeByDays(intervalDays: Int, now: Long = System.currentTimeMillis()): Long {
        return computeNextReviewTimeByDays(now, intervalDays)
    }

    private fun computeNextReviewTimeByDays(now: Long, intervalDays: Int): Long {
        val safeDays = intervalDays.coerceAtLeast(1).toLong()
        val zoneId = ZoneId.systemDefault()
        val zonedNow = Instant.ofEpochMilli(now).atZone(zoneId)
        val learningDate = if (zonedNow.hour < DAY_REFRESH_HOUR) {
            zonedNow.toLocalDate().minusDays(1)
        } else {
            zonedNow.toLocalDate()
        }
        return learningDate
            .plusDays(safeDays)
            .atTime(DAY_REFRESH_HOUR, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
