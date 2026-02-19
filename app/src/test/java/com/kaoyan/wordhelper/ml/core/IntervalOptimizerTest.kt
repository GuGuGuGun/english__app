package com.kaoyan.wordhelper.ml.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntervalOptimizerTest {

    @Test
    fun `高遗忘概率应缩短间隔`() {
        val adjusted = IntervalOptimizer.optimize(
            baseIntervalDays = 10,
            forgetProb = 0.5f,
            confidence = 1.0f
        )
        assertTrue("高遗忘概率应缩短间隔: base=10, adjusted=$adjusted", adjusted < 10)
    }

    @Test
    fun `低遗忘概率应延长间隔`() {
        val adjusted = IntervalOptimizer.optimize(
            baseIntervalDays = 10,
            forgetProb = 0.02f,
            confidence = 1.0f
        )
        assertTrue("低遗忘概率应延长间隔: base=10, adjusted=$adjusted", adjusted > 10)
    }

    @Test
    fun `间隔调整不应超过正负50%`() {
        val base = 10
        val shortened = IntervalOptimizer.optimize(base, forgetProb = 0.99f, confidence = 1.0f)
        val lengthened = IntervalOptimizer.optimize(base, forgetProb = 0.001f, confidence = 1.0f)

        assertTrue("缩短不应超过50%: $shortened >= ${base / 2}", shortened >= base / 2)
        assertTrue("延长不应超过50%: $lengthened <= ${base * 3 / 2}", lengthened <= base * 3 / 2)
    }

    @Test
    fun `低置信度应减小调整幅度`() {
        val highConfAdjust = IntervalOptimizer.optimize(10, 0.5f, 1.0f)
        val lowConfAdjust = IntervalOptimizer.optimize(10, 0.5f, 0.1f)

        val highDelta = kotlin.math.abs(highConfAdjust - 10)
        val lowDelta = kotlin.math.abs(lowConfAdjust - 10)
        assertTrue("低置信度调整应更小: highDelta=$highDelta, lowDelta=$lowDelta", lowDelta <= highDelta)
    }

    @Test
    fun `零置信度不应调整间隔`() {
        val adjusted = IntervalOptimizer.optimize(10, 0.5f, 0f)
        assertEquals(10, adjusted)
    }

    @Test
    fun `间隔至少为1天`() {
        val adjusted = IntervalOptimizer.optimize(1, 0.99f, 1.0f)
        assertTrue("间隔不应小于1天: $adjusted", adjusted >= 1)
    }

    @Test
    fun `零基础间隔应原样返回`() {
        val adjusted = IntervalOptimizer.optimize(0, 0.5f, 1.0f)
        assertEquals(0, adjusted)
    }

    @Test
    fun `computeAdjustment应返回完整调度调整`() {
        val adj = IntervalOptimizer.computeAdjustment(
            baseIntervalDays = 7,
            baseEaseFactor = 2.5f,
            forgetProb = 0.3f,
            confidence = 0.8f
        )
        assertTrue("间隔应在合理范围", adj.adjustedIntervalDays > 0)
        assertTrue("EF应在 [1.3, 3.0]", adj.adjustedEaseFactor in 1.3f..3.0f)
        assertEquals(0.3f, adj.forgetProbability, 0.001f)
        assertTrue("原因不应为空", adj.reason.isNotBlank())
    }
}
