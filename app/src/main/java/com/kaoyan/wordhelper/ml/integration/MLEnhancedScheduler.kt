package com.kaoyan.wordhelper.ml.integration

import com.kaoyan.wordhelper.data.entity.MLModelState
import com.kaoyan.wordhelper.data.entity.Progress
import com.kaoyan.wordhelper.ml.core.AdaptiveEaseFactor
import com.kaoyan.wordhelper.ml.core.IntervalOptimizer
import com.kaoyan.wordhelper.ml.core.PersonalRetentionPredictor
import com.kaoyan.wordhelper.ml.core.SchedulingAdjustment
import com.kaoyan.wordhelper.ml.features.FeatureExtractor
import com.kaoyan.wordhelper.ml.features.FeatureVector
import com.kaoyan.wordhelper.ml.training.ColdStartManager
import kotlin.math.roundToInt

/**
 * ML增强调度器：双层调度架构
 * 第一层：SM-2 基础调度
 * 第二层：ML 微调层（本类）
 *
 * 设计原则：
 * - ML关闭时行为与原系统完全一致
 * - 间隔调整约束在 ±50%
 * - EF 约束在 [1.3, 3.0]
 */
class MLEnhancedScheduler(
    private val predictor: PersonalRetentionPredictor,
    private val coldStartManager: ColdStartManager
) {
    private var modelState: MLModelState? = null

    fun setModelState(state: MLModelState?) {
        modelState = state
    }

    /**
     * 对 SM-2 的调度结果进行 ML 微调
     *
     * @param baseIntervalDays SM-2 计算的间隔
     * @param baseEaseFactor SM-2 计算的 EF
     * @param progress 当前进度
     * @param sessionPosition 会话中已完成数量
     * @param sessionTotal 会话总数
     * @param mlEnabled ML开关是否开启
     * @return 调整结果（包含调整后的间隔、EF、原因等）
     */
    fun adjust(
        baseIntervalDays: Int,
        baseEaseFactor: Float,
        progress: Progress?,
        sessionPosition: Int = 0,
        sessionTotal: Int = 1,
        mlEnabled: Boolean = false
    ): SchedulingAdjustment {
        if (!mlEnabled) {
            return SchedulingAdjustment.noAdjustment(
                originalIntervalDays = baseIntervalDays,
                originalEaseFactor = baseEaseFactor
            )
        }

        val features = FeatureExtractor.extract(
            progress = progress,
            sessionPosition = sessionPosition,
            sessionTotal = sessionTotal,
            modelState = modelState
        )

        val sampleCount = predictor.sampleCount
        val confidence = coldStartManager.getConfidence(sampleCount)

        if (confidence < 0.01f) {
            return SchedulingAdjustment.noAdjustment(
                originalIntervalDays = baseIntervalDays,
                originalEaseFactor = baseEaseFactor,
                reason = "冷启动中，样本不足"
            )
        }

        val forgetProb = coldStartManager.blendedPredict(predictor, features, sampleCount)

        return IntervalOptimizer.computeAdjustment(
            baseIntervalDays = baseIntervalDays,
            baseEaseFactor = baseEaseFactor,
            forgetProb = forgetProb,
            confidence = confidence
        )
    }

    /**
     * 预测当前单词的遗忘概率（用于UI展示）
     */
    fun predictForgetProbability(
        progress: Progress?,
        sessionPosition: Int = 0,
        sessionTotal: Int = 1
    ): Float {
        val features = FeatureExtractor.extract(
            progress = progress,
            sessionPosition = sessionPosition,
            sessionTotal = sessionTotal,
            modelState = modelState
        )
        return coldStartManager.blendedPredict(
            predictor, features, predictor.sampleCount
        )
    }

    /**
     * 获取当前模型的置信度
     */
    fun getConfidence(): Float {
        return coldStartManager.getConfidence(predictor.sampleCount)
    }

    /**
     * 提取特征向量（用于训练样本记录）
     */
    fun extractFeatures(
        progress: Progress?,
        sessionPosition: Int = 0,
        sessionTotal: Int = 1
    ): FeatureVector {
        return FeatureExtractor.extract(
            progress = progress,
            sessionPosition = sessionPosition,
            sessionTotal = sessionTotal,
            modelState = modelState
        )
    }
}
