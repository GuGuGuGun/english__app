package com.kaoyan.wordhelper.ml.training

import com.kaoyan.wordhelper.data.dao.TrainingSampleDao
import com.kaoyan.wordhelper.data.entity.MLModelState
import com.kaoyan.wordhelper.data.entity.TrainingSample
import com.kaoyan.wordhelper.ml.core.PersonalRetentionPredictor
import com.kaoyan.wordhelper.ml.features.FeatureVector
import kotlin.math.sqrt

/**
 * 在线训练器：
 * - 每次复习完成即时单步更新
 * - 每10个样本 mini-batch 修正
 * - 管理训练样本的存储和清理
 */
class OnlineTrainer(
    private val predictor: PersonalRetentionPredictor,
    private val trainingSampleDao: TrainingSampleDao,
    private val modelPersistence: ModelPersistence
) {
    private var pendingSamplesCount = 0

    /**
     * 复习完成后调用：记录样本 + 即时单步更新
     *
     * @param wordId 单词ID
     * @param features 特征向量
     * @param isCorrect true=记住，false=遗忘
     * @return 预测误差
     */
    suspend fun onReviewCompleted(
        wordId: Long,
        features: FeatureVector,
        isCorrect: Boolean
    ): Float {
        val label = if (isCorrect) 0 else 1 // 0=记住，1=遗忘

        // 即时单步更新
        val error = predictor.updateOnline(features, label)

        // 记录训练样本
        val sample = TrainingSample(
            wordId = wordId,
            featuresJson = features.toJson(),
            outcome = label,
            timestamp = System.currentTimeMillis(),
            predictionError = error
        )
        trainingSampleDao.insert(sample)
        var totalCount = trainingSampleDao.count()
        if (totalCount > MAX_SAMPLES) {
            trainingSampleDao.trimOldSamples(MAX_SAMPLES)
            totalCount = MAX_SAMPLES
        }
        modelPersistence.updateSampleCount(totalCount)
        refreshLearningMetrics(totalCount = totalCount, now = sample.timestamp)

        pendingSamplesCount++

        // 每10个样本做一次mini-batch修正
        if (pendingSamplesCount >= MINI_BATCH_SIZE) {
            performMiniBatchCorrection()
            pendingSamplesCount = 0
        }

        // 定期持久化模型
        if (predictor.sampleCount % PERSIST_INTERVAL == 0) {
            val modelState = modelPersistence.getModelState()
            modelPersistence.save(predictor, modelState)
        }

        return error
    }

    /**
     * Mini-batch修正：重放最近的样本修正偏差
     */
    private suspend fun performMiniBatchCorrection() {
        val recentSamples = trainingSampleDao.getRecent(MINI_BATCH_SIZE)
        if (recentSamples.size < MINI_BATCH_SIZE) return

        recentSamples.forEach { sample ->
            val features = FeatureVector.fromJson(sample.featuresJson)
            predictor.updateOnline(features, sample.outcome)
        }
    }

    /**
     * 批量回放训练（日切时调用）
     */
    suspend fun performDailyReplay() {
        val sampleCount = trainingSampleDao.count()
        if (sampleCount < ColdStartManager.COLD_START_THRESHOLD) return

        val batchSize = 50
        var offset = 0
        var processedCount = 0
        val maxProcess = 500 // 限制单次回放数量

        while (processedCount < maxProcess) {
            val batch = trainingSampleDao.getRecentBatch(batchSize, offset)
            if (batch.isEmpty()) break

            batch.forEach { sample ->
                val features = FeatureVector.fromJson(sample.featuresJson)
                predictor.updateOnline(features, sample.outcome)
            }

            offset += batchSize
            processedCount += batch.size
        }

        // 持久化更新后的模型
        val modelState = modelPersistence.getModelState()
        modelPersistence.save(predictor, modelState)
    }

    /**
     * 更新全局响应时间统计
     */
    suspend fun updateResponseTimeStats(responseTimes: List<Float>) {
        if (responseTimes.isEmpty()) return
        val avg = responseTimes.average().toFloat()
        val variance = responseTimes.map { (it - avg) * (it - avg) }.average().toFloat()
        val std = sqrt(variance)
        modelPersistence.updateResponseTimeStats(avg, std)
    }

    companion object {
        private const val MINI_BATCH_SIZE = 10
        private const val PERSIST_INTERVAL = 10
        private const val MAX_SAMPLES = 5000
        private const val RETENTION_BASELINE = 0.85f
        private const val METRICS_WINDOW_SIZE = 200
        private const val METRICS_UPDATE_INTERVAL = 5
        private const val METRICS_BOOTSTRAP_COUNT = 20
    }

    private suspend fun refreshLearningMetrics(totalCount: Int, now: Long) {
        val shouldRefresh = totalCount <= METRICS_BOOTSTRAP_COUNT || totalCount % METRICS_UPDATE_INTERVAL == 0
        if (!shouldRefresh) return

        val samples = trainingSampleDao.getRecent(METRICS_WINDOW_SIZE)
        if (samples.isEmpty()) return

        val remembered = samples.count { it.outcome == 0 }
        val retentionRaw = remembered.toFloat() / samples.size.toFloat()
        val blendWeight = (samples.size.toFloat() / ColdStartManager.COLD_START_THRESHOLD.toFloat())
            .coerceIn(0f, 1f)
        val retention = RETENTION_BASELINE * (1f - blendWeight) + retentionRaw * blendWeight

        val avgPredictionError = samples.map { it.predictionError }.average().toFloat()
        val accuracy = (1f - avgPredictionError).coerceIn(0f, 1f)

        modelPersistence.updateLearningMetrics(
            retention = retention,
            accuracy = accuracy,
            time = now
        )
    }
}
