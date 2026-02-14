package com.kaoyan.wordhelper.data.repository

import android.content.Context
import com.kaoyan.wordhelper.data.model.AIConfig
import com.kaoyan.wordhelper.util.AISecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AIConfigRepository(private val context: Context) {

    suspend fun getConfig(): AIConfig = withContext(Dispatchers.IO) {
        AISecureStorage.getConfig(context)
    }

    suspend fun saveConfig(config: AIConfig) = withContext(Dispatchers.IO) {
        AISecureStorage.saveConfig(context, config)
    }

    suspend fun updateEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        AISecureStorage.updateEnabled(context, enabled)
    }
}
