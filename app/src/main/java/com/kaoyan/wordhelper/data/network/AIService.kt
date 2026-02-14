package com.kaoyan.wordhelper.data.network

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AIService {
    @POST("chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Body request: ChatRequest
    ): ChatResponse
}
