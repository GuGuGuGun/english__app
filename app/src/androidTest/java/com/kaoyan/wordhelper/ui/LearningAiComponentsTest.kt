package com.kaoyan.wordhelper.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaoyan.wordhelper.data.model.AIContentType
import com.kaoyan.wordhelper.ui.screen.LearningAiActionSheetContent
import com.kaoyan.wordhelper.ui.screen.LearningAiResultPanel
import com.kaoyan.wordhelper.ui.theme.KaoyanWordTheme
import com.kaoyan.wordhelper.ui.viewmodel.LearningAiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearningAiComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun actionSheetRendersExpectedActions() {
        composeRule.setContent {
            KaoyanWordTheme {
            LearningAiActionSheetContent(
                isAvailable = true,
                showExampleAction = true,
                showMemoryAidSuggestion = true,
                hasExampleContent = false,
                hasMemoryContent = false,
                isLoading = false,
                onGenerateExample = {},
                onRegenerateExample = {},
                onGenerateMemoryAid = {},
                onRegenerateMemoryAid = {}
            )
            }
        }
        composeRule.onNodeWithTag("learning_ai_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("learning_ai_action_example").assertIsDisplayed()
        composeRule.onNodeWithTag("learning_ai_action_memory").assertIsDisplayed()
    }

    @Test
    fun resultPanelShowsLoadingState() {
        composeRule.setContent {
            KaoyanWordTheme {
            LearningAiResultPanel(
                aiState = LearningAiState(
                    isEnabled = true,
                    isConfigured = true,
                    isLoading = true,
                    activeType = AIContentType.EXAMPLE
                ),
                showMemoryAidSuggestion = false,
                onRetryExample = {},
                onRetryMemoryAid = {},
                onOpenAiEntry = {}
            )
            }
        }
        composeRule.onNodeWithTag("learning_ai_loading").assertIsDisplayed()
    }

    @Test
    fun resultPanelShowsErrorAndRetryAction() {
        var retried = false
        composeRule.setContent {
            KaoyanWordTheme {
            LearningAiResultPanel(
                aiState = LearningAiState(
                    isEnabled = true,
                    isConfigured = true,
                    isLoading = false,
                    activeType = AIContentType.MEMORY_AID,
                    error = "网络超时"
                ),
                showMemoryAidSuggestion = false,
                onRetryExample = {},
                onRetryMemoryAid = { retried = true },
                onOpenAiEntry = {}
            )
            }
        }
        composeRule.onNodeWithTag("learning_ai_error").assertIsDisplayed()
        composeRule.onNodeWithTag("learning_ai_retry").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertTrue("retry callback should be invoked", retried)
        }
    }

    @Test
    fun resultPanelShowsGeneratedContent() {
        composeRule.setContent {
            KaoyanWordTheme {
            LearningAiResultPanel(
                aiState = LearningAiState(
                    isEnabled = true,
                    isConfigured = true,
                    activeType = AIContentType.EXAMPLE,
                    content = "Example sentence output"
                ),
                showMemoryAidSuggestion = false,
                onRetryExample = {},
                onRetryMemoryAid = {},
                onOpenAiEntry = {}
            )
            }
        }
        composeRule.onNodeWithTag("learning_ai_content").assertIsDisplayed()
        composeRule.onNodeWithTag("learning_ai_badge").assertIsDisplayed()
        composeRule.onNodeWithText("内容由 AI 生成，请甄别参考。").assertIsDisplayed()
    }
}
