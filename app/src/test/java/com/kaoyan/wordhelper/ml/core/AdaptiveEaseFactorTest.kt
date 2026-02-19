package com.kaoyan.wordhelper.ml.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveEaseFactorTest {

    @Test
    fun `高遗忘概率应产生较低的初始EF`() {
        val ef = AdaptiveEaseFactor.predictInitialEf(forgetProb = 0.8f, confidence = 1.0f)
        assertTrue("高遗忘概率对应低EF: $ef", ef < 2.5f)
        assertTrue("EF不应低于1.3: $ef", ef >= 1.3f)
    }

    @Test
    fun `低遗忘概率应产生较高的初始EF`() {
        val ef = AdaptiveEaseFactor.predictInitialEf(forgetProb = 0.1f, confidence = 1.0f)
        assertTrue("低遗忘概率对应高EF: $ef", ef > 2.5f)
        assertTrue("EF不应高于3.0: $ef", ef <= 3.0f)
    }

    @Test
    fun `零置信度应返回默认EF`() {
        val ef = AdaptiveEaseFactor.predictInitialEf(forgetProb = 0.9f, confidence = 0f)
        assertEquals(2.5f, ef, 0.01f)
    }

    @Test
    fun `结果应始终在合法EF范围内`() {
        val testCases = listOf(0f, 0.1f, 0.3f, 0.5f, 0.7f, 0.9f, 1.0f)
        for (prob in testCases) {
            for (conf in testCases) {
                val ef = AdaptiveEaseFactor.predictInitialEf(prob, conf)
                assertTrue("EF=$ef 应在 [1.3, 3.0]", ef in 1.3f..3.0f)
            }
        }
    }

    @Test
    fun `adjustEf预测会忘但实际记住应提高EF`() {
        val adjusted = AdaptiveEaseFactor.adjustEf(
            currentEf = 2.5f,
            forgetProb = 0.7f,
            actualOutcome = 0, // 记住
            confidence = 1.0f
        )
        assertTrue("EF应提高: $adjusted", adjusted > 2.5f)
    }

    @Test
    fun `adjustEf预测记住但实际忘了应降低EF`() {
        val adjusted = AdaptiveEaseFactor.adjustEf(
            currentEf = 2.5f,
            forgetProb = 0.2f,
            actualOutcome = 1, // 遗忘
            confidence = 1.0f
        )
        assertTrue("EF应降低: $adjusted", adjusted < 2.5f)
    }

    @Test
    fun `adjustEf结果应在合法范围内`() {
        val extremeLow = AdaptiveEaseFactor.adjustEf(1.3f, 0.01f, 1, 1.0f)
        val extremeHigh = AdaptiveEaseFactor.adjustEf(3.0f, 0.99f, 0, 1.0f)
        assertTrue("不应低于1.3: $extremeLow", extremeLow >= 1.3f)
        assertTrue("不应高于3.0: $extremeHigh", extremeHigh <= 3.0f)
    }
}
