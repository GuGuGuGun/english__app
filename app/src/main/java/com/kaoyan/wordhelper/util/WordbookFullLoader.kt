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
    private data class BuiltinWordbookSource(
        val fileName: String,
        val bookName: String
    )

    private data class BuiltinWordbookCache(
        val presets: List<PresetBookSeed>,
        val pronunciationIndex: Map<String, WordbookPronunciation>
    )

    private val builtinSources = listOf(
        BuiltinWordbookSource(
            fileName = "wordbook_full_from_e2c.json",
            bookName = "完整词库"
        ),
        BuiltinWordbookSource(
            fileName = "wordbook_full.json",
            bookName = "完整词库（精选）"
        )
    )

    private val gson = Gson()
    @Volatile
    private var cachedWordbook: BuiltinWordbookCache? = null

    fun loadPresets(context: Context): Result<List<PresetBookSeed>> {
        return runCatching {
            val presets = loadWordbookCache(context).presets
            require(presets.isNotEmpty()) { "内置词库为空" }
            presets
        }
    }

    fun loadPreset(context: Context): Result<PresetBookSeed> {
        return loadPresets(context).mapCatching { presets ->
            presets.first()
        }
    }

    fun loadPronunciationIndex(context: Context): Result<Map<String, WordbookPronunciation>> {
        return runCatching { loadWordbookCache(context).pronunciationIndex }
    }

    private fun toWordDraft(entry: WordbookFullEntry): WordDraft? {
        val rawWord = entry.word.trim()
        if (rawWord.isBlank()) return null

        val phonetic = entry.ukphone.trim().ifBlank { entry.usphone.trim() }
        val phrases = buildPhrases(entry)
        val synonyms = buildSynonyms(entry)
        val relWords = buildRelWords(entry)
        val meaning = buildMeaning(entry, phrases, synonyms, relWords)
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

    private fun buildMeaning(
        entry: WordbookFullEntry,
        phrases: String,
        synonyms: String,
        relWords: String
    ): String {
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

        if (phrases.isNotBlank()) {
            return phrases
        }

        if (synonyms.isNotBlank()) {
            return synonyms
        }

        return relWords
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

    private fun normalizeWordKey(rawWord: String): String {
        return rawWord.trim().lowercase()
    }

    private fun loadWordbookCache(context: Context): BuiltinWordbookCache {
        cachedWordbook?.let { return it }
        return synchronized(this) {
            cachedWordbook?.let { return@synchronized it }

            val presets = ArrayList<PresetBookSeed>(builtinSources.size)
            val pronunciationMap = LinkedHashMap<String, WordbookPronunciation>()

            builtinSources.forEach { source ->
                val entries = runCatching {
                    loadEntries(context, source.fileName)
                }.getOrElse {
                    return@forEach
                }
                if (entries.isEmpty()) {
                    return@forEach
                }

                val draftsByKey = LinkedHashMap<String, WordDraft>(entries.size)
                entries.forEach { entry ->
                    val draft = toWordDraft(entry) ?: return@forEach
                    val key = normalizeWordKey(draft.word)
                    if (key.isBlank() || draftsByKey.containsKey(key)) {
                        return@forEach
                    }
                    draftsByKey[key] = draft
                }

                if (draftsByKey.isNotEmpty()) {
                    presets.add(
                        PresetBookSeed(
                            name = source.bookName,
                            drafts = draftsByKey.values.toList()
                        )
                    )
                }

                entries.forEach { entry ->
                    val key = normalizeWordKey(entry.word)
                    if (key.isBlank()) {
                        return@forEach
                    }
                    val candidate = WordbookPronunciation(
                        ukSpeech = entry.ukspeech.trim(),
                        usSpeech = entry.usspeech.trim()
                    )
                    val existing = pronunciationMap[key]
                    if (existing == null) {
                        pronunciationMap[key] = candidate
                        return@forEach
                    }
                    val merged = WordbookPronunciation(
                        ukSpeech = if (existing.ukSpeech.isBlank()) candidate.ukSpeech else existing.ukSpeech,
                        usSpeech = if (existing.usSpeech.isBlank()) candidate.usSpeech else existing.usSpeech
                    )
                    pronunciationMap[key] = merged
                }
            }

            val resolved = BuiltinWordbookCache(
                presets = presets,
                pronunciationIndex = pronunciationMap
            )
            cachedWordbook = resolved
            resolved
        }
    }

    private fun loadEntries(context: Context, fileName: String): List<WordbookFullEntry> {
        return context.assets.open(fileName).use { input ->
            InputStreamReader(input).use { reader ->
                val payload = gson.fromJson(reader, WordbookFullPayload::class.java)
                payload.data.orEmpty()
            }
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
