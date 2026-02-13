package com.kaoyan.wordhelper

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.kaoyan.wordhelper.data.entity.Book
import com.kaoyan.wordhelper.data.model.StudyRating
import com.kaoyan.wordhelper.data.model.WordDraft
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M12AcceptanceTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun spellingFlowShowsWrongAnswerFeedback() {
        composeTestRule.waitForLearningWordCard()

        composeTestRule.onNodeWithTag("learning_mode_spelling").performClick()
        composeTestRule.onNodeWithTag("spelling_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("spelling_input").performTextInput("zzzz")
        composeTestRule.onNodeWithTag("spelling_submit").performClick()
        composeTestRule.onNodeWithTag("spelling_input").assertIsDisplayed()
    }

    @Test
    fun reviewTimeTagOpensScheduleDialog() {
        composeTestRule.waitForLearningWordCard()

        composeTestRule.onNodeWithTag("learning_review_tag").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("review_dialog_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("review_last_review_time").assertIsDisplayed()
        composeTestRule.onNodeWithTag("review_next_review_time").assertIsDisplayed()
    }

    @Test
    fun againActionDoesNotLockButtons() {
        composeTestRule.waitForLearningWordCard()

        composeTestRule.onNodeWithTag("learning_action_again").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeTestRule.onNodeWithTag("learning_action_again").assertIsEnabled()
                composeTestRule.onNodeWithTag("learning_action_hard").assertIsEnabled()
                composeTestRule.onNodeWithTag("learning_action_good").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithTag("learning_action_hard").performClick()
    }

    @Test
    fun learningProgressAdvancesAfterAnswer() {
        composeTestRule.waitForLearningWordCard()
        val beforeIndex = composeTestRule.readLearningProgressIndex()

        composeTestRule.onNodeWithTag("learning_action_good").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.readLearningProgressIndex() > beforeIndex
        }
    }

    @Test
    fun importedBookRemainingCountDecreasesAfterAnswer() {
        composeTestRule.waitForLearningWordCard()
        val app = composeTestRule.activity.application as KaoyanWordApp
        val originalBookId = runBlocking { app.database.bookDao().getActiveBook()?.id }
        val importedBookName = "导入词书回归_${System.currentTimeMillis()}"
        val drafts = (1..25).map { index ->
            WordDraft(
                word = "imported_word_$index",
                meaning = "导入释义$index"
            )
        }
        val importedBookId = runBlocking {
            app.repository.importBook(importedBookName, drafts)
        }

        try {
            runBlocking {
                app.repository.switchBook(importedBookId)
            }
            composeTestRule.waitUntil(timeoutMillis = 8_000) {
                runCatching {
                    composeTestRule.onNodeWithText(importedBookName, substring = true).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            val (beforeIndex, beforeTotal) = composeTestRule.readLearningProgress()
            val beforeRemaining = (beforeTotal - beforeIndex).coerceAtLeast(0)

            composeTestRule.onNodeWithTag("learning_action_good").performClick()

            composeTestRule.waitUntil(timeoutMillis = 8_000) {
                val (afterIndex, afterTotal) = composeTestRule.readLearningProgress()
                val afterRemaining = (afterTotal - afterIndex).coerceAtLeast(0)
                afterRemaining < beforeRemaining
            }
        } finally {
            runBlocking {
                if (originalBookId != null) {
                    app.repository.switchBook(originalBookId)
                }
                app.repository.getBookById(importedBookId)?.let { app.repository.deleteBookWithData(it) }
            }
        }
    }

    @Test
    fun importedBookRemainingCountDoesNotIncreaseAfterAgain() {
        composeTestRule.waitForLearningWordCard()
        val app = composeTestRule.activity.application as KaoyanWordApp
        val originalBookId = runBlocking { app.database.bookDao().getActiveBook()?.id }
        val originalLimit = runBlocking { app.settingsRepository.settingsFlow.first().newWordsLimit }
        val baselineTodayLearned = runBlocking { app.repository.getTodayNewWordsCount() }
        val boostedLimit = (baselineTodayLearned + 20).coerceIn(5, 500)
        val importedBookName = "不认识计数回归_${System.currentTimeMillis()}"
        val drafts = (1..80).map { index ->
            WordDraft(
                word = "again_word_$index",
                meaning = "不认识释义$index"
            )
        }
        val importedBookId = runBlocking {
            app.settingsRepository.updateNewWordsLimit(boostedLimit)
            app.repository.importBook(importedBookName, drafts)
        }

        try {
            runBlocking {
                app.repository.switchBook(importedBookId)
            }
            composeTestRule.waitUntil(timeoutMillis = 8_000) {
                runCatching {
                    composeTestRule.onNodeWithText(importedBookName, substring = true).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            val (beforeIndex, beforeTotal) = composeTestRule.readLearningProgress()
            val beforeRemaining = (beforeTotal - beforeIndex).coerceAtLeast(0)

            composeTestRule.onNodeWithTag("learning_action_again").performClick()

            composeTestRule.waitUntil(timeoutMillis = 8_000) {
                val (afterIndex, afterTotal) = composeTestRule.readLearningProgress()
                val afterRemaining = (afterTotal - afterIndex).coerceAtLeast(0)
                afterRemaining <= beforeRemaining
            }
            val (afterIndex, afterTotal) = composeTestRule.readLearningProgress()
            val afterRemaining = (afterTotal - afterIndex).coerceAtLeast(0)
            assertTrue(
                "remaining count should not increase after AGAIN: before=$beforeRemaining after=$afterRemaining",
                afterRemaining <= beforeRemaining
            )
        } finally {
            runBlocking {
                app.settingsRepository.updateNewWordsLimit(originalLimit)
                if (originalBookId != null) {
                    app.repository.switchBook(originalBookId)
                }
                app.repository.getBookById(importedBookId)?.let { app.repository.deleteBookWithData(it) }
            }
        }
    }

    @Test
    fun sharedProgressAcrossBooksForSameWord() {
        composeTestRule.waitForLearningWordCard()
        val app = composeTestRule.activity.application as KaoyanWordApp
        val originalBookId = runBlocking { app.database.bookDao().getActiveBook()?.id }
        val presetBookId = runBlocking {
            app.database.bookDao().getAllBooksList().firstOrNull { it.type == Book.TYPE_PRESET }?.id
        } ?: return

        val importedBookName = "共享进度回归_${System.currentTimeMillis()}"
        val importedBookId = runBlocking {
            app.repository.importBook(
                name = importedBookName,
                drafts = listOf(WordDraft(word = "abandon", meaning = "导入释义"))
            )
        }

        try {
            runBlocking {
                app.repository.switchBook(importedBookId)
            }
            composeTestRule.waitUntil(timeoutMillis = 8_000) {
                runCatching {
                    composeTestRule.onNodeWithText(importedBookName, substring = true).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            composeTestRule.onNodeWithTag("learning_action_good").performClick()
            composeTestRule.waitUntil(timeoutMillis = 8_000) {
                runBlocking {
                    val wordId = app.database.wordDao().getWordIdByKey("abandon")
                    if (wordId == null) {
                        false
                    } else {
                        val importedProgress = app.database.progressDao().getProgress(wordId, importedBookId)
                        val presetProgress = app.database.progressDao().getProgress(wordId, presetBookId)
                        importedProgress != null &&
                            presetProgress != null &&
                            importedProgress.reviewCount > 0 &&
                            presetProgress.reviewCount > 0
                    }
                }
            }

            runBlocking {
                val wordId = app.database.wordDao().getWordIdByKey("abandon")
                assertNotNull("wordId should exist for abandon", wordId)
                val safeWordId = wordId ?: return@runBlocking
                val importedProgress = app.database.progressDao().getProgress(safeWordId, importedBookId)
                val presetProgress = app.database.progressDao().getProgress(safeWordId, presetBookId)
                assertNotNull("imported progress missing", importedProgress)
                assertNotNull("preset progress missing", presetProgress)
                val imported = importedProgress ?: return@runBlocking
                val preset = presetProgress ?: return@runBlocking
                assertEquals("status should be shared", imported.status, preset.status)
                assertEquals("reviewCount should be shared", imported.reviewCount, preset.reviewCount)
                assertEquals("intervalDays should be shared", imported.intervalDays, preset.intervalDays)
            }
        } finally {
            runBlocking {
                if (originalBookId != null) {
                    app.repository.switchBook(originalBookId)
                }
                app.repository.getBookById(importedBookId)?.let { app.repository.deleteBookWithData(it) }
            }
        }
    }

    @Test
    fun importedBookInheritsLearnedProgressForExistingWord() {
        composeTestRule.waitForLearningWordCard()
        val app = composeTestRule.activity.application as KaoyanWordApp
        val originalBookId = runBlocking { app.database.bookDao().getActiveBook()?.id }
        val presetBookId = runBlocking {
            app.database.bookDao().getAllBooksList().firstOrNull { it.type == Book.TYPE_PRESET }?.id
        } ?: return

        val targetWord = runBlocking {
            val abandonId = app.database.wordDao().getWordIdByKey("abandon")
            if (abandonId != null) {
                app.repository.getWordById(abandonId)
            } else {
                app.repository.getWordsByBookList(presetBookId).firstOrNull()
            }
        } ?: return

        val importedBookName = "继承已学回归_${System.currentTimeMillis()}"
        val importedBookId = runBlocking {
            app.repository.applyStudyResult(targetWord.id, presetBookId, StudyRating.GOOD)
            app.repository.importBook(
                name = importedBookName,
                drafts = listOf(WordDraft(word = targetWord.word, meaning = "导入释义"))
            )
        }

        try {
            runBlocking {
                val importedProgress = app.database.progressDao().getProgress(targetWord.id, importedBookId)
                assertNotNull("imported progress should be synced from global progress", importedProgress)
                val learnedCount = app.repository.getLearnedCount(importedBookId).first()
                assertTrue(
                    "learned count should include synced word: learned=$learnedCount",
                    learnedCount >= 1
                )
            }
        } finally {
            runBlocking {
                if (originalBookId != null) {
                    app.repository.switchBook(originalBookId)
                }
                app.repository.getBookById(importedBookId)?.let { app.repository.deleteBookWithData(it) }
            }
        }
    }

    @Test
    fun changingDailyLimitDoesNotResetTodayLearningProgress() {
        composeTestRule.waitForLearningWordCard()
        val app = composeTestRule.activity.application as KaoyanWordApp
        val originalBookId = runBlocking { app.database.bookDao().getActiveBook()?.id }
        val originalLimit = runBlocking { app.settingsRepository.settingsFlow.first().newWordsLimit }
        val baselineTodayLearned = runBlocking { app.repository.getTodayNewWordsCount() }
        val limitA = (baselineTodayLearned + 2).coerceIn(1, 500)
        val limitB = (baselineTodayLearned + 4).coerceIn(1, 500)

        val importedBookName = "每日配额回归_${System.currentTimeMillis()}"
        val importedBookId = runBlocking {
            app.repository.importBook(
                name = importedBookName,
                drafts = (1..30).map { index -> WordDraft(word = "quota_word_$index", meaning = "配额释义$index") }
            )
        }

        try {
            runBlocking {
                app.settingsRepository.updateNewWordsLimit(limitA)
                app.repository.switchBook(importedBookId)
            }
            composeTestRule.waitUntil(timeoutMillis = 8_000) {
                runCatching {
                    composeTestRule.onNodeWithText(importedBookName, substring = true).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            val beforeIndex = composeTestRule.readLearningProgressIndex()
            composeTestRule.onNodeWithTag("learning_action_good").performClick()
            composeTestRule.waitUntil(timeoutMillis = 8_000) {
                composeTestRule.readLearningProgressIndex() > beforeIndex
            }
            val indexAfterAnswer = composeTestRule.readLearningProgressIndex()

            runBlocking {
                app.settingsRepository.updateNewWordsLimit(limitB)
            }
            composeTestRule.waitUntil(timeoutMillis = 8_000) {
                composeTestRule.readLearningProgressIndex() >= indexAfterAnswer
            }
            val indexAfterLimitChange = composeTestRule.readLearningProgressIndex()
            assertTrue(
                "current index should not reset after changing daily limit: before=$indexAfterAnswer after=$indexAfterLimitChange",
                indexAfterLimitChange >= indexAfterAnswer
            )
        } finally {
            runBlocking {
                app.settingsRepository.updateNewWordsLimit(originalLimit)
                if (originalBookId != null) {
                    app.repository.switchBook(originalBookId)
                }
                app.repository.getBookById(importedBookId)?.let { app.repository.deleteBookWithData(it) }
            }
        }
    }

    @Test
    fun keepsOnlyOnePresetBook() {
        composeTestRule.waitForLearningWordCard()
        val app = composeTestRule.activity.application as KaoyanWordApp
        val presetBooks = runBlocking {
            app.database.bookDao().getAllBooksList().filter { it.type == Book.TYPE_PRESET }
        }
        val names = presetBooks.joinToString { "${it.id}:${it.name}" }
        assertEquals("presetBooks=$names", 1, presetBooks.size)
    }

    @Test
    fun profileStatsEntryOpensStatsScreen() {
        composeTestRule.onNodeWithTag("tab_profile").performClick()
        composeTestRule.onNodeWithTag("profile_open_stats").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 8_000) {
            composeTestRule.onAllNodesWithTag("stats_content").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("stats_content").assertIsDisplayed()
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithTag("stats_line_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithTag("stats_mastery_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithTag("stats_heatmap_card").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun coreFlowRegressionAndDarkModeSmoke() {
        composeTestRule.waitForLearningWordCard()

        composeTestRule.onNodeWithTag("tab_search").performClick()
        composeTestRule.onNodeWithTag("search_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("search_input").performTextInput("abandon")
        composeTestRule.onNodeWithText("abandon", substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithTag("tab_book_manage").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithText("已掌握率", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("tab_profile").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            composeTestRule.onAllNodesWithTag("profile_open_stats").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("profile_open_stats").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("tab_learning").performClick()
        composeTestRule.waitForLearningWordCard()
    }
}

private fun ComposeTestRule.waitForLearningWordCard() {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodesWithTag("learning_word_card").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithTag("learning_word_card").assertIsDisplayed()
}

private fun ComposeTestRule.readLearningProgressIndex(): Int {
    val (index, _) = readLearningProgress()
    return index
}

private fun ComposeTestRule.readLearningProgress(): Pair<Int, Int> {
    val semanticsText = runCatching {
        onNodeWithTag("learning_count_text")
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
    }.getOrDefault(emptyList())
    val text = semanticsText.joinToString(separator = "") { annotated -> annotated.text }
    val match = Regex("""(\d+)\s*/\s*(\d+)""").find(text) ?: return -1 to -1
    val index = match.groupValues[1].toIntOrNull() ?: -1
    val total = match.groupValues[2].toIntOrNull() ?: -1
    return index to total
}

