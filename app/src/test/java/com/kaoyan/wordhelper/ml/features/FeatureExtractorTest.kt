package com.kaoyan.wordhelper.ml.features

import com.kaoyan.wordhelper.data.entity.MLModelState
import com.kaoyan.wordhelper.data.entity.Progress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureExtractorTest {

    @Test
    fun `提取特征应返回12维向量`() {
        val features = FeatureExtractor.extract(progress = null)
        assertEquals(FeatureVector.DIMENSION, features.values.size)
    }

    @Test
    fun `所有特征值应在0到1范围内`() {
        val progress = Progress(
            wordId = 1L,
            bookId = 1L,
            easeFactor = 2.5f,
            intervalDays = 7,
            reviewCount = 10,
            spellWrongCount = 2,
            consecutiveCorrect = 3,
            avgResponseTimeMs = 3500f,
            lastReviewTime = System.currentTimeMillis() - 3600_000
        )
        val features = FeatureExtractor.extract(
            progress = progress,
            sessionPosition = 5,
            sessionTotal = 20,
            modelState = MLModelState()
        )
        features.values.forEachIndexed { index, value ->
            assertTrue(
                "特征 f${index + 1} 应在 [0, 1]，实际为 $value",
                value in 0f..1f
            )
        }
    }

    @Test
    fun `null progress应返回合理默认特征`() {
        val features = FeatureExtractor.extract(progress = null)
        // f1: difficulty = (3.0 - 2.5) / 1.7 ≈ 0.294
        assertTrue("f1应在合理范围", features[0] in 0f..1f)
        // f7: accuracy = 0.5 (默认)
        assertEquals(0.5f, features[6], 0.01f)
        // f10: lastReviewTime=0 → 1.0
        assertEquals(1.0f, features[9], 0.01f)
    }

    @Test
    fun `难度特征应与EF反相关`() {
        val easyProgress = Progress(wordId = 1, bookId = 1, easeFactor = 3.0f)
        val hardProgress = Progress(wordId = 2, bookId = 1, easeFactor = 1.3f)

        val easyFeatures = FeatureExtractor.extract(progress = easyProgress)
        val hardFeatures = FeatureExtractor.extract(progress = hardProgress)

        assertTrue(
            "低EF应对应高难度: easy_f1=${easyFeatures[0]}, hard_f1=${hardFeatures[0]}",
            hardFeatures[0] > easyFeatures[0]
        )
    }

    @Test
    fun `间隔特征应单调递增`() {
        val short = Progress(wordId = 1, bookId = 1, intervalDays = 1)
        val long = Progress(wordId = 2, bookId = 1, intervalDays = 30)

        val shortF = FeatureExtractor.extract(progress = short)
        val longF = FeatureExtractor.extract(progress = long)

        assertTrue(
            "长间隔应有更大的f5: short=${shortF[4]}, long=${longF[4]}",
            longF[4] > shortF[4]
        )
    }

    @Test
    fun `正确率特征应正确计算`() {
        val perfectProgress = Progress(
            wordId = 1, bookId = 1,
            reviewCount = 10, spellWrongCount = 0
        )
        val halfProgress = Progress(
            wordId = 2, bookId = 1,
            reviewCount = 10, spellWrongCount = 5
        )

        val perfectF = FeatureExtractor.extract(progress = perfectProgress)
        val halfF = FeatureExtractor.extract(progress = halfProgress)

        assertEquals(1.0f, perfectF[6], 0.01f)
        assertEquals(0.5f, halfF[6], 0.01f)
    }

    @Test
    fun `会话疲劳应反映当前位置`() {
        val startF = FeatureExtractor.extract(
            progress = null, sessionPosition = 0, sessionTotal = 20
        )
        val endF = FeatureExtractor.extract(
            progress = null, sessionPosition = 19, sessionTotal = 20
        )

        assertTrue(
            "会话后期疲劳应更高: start=${startF[3]}, end=${endF[3]}",
            endF[3] > startF[3]
        )
    }

    @Test
    fun `连续正确sigmoid应单调递增`() {
        val low = FeatureExtractor.sigmoid(0f, 5f)
        val mid = FeatureExtractor.sigmoid(5f, 5f)
        val high = FeatureExtractor.sigmoid(10f, 5f)

        assertTrue("sigmoid在中点应接近0.5", mid in 0.49f..0.51f)
        assertTrue("sigmoid应单调递增", low < mid && mid < high)
    }

    @Test
    fun `FeatureVector序列化反序列化应一致`() {
        val original = FeatureVector(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f, 0.9f, 0.0f, 0.5f, 0.3f))
        val json = original.toJson()
        val restored = FeatureVector.fromJson(json)
        original.values.forEachIndexed { i, v ->
            assertEquals(v, restored[i], 0.001f)
        }
    }
}
