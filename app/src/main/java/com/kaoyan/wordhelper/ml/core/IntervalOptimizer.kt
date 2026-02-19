package com.kaoyan.wordhelper.ml.core

import kotlin.math.roundToInt

/**
 * 基于遗忘概率微调 SM-2 间隔
 * 约束：调整幅度不超过原间隔的 ±50%
 */
object IntervalOptimizer {

    private const val TARGET_RETENTION = 0.9f
    private const val MAX_ADJUSTMENT_RATIO = 0.5f
    private const val MIN_INTERVAL_DAYS = 1

    /**
     * 根据ML遗忘概率调整间隔
     *
     * @param baseIntervalDays SM-2 计算的原始间隔
     * @param forgetProb ML预测的遗忘概率 [0, 1]
     * @param confidence 模型置信度 [0, 1]
     * @return 调整后的间隔天数
     */
    fun optimize(
        baseIntervalDays: Int,
        forgetProb: Float,
        confidence: Float
    ): Int {
        if (baseIntervalDays <= 0) return baseIntervalDays
        if (confidence <= 0.01f) return baseIntervalDays

        // 目标保留率 0.9 → 允许遗忘概率 0.1
        // 如果预测遗忘概率 > 0.1 → 缩短间隔
        // 如果预测遗忘概率 < 0.1 → 延长间隔
        val targetForgetProb = 1f - TARGET_RETENTION
        val ratio = if (forgetProb > 0.01f) {
            targetForgetProb / forgetProb
        } else {
            1.5f // 遗忘概率极低，可以适当延长
        }

        // 置信度加权：低置信度时调整幅度更小
        val adjustedRatio = 1f + (ratio - 1f) * confidence
        // 约束在 ±50%
        val clampedRatio = adjustedRatio.coerceIn(
            1f - MAX_ADJUSTMENT_RATIO,
            1f + MAX_ADJUSTMENT_RATIO
        )

        val optimized = (baseIntervalDays * clampedRatio).roundToInt()
        return optimized.coerceAtLeast(MIN_INTERVAL_DAYS)
    }

    /**
     * 计算基于当前遗忘概率的调度建议
     */
    fun computeAdjustment(
        baseIntervalDays: Int,
        baseEaseFactor: Float,
        forgetProb: Float,
        confidence: Float
    ): SchedulingAdjustment {
        val adjustedInterval = optimize(baseIntervalDays, forgetProb, confidence)
        val adjustedEf = AdaptiveEaseFactor.predictInitialEf(forgetProb, confidence)

        val reason = when {
            confidence < 0.1f -> "数据不足，保持原始调度"
            forgetProb > 0.3f -> "遗忘风险较高(${(forgetProb * 100).roundToInt()}%)，缩短间隔"
            forgetProb < 0.05f -> "记忆稳固(${(forgetProb * 100).roundToInt()}%)，适当延长间隔"
            else -> "遗忘概率适中(${(forgetProb * 100).roundToInt()}%)，微调间隔"
        }

        return SchedulingAdjustment(
            adjustedIntervalDays = adjustedInterval,
            adjustedEaseFactor = adjustedEf,
            forgetProbability = forgetProb,
            confidence = confidence,
            reason = reason
        )
    }
}
