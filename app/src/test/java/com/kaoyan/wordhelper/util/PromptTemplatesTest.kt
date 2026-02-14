package com.kaoyan.wordhelper.util

import com.kaoyan.wordhelper.data.model.AIContentType
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTemplatesTest {

    @Test
    fun buildExamplePrompt_containsWordAndFormattingRules() {
        val prompt = PromptTemplates.build(AIContentType.EXAMPLE, "abandon")
        assertTrue(prompt.contains("abandon"))
        assertTrue(prompt.contains("2 个考研阅读风格英文例句"))
        assertTrue(prompt.contains("只输出上述模板"))
    }

    @Test
    fun buildMemoryAidPrompt_containsWordAndLengthLimit() {
        val prompt = PromptTemplates.build(AIContentType.MEMORY_AID, "abandon")
        assertTrue(prompt.contains("abandon"))
        assertTrue(prompt.contains("词根词缀"))
        assertTrue(prompt.contains("120 字"))
    }

    @Test
    fun buildSentencePrompt_containsStructuredSections() {
        val prompt = PromptTemplates.build(AIContentType.SENTENCE, "This is a long sentence.")
        assertTrue(prompt.contains("句子主干"))
        assertTrue(prompt.contains("语法成分标注"))
        assertTrue(prompt.contains("中文翻译"))
    }

    @Test
    fun systemInstruction_enforcesStrictOutput() {
        val instruction = PromptTemplates.systemInstruction(AIContentType.EXAMPLE)
        assertTrue(instruction.contains("严格"))
        assertTrue(instruction.contains("模板"))
    }
}
