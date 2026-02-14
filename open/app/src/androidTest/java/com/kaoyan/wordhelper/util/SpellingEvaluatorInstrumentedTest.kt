package com.kaoyan.wordhelper.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaoyan.wordhelper.data.model.SpellingOutcome
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpellingEvaluatorInstrumentedTest {

    @Test
    fun perfectWithoutHintMapsToPerfect() {
        val outcome = SpellingEvaluator.evaluate(
            input = "abandon",
            correctWord = "abandon",
            hintUsed = false,
            attemptCount = 1
        )
        assertEquals(SpellingOutcome.PERFECT, outcome)
    }

    @Test
    fun hintedCorrectMapsToHinted() {
        val outcome = SpellingEvaluator.evaluate(
            input = "abandon",
            correctWord = "abandon",
            hintUsed = true,
            attemptCount = 1
        )
        assertEquals(SpellingOutcome.HINTED, outcome)
    }

    @Test
    fun retrySuccessMapsToRetrySuccess() {
        val outcome = SpellingEvaluator.evaluate(
            input = "abandon",
            correctWord = "abandon",
            hintUsed = false,
            attemptCount = 2
        )
        assertEquals(SpellingOutcome.RETRY_SUCCESS, outcome)
    }

    @Test
    fun wrongInputMapsToFailed() {
        val outcome = SpellingEvaluator.evaluate(
            input = "abandonn",
            correctWord = "abandon",
            hintUsed = false,
            attemptCount = 3
        )
        assertEquals(SpellingOutcome.FAILED, outcome)
    }
}
