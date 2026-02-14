package com.kaoyan.wordhelper.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun AnimatedStarToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scale = remember { Animatable(1f) }
    var hasInteracted by remember { mutableStateOf(false) }

    LaunchedEffect(checked) {
        if (!hasInteracted) {
            hasInteracted = true
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        if (checked) {
            scale.animateTo(1.18f, animationSpec = tween(durationMillis = 120))
            scale.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else {
            scale.animateTo(1f, animationSpec = tween(durationMillis = 120))
        }
    }

    IconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (checked) "已加入生词本" else "加入生词本"
        )
    }
}
