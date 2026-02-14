package com.kaoyan.wordhelper.util

import com.kaoyan.wordhelper.data.model.AIContentType

object AIContentFormatter {

    fun normalize(type: AIContentType, raw: String): String {
        val content = raw.trim()
        if (content.isBlank()) return content
        return when (type) {
            AIContentType.EXAMPLE -> normalizeExample(content)
            AIContentType.MEMORY_AID -> normalizeMemoryAid(content)
            AIContentType.SENTENCE -> normalizeSentence(content)
        }
    }

    private fun normalizeExample(content: String): String {
        if (content.contains("【例句1】") && content.contains("【翻译1】")) {
            return content
        }
        val lines = collectCleanLines(content)
        val englishLines = lines.filter { it.any { ch -> ch in 'A'..'Z' || ch in 'a'..'z' } }
        val chineseLines = lines.filter { it.any { ch -> ch in '\u4e00'..'\u9fff' } }
        val e1 = englishLines.getOrNull(0) ?: "（未提取到标准例句，请重试）"
        val c1 = chineseLines.getOrNull(0) ?: "（未提取到标准翻译，请重试）"
        val e2 = englishLines.getOrNull(1) ?: "（未提取到第二条例句，请重试）"
        val c2 = chineseLines.getOrNull(1) ?: "（未提取到第二条翻译，请重试）"
        return """
            【例句1】
            $e1
            【翻译1】
            $c1
            【例句2】
            $e2
            【翻译2】
            $c2
        """.trimIndent()
    }

    private fun normalizeMemoryAid(content: String): String {
        if (content.contains("【词根词缀】") && content.contains("【联想记忆】")) {
            return content
        }
        val lines = collectCleanLines(content)
        val root = lines.getOrNull(0) ?: "（未提取到词根词缀信息，请重试）"
        val association = lines.getOrNull(1) ?: "（未提取到联想记忆信息，请重试）"
        val tip = lines.getOrNull(2) ?: "（未提取到复习提示，请重试）"
        return """
            【词根词缀】
            $root
            【联想记忆】
            $association
            【复习提示】
            $tip
        """.trimIndent()
    }

    private fun normalizeSentence(content: String): String {
        if (content.contains("## 句子主干") &&
            content.contains("## 语法成分标注") &&
            content.contains("## 中文翻译")
        ) {
            return content
        }

        val normalized = content.replace("\r\n", "\n")
        val blocks = normalized
            .split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val mainClause = blocks.firstOrNull() ?: "未识别到句子主干，请重试。"
        val grammar = when {
            blocks.size >= 3 -> blocks.subList(1, blocks.lastIndex).joinToString("\n\n")
            blocks.size == 2 -> blocks[1]
            else -> normalized
        }.ifBlank { "未识别到语法成分标注，请重试。" }
        val translation = normalized
            .lineSequence()
            .map { it.trim() }
            .lastOrNull { it.any { ch -> ch in '\u4e00'..'\u9fff' } }
            ?.takeIf { it.isNotBlank() }
            ?: "未识别到中文翻译，请重试。"

        return """
            ## 句子主干
            $mainClause
            ## 语法成分标注
            $grammar
            ## 中文翻译
            $translation
        """.trimIndent()
    }

    private fun collectCleanLines(content: String): List<String> {
        return content
            .replace("\r\n", "\n")
            .lineSequence()
            .map { line ->
                line.trim()
                    .removePrefix("-")
                    .removePrefix("*")
                    .replace(Regex("""^\d+[).]\s*"""), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .toList()
    }
}
