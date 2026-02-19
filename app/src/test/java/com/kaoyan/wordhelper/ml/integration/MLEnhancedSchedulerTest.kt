package com.kaoyan.wordhelper.ml.integration

import com.kaoyan.wordhelper.data.entity.Progress
import com.kaoyan.wordhelper.ml.core.PersonalRetentionPredictor
import com.kaoyan.wordhelper.ml.training.ColdStartManager
import com.kaoyan.wordhelper.ml.training.ModelPersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MLEnhancedSchedulerTest {

    private lateinit var scheduler: MLEnhancedScheduler
    private lateinit var predictor: PersonalRetentionPredictor
    private lateinit var coldStartManager: ColdStartManager

    private val populationPrior = ModelPersistence.defaultPopulationPrior()

    @Before
    fun setup() {
        predictor = PersonalRetentionPredictor()
        coldStartManager = ColdStartManager(populationPrior)
        scheduler = MLEnhancedScheduler(predictor, coldStartManager)
    }

    @Test
    fun `ML关闭时应返回无调整`() {
        val result = scheduler.adjust(
            baseIntervalDays = 7,
            baseEaseFactor = 2.5f,
            progress = null,
            mlEnabled = false
        )
        assertEquals(7, result.adjustedIntervalDays)
        assertEquals(2.5f, result.adjustedEaseFactor, 0.01f)
        assertEquals(0f, result.confidence, 0.01f)
    }

    @Test
    fun `冷启动时样本为0应返回无调整`() {
        val result = scheduler.adjust(
            baseIntervalDays = 7,
            baseEaseFactor = 2.5f,
            progress = null,
            mlEnabled = true
        )
        // 样本为0, 置信度 < 0.01
        assertEquals(7, result.adjustedIntervalDays)
        assertEquals("冷启动中，样本不足", result.reason)
    }

    @Test
    fun `有足够样本时应产生调整`() {
        // 训练足够多的样本（模拟遗忘为主）
        coldStartManager.initializePredictor(predictor)
        val features = scheduler.extractFeatures(
            progress = Progress(wordId = 1, bookId = 1, easeFactor = 2.0f, intervalDays = 5),
            sessionPosition = 5,
            sessionTotal = 20
        )
        repeat(100) {
            predictor.updateOnline(features, 1) // 遗忘
        }

        val result = scheduler.adjust(
            baseIntervalDays = 10,
            baseEaseFactor = 2.5f,
            progress = Progress(wordId = 1, bookId = 1, easeFactor = 2.0f, intervalDays = 5),
            sessionPosition = 5,
            sessionTotal = 20,
            mlEnabled = true
        )

        assertTrue("置信度应 > 0: ${result.confidence}", result.confidence > 0f)
        assertTrue("应有调度原因", result.reason.isNotBlank())
    }

    @Test
    fun `间隔调整应在正负50比例约束内`() {
        coldStartManager.initializePredictor(predictor)
        val features = scheduler.extractFeatures(progress = null)
        repeat(300) { predictor.updateOnline(features, 1) }

        val base = 20
        val result = scheduler.adjust(
            baseIntervalDays = base,
            baseEaseFactor = 2.5f,
            progress = null,
            mlEnabled = true
        )

        val minAllowed = (base * 0.5f).toInt()
        val maxAllowed = (base * 1.5f).toInt()
        assertTrue(
            "调整后间隔 ${result.adjustedIntervalDays} 应在 [$minAllowed, $maxAllowed]",
            result.adjustedIntervalDays in minAllowed..maxAllowed
        )
    }

    @Test
    fun `predictForgetProbability应返回有效概率`() {
        coldStartManager.initializePredictor(predictor)
        val prob = scheduler.predictForgetProbability(
            progress = Progress(wordId = 1, bookId = 1),
            sessionPosition = 0,
            sessionTotal = 10
        )
        assertTrue("遗忘概率应在 [0, 1]: $prob", prob in 0f..1f)
    }

    @Test
    fun `getConfidence应与样本数一致`() {
        assertEquals(0f, scheduler.getConfidence(), 0.01f)

        val features = scheduler.extractFeatures(progress = null)
        repeat(50) { predictor.updateOnline(features, 0) }
        assertTrue("50样本后应有置信度", scheduler.getConfidence() > 0f)
    }

    @Test
    fun `extractFeatures应返回12维向量`() {
        val features = scheduler.extractFeatures(
            progress = Progress(wordId = 1, bookId = 1),
            sessionPosition = 3,
            sessionTotal = 15
        )
        assertEquals(12, features.values.size)
    }
}
