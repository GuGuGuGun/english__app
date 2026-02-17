package com.kaoyan.wordhelper.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaoyan.wordhelper.ui.component.HeatmapGrid
import com.kaoyan.wordhelper.ui.component.HeatmapLegend
import com.kaoyan.wordhelper.ui.component.MasteryDonutChart
import com.kaoyan.wordhelper.ui.component.MasteryLegend
import com.kaoyan.wordhelper.ui.component.MemoryLineChart
import com.kaoyan.wordhelper.ui.theme.AlertRed
import com.kaoyan.wordhelper.ui.theme.FuzzyYellow
import com.kaoyan.wordhelper.ui.theme.KnownGreen
import com.kaoyan.wordhelper.ui.viewmodel.CalendarMode
import com.kaoyan.wordhelper.ui.viewmodel.ForecastDayUi
import com.kaoyan.wordhelper.ui.viewmodel.ForecastWordPreview
import com.kaoyan.wordhelper.ui.viewmodel.HeatmapCell
import com.kaoyan.wordhelper.ui.viewmodel.LineChartEntry
import com.kaoyan.wordhelper.ui.viewmodel.MasteryDistribution
import com.kaoyan.wordhelper.ui.viewmodel.StatsRange
import com.kaoyan.wordhelper.ui.viewmodel.StatsUiState
import com.kaoyan.wordhelper.ui.viewmodel.StatsViewModel
import com.kaoyan.wordhelper.util.PressureLevel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "学习数据") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        StatsContent(
            uiState = uiState,
            onRangeSelect = viewModel::updateRange,
            onCalendarModeSelect = viewModel::updateCalendarMode,
            onForecastDateSelect = viewModel::selectForecastDate,
            onPullForward = viewModel::pullForwardForSelectedDate,
            modifier = Modifier
                .fillMaxSize()
                .testTag("stats_content")
                .padding(innerPadding)
        )
    }
}

@Composable
fun StatsContent(
    uiState: StatsUiState,
    onRangeSelect: (StatsRange) -> Unit,
    onCalendarModeSelect: (CalendarMode) -> Unit,
    onForecastDateSelect: (LocalDate) -> Unit,
    onPullForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            LearningCalendar(
                mode = uiState.calendarMode,
                heatmap = uiState.heatmap,
                forecast = uiState.forecast,
                selectedForecastDate = uiState.selectedForecastDate,
                selectedForecastWords = uiState.selectedForecastWords,
                gestureEasyCount = uiState.gestureEasyCount,
                gestureNotebookCount = uiState.gestureNotebookCount,
                todayFuzzyCount = uiState.todayFuzzyCount,
                todayRecognizedCount = uiState.todayRecognizedCount,
                forecastLoadLabel = uiState.forecastLoadLabel,
                onModeSelect = onCalendarModeSelect,
                onForecastDateSelect = onForecastDateSelect,
                onPullForward = onPullForward
            )
        }
        item {
            RangeSelector(
                selected = uiState.range,
                onSelect = onRangeSelect
            )
        }
        item {
            LineChartCard(lineData = uiState.lineData, range = uiState.range)
        }
        item {
            MasteryChartCard(mastery = uiState.mastery)
        }
    }
}

@Composable
private fun LearningCalendar(
    mode: CalendarMode,
    heatmap: List<HeatmapCell>,
    forecast: List<ForecastDayUi>,
    selectedForecastDate: LocalDate?,
    selectedForecastWords: List<ForecastWordPreview>,
    gestureEasyCount: Int,
    gestureNotebookCount: Int,
    todayFuzzyCount: Int,
    todayRecognizedCount: Int,
    forecastLoadLabel: String,
    onModeSelect: (CalendarMode) -> Unit,
    onForecastDateSelect: (LocalDate) -> Unit,
    onPullForward: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stats_heatmap_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "学习日历", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarMode.entries.forEach { item ->
                    FilterChip(
                        selected = item == mode,
                        onClick = { onModeSelect(item) },
                        label = { Text(text = item.label) }
                    )
                }
            }
            Text(
                text = "手势统计（当前范围）：左滑太简单 $gestureEasyCount · 右滑生词本 $gestureNotebookCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("stats_gesture_summary")
            )
            Text(
                text = "今日认词：模糊 $todayFuzzyCount · 认识 $todayRecognizedCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("stats_today_recognition")
            )

            if (mode == CalendarMode.HISTORY) {
                Text(
                    text = "过去 12 周学习热力图",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (heatmap.isEmpty()) {
                    EmptyState(text = "暂无学习记录")
                } else {
                    HeatmapGrid(cells = heatmap)
                    HeatmapLegend()
                }
            } else {
                FutureForecastSection(
                    forecast = forecast,
                    selectedDate = selectedForecastDate,
                    selectedWords = selectedForecastWords,
                    forecastLoadLabel = forecastLoadLabel,
                    onDateSelect = onForecastDateSelect,
                    onPullForward = onPullForward
                )
            }
        }
    }
}

@Composable
private fun FutureForecastSection(
    forecast: List<ForecastDayUi>,
    selectedDate: LocalDate?,
    selectedWords: List<ForecastWordPreview>,
    forecastLoadLabel: String,
    onDateSelect: (LocalDate) -> Unit,
    onPullForward: () -> Unit
) {
    Text(
        text = "未来 7 天复习压力",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (forecast.isEmpty()) {
        EmptyState(text = "暂无预测数据")
        return
    }
    Text(
        text = forecastLoadLabel,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("stats_forecast_source")
    )

    ReviewPressureChart(
        days = forecast,
        selectedDate = selectedDate,
        onDateSelect = onDateSelect
    )
    PressureLegend()

    val selectedDay = forecast.firstOrNull { it.date == selectedDate } ?: forecast.first()
    Text(
        text = "任务详情：${selectedDay.date.format(DATE_FORMATTER)}",
        style = MaterialTheme.typography.titleSmall
    )
    Text(
        text = "复习 ${selectedDay.reviewCount} · 新词 ${selectedDay.newWordQuota} · 总计 ${selectedDay.totalCount}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = "压力等级：${pressureLabel(selectedDay.pressureLevel)}",
        style = MaterialTheme.typography.bodySmall
    )
    if (selectedDay.pressureLevel == PressureLevel.HIGH) {
        Row(
            modifier = Modifier.testTag("stats_pressure_warning"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "高压预警",
                tint = AlertRed
            )
            Text(
                text = "高压预警：建议提前消化或分散复习压力。",
                style = MaterialTheme.typography.bodySmall,
                color = AlertRed
            )
        }
    }

    if (selectedWords.isEmpty()) {
        Text(
            text = "当日暂无可预览任务",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            selectedWords.forEach { item ->
                Text(
                    text = "• ${item.word}  ${item.meaning}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    val canPullForward = selectedDay.date.isAfter(LocalDate.now()) && selectedDay.reviewCount > 0
    OutlinedButton(
        onClick = onPullForward,
        enabled = canPullForward,
        modifier = Modifier.testTag("stats_pull_forward_button")
    ) {
        Text(text = "提前消化 5 个")
    }
}

@Composable
private fun ReviewPressureChart(
    days: List<ForecastDayUi>,
    selectedDate: LocalDate?,
    onDateSelect: (LocalDate) -> Unit
) {
    val maxTotal = (days.maxOfOrNull { it.totalCount } ?: 1).coerceAtLeast(1)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .testTag("stats_forecast_card"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEach { day ->
            val fraction = (day.totalCount.toFloat() / maxTotal.toFloat()).coerceAtLeast(0.08f)
            val isSelected = day.date == selectedDate
            val barColor = pressureColor(day.pressureLevel)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .semantics {
                        contentDescription = buildString {
                            append(chartDateA11yLabel(day.date, day.isToday))
                            append("，")
                            append(pressureLabel(day.pressureLevel))
                            append("，总计")
                            append(day.totalCount)
                            append("，复习")
                            append(day.reviewCount)
                            append("，新词")
                            append(day.newWordQuota)
                        }
                    }
                    .clickable { onDateSelect(day.date) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = day.totalCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height((120 * fraction).dp)
                        .background(
                            color = barColor,
                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                        )
                        .then(
                            if (isSelected || day.isToday) {
                                Modifier.background(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                )
                Text(
                    text = chartDateLabel(day.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = pressureIcon(day.pressureLevel),
                        contentDescription = null,
                        tint = barColor,
                        modifier = Modifier
                            .width(10.dp)
                            .height(10.dp)
                    )
                    Text(
                        text = pressureLabel(day.pressureLevel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PressureLegend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PressureLegendItem(
            text = "低压(<50)",
            color = KnownGreen,
            icon = Icons.Filled.CheckCircle,
            iconDescription = "低压"
        )
        PressureLegendItem(
            text = "中压(50-100)",
            color = FuzzyYellow,
            icon = Icons.Filled.RemoveCircle,
            iconDescription = "中压"
        )
        PressureLegendItem(
            text = "高压(>100)",
            color = AlertRed,
            icon = Icons.Filled.Warning,
            iconDescription = "高压"
        )
    }
}

@Composable
private fun PressureLegendItem(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconDescription: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription,
            tint = color,
            modifier = Modifier
                .width(12.dp)
                .height(12.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RangeSelector(
    selected: StatsRange,
    onSelect: (StatsRange) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsRange.values().forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                label = { Text(text = range.label) }
            )
        }
    }
}

@Composable
private fun LineChartCard(
    lineData: List<LineChartEntry>,
    range: StatsRange
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stats_line_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "记忆曲线", style = MaterialTheme.typography.titleLarge)
            Text(text = range.label, style = MaterialTheme.typography.bodySmall)
            if (lineData.isEmpty()) {
                EmptyState(text = "暂无学习记录")
            } else {
                MemoryLineChart(data = lineData, modifier = Modifier.height(220.dp))
            }
        }
    }
}

@Composable
private fun MasteryChartCard(mastery: MasteryDistribution) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stats_mastery_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "掌握度分布", style = MaterialTheme.typography.titleLarge)
            Text(text = "当前词书统计", style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MasteryStat(label = "未学", value = mastery.unlearned)
                MasteryStat(label = "学习中", value = mastery.learning)
                MasteryStat(label = "已掌握", value = mastery.mastered)
            }
            if (mastery.total <= 0) {
                EmptyState(text = "暂无词书数据")
            } else {
                MasteryDonutChart(mastery = mastery, modifier = Modifier.height(200.dp))
                MasteryLegend()
                Text(
                    text = "共 ${mastery.total} 词",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MasteryStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun pressureColor(level: PressureLevel) = when (level) {
    PressureLevel.LOW -> KnownGreen.copy(alpha = 0.75f)
    PressureLevel.MEDIUM -> FuzzyYellow.copy(alpha = 0.82f)
    PressureLevel.HIGH -> AlertRed.copy(alpha = 0.78f)
}

private fun pressureLabel(level: PressureLevel): String = when (level) {
    PressureLevel.LOW -> "低压"
    PressureLevel.MEDIUM -> "中压"
    PressureLevel.HIGH -> "高压"
}

private fun pressureIcon(level: PressureLevel): androidx.compose.ui.graphics.vector.ImageVector = when (level) {
    PressureLevel.LOW -> Icons.Filled.CheckCircle
    PressureLevel.MEDIUM -> Icons.Filled.RemoveCircle
    PressureLevel.HIGH -> Icons.Filled.Warning
}

private fun chartDateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> date.format(CHART_DATE_FORMATTER)
    }
}

private fun chartDateA11yLabel(date: LocalDate, isToday: Boolean): String {
    return if (isToday) "今天" else date.format(DATE_FORMATTER)
}

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日")
private val CHART_DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d")
