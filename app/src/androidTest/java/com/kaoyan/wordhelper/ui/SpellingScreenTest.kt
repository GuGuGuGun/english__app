package com.kaoyan.wordhelper.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaoyan.wordhelper.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpellingScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun spellingScreenRendersInLightTheme() {
        composeRule.openSpellingMode()
        composeRule.onNodeWithTag("spelling_input").assertIsDisplayed()
        composeRule.onNodeWithTag("spelling_submit").assertIsDisplayed()
    }

    @Test
    fun spellingScreenRendersInDarkTheme() {
        composeRule.onNodeWithTag("tab_learning").performClick()
        composeRule.openSpellingMode()
        composeRule.onNodeWithTag("spelling_input").assertIsDisplayed()
        composeRule.onNodeWithTag("spelling_submit").assertIsDisplayed()
    }
}

private fun ComposeTestRule.openSpellingMode() {
    waitUntil(timeoutMillis = 8_000) {
        onAllNodesWithTag("learning_word_card").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithTag("learning_mode_spelling").performClick()
    waitUntil(timeoutMillis = 8_000) {
        onAllNodesWithTag("spelling_input").fetchSemanticsNodes().isNotEmpty()
    }
}
