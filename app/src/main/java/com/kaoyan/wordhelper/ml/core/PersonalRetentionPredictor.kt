package com.kaoyan.wordhelper.ml.core

import com.kaoyan.wordhelper.ml.features.FeatureVector
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * FTRL-Proximal 在线学习模型
 * 输入12维特征向量，输出遗忘概率 [0, 1]
 */
class PersonalRetentionPredictor(
    private val alpha: Float = 0.1f,
    private val beta: Float = 1.0f,
    private val lambda1: Float = 0.001f,
    private val lambda2: Float = 0.01f
) {
    private val dim = FeatureVector.DIMENSION

    // FTRL 内部参数
    var n: FloatArray = FloatArray(dim)
        private set
    var z: FloatArray = FloatArray(dim)
        private set
    var weights: FloatArray = FloatArray(dim)
        private set

    var version: Int = 0
        private set
    var sampleCount: Int = 0
        private set

    /**
     * 预测遗忘概率 P(forget | features)
     * 返回值在 [0, 1]，越高越可能遗忘
     */
    fun predictForgetProbability(features: FeatureVector): Float {
        computeWeights()
        val logit = dotProduct(weights, features.values)
        return sigmoid(logit)
    }

    /**
     * 在线单步更新
     * @param features 特征向量
     * @param label 1=遗忘（AGAIN/FAILED），0=记住（HARD/GOOD/PERFECT）
     * @return 预测误差（用于记录）
     */
    fun updateOnline(features: FeatureVector, label: Int): Float {
        computeWeights()
        val prediction = sigmoid(dotProduct(weights, features.values))
        val error = prediction - label.toFloat()

        for (i in 0 until dim) {
            val gradient = error * features[i]
            val sigma = (sqrt(n[i] + gradient * gradient) - sqrt(n[i])) / alpha
            z[i] += gradient - sigma * weights[i]
            n[i] += gradient * gradient
        }

        sampleCount++
        version++
        return abs(error)
    }

    /**
     * 根据 FTRL 公式从 z 和 n 计算当前权重
     */
    private fun computeWeights() {
        for (i in 0 until dim) {
            if (abs(z[i]) <= lambda1) {
                weights[i] = 0f
            } else {
                val sign = if (z[i] >= 0f) 1f else -1f
                val lr = -1f / ((beta + sqrt(n[i])) / alpha + lambda2)
                weights[i] = lr * (z[i] - sign * lambda1)
            }
        }
    }

    /**
     * 从已保存的参数恢复模型
     */
    fun restore(
        savedN: FloatArray,
        savedZ: FloatArray,
        savedWeights: FloatArray,
        savedVersion: Int,
        savedSampleCount: Int
    ) {
        require(savedN.size == dim && savedZ.size == dim && savedWeights.size == dim) {
            "参数维度不匹配"
        }
        savedN.copyInto(n)
        savedZ.copyInto(z)
        savedWeights.copyInto(weights)
        version = savedVersion
        sampleCount = savedSampleCount
    }

    /**
     * 从人群先验权重初始化
     */
    fun initFromPrior(priorWeights: FloatArray) {
        require(priorWeights.size == dim) { "先验权重维度不匹配" }
        priorWeights.copyInto(weights)
        // 将先验权重转为 z 参数，使 FTRL 以先验为起点
        for (i in 0 until dim) {
            z[i] = -weights[i] * (beta / alpha + lambda2)
        }
    }

    /**
     * 重置模型到初始状态
     */
    fun reset() {
        n = FloatArray(dim)
        z = FloatArray(dim)
        weights = FloatArray(dim)
        version = 0
        sampleCount = 0
    }

    companion object {
        private fun sigmoid(x: Float): Float {
            val clipped = x.coerceIn(-20f, 20f)
            return (1.0 / (1.0 + exp(-clipped.toDouble()))).toFloat()
        }

        private fun dotProduct(a: FloatArray, b: FloatArray): Float {
            var sum = 0f
            for (i in a.indices) {
                sum += a[i] * b[i]
            }
            return sum
        }
    }
}
