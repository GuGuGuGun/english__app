package com.kaoyan.wordhelper.ml.features

/**
 * 12维特征向量，用于 FTRL 在线学习模型
 */
data class FeatureVector(
    val values: FloatArray
) {
    init {
        require(values.size == DIMENSION) { "特征向量维度必须为 $DIMENSION，实际为 ${values.size}" }
    }

    operator fun get(index: Int): Float = values[index]

    fun toJson(): String {
        return values.joinToString(",", prefix = "[", postfix = "]")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FeatureVector) return false
        return values.contentEquals(other.values)
    }

    override fun hashCode(): Int = values.contentHashCode()

    companion object {
        const val DIMENSION = 12

        fun fromJson(json: String): FeatureVector {
            val cleaned = json.trim().removePrefix("[").removeSuffix("]")
            val values = cleaned.split(",").map { it.trim().toFloat() }.toFloatArray()
            return FeatureVector(values)
        }

        fun zeros(): FeatureVector = FeatureVector(FloatArray(DIMENSION))
    }
}
