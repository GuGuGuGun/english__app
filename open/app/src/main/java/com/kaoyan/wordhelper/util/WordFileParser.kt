package com.kaoyan.wordhelper.util

import com.kaoyan.wordhelper.data.model.WordDraft

object WordFileParser {
    private val primarySeparators = listOf(",", "\t", "|", ";")
    private val fallbackSeparators = listOf(" - ", " — ", ":", "：")

    fun parse(text: String): List<WordDraft> = parseLines(text.lineSequence())

    fun parseLines(lines: Sequence<String>): List<WordDraft> {
        return lines.mapNotNull { line -> parseLine(line) }.toList()
    }

    private fun parseLine(rawLine: String): WordDraft? {
        val cleanedLine = rawLine.trim().removePrefix("\uFEFF")
        if (cleanedLine.isBlank() || cleanedLine.startsWith("#")) return null

        val primary = splitBySeparators(cleanedLine, primarySeparators)
        val parts = if (primary.size > 1) primary else splitBySeparators(cleanedLine, fallbackSeparators)
        val normalized = parts.map { it.trim() }.filter { it.isNotBlank() }
        if (normalized.isEmpty()) return null

        return when (normalized.size) {
            1 -> WordDraft(word = normalized[0])
            2 -> WordDraft(word = normalized[0], meaning = normalized[1])
            3 -> WordDraft(word = normalized[0], phonetic = normalized[1], meaning = normalized[2])
            else -> WordDraft(
                word = normalized[0],
                phonetic = normalized.getOrElse(1) { "" },
                meaning = normalized.getOrElse(2) { "" },
                example = normalized.drop(3).joinToString(" ")
            )
        }
    }

    private fun splitBySeparators(line: String, separators: List<String>): List<String> {
        val separator = separators.firstOrNull { line.contains(it) } ?: return listOf(line)
        return line.split(separator, limit = 6)
    }
}
