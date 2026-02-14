package com.kaoyan.wordhelper.data.model

data class WordDraft(
    val word: String,
    val phonetic: String = "",
    val meaning: String = "",
    val example: String = ""
)
