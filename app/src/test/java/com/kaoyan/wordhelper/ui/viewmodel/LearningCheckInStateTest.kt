package com.kaoyan.wordhelper.ui.viewmodel

import com.kaoyan.wordhelper.data.entity.Word
import com.kaoyan.wordhelper.data.model.DailyStatsAggregate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LearningCheckInStateTest {

    @Test
    fun showsCompletionPanelOnlyWhenSessionHasFinished() {
        assertTrue(shouldShowCompletionCheckInPanel(currentWord = null, totalCount = 8))
        assertFalse(shouldShowCompletionCheckInPanel(currentWord = Word(id = 1, word = "test", bookId = 1), totalCount = 8))
        assertFalse(shouldShowCompletionCheckInPanel(currentWord = null, totalCount = 0))
    }

    @Test
    fun checkInStateUsesExplicitCheckInInsteadOfStudyOnly() {
        val today = LocalDate.of(2024, 1, 2)
        val weekStats = listOf(
            aggregate(date = "2024-01-02", newWordsCount = 20, checkInCount = 0),
            aggregate(date = "2024-01-01", newWordsCount = 15, checkInCount = 1)
        )

        val state = buildLearningCheckInUiState(
            weekAggregates = weekStats,
            heatmapAggregates = weekStats,
            currentWord = null,
            totalCount = 20,
            today = today
        )

        assertTrue(state.showCompletionPanel)
        assertFalse(state.checkedInToday)
        assertEquals(1, state.weekCheckInDays)
        assertEquals(0, state.streakDays)
        assertEquals(20, state.todayStudyCount)
    }

    @Test
    fun checkInStateBuildsStreakFromManualCheckIns() {
        val today = LocalDate.of(2024, 1, 3)
        val weekStats = listOf(
            aggregate(date = "2024-01-03", newWordsCount = 10, checkInCount = 1, lastCheckInTime = 1_704_240_000_000),
            aggregate(date = "2024-01-02", newWordsCount = 12, checkInCount = 1),
            aggregate(date = "2024-01-01", newWordsCount = 9, checkInCount = 1)
        )

        val state = buildLearningCheckInUiState(
            weekAggregates = weekStats,
            heatmapAggregates = weekStats,
            currentWord = null,
            totalCount = 10,
            today = today
        )

        assertTrue(state.checkedInToday)
        assertEquals(3, state.streakDays)
        assertEquals(3, state.weekCheckInDays)
        assertTrue(state.todayCheckInTimeText.isNotBlank())
    }

    private fun aggregate(
        date: String,
        newWordsCount: Int = 0,
        reviewWordsCount: Int = 0,
        spellPracticeCount: Int = 0,
        checkInCount: Int = 0,
        lastCheckInTime: Long = 0L
    ) = DailyStatsAggregate(
        date = date,
        newWordsCount = newWordsCount,
        reviewWordsCount = reviewWordsCount,
        spellPracticeCount = spellPracticeCount,
        durationMillis = 0L,
        gestureEasyCount = 0,
        gestureNotebookCount = 0,
        fuzzyWordsCount = 0,
        recognizedWordsCount = 0,
        checkInCount = checkInCount,
        lastCheckInTime = lastCheckInTime
    )
}
