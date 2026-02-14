package com.kaoyan.wordhelper.data.model

data class AIProviderPreset(
    val name: String,
    val baseUrl: String
)

object AIPresets {
    val OPENAI = AIProviderPreset(
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1/"
    )
    val DEEPSEEK = AIProviderPreset(
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1/"
    )
    val ZHIPU = AIProviderPreset(
        name = "智谱AI",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4/"
    )

    val presets: List<AIProviderPreset> = listOf(OPENAI, DEEPSEEK, ZHIPU)

    const val CUSTOM_NAME = "自定义"

    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    fun inferProviderName(baseUrl: String): String {
        val normalized = normalizeBaseUrl(baseUrl)
        return presets.firstOrNull { normalizeBaseUrl(it.baseUrl) == normalized }?.name ?: CUSTOM_NAME
    }
}
