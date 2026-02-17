package com.kaoyan.wordhelper.data.network

import retrofit2.http.GET
import retrofit2.http.Path

interface FreeDictionaryService {
    @GET("api/v2/entries/en/{word}")
    suspend fun lookup(@Path("word") word: String): List<FreeDictionaryEntry>
}
