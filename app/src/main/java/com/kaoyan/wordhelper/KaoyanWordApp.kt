package com.kaoyan.wordhelper

import android.app.Application
import com.kaoyan.wordhelper.data.database.AppDatabase
import com.kaoyan.wordhelper.data.repository.AIConfigRepository
import com.kaoyan.wordhelper.data.repository.AIRepository
import com.kaoyan.wordhelper.data.repository.ForecastRepository
import com.kaoyan.wordhelper.data.repository.PronunciationRepository
import com.kaoyan.wordhelper.data.repository.SettingsRepository
import com.kaoyan.wordhelper.data.repository.WordRepository
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

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val algorithmV4Enabled = settingsRepository.settingsFlow.first().algorithmV4Enabled
            if (algorithmV4Enabled) {
                repository.repairMasteredStatusForV4()
            }
            val presetSeed = WordbookFullLoader.loadPreset(this@KaoyanWordApp).getOrNull()
            if (presetSeed != null) {
                repository.ensurePresetBooks(listOf(presetSeed))
            }
        }
    }
}
