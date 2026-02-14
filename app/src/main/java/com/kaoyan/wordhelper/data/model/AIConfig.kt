package com.kaoyan.wordhelper.data.model

data class AIConfig(
    val enabled: Boolean = false,
    val apiBaseUrl: String = AIPresets.OPENAI.baseUrl,
    val apiKey: String = "",
    val modelName: String = DEFAULT_MODEL_NAME
) {
    fun isConfigured(): Boolean {
        return apiBaseUrl.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()
    }

    companion object {
        const val DEFAULT_MODEL_NAME = "gpt-3.5-turbo"
    }
}
