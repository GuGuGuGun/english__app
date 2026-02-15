package com.kaoyan.wordhelper.util

import com.kaoyan.wordhelper.data.model.WordDraft

object WordFileParser {
    private val primarySeparators = listOf(",", "\t", "|", ";")
    private val fallbackSeparators = listOf(" - ", " — ", ":", "：")
    private val packedEntryStartRegex = Regex("""(?:^|\s)[A-Za-z][A-Za-z0-9 .'\-/()&]{0,63}\s*,""")

    fun parse(text: String): List<WordDraft> {
        val normalizedText = text.removePrefix("\uFEFF")
        val logicalLines = mergeMeaningContinuationLines(splitLogicalLines(normalizedText))
        val lineBased = parseLines(logicalLines.asSequence())
        val packed = if (looksLikePackedWordMeaningText(normalizedText)) {
            parsePackedWordMeaningText(normalizedText)
        } else {
            emptyList()
        }
        return if (packed.isNotEmpty() && packed.size >= lineBased.size * 2) packed else lineBased
    }

    fun parseLines(lines: Sequence<String>): List<WordDraft> {
        return lines.mapNotNull { line -> parseLine(line) }.toList()
    }

    private fun splitLogicalLines(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            when {
                ch == '"' -> {
                    if (inQuotes && index + 1 < text.length && text[index + 1] == '"') {
                        current.append('"')
                        current.append('"')
                        index += 2
                        continue
                    }
                    inQuotes = !inQuotes
                    current.append(ch)
                    index += 1
                }

                ch == '\r' -> {
                    val isCrlf = index + 1 < text.length && text[index + 1] == '\n'
                    if (!inQuotes) {
                        lines.add(current.toString())
                        current.clear()
                    } else {
                        current.append('\n')
                    }
                    index += if (isCrlf) 2 else 1
                }

                ch == '\n' -> {
                    if (!inQuotes) {
                        lines.add(current.toString())
                        current.clear()
                    } else {
                        current.append('\n')
                    }
                    index += 1
                }

                else -> {
                    current.append(ch)
                    index += 1
                }
            }
        }
        if (current.isNotEmpty()) {
            lines.add(current.toString())
        }
        return lines
    }

    private fun mergeMeaningContinuationLines(lines: List<String>): List<String> {
        if (lines.isEmpty()) return lines
        val merged = mutableListOf<String>()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue
            if (merged.isNotEmpty() && shouldAppendToPrevious(line, merged.last())) {
                merged[merged.lastIndex] = merged.last() + "\n" + line
            } else {
                merged.add(line)
            }
        }
        return merged
    }

    private fun shouldAppendToPrevious(currentLine: String, previousLine: String): Boolean {
        if (currentLine.startsWith("#")) return false
        if (!containsPrimarySeparator(previousLine)) return false
        if (containsPrimarySeparator(currentLine)) return false
        return !isLikelyPackedWord(currentLine)
    }

    private fun containsPrimarySeparator(line: String): Boolean {
        return primarySeparators.any { separator -> line.contains(separator) }
    }

    private fun parseLine(rawLine: String): WordDraft? {
        val cleanedLine = rawLine.trim().removePrefix("\uFEFF")
        if (cleanedLine.isBlank() || cleanedLine.startsWith("#")) return null

        val primary = splitBySeparators(cleanedLine, primarySeparators)
        val parts = if (primary.size > 1) primary else splitBySeparators(cleanedLine, fallbackSeparators)
        val normalized = parts.map(::normalizeField).filter { it.isNotBlank() }
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
        return if (separator.length == 1) {
            splitRespectingQuotes(line, separator[0], limit = 6)
        } else {
            line.split(separator, limit = 6)
        }
    }

    private fun splitRespectingQuotes(line: String, separator: Char, limit: Int): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val ch = line[index]
            when {
                ch == '"' -> {
                    if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                        current.append('"')
                        index += 1
                    } else {
                        inQuotes = !inQuotes
                    }
                }

                ch == separator && !inQuotes && fields.size < limit - 1 -> {
                    fields.add(current.toString())
                    current.clear()
                }

                else -> current.append(ch)
            }
            index += 1
        }
        fields.add(current.toString())
        return fields
    }

    private fun normalizeField(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
            return trimmed.substring(1, trimmed.length - 1).replace("\"\"", "\"").trim()
        }
        return trimmed
    }

    private fun looksLikePackedWordMeaningText(text: String): Boolean {
        if (!text.contains(',')) return false
        val sample = text.take(80_000)
        val entryLikeCount = packedEntryStartRegex.findAll(sample).count()
        val lineCount = sample.count { it == '\n' }.coerceAtLeast(1)
        return entryLikeCount >= 3 && entryLikeCount >= lineCount * 2
    }

    private fun parsePackedWordMeaningText(text: String): List<WordDraft> {
        val source = text
            .lineSequence()
            .filterNot { it.trimStart().startsWith("#") }
            .joinToString(" ")
            .trim()
        if (source.isBlank()) return emptyList()

        val drafts = mutableListOf<WordDraft>()
        var index = 0
        while (index < source.length) {
            index = skipWhitespace(source, index)
            if (index >= source.length) break

            val commaIndex = source.indexOf(',', startIndex = index)
            if (commaIndex < 0) break

            val word = source.substring(index, commaIndex).trim()
            if (!isLikelyPackedWord(word)) {
                index = commaIndex + 1
                continue
            }

            val (meaning, nextIndex) = readPackedMeaning(source, commaIndex + 1)
            if (meaning.isNotBlank()) {
                drafts.add(WordDraft(word = word, meaning = meaning.trim()))
            }
            index = nextIndex
        }
        return drafts
    }

    private fun readPackedMeaning(source: String, start: Int): Pair<String, Int> {
        var index = skipWhitespace(source, start)
        if (index >= source.length) return "" to index

        if (source[index] == '"') {
            return readQuotedMeaning(source, index)
        }

        val builder = StringBuilder()
        while (index < source.length) {
            val ch = source[index]
            if (ch.isWhitespace()) {
                val nextStart = skipWhitespace(source, index)
                if (isWordStartAt(source, nextStart)) {
                    return builder.toString().trimEnd() to nextStart
                }
                if (nextStart > index) {
                    builder.append(' ')
                    index = nextStart
                    continue
                }
            }
            builder.append(ch)
            index += 1
        }
        return builder.toString().trimEnd() to index
    }

    private fun readQuotedMeaning(source: String, startQuoteIndex: Int): Pair<String, Int> {
        val builder = StringBuilder()
        var index = startQuoteIndex + 1
        while (index < source.length) {
            val ch = source[index]
            if (ch == '"') {
                if (index + 1 < source.length && source[index + 1] == '"') {
                    builder.append('"')
                    index += 2
                    continue
                }
                index += 1
                break
            }
            builder.append(ch)
            index += 1
        }
        return builder.toString() to skipWhitespace(source, index)
    }

    private fun isWordStartAt(source: String, start: Int): Boolean {
        if (start >= source.length) return false
        val commaIndex = source.indexOf(',', start)
        if (commaIndex <= start) return false
        val candidate = source.substring(start, commaIndex).trim()
        return isLikelyPackedWord(candidate)
    }

    private fun isLikelyPackedWord(token: String): Boolean {
        if (token.isBlank() || token.length > 64) return false
        var hasLetter = false
        for (ch in token) {
            if (ch.code >= 128) return false
            val allowed = ch.isLetterOrDigit() ||
                ch == ' ' ||
                ch == '\'' ||
                ch == '-' ||
                ch == '.' ||
                ch == '/' ||
                ch == '(' ||
                ch == ')' ||
                ch == '&'
            if (!allowed) return false
            if (ch.isLetter()) {
                hasLetter = true
            }
        }
        return hasLetter
    }

    private fun skipWhitespace(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index].isWhitespace()) {
            index += 1
        }
        return index
    }
}
