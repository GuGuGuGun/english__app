package com.kaoyan.wordhelper.ml.core

/**
 * 自适应响应时间阈值
 * 替代 LearningViewModel 中固定的 6000ms 阈值
 * 基于个人响应时间分布（均值 + 1.5σ），保底 3000ms-15000ms
 */
object AdaptiveResponseThreshold {

    private const val DEFAULT_THRESHOLD_MS = 6000L
    private const val MIN_THRESHOLD_MS = 3000L
    private const val MAX_THRESHOLD_MS = 15000L
    private const val SIGMA_MULTIPLIER = 1.5f

    /**
     * 计算个性化响应时间阈值
     *
     * @param avgResponseTime 个人平均响应时间(ms)
     * @param stdResponseTime 个人响应时间标准差(ms)
     * @param mlEnabled ML开关是否开启
     * @return 阈值(ms)
     */
    fun computeThreshold(
        avgResponseTime: Float,
        stdResponseTime: Float,
        mlEnabled: Boolean
    ): Long {
        if (!mlEnabled) return DEFAULT_THRESHOLD_MS
        if (avgResponseTime <= 0f) return DEFAULT_THRESHOLD_MS

        val threshold = avgResponseTime + SIGMA_MULTIPLIER * stdResponseTime
        return threshold.toLong().coerceIn(MIN_THRESHOLD_MS, MAX_THRESHOLD_MS)
    }
}
