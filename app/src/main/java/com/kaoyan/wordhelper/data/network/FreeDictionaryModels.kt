package com.kaoyan.wordhelper.data.network

data class FreeDictionaryEntry(
    val phonetics: List<FreeDictionaryPhonetic> = emptyList()
)

data class FreeDictionaryPhonetic(
    val audio: String = ""
)
