package com.kaoyan.wordhelper.data.repository

import com.kaoyan.wordhelper.data.dao.AICacheDao
import com.kaoyan.wordhelper.data.entity.AICache
import com.kaoyan.wordhelper.data.model.AIConfig
import com.kaoyan.wordhelper.data.model.AIPresets
import com.kaoyan.wordhelper.data.network.AIService
import com.kaoyan.wordhelper.data.network.ChatChoice
import com.kaoyan.wordhelper.data.network.ChatMessage
import com.kaoyan.wordhelper.data.network.ChatRequest
import com.kaoyan.wordhelper.data.network.ChatResponse
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AIRepositoryTest {

    @Test
    fun getAIContent_prefersCacheAndSkipsNetwork() = runBlocking {
        val cacheDao = InMemoryAICacheDao()
        cacheDao.insert(
            AICache(
                wordId = 1L,
                queryContent = "abandon",
                type = "EXAMPLE",
                aiContent = "cached-content",
                modelName = "test-model",
                createdAt = 1L
            )
        )
        val fakeService = FakeAIService {
            ChatResponse(
                choices = listOf(
                    ChatChoice(message = ChatMessage(role = "assistant", content = "network-content"))
                )
            )
        }
        val repository = AIRepository(
            cacheDao = cacheDao,
            loadConfig = { enabledConfig() },
            serviceFactory = { fakeService },
            nowProvider = { 10L }
        )

        val result = repository.generateExample(wordId = 1L, word = "abandon")

        assertTrue(result.isSuccess)
        assertEquals("cached-content", result.getOrNull())
        assertEquals(0, fakeService.callCount)
    }

    @Test
    fun getAIContent_retriesOnceOnTimeoutAndSucceeds() = runBlocking {
        val cacheDao = InMemoryAICacheDao()
        val fakeService = FakeAIService {
            if (it.callCount == 1) {
                throw SocketTimeoutException("timeout")
            }
            ChatResponse(
                choices = listOf(
                    ChatChoice(message = ChatMessage(role = "assistant", content = "retry-success"))
                )
            )
        }
        val repository = AIRepository(
            cacheDao = cacheDao,
            loadConfig = { enabledConfig() },
            serviceFactory = { fakeService },
            nowProvider = { 100L }
        )

        val result = repository.generateMemoryAid(wordId = 2L, word = "resilient", forceRefresh = true)

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.contains("【词根词缀】") == true)
        assertTrue(result.getOrNull()?.contains("retry-success") == true)
        assertEquals(2, fakeService.callCount)
        val cached = cacheDao.getByQueryAndType("resilient", "MEMORY_AID")
        assertTrue(cached?.aiContent?.contains("retry-success") == true)
    }

    @Test
    fun getAIContent_maps401ToReadableMessage() = runBlocking {
        val cacheDao = InMemoryAICacheDao()
        val http401 = HttpException(
            Response.error<ChatResponse>(
                401,
                "{}".toResponseBody("application/json".toMediaType())
            )
        )
        val fakeService = FakeAIService { throw http401 }
        val repository = AIRepository(
            cacheDao = cacheDao,
            loadConfig = { enabledConfig() },
            serviceFactory = { fakeService }
        )

        val result = repository.generateExample(wordId = 3L, word = "valid")

        assertTrue(result.isFailure)
        assertEquals("API Key 无效或已过期", result.exceptionOrNull()?.message)
    }

    private fun enabledConfig(): AIConfig {
        return AIConfig(
            enabled = true,
            apiBaseUrl = AIPresets.OPENAI.baseUrl,
            apiKey = "test-key",
            modelName = "test-model"
        )
    }
}

private class FakeAIService(
    private val block: suspend (FakeAIService) -> ChatResponse
) : AIService {
    var callCount: Int = 0
        private set

    override suspend fun chat(auth: String, request: ChatRequest): ChatResponse {
        if (!auth.startsWith("Bearer ")) {
            throw IllegalStateException("unexpected auth header")
        }
        if (request.messages.isEmpty()) {
            throw IllegalStateException("messages should not be empty")
        }
        callCount += 1
        return block(this)
    }
}

private class InMemoryAICacheDao : AICacheDao {
    private val caches = mutableListOf<AICache>()
    private var nextId = 1L

    override suspend fun getByWordIdAndType(wordId: Long, type: String): AICache? {
        return caches
            .filter { it.wordId == wordId && it.type == type }
            .maxByOrNull { it.createdAt }
    }

    override suspend fun getByQueryAndType(queryContent: String, type: String): AICache? {
        return caches
            .filter { it.queryContent == queryContent && it.type == type }
            .maxByOrNull { it.createdAt }
    }

    override suspend fun insert(cache: AICache): Long {
        if (cache.id > 0) {
            caches.removeAll { it.id == cache.id }
            caches.add(cache)
            return cache.id
        }
        val assigned = cache.copy(id = nextId++)
        caches.add(assigned)
        return assigned.id
    }

    override suspend fun deleteOlderThan(beforeTime: Long): Int {
        val toDelete = caches.count { it.createdAt < beforeTime }
        if (toDelete == 0) return 0
        caches.removeAll { it.createdAt < beforeTime }
        return toDelete
    }
}
