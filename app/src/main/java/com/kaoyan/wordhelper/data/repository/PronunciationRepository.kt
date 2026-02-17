package com.kaoyan.wordhelper.data.repository

import com.kaoyan.wordhelper.data.network.FreeDictionaryService
import com.kaoyan.wordhelper.data.model.PronunciationSource
import com.kaoyan.wordhelper.util.WordbookPronunciation
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.ConnectException
import java.net.URLEncoder
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class PronunciationRepository(
    private val service: FreeDictionaryService = buildService(),
    private val wordbookPronunciations: Map<String, WordbookPronunciation> = emptyMap()
) {
    suspend fun getPronunciationAudioUrl(
        word: String,
        preferredSource: PronunciationSource = PronunciationSource.FREE_DICTIONARY
    ): Result<String> {
        val normalizedWord = word.trim().lowercase()
        if (normalizedWord.isBlank()) {
            return Result.failure(IllegalArgumentException("单词不能为空"))
        }

        val builtInAudio = wordbookPronunciations[normalizedWord]
            ?.let { pronunciation ->
                pronunciation.usSpeech.ifBlank { pronunciation.ukSpeech }
            }
            ?.trim()
            .orEmpty()
        if (builtInAudio.isNotBlank()) {
            return Result.success(resolveAudioUrl(builtInAudio))
        }

        if (preferredSource == PronunciationSource.YOUDAO) {
            return Result.success(buildYoudaoAudioUrl(normalizedWord))
        }

        return try {
            val entries = service.lookup(normalizedWord)
            val rawAudioUrl = entries.asSequence()
                .flatMap { it.phonetics.asSequence() }
                .map { it.audio.trim() }
                .firstOrNull { it.isNotBlank() }

            if (rawAudioUrl.isNullOrBlank()) {
                Result.success(buildYoudaoAudioUrl(normalizedWord))
            } else {
                val resolvedUrl = resolveAudioUrl(rawAudioUrl)
                Result.success(resolvedUrl)
            }
        } catch (throwable: Throwable) {
            val youdaoAudio = buildYoudaoAudioUrl(normalizedWord)
            if (youdaoAudio.isNotBlank()) {
                Result.success(youdaoAudio)
            } else {
                Result.failure(Exception(mapErrorMessage(throwable), throwable))
            }
        }
    }

    private fun buildYoudaoAudioUrl(word: String): String {
        val encodedWord = URLEncoder.encode(word, StandardCharsets.UTF_8.toString())
        return "https://dict.youdao.com/dictvoice?audio=$encodedWord&type=2"
    }

    private fun resolveAudioUrl(rawAudioUrl: String): String {
        return if (rawAudioUrl.startsWith("//")) {
            "https:$rawAudioUrl"
        } else {
            rawAudioUrl
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
