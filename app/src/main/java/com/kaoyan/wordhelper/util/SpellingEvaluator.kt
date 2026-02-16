package com.kaoyan.wordhelper.util

import com.kaoyan.wordhelper.data.model.SpellingOutcome

object SpellingEvaluator {

    fun evaluate(
        input: String,
        correctWord: String,
        hintUsed: Boolean,
        attemptCount: Int,
        algorithmV4Enabled: Boolean = false
    ): SpellingOutcome {
        if (!algorithmV4Enabled) {
            return evaluateLegacy(input, correctWord, hintUsed, attemptCount)
        }
        return evaluateV4(input, correctWord, hintUsed, attemptCount)
    }

    private fun evaluateLegacy(
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

    private fun evaluateV4(
        input: String,
        correctWord: String,
        hintUsed: Boolean,
        attemptCount: Int
    ): SpellingOutcome {
        val normalizedInput = input.trim().lowercase()
        val normalizedCorrect = correctWord.trim().lowercase()
        if (normalizedInput.isBlank() || normalizedCorrect.isBlank()) {
            return SpellingOutcome.FAILED
        }

        val editDistance = boundedLevenshteinDistance(
            source = normalizedInput,
            target = normalizedCorrect,
            maxDistance = MAX_EDIT_DISTANCE_FOR_PASS
        )
        if (editDistance > MAX_EDIT_DISTANCE_FOR_PASS) {
            return SpellingOutcome.FAILED
        }

        val baseQuality = when (editDistance) {
            0 -> 5
            1 -> 4
            2 -> 3
            else -> 0
        }

        var finalQuality = baseQuality
        if (hintUsed && finalQuality > 3) {
            finalQuality -= 1
        }
        if (attemptCount > 1 && finalQuality > 3) {
            finalQuality -= 1
        }

        return when (finalQuality) {
            5 -> SpellingOutcome.PERFECT
            4 -> SpellingOutcome.HINTED
            3 -> SpellingOutcome.RETRY_SUCCESS
            else -> SpellingOutcome.FAILED
        }
    }

    private fun boundedLevenshteinDistance(
        source: String,
        target: String,
        maxDistance: Int
    ): Int {
        val sourceLen = source.length
        val targetLen = target.length
        if (kotlin.math.abs(sourceLen - targetLen) > maxDistance) {
            return maxDistance + 1
        }
        if (sourceLen == 0) return targetLen
        if (targetLen == 0) return sourceLen

        var previous = IntArray(targetLen + 1) { it }
        var current = IntArray(targetLen + 1)

        for (i in 1..sourceLen) {
            current[0] = i
            var minInRow = current[0]
            val sourceChar = source[i - 1]
            for (j in 1..targetLen) {
                val substitutionCost = if (sourceChar == target[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + substitutionCost
                )
                if (current[j] < minInRow) {
                    minInRow = current[j]
                }
            }
            if (minInRow > maxDistance) {
                return maxDistance + 1
            }
            val temp = previous
            previous = current
            current = temp
        }
        return previous[targetLen]
    }

    private const val MAX_EDIT_DISTANCE_FOR_PASS = 2
}
