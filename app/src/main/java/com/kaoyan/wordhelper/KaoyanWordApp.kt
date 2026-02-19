package com.kaoyan.wordhelper

import android.app.Application
import com.kaoyan.wordhelper.data.database.AppDatabase
import com.kaoyan.wordhelper.data.repository.AIConfigRepository
import com.kaoyan.wordhelper.data.repository.AIRepository
import com.kaoyan.wordhelper.data.repository.ForecastRepository
import com.kaoyan.wordhelper.data.repository.PronunciationRepository
import com.kaoyan.wordhelper.data.repository.SettingsRepository
import com.kaoyan.wordhelper.data.repository.WordRepository
import com.kaoyan.wordhelper.ml.core.PersonalRetentionPredictor
import com.kaoyan.wordhelper.ml.integration.MLEnhancedScheduler
import com.kaoyan.wordhelper.ml.training.ColdStartManager
import com.kaoyan.wordhelper.ml.training.ModelPersistence
import com.kaoyan.wordhelper.util.WordbookFullLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class KaoyanWordApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val repository: WordRepository by lazy {
        WordRepository(database)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }

    val forecastRepository: ForecastRepository by lazy {
        ForecastRepository(database, settingsRepository)
    }

    val aiConfigRepository: AIConfigRepository by lazy {
        AIConfigRepository(this)
    }

    val aiRepository: AIRepository by lazy {
        AIRepository(database, aiConfigRepository)
    }

    val pronunciationRepository: PronunciationRepository by lazy {
        val pronunciationIndex = WordbookFullLoader
            .loadPronunciationIndex(this)
            .getOrDefault(emptyMap())
        PronunciationRepository(wordbookPronunciations = pronunciationIndex)
    }

    val mlPredictor: PersonalRetentionPredictor by lazy {
        PersonalRetentionPredictor()
    }

    val mlModelPersistence: ModelPersistence by lazy {
        ModelPersistence(database.mlModelStateDao())
    }

    val mlColdStartManager: ColdStartManager by lazy {
        val prior = ModelPersistence.loadPopulationPrior(this)
        ColdStartManager(prior)
    }

    val mlScheduler: MLEnhancedScheduler by lazy {
        MLEnhancedScheduler(mlPredictor, mlColdStartManager)
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.algorithmV4Enabled) {
                repository.repairMasteredStatusForV4()
            }
            val presetSeeds = WordbookFullLoader.loadPresets(this@KaoyanWordApp).getOrNull()
            if (!presetSeeds.isNullOrEmpty()) {
                repository.ensurePresetBooks(presetSeeds)
            }
            // ML模型初始化
            if (settings.mlAdaptiveEnabled) {
                initializeMLEngine()
            }
        }
    }

    private suspend fun initializeMLEngine() {
        mlModelPersistence.ensureInitialized()
        val modelState = mlModelPersistence.load(mlPredictor)
        mlScheduler.setModelState(modelState)
        if (mlPredictor.sampleCount == 0) {
            mlColdStartManager.initializePredictor(mlPredictor)
        }
    }

    suspend fun ensureMLEngineInitialized() {
        initializeMLEngine()
    }
}
