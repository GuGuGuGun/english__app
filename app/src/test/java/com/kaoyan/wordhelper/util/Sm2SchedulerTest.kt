package com.kaoyan.wordhelper.util

import com.kaoyan.wordhelper.data.entity.Progress
import com.kaoyan.wordhelper.data.model.StudyRating
import org.junit.Assert.assertEquals
import org.junit.Test

class Sm2SchedulerTest {

    @Test
    fun scheduleV4_learningGood_movesToFirstEbbinghausStep() {
        val now = 1_700_000_000_000L
        val current = Progress(
            wordId = 1L,
            bookId = 1L,
            status = Progress.STATUS_NEW,
            repetitions = 0,
            intervalDays = 0,
            nextReviewTime = 0L,
            easeFactor = 2.5f,
            reviewCount = 0
        )

        val result = Sm2Scheduler.schedule(
            current = current,
            rating = StudyRating.GOOD,
            now = now,
            algorithmV4Enabled = true
        )

        assertEquals(1, result.intervalDays)
    }

    @Test
    fun scheduleV4_reviewGood_calibratesToNearestEbbinghausStep() {
        val now = 1_700_000_000_000L
        val current = Progress(
            wordId = 1L,
            bookId = 1L,
            status = Progress.STATUS_LEARNING,
            repetitions = 5,
            intervalDays = 5,
            nextReviewTime = now + daysToMillis(7),
            easeFactor = 2.5f,
            reviewCount = 5
        )

        val result = Sm2Scheduler.schedule(
            current = current,
            rating = StudyRating.GOOD,
            now = now,
            algorithmV4Enabled = true
        )

        assertEquals(14, result.intervalDays)
    }

    @Test
    fun scheduleV4_shortTermReviewDecay_forcesOneDayInterval() {
        val now = 1_700_000_000_000L
        val current = Progress(
            wordId = 1L,
            bookId = 1L,
            status = Progress.STATUS_LEARNING,
            repetitions = 6,
            intervalDays = 14,
            nextReviewTime = now + daysToMillis(14) - hoursToMillis(6),
            easeFactor = 2.5f,
            reviewCount = 6
        )

        val result = Sm2Scheduler.schedule(
            current = current,
            rating = StudyRating.GOOD,
            now = now,
            algorithmV4Enabled = true
        )

        assertEquals(1, result.intervalDays)
    }

    @Test
    fun masteredThresholdV4_requiresAtLeastFourReviews() {
        val now = 1_700_000_000_000L
        val notEnoughReviews = Progress(
            wordId = 1L,
            bookId = 1L,
            status = Progress.STATUS_LEARNING,
            repetitions = 2,
            intervalDays = 30,
            nextReviewTime = now + daysToMillis(60),
            easeFactor = 2.5f,
            reviewCount = 2
        )
        val enoughReviews = notEnoughReviews.copy(reviewCount = 3, repetitions = 3)

        val resultNotMastered = Sm2Scheduler.schedule(
            current = notEnoughReviews,
            rating = StudyRating.GOOD,
            now = now,
            algorithmV4Enabled = true
        )
        val resultMastered = Sm2Scheduler.schedule(
            current = enoughReviews,
            rating = StudyRating.GOOD,
            now = now,
            algorithmV4Enabled = true
        )

        assertEquals(Progress.STATUS_LEARNING, resultNotMastered.status)
        assertEquals(Progress.STATUS_MASTERED, resultMastered.status)
    }

    private fun daysToMillis(days: Int): Long {
        return days * 24L * 60L * 60L * 1000L
    }

    private fun hoursToMillis(hours: Int): Long {
        return hours * 60L * 60L * 1000L
    }
}
