package com.kaoyan.wordhelper.ml.training

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.kaoyan.wordhelper.data.dao.MLModelStateDao
import com.kaoyan.wordhelper.data.entity.MLModelState
import com.kaoyan.wordhelper.ml.core.PersonalRetentionPredictor
import kotlinx.coroutines.flow.Flow

/**
 * 模型参数持久化：JSON序列化/反序列化 + Room DB存储
 */
class ModelPersistence(
    private val mlModelStateDao: MLModelStateDao
) {

    suspend fun save(predictor: PersonalRetentionPredictor, modelState: MLModelState?) {
        val state = (modelState ?: MLModelState()).copy(
            nParamsJson = floatArrayToJson(predictor.n),
            zParamsJson = floatArrayToJson(predictor.z),
            weightsJson = floatArrayToJson(predictor.weights),
            version = predictor.version,
            sampleCount = predictor.sampleCount,
            lastTrainingTime = System.currentTimeMillis()
        )
        mlModelStateDao.upsert(state)
    }

    suspend fun load(predictor: PersonalRetentionPredictor): MLModelState? {
        val state = mlModelStateDao.get() ?: return null
        if (state.nParamsJson.isBlank()) return state

        val savedN = jsonToFloatArray(state.nParamsJson)
        val savedZ = jsonToFloatArray(state.zParamsJson)
        val savedWeights = jsonToFloatArray(state.weightsJson)

        if (savedN.size == 12 && savedZ.size == 12 && savedWeights.size == 12) {
            predictor.restore(
                savedN = savedN,
                savedZ = savedZ,
                savedWeights = savedWeights,
                savedVersion = state.version,
                savedSampleCount = state.sampleCount
            )
        }
        return state
    }

    suspend fun getModelState(): MLModelState? = mlModelStateDao.get()

    fun observeModelState(): Flow<MLModelState?> = mlModelStateDao.observe()

    suspend fun updateSampleCount(sampleCount: Int, time: Long = System.currentTimeMillis()) {
        ensureInitialized()
        mlModelStateDao.updateSampleCount(sampleCount = sampleCount, time = time)
    }

    suspend fun updateResponseTimeStats(avgTime: Float, stdTime: Float) {
        mlModelStateDao.updateResponseTimeStats(avgTime, stdTime)
    }

    suspend fun updateLearningMetrics(
        retention: Float,
        accuracy: Float,
        time: Long = System.currentTimeMillis()
    ) {
        ensureInitialized()
        mlModelStateDao.updateLearningMetrics(
            retention = retention.coerceIn(0.05f, 0.99f),
            accuracy = accuracy.coerceIn(0f, 1f),
            time = time
        )
    }

    suspend fun ensureInitialized(): MLModelState {
        val existing = mlModelStateDao.get()
        if (existing != null) return existing
        val initial = MLModelState()
        mlModelStateDao.upsert(initial)
        return initial
    }

    companion object {
        fun floatArrayToJson(array: FloatArray): String {
            val jsonArray = JsonArray(array.size)
            array.forEach { jsonArray.add(it.toDouble()) }
            return jsonArray.toString()
        }

        fun jsonToFloatArray(json: String): FloatArray {
            if (json.isBlank()) return FloatArray(0)
            val jsonArray = JsonParser.parseString(json).asJsonArray
            return FloatArray(jsonArray.size()) { index -> jsonArray[index].asFloat }
        }

        /**
         * 从 assets 加载人群先验权重
         */
        fun loadPopulationPrior(context: Context): FloatArray {
            return try {
                val jsonStr = context.assets
                    .open("ml/population_prior.json")
                    .bufferedReader()
                    .use { it.readText() }
                val jsonArray = JsonParser.parseString(jsonStr).asJsonArray
                FloatArray(jsonArray.size()) { index -> jsonArray[index].asFloat }
            } catch (e: Exception) {
                // 加载失败时返回默认先验
                defaultPopulationPrior()
            }
        }

        fun defaultPopulationPrior(): FloatArray {
            // 12维默认先验权重（基于记忆研究经验值）
            return floatArrayOf(
                0.8f,    // f1: 难度↑ → 遗忘概率↑
                -0.05f,  // f2: 时间（影响较小）
                0.02f,   // f3: 星期（影响较小）
                0.3f,    // f4: 疲劳↑ → 遗忘概率↑
                -0.6f,   // f5: 间隔长 → 基线遗忘概率↓（经过长间隔仍记住说明记忆强）
                -0.5f,   // f6: EF高 → 遗忘概率↓
                -0.7f,   // f7: 正确率高 → 遗忘概率↓
                0.2f,    // f8: 响应时间长 → 遗忘概率↑
                -0.4f,   // f9: 连续正确多 → 遗忘概率↓
                0.5f,    // f10: 距上次复习久 → 遗忘概率↑
                -0.3f,   // f11: 全局记忆稳定 → 遗忘概率↓
                0.0f     // f12: 词书切换（暂无影响）
            )
        }
    }
}
