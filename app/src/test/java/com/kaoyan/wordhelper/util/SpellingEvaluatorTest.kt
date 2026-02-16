package com.kaoyan.wordhelper.util

import com.kaoyan.wordhelper.data.model.SpellingOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class SpellingEvaluatorTest {

    @Test
    fun legacyMode_keepsExactMatchRule() {
        val failed = SpellingEvaluator.evaluate(
            input = "abndon",
            correctWord = "abandon",
            hintUsed = false,
            attemptCount = 1,
            algorithmV4Enabled = false
        )
        val perfect = SpellingEvaluator.evaluate(
            input = "abandon",
            correctWord = "abandon",
            hintUsed = false,
            attemptCount = 1,
            algorithmV4Enabled = false
        )

        assertEquals(SpellingOutcome.FAILED, failed)
        assertEquals(SpellingOutcome.PERFECT, perfect)
    }

    @Test
    fun v4Mode_usesEditDistanceAsPrimarySignal() {
        val oneTypo = SpellingEvaluator.evaluate(
            input = "abndon",
            correctWord = "abandon",
            hintUsed = false,
            attemptCount = 1,
            algorithmV4Enabled = true
        )
        val twoTypos = SpellingEvaluator.evaluate(
            input = "abndn",
            correctWord = "abandon",
            hintUsed = false,
            attemptCount = 1,
            algorithmV4Enabled = true
        )
        val farAway = SpellingEvaluator.evaluate(
            input = "abc",
            correctWord = "abandon",
            hintUsed = false,
            attemptCount = 1,
            algorithmV4Enabled = true
        )

        assertEquals(SpellingOutcome.HINTED, oneTypo)
        assertEquals(SpellingOutcome.RETRY_SUCCESS, twoTypos)
        assertEquals(SpellingOutcome.FAILED, farAway)
    }
}
