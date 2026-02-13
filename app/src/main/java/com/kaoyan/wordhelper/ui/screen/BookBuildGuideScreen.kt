package com.kaoyan.wordhelper.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookBuildGuideScreen(onBack: () -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "词书构建教程") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GuideCard(
                    title = "1. 文件格式",
                    lines = listOf(
                        "支持 txt/csv 文本文件，推荐 UTF-8 编码。",
                        "每行一条词条，推荐用 Tab 分隔：",
                        "word<TAB>phonetic<TAB>meaning<TAB>example",
                        "也支持逗号、竖线、分号等分隔符。"
                    )
                )
            }
            item {
                GuideCard(
                    title = "2. 最小可用内容",
                    lines = listOf(
                        "最少只写单词：abandon",
                        "推荐至少写到“单词 + 释义”：",
                        "abandon<TAB>vt. 放弃；抛弃",
                        "带音标与例句会有更完整的学习体验。"
                    )
                )
            }
            item {
                GuideCard(
                    title = "3. 示例模板",
                    lines = listOf(
                        "abandon\t[əˈbændən]\tvt. 放弃；抛弃\tHe abandoned the plan.",
                        "allocate\t[ˈæləkeɪt]\tvt. 分配\tThe team allocated resources fairly.",
                        "in terms of\t\t在……方面\tIn terms of speed, this is better."
                    )
                )
            }
            item {
                GuideCard(
                    title = "4. 导入前检查",
                    lines = listOf(
                        "词书名称是否明确（如：考研高频动词）。",
                        "是否存在空行和重复词条。",
                        "释义是否简洁一致，避免过长段落。",
                        "导入后可先用“导出词书”做回读校验。"
                    )
                )
            }
            item {
                GuideCard(
                    title = "5. 推荐流程",
                    lines = listOf(
                        "先用 50-100 条做小规模试导入。",
                        "确认展示、拼写模式、搜索结果正常。",
                        "再批量扩展到完整词书。",
                        "定期导出做版本备份。"
                    )
                )
            }
        }
    }
}

@Composable
private fun GuideCard(
    title: String,
    lines: List<String>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
