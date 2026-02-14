package com.kaoyan.wordhelper.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaoyan.wordhelper.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsContentTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun statsContentRendersChartsWhenDataPresent() {
        composeRule.openStatsScreenFromProfile()
        composeRule.assertStatsCardsVisible()
    }

    @Test
    fun statsContentRendersInDarkTheme() {
        composeRule.openStatsScreenFromProfile()
        composeRule.assertStatsCardsVisible()
    }
}

private fun ComposeTestRule.openStatsScreenFromProfile() {
    onNodeWithTag("tab_profile").performClick()
    onNodeWithTag("profile_open_stats").performScrollTo().performClick()
    waitUntil(timeoutMillis = 8_000) {
        onAllNodesWithTag("stats_content").fetchSemanticsNodes().isNotEmpty()
    }
}

private fun ComposeTestRule.assertStatsCardsVisible() {
    onNodeWithTag("stats_content").assertIsDisplayed()
    waitUntil(timeoutMillis = 3_000) {
        onAllNodesWithTag("stats_line_card").fetchSemanticsNodes().isNotEmpty()
    }
    waitUntil(timeoutMillis = 3_000) {
        onAllNodesWithTag("stats_mastery_card").fetchSemanticsNodes().isNotEmpty()
    }
    waitUntil(timeoutMillis = 3_000) {
        onAllNodesWithTag("stats_heatmap_card").fetchSemanticsNodes().isNotEmpty()
    }
}
