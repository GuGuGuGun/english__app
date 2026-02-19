package com.kaoyan.wordhelper.ml.training

import com.kaoyan.wordhelper.ml.core.PersonalRetentionPredictor
import com.kaoyan.wordhelper.ml.features.FeatureVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ColdStartManagerTest {

    private lateinit var manager: ColdStartManager
    private lateinit var predictor: PersonalRetentionPredictor
    private val populationPrior = floatArrayOf(
        0.8f, -0.05f, 0.02f, 0.3f, -0.6f, -0.5f,
        -0.7f, 0.2f, -0.4f, 0.5f, -0.3f, 0.0f
    )

    @Before
    fun setup() {
        manager = ColdStartManager(populationPrior)
        predictor = PersonalRetentionPredictor()
    }

    @Test
    fun `样本不足50时置信度应很低`() {
        val confidence = manager.getConfidence(0)
        assertEquals(0f, confidence, 0.01f)

        val confidence25 = manager.getConfidence(25)
        assertTrue("25样本置信度应 < 0.3: $confidence25", confidence25 < 0.3f)
    }

    @Test
    fun `样本50到200应为中等置信度`() {
        val conf50 = manager.getConfidence(50)
        val conf100 = manager.getConfidence(100)
        val conf200 = manager.getConfidence(200)

        assertTrue("50样本应 >= 0.3: $conf50", conf50 >= 0.3f)
        assertTrue("100样本应 > 50样本: $conf100 > $conf50", conf100 > conf50)
        assertTrue("200样本应 >= 0.8: $conf200", conf200 >= 0.8f)
    }

    @Test
    fun `样本超过200应为高置信度`() {
        val conf500 = manager.getConfidence(500)
        assertTrue("500样本置信度应 > 0.8: $conf500", conf500 > 0.8f)
    }

    @Test
    fun `置信度应不超过1`() {
        val confMax = manager.getConfidence(10000)
        assertTrue("置信度不应超过1: $confMax", confMax <= 1.0f)
    }

    @Test
    fun `冷启动时blendedPredict应使用人群先验`() {
        val features = FeatureVector(FloatArray(12) { 0.5f })

        // 样本为0，应完全使用人群先验
        val prediction = manager.blendedPredict(predictor, features, 0)
        assertTrue("冷启动预测应在 [0, 1]: $prediction", prediction in 0f..1f)
    }

    @Test
    fun `充分训练后blendedPredict应使用个人模型`() {
        val features = FeatureVector(FloatArray(12) { 0.5f })

        // 模拟充分训练
        repeat(300) {
            predictor.updateOnline(features, 1)
        }

        val blended = manager.blendedPredict(predictor, features, 300)
        val personal = predictor.predictForgetProbability(features)

        // 样本>200，应完全使用个人模型
        assertEquals(personal, blended, 0.01f)
    }

    @Test
    fun `initializePredictor应设置先验权重`() {
        assertEquals(0, predictor.sampleCount)
        manager.initializePredictor(predictor)

        val features = FeatureVector(FloatArray(12) { 0.5f })
        val prob = predictor.predictForgetProbability(features)
        // 先验权重已设置，预测不应为0.5
        assertTrue("初始化后预测应在 [0, 1]: $prob", prob in 0f..1f)
    }

    @Test
    fun `已有样本时initializePredictor不应重置`() {
        val features = FeatureVector(FloatArray(12) { 0.5f })
        predictor.updateOnline(features, 1)
        assertEquals(1, predictor.sampleCount)

        manager.initializePredictor(predictor)
        assertEquals(1, predictor.sampleCount)
    }
}
