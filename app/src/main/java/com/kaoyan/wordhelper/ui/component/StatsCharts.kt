package com.kaoyan.wordhelper.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.kaoyan.wordhelper.ui.theme.FuzzyYellow
import com.kaoyan.wordhelper.ui.theme.KnownGreen
import com.kaoyan.wordhelper.ui.viewmodel.HeatmapCell
import com.kaoyan.wordhelper.ui.viewmodel.LineChartEntry
import com.kaoyan.wordhelper.ui.viewmodel.MasteryDistribution
import java.time.format.DateTimeFormatter
import kotlin.math.max

@Composable
fun MemoryLineChart(
    data: List<LineChartEntry>,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true
) {
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val reviewColor = MaterialTheme.colorScheme.tertiary.toArgb()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val labels = data.map { it.date.format(LABEL_FORMATTER) }
    val drawCircles = data.size <= 14

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setTouchEnabled(false)
                axisRight.isEnabled = false
                axisLeft.axisMinimum = 0f
                xAxis.position = XAxis.XAxisPosition.BOTTOM
                xAxis.setDrawGridLines(false)
                xAxis.granularity = 1f
            }
        },
        update = { chart ->
            chart.axisLeft.textColor = axisColor
            chart.xAxis.textColor = axisColor
            chart.legend.isEnabled = showLegend
            chart.legend.textColor = axisColor
            if (showLegend) {
                chart.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
            }

            val newEntries = data.mapIndexed { index, entry ->
                Entry(index.toFloat(), entry.newCount.toFloat())
            }
            val reviewEntries = data.mapIndexed { index, entry ->
                Entry(index.toFloat(), entry.reviewCount.toFloat())
            }
            val newDataSet = LineDataSet(newEntries, "新词").apply {
                color = lineColor
                setCircleColor(lineColor)
                lineWidth = 2f
                circleRadius = 3f
                setDrawValues(false)
                setDrawCircles(drawCircles)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }
            val reviewDataSet = LineDataSet(reviewEntries, "复习").apply {
                color = reviewColor
                setCircleColor(reviewColor)
                lineWidth = 2f
                circleRadius = 3f
                setDrawValues(false)
                setDrawCircles(drawCircles)
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }
            chart.data = LineData(newDataSet, reviewDataSet)
            chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            chart.xAxis.setLabelCount(max(2, labels.size / 5), false)
            chart.invalidate()
        }
    )
}

@Composable
fun MasteryDonutChart(
    mastery: MasteryDistribution,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val unlearnedColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val learningColor = FuzzyYellow.toArgb()
    val masteredColor = KnownGreen.toArgb()
    val percent = if (mastery.total > 0) {
        (mastery.mastered * 100f / mastery.total).toInt()
    } else {
        0
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            PieChart(context).apply {
                description.isEnabled = false
                setDrawEntryLabels(false)
                legend.isEnabled = false
                isDrawHoleEnabled = true
                holeRadius = 58f
                transparentCircleRadius = 62f
                setUsePercentValues(false)
            }
        },
        update = { chart ->
            val entries = listOf(
                PieEntry(mastery.unlearned.toFloat(), "未学"),
                PieEntry(mastery.learning.toFloat(), "学习中"),
                PieEntry(mastery.mastered.toFloat(), "已掌握")
            )
            val dataSet = PieDataSet(entries, "").apply {
                colors = listOf(unlearnedColor, learningColor, masteredColor)
                setDrawValues(false)
            }
            chart.data = PieData(dataSet)
            chart.centerText = "已掌握\n$percent%"
            chart.setCenterTextSize(14f)
            chart.setCenterTextColor(textColor)
            chart.invalidate()
        }
    )
}

@Composable
fun MasteryLegend() {
    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
        LegendItem(label = "未学", color = MaterialTheme.colorScheme.surfaceVariant)
        LegendItem(label = "学习中", color = FuzzyYellow)
        LegendItem(label = "已掌握", color = KnownGreen)
    }
}

@Composable
fun HeatmapGrid(cells: List<HeatmapCell>) {
    val weeks = cells.chunked(7)
    val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        dayLabels.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                weeks.forEach { week ->
                    val cell = week.getOrNull(index)
                    HeatmapCellBox(cell = cell)
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }
    }
}

@Composable
fun HeatmapLegend() {
    val colors = heatmapColors()
    Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "少", style = MaterialTheme.typography.bodySmall)
        colors.drop(1).forEach { color ->
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
        }
        Text(text = "多", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun HeatmapCellBox(cell: HeatmapCell?) {
    val colors = heatmapColors()
    val baseColor = cell?.let { colors.getOrElse(it.level) { colors.last() } } ?: Color.Transparent
    val color = if (cell?.inRange == false) {
        baseColor.copy(alpha = 0.2f)
    } else {
        baseColor
    }
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(color, RoundedCornerShape(3.dp))
    )
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun heatmapColors(): List<Color> {
    val base = MaterialTheme.colorScheme.primary
    return listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        base.copy(alpha = 0.2f),
        base.copy(alpha = 0.4f),
        base.copy(alpha = 0.6f),
        base.copy(alpha = 0.8f)
    )
}

private val LABEL_FORMATTER = DateTimeFormatter.ofPattern("M/d")
