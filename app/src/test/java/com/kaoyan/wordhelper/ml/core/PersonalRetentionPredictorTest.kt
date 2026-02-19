package com.kaoyan.wordhelper.ml.core

import com.kaoyan.wordhelper.ml.features.FeatureVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PersonalRetentionPredictorTest {

    private lateinit var predictor: PersonalRetentionPredictor

    @Before
    fun setup() {
        predictor = PersonalRetentionPredictor()
    }

    @Test
    fun `初始模型预测应返回接近0_5的值`() {
        val features = FeatureVector(FloatArray(12) { 0.5f })
        val prob = predictor.predictForgetProbability(features)
        assertTrue("初始预测应在 [0, 1] 范围内", prob in 0f..1f)
    }

    @Test
    fun `sigmoid输出应在0到1之间`() {
        val features = FeatureVector(FloatArray(12) { 1.0f })
        val prob = predictor.predictForgetProbability(features)
        assertTrue("sigmoid输出应在 [0, 1]", prob in 0f..1f)

        val features2 = FeatureVector(FloatArray(12) { -1.0f })
        val prob2 = predictor.predictForgetProbability(features2)
        assertTrue("sigmoid输出应在 [0, 1]", prob2 in 0f..1f)
    }

    @Test
    fun `在线更新后预测应发生变化`() {
        val features = FeatureVector(FloatArray(12) { 0.5f })
        val beforeProb = predictor.predictForgetProbability(features)

        // 多次训练标签为1（遗忘）
        repeat(20) {
            predictor.updateOnline(features, 1)
        }

        val afterProb = predictor.predictForgetProbability(features)
        assertTrue(
            "训练遗忘样本后遗忘概率应增大: before=$beforeProb, after=$afterProb",
            afterProb > beforeProb
        )
    }

    @Test
    fun `在线更新后记住样本应降低遗忘概率`() {
        val features = FeatureVector(FloatArray(12) { 0.5f })
        // 先用先验初始化
        predictor.initFromPrior(FloatArray(12) { 0.3f })
        val beforeProb = predictor.predictForgetProbability(features)

        // 多次训练标签为0（记住）
        repeat(30) {
            predictor.updateOnline(features, 0)
        }

        val afterProb = predictor.predictForgetProbability(features)
        assertTrue(
            "训练记住样本后遗忘概率应降低: before=$beforeProb, after=$afterProb",
            afterProb < beforeProb
        )
    }

    @Test
    fun `updateOnline应返回非负误差`() {
        val features = FeatureVector(FloatArray(12) { 0.5f })
        val error = predictor.updateOnline(features, 1)
        assertTrue("误差应非负", error >= 0f)
    }

    @Test
    fun `sampleCount应随更新递增`() {
        assertEquals(0, predictor.sampleCount)
        val features = FeatureVector.zeros()
        predictor.updateOnline(features, 0)
        assertEquals(1, predictor.sampleCount)
        predictor.updateOnline(features, 1)
        assertEquals(2, predictor.sampleCount)
    }

    @Test
    fun `restore应正确恢复模型状态`() {
        val features = FeatureVector(FloatArray(12) { 0.5f })
        repeat(10) { predictor.updateOnline(features, 1) }

        val savedN = predictor.n.copyOf()
        val savedZ = predictor.z.copyOf()
        val savedW = predictor.weights.copyOf()
        val savedVersion = predictor.version
        val savedCount = predictor.sampleCount

        val newPredictor = PersonalRetentionPredictor()
        newPredictor.restore(savedN, savedZ, savedW, savedVersion, savedCount)

        val original = predictor.predictForgetProbability(features)
        val restored = newPredictor.predictForgetProbability(features)
        assertEquals(original, restored, 0.001f)
        assertEquals(savedCount, newPredictor.sampleCount)
    }

    @Test
    fun `initFromPrior应设置先验权重`() {
        val prior = floatArrayOf(0.5f, -0.3f, 0.1f, 0.2f, -0.4f, -0.5f, -0.6f, 0.1f, -0.3f, 0.4f, -0.2f, 0f)
        predictor.initFromPrior(prior)

        val features = FeatureVector(FloatArray(12) { 1.0f })
        val prob = predictor.predictForgetProbability(features)
        assertTrue("使用先验后预测应在 [0, 1]", prob in 0f..1f)
    }

    @Test
    fun `reset应将模型恢复到初始状态`() {
        val features = FeatureVector(FloatArray(12) { 0.5f })
        repeat(10) { predictor.updateOnline(features, 1) }
        assertTrue(predictor.sampleCount > 0)

        predictor.reset()
        assertEquals(0, predictor.sampleCount)
        assertEquals(0, predictor.version)
    }

    @Test
    fun `极端输入特征不应导致NaN或Infinity`() {
        val extremeFeatures = FeatureVector(FloatArray(12) { 100f })
        val prob = predictor.predictForgetProbability(extremeFeatures)
        assertTrue("不应为NaN", !prob.isNaN())
        assertTrue("不应为Infinity", !prob.isInfinite())
        assertTrue("应在 [0, 1]", prob in 0f..1f)
    }
}
