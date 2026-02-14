package com.kaoyan.wordhelper.data.model

enum class SpellingOutcome(val quality: Int) {
    PERFECT(5),
    HINTED(4),
    RETRY_SUCCESS(3),
    FAILED(0);

    val shouldAddImmediateRetry: Boolean
        get() = this == FAILED

    val spellCorrectDelta: Int
        get() = if (quality >= 4) 1 else 0

    val spellWrongDelta: Int
        get() = if (this == FAILED) 1 else 0
}
