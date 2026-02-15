package com.kaoyan.wordhelper.data.repository

import androidx.annotation.VisibleForTesting
import com.kaoyan.wordhelper.BuildConfig
import com.kaoyan.wordhelper.data.dao.AICacheDao
import com.kaoyan.wordhelper.data.database.AppDatabase
import com.kaoyan.wordhelper.data.entity.AICache
import com.kaoyan.wordhelper.data.model.AIConfig
import com.kaoyan.wordhelper.data.model.AIContentType
import com.kaoyan.wordhelper.data.model.AIPresets
import com.kaoyan.wordhelper.data.network.AIService
import com.kaoyan.wordhelper.data.network.ChatMessage
import com.kaoyan.wordhelper.data.network.ChatRequest
import com.kaoyan.wordhelper.util.AIContentFormatter
import com.kaoyan.wordhelper.util.PromptTemplates
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class AIRepository internal constructor(
    private val cacheDao: AICacheDao,
    private val loadConfig: suspend () -> AIConfig,
    private val serviceFactory: (String) -> AIService,
    @VisibleForTesting internal val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val serviceCache = ConcurrentHashMap<String, AIService>()

    constructor(database: AppDatabase, configRepository: AIConfigRepository) : this(
        cacheDao = database.aiCacheDao(),
        loadConfig = { configRepository.getConfig() },
        serviceFactory = { baseUrl -> buildDefaultService(baseUrl) }
    )

    suspend fun generateExample(
        wordId: Long,
        word: String,
        forceRefresh: Boolean = false
    ): Result<String> {
        return getAIContent(
            wordId = wordId,
            queryContent = word.trim(),
            type = AIContentType.EXAMPLE,
            forceRefresh = forceRefresh
        )
    }

    suspend fun generateMemoryAid(
        wordId: Long,
        word: String,
        forceRefresh: Boolean = false
    ): Result<String> {
        return getAIContent(
            wordId = wordId,
            queryContent = word.trim(),
            type = AIContentType.MEMORY_AID,
            forceRefresh = forceRefresh
        )
    }

    suspend fun analyzeSentence(
        sentence: String,
        forceRefresh: Boolean = false
    ): Result<String> {
        return getAIContent(
            wordId = null,
            queryContent = sentence.trim(),
            type = AIContentType.SENTENCE,
            forceRefresh = forceRefresh
        )
    }

    suspend fun getCachedExample(wordId: Long, word: String): String? {
        return getCachedContent(
            wordId = wordId,
            queryContent = word.trim(),
            type = AIContentType.EXAMPLE
        )
    }

    suspend fun getCachedMemoryAid(wordId: Long, word: String): String? {
        return getCachedContent(
            wordId = wordId,
            queryContent = word.trim(),
            type = AIContentType.MEMORY_AID
        )
    }

    suspend fun getAIContent(
        wordId: Long?,
        queryContent: String,
        type: AIContentType,
        forceRefresh: Boolean = false
    ): Result<String> {
        val normalizedQuery = queryContent.trim()
        if (normalizedQuery.isBlank()) {
            return Result.failure(IllegalArgumentException("输入内容不能为空"))
        }

        return try {
            if (!forceRefresh) {
                cacheDao.getByQueryAndType(normalizedQuery, type.name)?.let {
                    return Result.success(it.aiContent)
                }
                if (wordId != null) {
                    cacheDao.getByWordIdAndType(wordId, type.name)?.let {
                        return Result.success(it.aiContent)
                    }
                }
            }

            val config = normalizeConfig(loadConfig())
            validateConfig(config, requireEnabled = true)?.let { reason ->
                return Result.failure(IllegalStateException(reason))
            }

            val content = requestContent(
                config = config,
                type = type,
                prompt = PromptTemplates.build(type, normalizedQuery)
            )
            val normalizedContent = AIContentFormatter.normalize(type, content)
            cacheDao.insert(
                AICache(
                    wordId = wordId,
                    queryContent = normalizedQuery,
                    type = type.name,
                    aiContent = normalizedContent,
                    modelName = config.modelName.trim(),
                    createdAt = nowProvider()
                )
            )
            Result.success(normalizedContent)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            Result.failure(Exception(mapErrorMessage(throwable), throwable))
        }
    }

    private suspend fun getCachedContent(
        wordId: Long?,
        queryContent: String,
        type: AIContentType
    ): String? {
        val normalizedQuery = queryContent.trim()
        if (normalizedQuery.isBlank()) return null
        cacheDao.getByQueryAndType(normalizedQuery, type.name)?.let {
            return it.aiContent
        }
        if (wordId != null) {
            cacheDao.getByWordIdAndType(wordId, type.name)?.let {
                return it.aiContent
            }
        }
        return null
    }

    suspend fun testConnection(configOverride: AIConfig? = null): Result<String> {
        return try {
            val config = normalizeConfig(configOverride ?: loadConfig())
            validateConfig(config, requireEnabled = false)?.let { reason ->
                return Result.failure(IllegalStateException(reason))
            }
            val probe = requestContent(
                config = config,
                prompt = "请回复“连接成功”，不需要额外说明。"
            )
            Result.success(probe)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            Result.failure(Exception(mapErrorMessage(throwable), throwable))
        }
    }

    private suspend fun requestContent(
        config: AIConfig,
        prompt: String,
        type: AIContentType? = null
    ): String {
        val service = getService(config.apiBaseUrl)
        var lastFailure: Throwable? = null
        val messages = buildMessages(type, prompt)

        repeat(MAX_REQUEST_ATTEMPTS) { attempt ->
            try {
                val response = service.chat(
                    auth = "Bearer ${config.apiKey.trim()}",
                    request = ChatRequest(
                        model = config.modelName.trim(),
                        messages = messages,
                        temperature = if (type != null) 0f else null
                    )
                )

                val content = response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
                if (content.isBlank()) {
                    val reason = response.error?.message?.takeIf { it.isNotBlank() } ?: "AI 返回内容为空"
                    throw IllegalStateException(reason)
                }
                return content
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                lastFailure = throwable
                val hasNextAttempt = attempt < MAX_REQUEST_ATTEMPTS - 1
                if (!hasNextAttempt || !shouldRetry(throwable)) {
                    throw throwable
                }
            }
        }

        throw lastFailure ?: IllegalStateException("请求失败，请稍后重试")
    }

    private fun buildMessages(type: AIContentType?, prompt: String): List<ChatMessage> {
        if (type == null) {
            return listOf(ChatMessage(role = "user", content = prompt))
        }
        return listOf(
            ChatMessage(role = "system", content = PromptTemplates.systemInstruction(type)),
            ChatMessage(role = "user", content = prompt)
        )
    }

    private fun shouldRetry(throwable: Throwable): Boolean {
        return when (throwable) {
            is SocketTimeoutException, is UnknownHostException, is ConnectException -> true
            is HttpException -> throwable.code() == 429 || throwable.code() in 500..599
            else -> false
        }
    }

    private fun getService(baseUrl: String): AIService {
        val normalizedBaseUrl = AIPresets.normalizeBaseUrl(baseUrl)
        return serviceCache.getOrPut(normalizedBaseUrl) {
            serviceFactory(normalizedBaseUrl)
        }
    }

    private fun normalizeConfig(config: AIConfig): AIConfig {
        return config.copy(
            apiBaseUrl = AIPresets.normalizeBaseUrl(config.apiBaseUrl),
            modelName = config.modelName.trim(),
            apiKey = config.apiKey.trim()
        )
    }

    private fun validateConfig(config: AIConfig, requireEnabled: Boolean): String? {
        if (requireEnabled && !config.enabled) return "AI 未启用"
        if (config.apiBaseUrl.isBlank()) return "请填写 Base URL"
        if (config.modelName.isBlank()) return "请填写模型名称"
        if (config.apiKey.isBlank()) return "请填写 API Key"
        return null
    }

    private fun mapErrorMessage(throwable: Throwable): String {
        return when (throwable) {
            is SocketTimeoutException, is InterruptedIOException -> "网络超时，请稍后重试"
            is UnknownHostException -> "网络不可用，请检查网络连接"
            is ConnectException -> "连接失败，请检查 Base URL"
            is HttpException -> {
                when (throwable.code()) {
                    400 -> "请求参数错误，请检查模型和输入内容"
                    401 -> "API Key 无效或已过期"
                    402 -> "账户额度不足"
                    403 -> "请求被拒绝，请检查服务商权限设置"
                    404 -> "接口地址或模型不存在，请检查 Base URL 与模型名"
                    429 -> "请求过于频繁或额度受限，请稍后重试"
                    in 500..599 -> "AI 服务暂时不可用，请稍后重试"
                    else -> throwable.message()
                }
            }
            else -> throwable.message ?: "请求失败，请稍后重试"
        }
    }

    companion object {
        private const val MAX_REQUEST_ATTEMPTS = 2

        private fun buildDefaultService(baseUrl: String): AIService {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(AIService::class.java)
        }
    }
}
