package com.kaoyan.wordhelper.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.testTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaoyan.wordhelper.ui.screen.StatsContent
import com.kaoyan.wordhelper.ui.theme.KaoyanWordTheme
import com.kaoyan.wordhelper.ui.viewmodel.CalendarMode
import com.kaoyan.wordhelper.ui.viewmodel.ForecastDayUi
import com.kaoyan.wordhelper.ui.viewmodel.ForecastWordPreview
import com.kaoyan.wordhelper.ui.viewmodel.StatsUiState
import com.kaoyan.wordhelper.util.PressureLevel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StatsCalendarComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun futureModeShowsGestureSummaryForecastSourceAndHighPressureWarning() {
        composeRule.setContent {
            KaoyanWordTheme {
                StatsContent(
                    uiState = buildFutureUiState(),
                    onRangeSelect = {},
                    onCalendarModeSelect = {},
                    onForecastDateSelect = {},
                    onPullForward = {},
                    modifier = Modifier.testTag("stats_content")
                )
            }
        }

        composeRule.onNodeWithTag("stats_content").assertIsDisplayed()
        composeRule.onNodeWithTag("stats_gesture_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("stats_forecast_source").assertIsDisplayed()
        composeRule.onNodeWithTag("stats_pressure_warning").assertIsDisplayed()
        composeRule.onNodeWithText("高压预警：建议提前消化或分散复习压力。").assertIsDisplayed()
    }

    @Test
    fun reviewPressureBarExposesAccessibleDescription() {
        composeRule.setContent {
            KaoyanWordTheme {
                StatsContent(
                    uiState = buildFutureUiState(),
                    onRangeSelect = {},
                    onCalendarModeSelect = {},
                    onForecastDateSelect = {},
                    onPullForward = {},
                    modifier = Modifier.testTag("stats_content")
                )
            }
        }

        composeRule.runOnIdle {
            assertTrue(
                "expected accessible forecast bar description to exist",
                composeRule
                    .onAllNodesWithContentDescription("今天，高压，总计130，复习120，新词10")
                    .fetchSemanticsNodes().isNotEmpty()
            )
        }
    }

    @Test
    fun historyModeHidesFutureOnlySections() {
        composeRule.setContent {
            KaoyanWordTheme {
                StatsContent(
                    uiState = buildFutureUiState().copy(calendarMode = CalendarMode.HISTORY),
                    onRangeSelect = {},
                    onCalendarModeSelect = {},
                    onForecastDateSelect = {},
                    onPullForward = {},
                    modifier = Modifier.testTag("stats_content")
                )
            }
        }

        composeRule.runOnIdle {
            assertTrue(
                "future source label should be hidden in history mode",
                composeRule.onAllNodesWithTag("stats_forecast_source").fetchSemanticsNodes().isEmpty()
            )
            assertTrue(
                "high pressure warning should be hidden in history mode",
                composeRule.onAllNodesWithTag("stats_pressure_warning").fetchSemanticsNodes().isEmpty()
            )
        }
    }

    private fun buildFutureUiState(): StatsUiState {
        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        return StatsUiState(
            calendarMode = CalendarMode.FUTURE,
            gestureEasyCount = 2,
            gestureNotebookCount = 3,
            forecastLoadLabel = "预测来源：缓存命中 · 耗时 3ms",
            forecast = listOf(
                ForecastDayUi(
                    date = today,
                    reviewCount = 120,
                    newWordQuota = 10,
                    totalCount = 130,
                    pressureLevel = PressureLevel.HIGH,
                    isToday = true
                ),
                ForecastDayUi(
                    date = tomorrow,
                    reviewCount = 15,
                    newWordQuota = 20,
                    totalCount = 35,
                    pressureLevel = PressureLevel.LOW,
                    isToday = false
                )
            ),
            selectedForecastDate = today,
            selectedForecastWords = listOf(
                ForecastWordPreview(
                    word = "abandon",
                    meaning = "放弃"
                )
            )
        )
    }
}
