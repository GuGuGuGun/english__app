package com.kaoyan.wordhelper.ml.training

import com.kaoyan.wordhelper.ml.core.PersonalRetentionPredictor
import com.kaoyan.wordhelper.ml.features.FeatureVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OnlineTrainerTest {

    private lateinit var predictor: PersonalRetentionPredictor

    @Before
    fun setup() {
        predictor = PersonalRetentionPredictor()
    }

    @Test
    fun `FTRL训练应收敛到低误差`() {
        // 模拟简单模式：特征全为高时容易遗忘
        val forgetFeatures = FeatureVector(floatArrayOf(0.9f, 0.5f, 0.5f, 0.8f, 0.1f, 0.2f, 0.3f, 0.7f, 0.1f, 0.8f, 0.3f, 0f))
        val rememberFeatures = FeatureVector(floatArrayOf(0.1f, 0.5f, 0.5f, 0.2f, 0.8f, 0.8f, 0.9f, 0.2f, 0.9f, 0.1f, 0.8f, 0f))

        // 训练100轮
        var totalError = 0f
        var lastBatchError = 0f
        repeat(100) { i ->
            val e1 = predictor.updateOnline(forgetFeatures, 1)
            val e2 = predictor.updateOnline(rememberFeatures, 0)
            totalError += e1 + e2
            if (i >= 90) {
                lastBatchError += e1 + e2
            }
        }

        val avgLastBatchError = lastBatchError / 20f // 最后10轮×2个样本
        assertTrue(
            "最后10轮平均误差应小于0.5: $avgLastBatchError",
            avgLastBatchError < 0.5f
        )
    }

    @Test
    fun `训练后应正确区分遗忘和记住模式`() {
        val forgetFeatures = FeatureVector(floatArrayOf(0.9f, 0.5f, 0.5f, 0.8f, 0.1f, 0.2f, 0.3f, 0.7f, 0.1f, 0.8f, 0.3f, 0f))
        val rememberFeatures = FeatureVector(floatArrayOf(0.1f, 0.5f, 0.5f, 0.2f, 0.8f, 0.8f, 0.9f, 0.2f, 0.9f, 0.1f, 0.8f, 0f))

        repeat(200) {
            predictor.updateOnline(forgetFeatures, 1)
            predictor.updateOnline(rememberFeatures, 0)
        }

        val forgetProb = predictor.predictForgetProbability(forgetFeatures)
        val rememberProb = predictor.predictForgetProbability(rememberFeatures)

        assertTrue(
            "遗忘模式的遗忘概率应更高: forget=$forgetProb, remember=$rememberProb",
            forgetProb > rememberProb
        )
    }

    @Test
    fun `sampleCount应准确记录`() {
        assertEquals(0, predictor.sampleCount)

        val features = FeatureVector.zeros()
        repeat(50) {
            predictor.updateOnline(features, if (it % 2 == 0) 1 else 0)
        }

        assertEquals(50, predictor.sampleCount)
    }

    @Test
    fun `ModelPersistence JSON序列化应正确`() {
        val original = floatArrayOf(0.1f, 0.2f, 0.3f, -0.5f, 1.0f)
        val json = ModelPersistence.floatArrayToJson(original)
        val restored = ModelPersistence.jsonToFloatArray(json)

        assertEquals(original.size, restored.size)
        original.forEachIndexed { i, v ->
            assertEquals(v, restored[i], 0.0001f)
        }
    }

    @Test
    fun `空JSON应返回空数组`() {
        val result = ModelPersistence.jsonToFloatArray("")
        assertTrue("空JSON应返回空数组", result.isEmpty())
    }

    @Test
    fun `defaultPopulationPrior应为12维`() {
        val prior = ModelPersistence.defaultPopulationPrior()
        assertEquals(12, prior.size)
    }
}
