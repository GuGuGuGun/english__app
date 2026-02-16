package com.kaoyan.wordhelper.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaoyan.wordhelper.data.model.SpellingOutcome
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpellingEvaluatorInstrumentedTest {

    @Test
    fun v4_perfectWithoutHint_mapsToPerfect() {
        val outcome = SpellingEvaluator.evaluate(
            input = "abandon",
            correctWord = "abandon",
            hintUsed = false,
            attemptCount = 1,
            algorithmV4Enabled = true
        )
        assertEquals(SpellingOutcome.PERFECT, outcome)
    }

    @Test
    fun v4_oneTypo_mapsToHinted() {
        val outcome = SpellingEvaluator.evaluate(
            input = "abndon",
            correctWord = "abandon",
            hintUsed = false,
            attemptCount = 1,
            algorithmV4Enabled = true
        )
        assertEquals(SpellingOutcome.HINTED, outcome)
    }

    @Test
    fun v4_twoTypos_mapsToRetrySuccess() {
        val outcome = SpellingEvaluator.evaluate(
            input = "abndn",
            correctWord = "abandon",
            hintUsed = false,
            attemptCount = 1,
            algorithmV4Enabled = true
        )
        assertEquals(SpellingOutcome.RETRY_SUCCESS, outcome)
    }

    @Test
    fun v4_largeDistance_mapsToFailed() {
        val outcome = SpellingEvaluator.evaluate(
            input = "abc",
            correctWord = "abandon",
            hintUsed = false,
            attemptCount = 1,
            algorithmV4Enabled = true
        )
        assertEquals(SpellingOutcome.FAILED, outcome)
    }

    @Test
    fun v4_hintAndRetry_penalizesToRetrySuccess() {
        val outcome = SpellingEvaluator.evaluate(
            input = "abandon",
            correctWord = "abandon",
            hintUsed = true,
            attemptCount = 2,
            algorithmV4Enabled = true
        )
        assertEquals(SpellingOutcome.RETRY_SUCCESS, outcome)
    }
}
