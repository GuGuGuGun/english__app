package com.kaoyan.wordhelper.util

import com.kaoyan.wordhelper.data.entity.Progress
import com.kaoyan.wordhelper.data.model.SpellingOutcome
import com.kaoyan.wordhelper.data.model.StudyRating
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
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
    private const val MASTERED_INTERVAL_DAYS_V3 = 21
    private const val MASTERED_MIN_REVIEW_COUNT_V3 = 2
    private const val MASTERED_INTERVAL_DAYS_V4 = 30
    private const val MASTERED_MIN_REVIEW_COUNT_V4 = 4
    private const val MASTERED_MIN_EASE = 2.3f
    private const val MAX_GROWTH_FACTOR = 2.5f
    private const val HARD_GROWTH_FACTOR = 1.2f
    private const val SHORT_TERM_REVIEW_DECAY_HOURS = 12L
    private const val V3_MIN_INTERVAL_DAYS = 1
    private const val V3_GOOD_INTERVAL_DAYS = 2
    private val EBBINGHAUS_LADDER = intArrayOf(1, 2, 6, 14, 30)

    data class ScheduleResult(
        val repetitions: Int,
        val intervalDays: Int,
        val easeFactor: Float,
        val nextReviewTime: Long,
        val status: Int
    )

    fun schedule(
        current: Progress?,
        rating: StudyRating,
        now: Long = System.currentTimeMillis(),
        algorithmV4Enabled: Boolean = false
    ): ScheduleResult {
        if (algorithmV4Enabled) {
            return scheduleRecognitionV4(current = current, rating = rating, now = now)
        }

        var ease = updateEase(current?.easeFactor ?: DEFAULT_EASE, rating.quality, MIN_EASE)
        val previousRepetitions = current?.repetitions ?: 0
        val previousIntervalDays = current?.intervalDays ?: 0
        val previousReviewCount = current?.reviewCount ?: 0
        val isLearningPhase = isLearningPhase(current)

        if (rating == StudyRating.AGAIN) {
            ease = (ease - 0.2f).coerceAtLeast(MIN_EASE)
            return ScheduleResult(
                repetitions = 0,
                intervalDays = V3_MIN_INTERVAL_DAYS,
                easeFactor = ease,
                nextReviewTime = computeNextReviewTimeByDays(now, V3_MIN_INTERVAL_DAYS),
                status = Progress.STATUS_LEARNING
            )
        }

        if (isLearningPhase) {
            val intervalDays = when (rating) {
                StudyRating.HARD -> V3_MIN_INTERVAL_DAYS
                StudyRating.GOOD -> V3_GOOD_INTERVAL_DAYS
                StudyRating.AGAIN -> V3_MIN_INTERVAL_DAYS
            }
            val effectiveReviewCount = previousReviewCount + 1
            return ScheduleResult(
                repetitions = previousRepetitions + 1,
                intervalDays = intervalDays,
                easeFactor = ease,
                nextReviewTime = nextReviewTimeByInterval(now, intervalDays),
                status = resolveStatus(intervalDays, effectiveReviewCount, ease, algorithmV4Enabled = false)
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
        val status = resolveStatus(intervalDays, effectiveReviewCount, ease, algorithmV4Enabled = false)
        val nextReviewTime = computeNextReviewTimeByDays(now, intervalDays)

        return ScheduleResult(
            repetitions = repetitions,
            intervalDays = intervalDays,
            easeFactor = ease,
            nextReviewTime = nextReviewTime,
            status = status
        )
    }

    private fun scheduleRecognitionV4(
        current: Progress?,
        rating: StudyRating,
        now: Long
    ): ScheduleResult {
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

        var intervalDays = if (isLearningPhase) {
            when (rating) {
                StudyRating.HARD -> 0
                StudyRating.GOOD -> nextEbbinghausStep(previousIntervalDays)
                StudyRating.AGAIN -> 0
            }
        } else {
            val oldInterval = previousIntervalDays.coerceAtLeast(1)
            val rawInterval = if (rating == StudyRating.HARD) {
                conservativeInterval(oldInterval, ease)
            } else {
                standardGoodInterval(oldInterval, ease)
            }
            closestEbbinghausStep(rawInterval)
        }

        if (intervalDays > 1 && reviewedWithinHours(current, now, SHORT_TERM_REVIEW_DECAY_HOURS)) {
            intervalDays = 1
        }

        val effectiveReviewCount = previousReviewCount + 1
        val repetitions = previousRepetitions + 1
        val status = resolveStatus(intervalDays, effectiveReviewCount, ease, algorithmV4Enabled = true)

        return ScheduleResult(
            repetitions = repetitions,
            intervalDays = intervalDays,
            easeFactor = ease,
            nextReviewTime = nextReviewTimeByInterval(now, intervalDays),
            status = status
        )
    }

    fun scheduleSpelling(
        current: Progress?,
        outcome: SpellingOutcome,
        now: Long = System.currentTimeMillis(),
        algorithmV4Enabled: Boolean = false
    ): ScheduleResult {
        if (algorithmV4Enabled) {
            return scheduleSpellingV4(current = current, outcome = outcome, now = now)
        }

        val previousReviewCount = current?.reviewCount ?: 0
        val previousIntervalDays = current?.intervalDays ?: 0
        val previousRepetitions = current?.repetitions ?: 0
        val isLearningPhase = isLearningPhase(current)
        var ease = updateEase(current?.easeFactor ?: DEFAULT_EASE, outcome.quality, MIN_EASE_SPELLING)

        if (outcome == SpellingOutcome.FAILED) {
            ease = (ease - 0.2f).coerceAtLeast(MIN_EASE_SPELLING)
            return ScheduleResult(
                repetitions = 0,
                intervalDays = V3_MIN_INTERVAL_DAYS,
                easeFactor = ease,
                nextReviewTime = computeNextReviewTimeByDays(now, V3_MIN_INTERVAL_DAYS),
                status = Progress.STATUS_LEARNING
            )
        }

        val intervalDays = when {
            isLearningPhase -> {
                when (outcome) {
                    SpellingOutcome.RETRY_SUCCESS -> V3_MIN_INTERVAL_DAYS
                    SpellingOutcome.HINTED,
                    SpellingOutcome.PERFECT -> V3_GOOD_INTERVAL_DAYS
                    SpellingOutcome.FAILED -> V3_MIN_INTERVAL_DAYS
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
        val status = resolveStatus(intervalDays, effectiveReviewCount, ease, algorithmV4Enabled = false)

        return ScheduleResult(
            repetitions = repetitions,
            intervalDays = intervalDays,
            easeFactor = ease,
            nextReviewTime = nextReviewTimeByInterval(now, intervalDays),
            status = status
        )
    }

    private fun scheduleSpellingV4(
        current: Progress?,
        outcome: SpellingOutcome,
        now: Long
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

        var intervalDays = if (isLearningPhase) {
            when (outcome) {
                SpellingOutcome.RETRY_SUCCESS -> 0
                SpellingOutcome.HINTED,
                SpellingOutcome.PERFECT -> nextEbbinghausStep(previousIntervalDays)
                SpellingOutcome.FAILED -> 0
            }
        } else {
            val oldInterval = previousIntervalDays.coerceAtLeast(1)
            val rawInterval = when (outcome) {
                SpellingOutcome.RETRY_SUCCESS -> {
                    conservativeInterval(oldInterval, ease)
                }

                SpellingOutcome.HINTED -> {
                    standardGoodInterval(oldInterval, ease)
                }

                SpellingOutcome.PERFECT -> {
                    val base = standardGoodInterval(oldInterval, ease)
                    (base * 1.1f).roundToInt().coerceAtLeast(1)
                }

                SpellingOutcome.FAILED -> 1
            }
            closestEbbinghausStep(rawInterval)
        }

        if (intervalDays > 1 && reviewedWithinHours(current, now, SHORT_TERM_REVIEW_DECAY_HOURS)) {
            intervalDays = 1
        }

        val effectiveReviewCount = previousReviewCount + 1
        val repetitions = previousRepetitions + 1
        val status = resolveStatus(intervalDays, effectiveReviewCount, ease, algorithmV4Enabled = true)

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

    private fun reviewedWithinHours(current: Progress?, now: Long, hours: Long): Boolean {
        val estimatedLastReview = estimateLastReviewTime(current) ?: return false
        val elapsed = now - estimatedLastReview
        if (elapsed < 0L) return false
        val threshold = hours * 60 * 60 * 1000
        return elapsed <= threshold
    }

    private fun nextEbbinghausStep(currentIntervalDays: Int): Int {
        if (currentIntervalDays <= 0) return EBBINGHAUS_LADDER.first()
        return EBBINGHAUS_LADDER.firstOrNull { it > currentIntervalDays } ?: EBBINGHAUS_LADDER.last()
    }

    private fun closestEbbinghausStep(intervalDays: Int): Int {
        val safeInterval = intervalDays.coerceAtLeast(1)
        var nearest = EBBINGHAUS_LADDER.first()
        EBBINGHAUS_LADDER.forEach { step ->
            val stepDiff = abs(step - safeInterval)
            val nearestDiff = abs(nearest - safeInterval)
            if (stepDiff < nearestDiff || (stepDiff == nearestDiff && step > nearest)) {
                nearest = step
            }
        }
        return nearest
    }

    private fun resolveStatus(
        intervalDays: Int,
        reviewCount: Int,
        easeFactor: Float,
        algorithmV4Enabled: Boolean
    ): Int {
        val masteredIntervalDays = if (algorithmV4Enabled) {
            MASTERED_INTERVAL_DAYS_V4
        } else {
            MASTERED_INTERVAL_DAYS_V3
        }
        val masteredMinReviewCount = if (algorithmV4Enabled) {
            MASTERED_MIN_REVIEW_COUNT_V4
        } else {
            MASTERED_MIN_REVIEW_COUNT_V3
        }
        return if (
            intervalDays >= masteredIntervalDays &&
            reviewCount >= masteredMinReviewCount &&
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
