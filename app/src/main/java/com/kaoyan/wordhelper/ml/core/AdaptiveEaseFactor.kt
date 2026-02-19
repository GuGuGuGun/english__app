package com.kaoyan.wordhelper.ml.core

import com.kaoyan.wordhelper.ml.features.FeatureVector

/**
 * 自适应EF因子：基于ML预测的个性化初始EF + 动态EF调整
 */
object AdaptiveEaseFactor {

    private const val DEFAULT_EF = 2.5f
    private const val MIN_EF = 1.3f
    private const val MAX_EF = 3.0f

    /**
     * 预测个性化初始EF
     * 冷启动时使用人群先验混合，样本充足后完全使用个人模型
     *
     * @param forgetProb ML预测的遗忘概率 [0, 1]
     * @param confidence 模型置信度 [0, 1]
     * @return 个性化初始EF [1.3, 3.0]
     */
    fun predictInitialEf(forgetProb: Float, confidence: Float): Float {
        // 遗忘概率高 → 单词难 → EF低；遗忘概率低 → EF高
        val mlEf = DEFAULT_EF - (forgetProb - 0.5f) * 1.5f
        // 置信度加权：低置信度时倾向默认EF
        val blended = DEFAULT_EF * (1f - confidence) + mlEf * confidence
        return blended.coerceIn(MIN_EF, MAX_EF)
    }

    /**
     * 基于ML预测误差动态调整当前EF
     *
     * @param currentEf 当前EF值
     * @param forgetProb 预测遗忘概率
     * @param actualOutcome 实际结果：0=记住，1=遗忘
     * @param confidence 模型置信度
     * @return 调整后的EF
     */
    fun adjustEf(
        currentEf: Float,
        forgetProb: Float,
        actualOutcome: Int,
        confidence: Float
    ): Float {
        // 预测误差 = 预测遗忘概率 - 实际遗忘
        val predictionError = forgetProb - actualOutcome.toFloat()
        // 正误差（预测会忘但实际记住）→ EF应该升高
        // 负误差（预测记住但实际忘了）→ EF应该降低
        val adjustment = predictionError * 0.3f * confidence
        val adjusted = currentEf + adjustment
        return adjusted.coerceIn(MIN_EF, MAX_EF)
    }
}
