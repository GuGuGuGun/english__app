package com.kaoyan.wordhelper.util

import com.kaoyan.wordhelper.data.entity.Progress
import com.kaoyan.wordhelper.data.model.SpellingOutcome
import com.kaoyan.wordhelper.data.model.StudyRating
import java.time.Instant
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt

object Sm2Scheduler {

    private const val DEFAULT_EASE = 2.5f
    private const val MIN_EASE = 1.3f
    private const val MIN_EASE_SPELLING = 1.1f
    private const val MAX_EASE = 3.0f
    private const val ONE_MINUTE = 60 * 1000L
    private const val TEN_MINUTES = 10 * 60 * 1000L
    private const val DAY_REFRESH_HOUR = 4
    private const val MASTERED_INTERVAL_DAYS = 21
    private const val MASTERED_MIN_REVIEW_COUNT = 2
    private const val MASTERED_MIN_EASE = 2.3f
    private const val MAX_GROWTH_FACTOR = 2.5f
    private const val HARD_GROWTH_FACTOR = 1.2f

    data class ScheduleResult(
        val repetitions: Int,
        val intervalDays: Int,
        val easeFactor: Float,
        val nextReviewTime: Long,
        val status: Int
    )

    fun schedule(current: Progress?, rating: StudyRating, now: Long = System.currentTimeMillis()): ScheduleResult {
        var ease = updateEase(current?.easeFactor ?: DEFAULT_EASE, rating.quality, MIN_EASE)
        val previousRepetitions = current?.repetitions ?: 0
        val previousIntervalDays = current?.intervalDays ?: 0
        val previousReviewCount = current?.reviewCount ?: 0
        val isLearningPhase = isLearningPhase(current)

        if (rating == StudyRating.AGAIN) {
            ease = (ease - 0.2f).coerceAtLeast(MIN_EASE)
            return ScheduleResult(
                repetitions = 0,
                intervalDays = 0,
                easeFactor = ease,
                nextReviewTime = now + ONE_MINUTE,
                status = Progress.STATUS_LEARNING
            )
        }

        if (isLearningPhase) {
            val intervalDays = when (rating) {
                StudyRating.HARD -> 0
                StudyRating.GOOD -> 1
                StudyRating.AGAIN -> 0
            }
            val effectiveReviewCount = previousReviewCount + 1
            return ScheduleResult(
                repetitions = previousRepetitions + 1,
                intervalDays = intervalDays,
                easeFactor = ease,
                nextReviewTime = nextReviewTimeByInterval(now, intervalDays),
                status = resolveStatus(intervalDays, effectiveReviewCount, ease)
            )
        }

        val oldInterval = previousIntervalDays.coerceAtLeast(1)
        val intervalDays = if (rating == StudyRating.HARD) {
            conservativeInterval(oldInterval, ease)
        } else {
            standardGoodInterval(oldInterval, ease)
        }

        val effectiveReviewCount = previousReviewCount + 1
        val repetitions = previousRepetitions + 1
        val status = resolveStatus(intervalDays, effectiveReviewCount, ease)
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
        val previousReviewCount = current?.reviewCount ?: 0
        val previousIntervalDays = current?.intervalDays ?: 0
        val previousRepetitions = current?.repetitions ?: 0
        val isLearningPhase = isLearningPhase(current)
        var ease = updateEase(current?.easeFactor ?: DEFAULT_EASE, outcome.quality, MIN_EASE_SPELLING)

        if (outcome == SpellingOutcome.FAILED) {
            ease = (ease - 0.2f).coerceAtLeast(MIN_EASE_SPELLING)
            return ScheduleResult(
                repetitions = 0,
                intervalDays = 0,
                easeFactor = ease,
                nextReviewTime = now + ONE_MINUTE,
                status = Progress.STATUS_LEARNING
            )
        }

        val intervalDays = when {
            isLearningPhase -> {
                when (outcome) {
                    SpellingOutcome.RETRY_SUCCESS -> 0
                    SpellingOutcome.HINTED,
                    SpellingOutcome.PERFECT -> 1
                    SpellingOutcome.FAILED -> 0
                }
            }

            outcome == SpellingOutcome.RETRY_SUCCESS -> {
                conservativeInterval(previousIntervalDays.coerceAtLeast(1), ease)
            }

            outcome == SpellingOutcome.HINTED -> {
                standardGoodInterval(previousIntervalDays.coerceAtLeast(1), ease)
            }

            else -> {
                val base = standardGoodInterval(previousIntervalDays.coerceAtLeast(1), ease)
                (base * 1.1f).roundToInt().coerceAtLeast(1)
            }
        }
        val effectiveReviewCount = previousReviewCount + 1
        val repetitions = previousRepetitions + 1
        val status = resolveStatus(intervalDays, effectiveReviewCount, ease)

        return ScheduleResult(
            repetitions = repetitions,
            intervalDays = intervalDays,
            easeFactor = ease,
            nextReviewTime = nextReviewTimeByInterval(now, intervalDays),
            status = status
        )
    }

    private fun isLearningPhase(current: Progress?): Boolean {
        if (current == null) return true
        if (current.status == Progress.STATUS_NEW) return true
        return current.intervalDays <= 0 || current.repetitions <= 0
    }

    private fun updateEase(currentEase: Float, quality: Int, minEase: Float): Float {
        val updated = currentEase - 0.8f + 0.28f * quality - 0.02f * quality * quality
        return updated.coerceIn(minEase, MAX_EASE)
    }

    private fun conservativeInterval(oldIntervalDays: Int, ease: Float): Int {
        val safeOldInterval = oldIntervalDays.coerceAtLeast(1)
        val byHard = (safeOldInterval * HARD_GROWTH_FACTOR).roundToInt()
        val byEase = (safeOldInterval * ease).roundToInt()
        return min(byHard, byEase).coerceAtLeast(1)
    }

    private fun standardGoodInterval(oldIntervalDays: Int, ease: Float): Int {
        val safeOldInterval = oldIntervalDays.coerceAtLeast(1)
        val grown = (safeOldInterval * ease).roundToInt().coerceAtLeast(1)
        val maxAllowed = (safeOldInterval * MAX_GROWTH_FACTOR).roundToInt().coerceAtLeast(1)
        return min(grown, maxAllowed)
    }

    private fun resolveStatus(intervalDays: Int, reviewCount: Int, easeFactor: Float): Int {
        return if (
            intervalDays >= MASTERED_INTERVAL_DAYS &&
            reviewCount >= MASTERED_MIN_REVIEW_COUNT &&
            easeFactor >= MASTERED_MIN_EASE
        ) {
            Progress.STATUS_MASTERED
        } else {
            Progress.STATUS_LEARNING
        }
    }

    private fun nextReviewTimeByInterval(now: Long, intervalDays: Int): Long {
        return if (intervalDays > 0) {
            computeNextReviewTimeByDays(now, intervalDays)
        } else {
            now + TEN_MINUTES
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
