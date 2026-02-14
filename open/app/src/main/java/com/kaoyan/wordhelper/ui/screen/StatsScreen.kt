package com.kaoyan.wordhelper.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaoyan.wordhelper.ui.component.HeatmapGrid
import com.kaoyan.wordhelper.ui.component.HeatmapLegend
import com.kaoyan.wordhelper.ui.component.MasteryDonutChart
import com.kaoyan.wordhelper.ui.component.MasteryLegend
import com.kaoyan.wordhelper.ui.component.MemoryLineChart
import com.kaoyan.wordhelper.ui.viewmodel.HeatmapCell
import com.kaoyan.wordhelper.ui.viewmodel.LineChartEntry
import com.kaoyan.wordhelper.ui.viewmodel.MasteryDistribution
import com.kaoyan.wordhelper.ui.viewmodel.StatsRange
import com.kaoyan.wordhelper.ui.viewmodel.StatsUiState
import com.kaoyan.wordhelper.ui.viewmodel.StatsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RangeSelector(
            selected = uiState.range,
            onSelect = onRangeSelect
        )
        LineChartCard(lineData = uiState.lineData, range = uiState.range)
        MasteryChartCard(mastery = uiState.mastery)
        HeatmapCard(cells = uiState.heatmap)
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
private fun HeatmapCard(cells: List<HeatmapCell>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stats_heatmap_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "日历热力图", style = MaterialTheme.typography.titleLarge)
            Text(text = "最近12周学习量", style = MaterialTheme.typography.bodySmall)
            if (cells.isEmpty()) {
                EmptyState(text = "暂无学习记录")
            } else {
                HeatmapGrid(cells = cells)
                HeatmapLegend()
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

