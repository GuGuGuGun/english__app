package com.kaoyan.wordhelper.util

import com.kaoyan.wordhelper.data.model.AIContentType
import org.junit.Assert.assertTrue
import org.junit.Test

class AIContentFormatterTest {

    @Test
    fun normalizeExample_wrapsIntoFixedTemplate() {
        val raw = "Example one.\n示例一翻译。\nExample two.\n示例二翻译。"
        val normalized = AIContentFormatter.normalize(AIContentType.EXAMPLE, raw)
        assertTrue(normalized.contains("【例句1】"))
        assertTrue(normalized.contains("【翻译2】"))
    }

    @Test
    fun normalizeMemoryAid_wrapsIntoFixedTemplate() {
        val raw = "ab- 离开\n联想：离开原地\n多看多背"
        val normalized = AIContentFormatter.normalize(AIContentType.MEMORY_AID, raw)
        assertTrue(normalized.contains("【词根词缀】"))
        assertTrue(normalized.contains("【复习提示】"))
    }

    @Test
    fun normalizeSentence_wrapsIntoThreeSections() {
        val raw = "主句是 committee approved it.\nbecause 从句解释原因。\n中文：尽管实验昂贵，委员会仍批准。"
        val normalized = AIContentFormatter.normalize(AIContentType.SENTENCE, raw)
        assertTrue(normalized.contains("## 句子主干"))
        assertTrue(normalized.contains("## 语法成分标注"))
        assertTrue(normalized.contains("## 中文翻译"))
    }
}
