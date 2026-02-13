package com.kaoyan.wordhelper.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun appTypography(fontScale: Float): Typography {
    val scale = fontScale.coerceIn(0.85f, 1.3f)
    return Typography(
        // 单词 - 超大号衬线体
        displayLarge = TextStyle(
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            fontSize = (48f * scale).sp,
            lineHeight = (56f * scale).sp
        ),
        // 音标
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (20f * scale).sp,
            lineHeight = (28f * scale).sp
        ),
        // 释义
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (18f * scale).sp,
            lineHeight = (26f * scale).sp
        ),
        // 按钮
        labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = (16f * scale).sp,
            lineHeight = (24f * scale).sp
        ),
        // 小字说明
        bodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = (14f * scale).sp,
            lineHeight = (20f * scale).sp
        )
    )
}
