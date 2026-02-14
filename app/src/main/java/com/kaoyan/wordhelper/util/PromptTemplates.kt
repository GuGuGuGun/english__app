package com.kaoyan.wordhelper.util

import com.kaoyan.wordhelper.data.model.AIContentType

object PromptTemplates {
    fun systemInstruction(type: AIContentType): String {
        return when (type) {
            AIContentType.EXAMPLE -> {
                "你是考研英语助教。必须严格按用户给定模板输出，不得添加额外说明、寒暄或总结。"
            }

            AIContentType.MEMORY_AID -> {
                "你是英语单词记忆教练。必须严格按模板输出，内容简短、可直接复习，不得输出多余段落。"
            }

            AIContentType.SENTENCE -> {
                "你是英语长难句解析助手。必须严格输出“句子主干/语法成分标注/中文翻译”三段，不得增加其他标题。"
            }
        }
    }

    fun build(type: AIContentType, query: String): String {
        return when (type) {
            AIContentType.EXAMPLE -> buildExamplePrompt(query)
            AIContentType.MEMORY_AID -> buildMemoryAidPrompt(query)
            AIContentType.SENTENCE -> buildSentencePrompt(query)
        }
    }

    private fun buildExamplePrompt(word: String): String {
        return """
            请围绕单词 "$word" 生成 2 个考研阅读风格英文例句，严格按以下格式输出：
            【例句1】
            <英文例句>
            【翻译1】
            <中文翻译>
            【例句2】
            <英文例句>
            【翻译2】
            <中文翻译>

            规则：
            1. 英文例句控制在 12-24 个词。
            2. 中文翻译准确、自然。
            3. 只输出上述模板，不要添加解释。
        """.trimIndent()
    }

    private fun buildMemoryAidPrompt(word: String): String {
        return """
            请为单词 "$word" 生成助记内容，严格按以下格式输出：
            【词根词缀】
            <一句话词根/词缀拆解>
            【联想记忆】
            <一句话谐音或场景联想>
            【复习提示】
            <一句话记忆抓手>

            规则：
            1. 每段 1 句话，总字数不超过 120 字。
            2. 只输出上述模板，不要添加解释。
        """.trimIndent()
    }

    private fun buildSentencePrompt(sentence: String): String {
        return """
            请解析下面英语长难句：
            "$sentence"
            请严格按以下格式输出，不要添加额外说明：
            ## 句子主干
            （一句话概括主谓宾核心结构）
            ## 语法成分标注
            （按短语/从句分点解释其语法功能）
            ## 中文翻译
            （给出自然、通顺的中文翻译）
            不得输出其他标题、前言或总结。
        """.trimIndent()
    }
}
