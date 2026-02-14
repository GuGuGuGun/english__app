package com.kaoyan.wordhelper.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaoyan.wordhelper.KaoyanWordApp
import com.kaoyan.wordhelper.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AILabFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun aiLabConfigFlow_canSaveSettings() {
        val app = composeRule.activity.application as KaoyanWordApp
        val original = runBlocking { app.aiConfigRepository.getConfig() }
        val expectedBaseUrl = "https://api.openai.com/v1/"
        val expectedModel = "gpt-3.5-turbo"
        val expectedApiKey = "sk-ui-flow-test"

        try {
            openAiLab()
            composeRule.onNodeWithTag("ai_base_url_input").performTextClearance()
            composeRule.onNodeWithTag("ai_base_url_input").performTextInput("https://api.openai.com/v1")
            composeRule.onNodeWithTag("ai_model_input").performTextClearance()
            composeRule.onNodeWithTag("ai_model_input").performTextInput(expectedModel)
            composeRule.onNodeWithTag("ai_api_key_input").performTextClearance()
            composeRule.onNodeWithTag("ai_api_key_input").performTextInput(expectedApiKey)
            composeRule.onNodeWithTag("ai_save_config").performClick()

            composeRule.waitUntil(timeoutMillis = 8_000) {
                runBlocking {
                    val saved = app.aiConfigRepository.getConfig()
                    saved.apiBaseUrl == expectedBaseUrl &&
                        saved.modelName == expectedModel &&
                        saved.apiKey == expectedApiKey
                }
            }

            runBlocking {
                val saved = app.aiConfigRepository.getConfig()
                assertEquals(expectedBaseUrl, saved.apiBaseUrl)
                assertEquals(expectedModel, saved.modelName)
                assertEquals(expectedApiKey, saved.apiKey)
            }
        } finally {
            runBlocking { app.aiConfigRepository.saveConfig(original) }
        }
    }

    @Test
    fun aiLabRiskEntries_openCostAndPrivacyDialogs() {
        openAiLab()

        composeRule.onNodeWithTag("ai_cost_entry").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("ai_cost_dialog_confirm").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("ai_privacy_entry").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("ai_privacy_dialog_confirm").assertIsDisplayed().performClick()
    }

    private fun openAiLab() {
        composeRule.onNodeWithTag("tab_profile").performClick()
        composeRule.onNodeWithTag("profile_list")
            .performScrollToNode(hasTestTag("profile_open_ai_lab"))
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("profile_open_ai_lab").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("profile_open_ai_lab").performScrollTo().performClick()
        composeRule.onNodeWithTag("ai_base_url_input").assertIsDisplayed()
    }
}
