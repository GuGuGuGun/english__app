package com.kaoyan.wordhelper.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kaoyan.wordhelper.data.model.AIConfig
import com.kaoyan.wordhelper.data.model.AIPresets

object AISecureStorage {
    private const val PREFS_FILE_NAME = "ai_secure_prefs"
    private const val KEY_AI_ENABLED = "ai_enabled"
    private const val KEY_API_BASE_URL = "api_base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL_NAME = "model_name"

    fun getConfig(context: Context): AIConfig {
        val prefs = getEncryptedPrefs(context)
        return AIConfig(
            enabled = prefs.getBoolean(KEY_AI_ENABLED, false),
            apiBaseUrl = prefs.getString(KEY_API_BASE_URL, AIPresets.OPENAI.baseUrl)
                ?: AIPresets.OPENAI.baseUrl,
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            modelName = prefs.getString(KEY_MODEL_NAME, AIConfig.DEFAULT_MODEL_NAME)
                ?: AIConfig.DEFAULT_MODEL_NAME
        )
    }

    fun saveConfig(context: Context, config: AIConfig) {
        val normalizedBaseUrl = AIPresets.normalizeBaseUrl(config.apiBaseUrl)
        getEncryptedPrefs(context).edit()
            .putBoolean(KEY_AI_ENABLED, config.enabled)
            .putString(KEY_API_BASE_URL, normalizedBaseUrl)
            .putString(KEY_API_KEY, config.apiKey.trim())
            .putString(KEY_MODEL_NAME, config.modelName.trim())
            .apply()
    }

    fun updateEnabled(context: Context, enabled: Boolean) {
        getEncryptedPrefs(context).edit()
            .putBoolean(KEY_AI_ENABLED, enabled)
            .apply()
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
