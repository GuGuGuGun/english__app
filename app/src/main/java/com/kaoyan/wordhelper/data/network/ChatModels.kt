package com.kaoyan.wordhelper.data.network

import com.google.gson.annotations.SerializedName

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float? = null,
    val stream: Boolean = false
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<ChatChoice> = emptyList(),
    val error: ChatError? = null
)

data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessage = ChatMessage(role = "", content = ""),
    @SerializedName("finish_reason")
    val finishReason: String? = null
)

data class ChatError(
    val message: String? = null
)
