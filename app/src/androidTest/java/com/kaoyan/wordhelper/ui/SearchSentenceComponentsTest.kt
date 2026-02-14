package com.kaoyan.wordhelper.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaoyan.wordhelper.ui.screen.SearchSentenceAnalysisPanel
import com.kaoyan.wordhelper.ui.theme.KaoyanWordTheme
import com.kaoyan.wordhelper.ui.viewmodel.SearchSentenceAiState
import com.kaoyan.wordhelper.ui.viewmodel.SentenceAnalysis
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchSentenceComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun panelHiddenWhenSentenceModeDisabled() {
        composeRule.setContent {
            KaoyanWordTheme {
                SearchSentenceAnalysisPanel(
                    state = SearchSentenceAiState(isSentenceMode = false),
                    onRetry = {},
                    onForceRefresh = {}
                )
            }
        }
        composeRule.runOnIdle {
            assertTrue(
                "sentence panel should stay hidden when sentence mode is disabled",
                composeRule.onAllNodesWithTag("search_sentence_panel").fetchSemanticsNodes().isEmpty()
            )
        }
    }

    @Test
    fun panelShowsLoadingState() {
        composeRule.setContent {
            KaoyanWordTheme {
                SearchSentenceAnalysisPanel(
                    state = SearchSentenceAiState(
                        isEnabled = true,
                        isConfigured = true,
                        isSentenceMode = true,
                        isLoading = true
                    ),
                    onRetry = {},
                    onForceRefresh = {}
                )
            }
        }
        composeRule.onNodeWithTag("search_sentence_loading").assertIsDisplayed()
    }

    @Test
    fun panelShowsErrorAndRetryAction() {
        var retried = false
        composeRule.setContent {
            KaoyanWordTheme {
                SearchSentenceAnalysisPanel(
                    state = SearchSentenceAiState(
                        isEnabled = true,
                        isConfigured = true,
                        isSentenceMode = true,
                        error = "网络超时"
                    ),
                    onRetry = { retried = true },
                    onForceRefresh = {}
                )
            }
        }
        composeRule.onNodeWithTag("search_sentence_error").assertIsDisplayed()
        composeRule.onNodeWithTag("search_sentence_retry").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertTrue("retry callback should be invoked", retried)
        }
    }

    @Test
    fun panelShowsStructuredAnalysisAndRegenerateAction() {
        var regenerated = false
        composeRule.setContent {
            KaoyanWordTheme {
                SearchSentenceAnalysisPanel(
                    state = SearchSentenceAiState(
                        isEnabled = true,
                        isConfigured = true,
                        isSentenceMode = true,
                        analysis = SentenceAnalysis(
                            mainClause = "主语 + 谓语 + 宾语",
                            grammarBreakdown = "1. 定语从句修饰主语\n2. 介词短语作状语",
                            chineseTranslation = "该句描述了研究方法及其影响。"
                        )
                    ),
                    onRetry = {},
                    onForceRefresh = { regenerated = true }
                )
            }
        }
        composeRule.onNodeWithTag("search_sentence_main").assertIsDisplayed()
        composeRule.onNodeWithTag("search_sentence_grammar").assertIsDisplayed()
        composeRule.onNodeWithTag("search_sentence_translation").assertIsDisplayed()
        composeRule.onNodeWithTag("search_sentence_ai_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("search_sentence_regenerate").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertTrue("regenerate callback should be invoked", regenerated)
        }
    }
}
