package com.kaoyan.wordhelper.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.kaoyan.wordhelper.data.model.PresetBookSeed
import com.kaoyan.wordhelper.data.model.WordDraft
import java.io.InputStreamReader

data class WordbookPronunciation(
    val ukSpeech: String,
    val usSpeech: String
)

object WordbookFullLoader {
    private const val BUILTIN_FILE_NAME = "wordbook_full_from_e2c.json"
    private const val BUILTIN_BOOK_NAME = "完整词库"
    private val gson = Gson()
    @Volatile
    private var cachedEntries: List<WordbookFullEntry>? = null

    fun loadPreset(context: Context): Result<PresetBookSeed> {
        return runCatching {
            val entries = loadEntries(context)
            val drafts = entries.asSequence()
                .mapNotNull(::toWordDraft)
                .distinctBy { it.word.trim().lowercase() }
                .toList()
            require(drafts.isNotEmpty()) { "内置词库为空" }
            PresetBookSeed(name = BUILTIN_BOOK_NAME, drafts = drafts)
        }
    }

    fun loadPronunciationIndex(context: Context): Result<Map<String, WordbookPronunciation>> {
        return runCatching {
            loadEntries(context).asSequence()
                .mapNotNull { entry ->
                    val word = entry.word.trim().lowercase()
                    if (word.isBlank()) return@mapNotNull null
                    word to WordbookPronunciation(
                        ukSpeech = entry.ukspeech.trim(),
                        usSpeech = entry.usspeech.trim()
                    )
                }
                .toMap()
        }
    }

    private fun toWordDraft(entry: WordbookFullEntry): WordDraft? {
        val rawWord = entry.word.trim()
        if (rawWord.isBlank()) return null

        val phonetic = entry.ukphone.trim().ifBlank { entry.usphone.trim() }
        val phrases = buildPhrases(entry)
        val synonyms = buildSynonyms(entry)
        val relWords = buildRelWords(entry)
        val meaning = buildMeaning(entry)
        val example = buildExample(entry)

        return WordDraft(
            word = rawWord,
            phonetic = if (phonetic.isBlank()) "" else "[$phonetic]",
            meaning = meaning,
            example = example,
            phrases = phrases,
            synonyms = synonyms,
            relWords = relWords
        )
    }

    private fun buildMeaning(entry: WordbookFullEntry): String {
        val translations = entry.translations
            .mapNotNull { item ->
                val text = item.tranCn.trim()
                if (text.isBlank()) return@mapNotNull null
                val pos = item.pos.trim()
                if (pos.isBlank()) text else "$pos. $text"
            }

        if (translations.isNotEmpty()) {
            return translations.joinToString("；")
        }

        val phraseFallback = buildPhrases(entry)
        if (phraseFallback.isNotBlank()) {
            return phraseFallback
        }

        val synonymFallback = buildSynonyms(entry)
        if (synonymFallback.isNotBlank()) {
            return synonymFallback
        }

        return buildRelWords(entry)
    }

    private fun buildPhrases(entry: WordbookFullEntry): String {
        return entry.phrases
            .mapNotNull { phrase ->
                val content = phrase.content.trim()
                if (content.isBlank()) return@mapNotNull null
                val cn = phrase.cn.trim()
                if (cn.isBlank()) content else "$content（$cn）"
            }
            .take(3)
            .joinToString("；")
    }

    private fun buildSynonyms(entry: WordbookFullEntry): String {
        return entry.synonyms
            .mapNotNull { group ->
                val words = group.words
                    .map { it.word.trim() }
                    .filter { it.isNotBlank() }
                if (words.isEmpty()) return@mapNotNull null
                val pos = group.pos.trim()
                val tran = group.tran.trim()
                val wordsText = words.joinToString(", ")
                when {
                    pos.isBlank() && tran.isBlank() -> wordsText
                    pos.isBlank() -> "$wordsText（$tran）"
                    tran.isBlank() -> "$pos: $wordsText"
                    else -> "$pos: $wordsText（$tran）"
                }
            }
            .take(3)
            .joinToString("；")
    }

    private fun buildRelWords(entry: WordbookFullEntry): String {
        return entry.relWords
            .mapNotNull { group ->
                val words = group.words
                    .mapNotNull { relWord ->
                        val word = relWord.word.trim()
                        if (word.isBlank()) return@mapNotNull null
                        val tran = relWord.tran.trim()
                        if (tran.isBlank()) word else "$word（$tran）"
                    }
                    .filter { it.isNotBlank() }
                if (words.isEmpty()) return@mapNotNull null
                val pos = group.pos.trim()
                if (pos.isBlank()) words.joinToString(", ") else "$pos: ${words.joinToString(", ")}"
            }
            .take(3)
            .joinToString("；")
    }

    private fun buildExample(entry: WordbookFullEntry): String {
        val sentence = entry.sentences.firstOrNull() ?: return ""
        val en = sentence.content.trim()
        val cn = sentence.cn.trim()
        if (en.isBlank() && cn.isBlank()) return ""
        if (en.isBlank()) return cn
        if (cn.isBlank()) return en
        return "$en\n$cn"
    }

    private fun loadEntries(context: Context): List<WordbookFullEntry> {
        cachedEntries?.let { return it }
        return synchronized(this) {
            cachedEntries?.let { return@synchronized it }
            val entries = context.assets.open(BUILTIN_FILE_NAME).use { input ->
                InputStreamReader(input).use { reader ->
                    val payload = gson.fromJson(reader, WordbookFullPayload::class.java)
                    payload.data.orEmpty()
                }
            }
            cachedEntries = entries
            entries
        }
    }
}

private data class WordbookFullPayload(
    val data: List<WordbookFullEntry>? = null
)

private data class WordbookFullEntry(
    val word: String = "",
    val ukphone: String = "",
    val usphone: String = "",
    val ukspeech: String = "",
    val usspeech: String = "",
    val translations: List<WordbookTranslation> = emptyList(),
    val phrases: List<WordbookPhrase> = emptyList(),
    val sentences: List<WordbookSentence> = emptyList(),
    val synonyms: List<WordbookSynonymGroup> = emptyList(),
    val relWords: List<WordbookRelWordGroup> = emptyList()
)

private data class WordbookTranslation(
    val pos: String = "",
    @SerializedName("tran_cn")
    val tranCn: String = ""
)

private data class WordbookPhrase(
    @SerializedName("p_content")
    val content: String = "",
    @SerializedName("p_cn")
    val cn: String = ""
)

private data class WordbookSentence(
    @SerializedName("s_content")
    val content: String = "",
    @SerializedName("s_cn")
    val cn: String = ""
)

private data class WordbookSynonymGroup(
    val pos: String = "",
    val tran: String = "",
    @SerializedName("Hwds")
    val words: List<WordbookSynonymWord> = emptyList()
)

private data class WordbookSynonymWord(
    val word: String = ""
)

private data class WordbookRelWordGroup(
    @SerializedName("Pos")
    val pos: String = "",
    @SerializedName("Hwds")
    val words: List<WordbookRelWord> = emptyList()
)

private data class WordbookRelWord(
    @SerializedName("hwd")
    val word: String = "",
    val tran: String = ""
)
