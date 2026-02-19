package com.kaoyan.wordhelper.ml.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaoyan.wordhelper.ui.theme.KnownGreen

/**
 * 24h × 7天记忆效率热力图
 * 横轴：24小时(0-23)，纵轴：7天(周一-周日)
 * 颜色深浅表示该时段的记忆效率
 */
data class MemoryHeatmapEntry(
    val dayOfWeek: Int, // 0=周一 ... 6=周日
    val hour: Int,      // 0-23
    val efficiency: Float // 0.0-1.0
)

@Composable
fun MemoryHeatmapCard(
    data: List<MemoryHeatmapEntry>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ml_heatmap_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "记忆效率热力图",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "展示不同时段的学习效率",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (data.isEmpty()) {
                Text(
                    text = "数据积累中，请继续学习…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                MemoryHeatmapView(data = data)
            }

            // 图例
            HeatmapEfficiencyLegend()
        }
    }
}

@Composable
private fun MemoryHeatmapView(
    data: List<MemoryHeatmapEntry>,
    modifier: Modifier = Modifier
) {
    val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val lowColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
    val highColor = KnownGreen.copy(alpha = 0.92f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp)
            .testTag("ml_heatmap_canvas")
    ) {
        val leftMargin = 28.dp.toPx()
        val topMargin = 20.dp.toPx()
        val cellWidth = (size.width - leftMargin) / 24f
        val cellHeight = (size.height - topMargin) / 7f

        // 小时标签（每隔4小时）
        for (h in 0..23 step 4) {
            val x = leftMargin + h * cellWidth
            drawText(
                textMeasurer = textMeasurer,
                text = "$h",
                topLeft = Offset(x, 0f),
                style = TextStyle(fontSize = 9.sp, color = labelColor)
            )
        }

        // 星期标签 + 热力图格子
        dayLabels.forEachIndexed { dayIndex, label ->
            val y = topMargin + dayIndex * cellHeight
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(4.dp.toPx(), y + cellHeight * 0.2f),
                style = TextStyle(fontSize = 9.sp, color = labelColor)
            )

            for (hour in 0..23) {
                val entry = data.firstOrNull { it.dayOfWeek == dayIndex && it.hour == hour }
                val efficiency = entry?.efficiency ?: 0f
                val color = lerpColor(lowColor, highColor, efficiency)
                val x = leftMargin + hour * cellWidth
                drawRect(
                    color = color,
                    topLeft = Offset(x + 1, y + 1),
                    size = Size(cellWidth - 2, cellHeight - 2)
                )
            }
        }
    }
}

@Composable
private fun HeatmapEfficiencyLegend() {
    val lowColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
    val highColor = KnownGreen.copy(alpha = 0.92f)
    val steps = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "低效",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.forEach { fraction ->
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(lerpColor(lowColor, highColor, fraction))
                )
            }
        }
        Text(
            text = "高效",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f
    )
}
