package com.kaoyan.wordhelper.ml.core

/**
 * 调度调整结果
 */
data class SchedulingAdjustment(
    val adjustedIntervalDays: Int,
    val adjustedEaseFactor: Float,
    val forgetProbability: Float,
    val confidence: Float,
    val reason: String
) {
    companion object {
        /** 无调整（ML关闭或置信度不足） */
        fun noAdjustment(
            originalIntervalDays: Int,
            originalEaseFactor: Float,
            reason: String = "ML未启用"
        ) = SchedulingAdjustment(
            adjustedIntervalDays = originalIntervalDays,
            adjustedEaseFactor = originalEaseFactor,
            forgetProbability = 0f,
            confidence = 0f,
            reason = reason
        )
    }
}
