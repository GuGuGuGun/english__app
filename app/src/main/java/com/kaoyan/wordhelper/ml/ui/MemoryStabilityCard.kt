package com.kaoyan.wordhelper.ml.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 记忆稳定性指数卡片
 * 显示用户整体记忆保留率 + 模型置信度 + 最佳学习时段
 */
@Composable
fun MemoryStabilityCard(
    stabilityIndex: Float,
    confidence: Float,
    optimalHourStart: Int,
    optimalHourEnd: Int,
    sampleCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ml_stability_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "记忆稳定性",
                style = MaterialTheme.typography.titleLarge
            )

            // 稳定性指数进度条
            val animatedStability by animateFloatAsState(
                targetValue = stabilityIndex.coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 600),
                label = "stability"
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "记忆保留率",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${(animatedStability * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = stabilityColor(animatedStability)
                    )
                }
                LinearProgressIndicator(
                    progress = { animatedStability },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = stabilityColor(animatedStability),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            // 模型状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                InfoChip(
                    label = "模型置信度",
                    value = "${(confidence * 100).roundToInt()}%"
                )
                InfoChip(
                    label = "训练样本",
                    value = "$sampleCount"
                )
            }

            // 最佳学习时段
            if (optimalHourStart in 0..23 && optimalHourEnd in 0..23) {
                Text(
                    text = "最佳学习时段：${formatHour(optimalHourStart)} - ${formatHour(optimalHourEnd)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 冷启动提示
            if (sampleCount < 50) {
                Text(
                    text = "继续学习以提升预测准确度（$sampleCount/50）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun stabilityColor(value: Float) = when {
    value >= 0.85f -> MaterialTheme.colorScheme.primary
    value >= 0.7f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private fun formatHour(hour: Int): String {
    return "${hour.toString().padStart(2, '0')}:00"
}
