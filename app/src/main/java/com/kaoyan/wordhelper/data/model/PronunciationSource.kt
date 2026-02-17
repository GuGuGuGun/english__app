package com.kaoyan.wordhelper.data.model

enum class PronunciationSource(val value: Int, val label: String) {
    FREE_DICTIONARY(0, "Free Dictionary"),
    YOUDAO(1, "Youdao");

    companion object {
        fun fromValue(value: Int): PronunciationSource {
            return entries.firstOrNull { it.value == value } ?: FREE_DICTIONARY
        }
    }
}
