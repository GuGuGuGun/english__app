package com.kaoyan.wordhelper

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M6AcceptanceTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun learningScreenShowsWordCardAndActions() {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("learning_word_card").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("learning_word_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("learning_action_again").assertIsDisplayed()
        composeTestRule.onNodeWithTag("learning_action_hard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("learning_action_good").assertIsDisplayed()
    }

    @Test
    fun canNavigateTabsAndSeeKeyElements() {
        composeTestRule.onNodeWithText("\u67e5\u8bcd").performClick()
        composeTestRule.onNodeWithTag("search_input").assertIsDisplayed()

        composeTestRule.onNodeWithText("\u8bcd\u5e93").performClick()
        composeTestRule.onNodeWithText("\u6211\u7684\u8bcd\u5e93").assertIsDisplayed()

        composeTestRule.onNodeWithText("\u6211\u7684").performClick()
        composeTestRule.onNodeWithText("\u5b66\u4e60\u7edf\u8ba1").assertIsDisplayed()
    }
}

private fun ComposeTestRule.waitUntil(
    timeoutMillis: Long = 1_000,
    condition: () -> Boolean
) {
    val start = System.currentTimeMillis()
    do {
        if (condition()) return
        waitForIdle()
        Thread.sleep(50)
    } while (System.currentTimeMillis() - start < timeoutMillis)
    throw AssertionError("Condition not met within $timeoutMillis ms")
}
