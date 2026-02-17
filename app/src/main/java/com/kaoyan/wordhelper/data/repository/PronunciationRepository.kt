package com.kaoyan.wordhelper.data.repository

import com.kaoyan.wordhelper.data.network.FreeDictionaryService
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class PronunciationRepository(
    private val service: FreeDictionaryService = buildService()
) {
    suspend fun getPronunciationAudioUrl(word: String): Result<String> {
        val normalizedWord = word.trim().lowercase()
        if (normalizedWord.isBlank()) {
            return Result.failure(IllegalArgumentException("单词不能为空"))
        }

        return try {
            val entries = service.lookup(normalizedWord)
            val rawAudioUrl = entries.asSequence()
                .flatMap { it.phonetics.asSequence() }
                .map { it.audio.trim() }
                .firstOrNull { it.isNotBlank() }

            if (rawAudioUrl.isNullOrBlank()) {
                Result.failure(IllegalStateException("未找到可用发音音频"))
            } else {
                val resolvedUrl = if (rawAudioUrl.startsWith("//")) {
                    "https:$rawAudioUrl"
                } else {
                    rawAudioUrl
                }
                Result.success(resolvedUrl)
            }
        } catch (throwable: Throwable) {
            Result.failure(Exception(mapErrorMessage(throwable), throwable))
        }
    }

    private fun mapErrorMessage(throwable: Throwable): String {
        return when (throwable) {
            is HttpException -> {
                when (throwable.code()) {
                    404 -> "Free Dictionary 未收录该单词发音"
                    else -> "发音查询失败，请稍后重试"
                }
            }

            is UnknownHostException -> "网络不可用，请检查连接"
            is SocketTimeoutException -> "发音查询超时，请重试"
            is ConnectException -> "发音服务连接失败"
            else -> throwable.message ?: "发音查询失败"
        }
    }

    companion object {
        private fun buildService(): FreeDictionaryService {
            val client = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
            return Retrofit.Builder()
                .baseUrl("https://api.dictionaryapi.dev/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FreeDictionaryService::class.java)
        }
    }
}
