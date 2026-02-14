package com.kaoyan.wordhelper.util

import com.kaoyan.wordhelper.data.model.SpellingOutcome

object SpellingEvaluator {

    fun evaluate(
        input: String,
        correctWord: String,
        hintUsed: Boolean,
        attemptCount: Int
    ): SpellingOutcome {
        val normalizedInput = input.trim()
        val isCorrect = normalizedInput.equals(correctWord.trim(), ignoreCase = true)
        if (!isCorrect) {
            return SpellingOutcome.FAILED
        }
        return when {
            hintUsed -> SpellingOutcome.HINTED
            attemptCount > 1 -> SpellingOutcome.RETRY_SUCCESS
            else -> SpellingOutcome.PERFECT
        }
    }
}
