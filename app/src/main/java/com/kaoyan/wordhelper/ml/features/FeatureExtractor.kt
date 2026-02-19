package com.kaoyan.wordhelper.ml.features

import com.kaoyan.wordhelper.data.entity.MLModelState
import com.kaoyan.wordhelper.data.entity.Progress
import java.util.Calendar
import kotlin.math.exp
import kotlin.math.ln

/**
 * 从现有数据中提取12维特征，所有值归一化到 [0, 1]
 */
object FeatureExtractor {

    /**
     * @param progress 当前单词的学习进度
     * @param sessionPosition 当前会话中已回答的题目数
     * @param sessionTotal 当前会话总题目数
     * @param modelState ML模型状态（用于全局记忆稳定性）
     * @param now 当前时间戳
     */
    fun extract(
        progress: Progress?,
        sessionPosition: Int = 0,
        sessionTotal: Int = 1,
        modelState: MLModelState? = null,
        now: Long = System.currentTimeMillis()
    ): FeatureVector {
        val ef = progress?.easeFactor ?: 2.5f
        val intervalDays = progress?.intervalDays ?: 0
        val reviewCount = progress?.reviewCount ?: 0
        val spellWrongCount = progress?.spellWrongCount ?: 0
        val consecutiveCorrect = progress?.consecutiveCorrect ?: 0
        val avgResponseTimeMs = progress?.avgResponseTimeMs ?: 0f
        val lastReviewTime = progress?.lastReviewTime ?: 0L

        val calendar = Calendar.getInstance().apply { timeInMillis = now }

        val f1 = clamp((3.0f - ef) / 1.7f)
        val f2 = clamp(calendar.get(Calendar.HOUR_OF_DAY) / 23.0f)
        // Calendar.DAY_OF_WEEK: 1=Sunday ... 7=Saturday → 归一化到 [0, 1]
        val f3 = clamp((calendar.get(Calendar.DAY_OF_WEEK) - 1) / 6.0f)
        val f4 = clamp(
            if (sessionTotal > 0) sessionPosition.toFloat() / sessionTotal.toFloat()
            else 0f
        )
        val f5 = clamp(
            (ln((intervalDays + 1).toDouble()) / ln(365.0)).toFloat()
        )
        val f6 = clamp((ef - 1.3f) / 1.7f)
        val f7 = if (reviewCount > 0) {
            clamp((reviewCount - spellWrongCount).toFloat() / reviewCount.toFloat())
        } else {
            0.5f
        }
        val f8 = clamp(avgResponseTimeMs / 10000.0f)
        val f9 = sigmoid(consecutiveCorrect.toFloat(), 5.0f)
        val f10 = if (lastReviewTime > 0L) {
            val hoursSince = ((now - lastReviewTime).coerceAtLeast(0L)) / 3_600_000.0
            clamp((ln(hoursSince + 1.0) / ln(720.0)).toFloat())
        } else {
            1.0f // 从未复习，视为最大间隔
        }
        val f11 = clamp(modelState?.userBaseRetention ?: 0.85f)
        val f12 = 0f // 词书切换次数，简化为0

        return FeatureVector(
            floatArrayOf(f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12)
        )
    }

    private fun clamp(value: Float, min: Float = 0f, max: Float = 1f): Float {
        return value.coerceIn(min, max)
    }

    /**
     * sigmoid 函数：将值映射到 (0, 1)，midpoint 控制中心位置
     */
    fun sigmoid(x: Float, midpoint: Float): Float {
        val exponent = -(x - midpoint)
        return (1.0 / (1.0 + exp(exponent.toDouble()))).toFloat()
    }
}
