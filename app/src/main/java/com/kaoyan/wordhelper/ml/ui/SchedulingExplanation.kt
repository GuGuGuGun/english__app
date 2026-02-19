package com.kaoyan.wordhelper.ml.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * "为什么现在复习"调度解释器
 * 向用户解释 ML 调度的决策依据
 */
@Composable
fun SchedulingExplanationCard(
    forgetProbability: Float,
    adjustedIntervalDays: Int,
    originalIntervalDays: Int,
    confidence: Float,
    reason: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ml_explanation_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "调度解释",
                style = MaterialTheme.typography.titleLarge
            )

            // 遗忘概率
            Text(
                text = "预测遗忘概率：${(forgetProbability * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = forgetProbColor(forgetProbability)
            )

            // 间隔调整说明
            if (adjustedIntervalDays != originalIntervalDays) {
                Text(
                    text = "间隔调整：${originalIntervalDays}天 → ${adjustedIntervalDays}天",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    text = "复习间隔：${adjustedIntervalDays}天",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // 决策原因
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 置信度
            Text(
                text = "模型置信度：${(confidence * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun forgetProbColor(prob: Float) = when {
    prob <= 0.1f -> MaterialTheme.colorScheme.primary
    prob <= 0.3f -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}
