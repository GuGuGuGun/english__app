package com.kaoyan.wordhelper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaoyan.wordhelper.KaoyanWordApp
import com.kaoyan.wordhelper.data.model.AIConfig
import com.kaoyan.wordhelper.data.model.AIPresets
import com.kaoyan.wordhelper.data.model.AIProviderPreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AILabUiState(
    val enabled: Boolean = false,
    val selectedProviderName: String = AIPresets.OPENAI.name,
    val baseUrl: String = AIPresets.OPENAI.baseUrl,
    val modelName: String = AIConfig.DEFAULT_MODEL_NAME,
    val apiKey: String = "",
    val algorithmV4Enabled: Boolean = false,
    val pronunciationEnabled: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false
)

class AILabViewModel(application: Application) : AndroidViewModel(application) {
    private val aiConfigRepository = (application as KaoyanWordApp).aiConfigRepository
    private val aiRepository = (application as KaoyanWordApp).aiRepository
    private val settingsRepository = (application as KaoyanWordApp).settingsRepository
    private val wordRepository = (application as KaoyanWordApp).repository

    val presets: List<AIProviderPreset> = AIPresets.presets

    private val _uiState = MutableStateFlow(AILabUiState())
    val uiState: StateFlow<AILabUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        loadConfig()
    }

    fun loadConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val config = aiConfigRepository.getConfig()
            val settings = settingsRepository.settingsFlow.first()
            val normalizedBaseUrl = AIPresets.normalizeBaseUrl(config.apiBaseUrl)
            _uiState.value = AILabUiState(
                enabled = config.enabled,
                selectedProviderName = AIPresets.inferProviderName(normalizedBaseUrl),
                baseUrl = normalizedBaseUrl.ifBlank { AIPresets.OPENAI.baseUrl },
                modelName = config.modelName,
                apiKey = config.apiKey,
                algorithmV4Enabled = settings.algorithmV4Enabled,
                pronunciationEnabled = settings.pronunciationEnabled,
                isLoading = false
            )
        }
    }

    fun updateAlgorithmV4Enabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAlgorithmV4Enabled(enabled)
            if (enabled) {
                wordRepository.repairMasteredStatusForV4()
            }
            _uiState.update { it.copy(algorithmV4Enabled = enabled) }
        }
    }

    fun updatePronunciationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updatePronunciationEnabled(enabled)
            _uiState.update { it.copy(pronunciationEnabled = enabled) }
        }
    }

    fun updateEnabled(enabled: Boolean) {
        _uiState.update { it.copy(enabled = enabled) }
    }

    fun selectPreset(preset: AIProviderPreset) {
        _uiState.update {
            it.copy(
                selectedProviderName = preset.name,
                baseUrl = preset.baseUrl
            )
        }
    }

    fun markCustomPreset() {
        _uiState.update { it.copy(selectedProviderName = AIPresets.CUSTOM_NAME) }
    }

    fun updateBaseUrl(baseUrl: String) {
        _uiState.update {
            it.copy(
                baseUrl = baseUrl,
                selectedProviderName = AIPresets.inferProviderName(baseUrl)
            )
        }
    }

    fun updateModelName(modelName: String) {
        _uiState.update { it.copy(modelName = modelName) }
    }

    fun updateApiKey(apiKey: String) {
        _uiState.update { it.copy(apiKey = apiKey) }
    }

    fun saveConfig() {
        val snapshot = _uiState.value
        if (snapshot.isLoading || snapshot.isSaving || snapshot.isTesting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val config = snapshot.toConfig()
            val result = runCatching {
                aiConfigRepository.saveConfig(config)
            }
            _uiState.update { state ->
                state.copy(
                    isSaving = false,
                    selectedProviderName = AIPresets.inferProviderName(state.baseUrl)
                )
            }
            _message.value = if (result.isSuccess) {
                "AI 配置已保存"
            } else {
                "保存失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
            }
        }
    }

    fun testConnection() {
        val snapshot = _uiState.value
        if (snapshot.isLoading || snapshot.isSaving || snapshot.isTesting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true) }
            val result = aiRepository.testConnection(snapshot.toConfig())
            _uiState.update { it.copy(isTesting = false) }
            _message.value = if (result.isSuccess) {
                "连接成功"
            } else {
                "连接失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun AILabUiState.toConfig(): AIConfig {
        return AIConfig(
            enabled = enabled,
            apiBaseUrl = baseUrl,
            apiKey = apiKey,
            modelName = modelName
        )
    }
}
