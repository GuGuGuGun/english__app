package com.kaoyan.wordhelper.ml.training

import com.kaoyan.wordhelper.ml.core.PersonalRetentionPredictor
import com.kaoyan.wordhelper.ml.features.FeatureVector

/**
 * 冷启动管理器：根据样本数量决定使用人群先验还是个人模型
 */
class ColdStartManager(
    private val populationPrior: FloatArray
) {
    /**
     * 获取当前模型置信度（基于样本数量）
     * - 样本 < 50：低置信度，主要使用人群先验
     * - 样本 50-200：中等置信度，线性混合
     * - 样本 > 200：高置信度，完全个人模型
     */
    fun getConfidence(sampleCount: Int): Float {
        return when {
            sampleCount < COLD_START_THRESHOLD -> {
                sampleCount.toFloat() / COLD_START_THRESHOLD.toFloat() * 0.3f
            }
            sampleCount < WARM_UP_THRESHOLD -> {
                val progress = (sampleCount - COLD_START_THRESHOLD).toFloat() /
                    (WARM_UP_THRESHOLD - COLD_START_THRESHOLD).toFloat()
                0.3f + progress * 0.5f
            }
            else -> {
                (0.8f + (sampleCount - WARM_UP_THRESHOLD).toFloat() / 1000f)
                    .coerceAtMost(1.0f)
            }
        }
    }

    /**
     * 混合预测：结合人群先验和个人模型
     */
    fun blendedPredict(
        predictor: PersonalRetentionPredictor,
        features: FeatureVector,
        sampleCount: Int
    ): Float {
        val personalPrediction = predictor.predictForgetProbability(features)
        val priorPrediction = predictWithPrior(features)
        val personalWeight = getPersonalWeight(sampleCount)

        return priorPrediction * (1f - personalWeight) + personalPrediction * personalWeight
    }

    /**
     * 初始化模型：冷启动时使用人群先验
     */
    fun initializePredictor(predictor: PersonalRetentionPredictor) {
        if (predictor.sampleCount == 0) {
            predictor.initFromPrior(populationPrior)
        }
    }

    private fun predictWithPrior(features: FeatureVector): Float {
        var logit = 0f
        for (i in populationPrior.indices.take(features.values.size)) {
            logit += populationPrior[i] * features[i]
        }
        return sigmoid(logit)
    }

    /**
     * 个人模型在混合中的权重
     */
    private fun getPersonalWeight(sampleCount: Int): Float {
        return when {
            sampleCount < COLD_START_THRESHOLD -> 0f
            sampleCount < WARM_UP_THRESHOLD -> {
                (sampleCount - COLD_START_THRESHOLD).toFloat() /
                    (WARM_UP_THRESHOLD - COLD_START_THRESHOLD).toFloat()
            }
            else -> 1f
        }
    }

    companion object {
        const val COLD_START_THRESHOLD = 50
        const val WARM_UP_THRESHOLD = 200

        private fun sigmoid(x: Float): Float {
            val clipped = x.coerceIn(-20f, 20f)
            return (1.0 / (1.0 + kotlin.math.exp(-clipped.toDouble()))).toFloat()
        }
    }
}
