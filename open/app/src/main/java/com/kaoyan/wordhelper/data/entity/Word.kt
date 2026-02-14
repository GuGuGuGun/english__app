package com.kaoyan.wordhelper.data.entity

data class Word(
    val id: Long = 0,
    val word: String,
    val phonetic: String = "",
    val meaning: String = "",
    val example: String = "",
    val bookId: Long
)
