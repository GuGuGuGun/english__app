package com.kaoyan.wordhelper.data.model

import com.kaoyan.wordhelper.data.entity.Word

data class PresetBookSeed(
    val name: String,
    val drafts: List<WordDraft>
)

object PresetBookCatalog {
    const val NEW_WORDS_BOOK_NAME = "生词本"

    val presets: List<PresetBookSeed> = listOf(
        PresetBookSeed(
            name = "考研核心词汇",
            drafts = listOf(
                WordDraft("abandon", "[əˈbændən]", "vt. 放弃；抛弃", "He abandoned his previous plan."),
                WordDraft("ability", "[əˈbɪləti]", "n. 能力；才能", "She has the ability to learn fast."),
                WordDraft("absolute", "[ˈæbsəluːt]", "adj. 绝对的；完全的", "Silence is an absolute requirement here."),
                WordDraft("absorb", "[əbˈzɔːrb]", "vt. 吸收；使专心", "Plants absorb sunlight for growth."),
                WordDraft("abstract", "[ˈæbstrækt]", "adj. 抽象的；n. 摘要", "The concept is too abstract for beginners."),
                WordDraft("abundant", "[əˈbʌndənt]", "adj. 丰富的；充足的", "The area has abundant water resources."),
                WordDraft("academic", "[ˌækəˈdemɪk]", "adj. 学术的", "He has strong academic performance."),
                WordDraft("access", "[ˈækses]", "n. 进入；使用权", "Students have free access to the database."),
                WordDraft("acquire", "[əˈkwaɪər]", "vt. 获得；习得", "You need time to acquire language skills."),
                WordDraft("adequate", "[ˈædɪkwət]", "adj. 足够的；适当的", "The report offers adequate evidence."),
                WordDraft("adjust", "[əˈdʒʌst]", "vt. 调整；适应", "You should adjust your strategy."),
                WordDraft("advocate", "[ˈædvəkeɪt]", "vt. 提倡；拥护", "Experts advocate early preparation."),
                WordDraft("allocate", "[ˈæləkeɪt]", "vt. 分配；拨给", "The team allocated tasks clearly."),
                WordDraft("alternative", "[ɔːlˈtɜːrnətɪv]", "n. 可选项；adj. 替代的", "We need an alternative method."),
                WordDraft("analysis", "[əˈnæləsɪs]", "n. 分析", "The paper provides a detailed analysis."),
                WordDraft("approach", "[əˈproʊtʃ]", "n. 方法；接近", "This approach improves efficiency."),
                WordDraft("assess", "[əˈses]", "vt. 评估；评价", "Teachers assess progress every week."),
                WordDraft("assume", "[əˈsuːm]", "vt. 假设；承担", "Do not assume all results are correct."),
                WordDraft("attribute", "[əˈtrɪbjuːt]", "vt. 归因于；n. 属性", "Success is attributed to persistence."),
                WordDraft("available", "[əˈveɪləbl]", "adj. 可获得的", "More resources are now available.")
            )
        )
    )

    fun buildWords(bookId: Long, drafts: List<WordDraft>): List<Word> {
        return drafts.map { draft ->
            Word(
                word = draft.word,
                phonetic = draft.phonetic,
                meaning = draft.meaning,
                example = draft.example,
                bookId = bookId
            )
        }
    }
}
