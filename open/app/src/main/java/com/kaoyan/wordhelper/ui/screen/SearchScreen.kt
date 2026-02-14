package com.kaoyan.wordhelper.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaoyan.wordhelper.data.entity.Progress
import com.kaoyan.wordhelper.ui.component.AnimatedStarToggle
import com.kaoyan.wordhelper.ui.viewmodel.SearchViewModel
import com.kaoyan.wordhelper.ui.viewmodel.SearchWordItem
import com.kaoyan.wordhelper.util.DateUtils
@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun SearchScreen(viewModel: SearchViewModel = viewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    var selectedWordId by remember { mutableStateOf<Long?>(null) }
    val selectedItem = results.firstOrNull { it.word.id == selectedWordId }

    if (selectedItem != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedWordId = null },
            sheetState = sheetState
        ) {
            SearchDetailSheet(
                item = selectedItem,
                onAddToNewWords = { viewModel.toggleNewWord(selectedItem.word, selectedItem.isInNewWords) }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::updateQuery,
            leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(text = "搜索单词") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_input"),
            singleLine = true
        )

        if (results.isEmpty() && query.isNotBlank()) {
            Text(text = "没有匹配结果", style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.word.id }) { item ->
                SearchResultItem(
                    item = item,
                    onToggle = { viewModel.toggleNewWord(item.word, item.isInNewWords) },
                    onClick = { selectedWordId = item.word.id }
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    item: SearchWordItem,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = item.word.word, style = MaterialTheme.typography.bodyLarge)
                if (item.word.phonetic.isNotBlank()) {
                    Text(text = item.word.phonetic, style = MaterialTheme.typography.bodySmall)
                }
                val statusLabel = when (item.progress?.status) {
                    Progress.STATUS_MASTERED -> "已掌握"
                    Progress.STATUS_LEARNING -> "学习中"
                    Progress.STATUS_NEW -> "新词"
                    else -> "未学习"
                }
                val nextReviewTime = item.progress?.nextReviewTime ?: 0L
                val nextReviewText = DateUtils.formatReviewDay(nextReviewTime)
                Text(
                    text = "$statusLabel，下次复习：$nextReviewText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            if (item.word.meaning.isNotBlank()) {
                Text(
                    text = item.word.meaning,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.size(8.dp))
            AnimatedStarToggle(
                checked = item.isInNewWords,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
private fun SearchDetailSheet(
    item: SearchWordItem,
    onAddToNewWords: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val statusLabel = when (item.progress?.status) {
            Progress.STATUS_MASTERED -> "已掌握"
            Progress.STATUS_LEARNING -> "学习中"
            Progress.STATUS_NEW -> "新词"
            else -> "未学习"
        }
        val nextReviewTime = item.progress?.nextReviewTime ?: 0L
        val nextReviewText = DateUtils.formatReviewDay(nextReviewTime)
        Text(text = item.word.word, style = MaterialTheme.typography.displaySmall)
        if (item.word.phonetic.isNotBlank()) {
            Text(text = item.word.phonetic, style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider()
        if (item.word.meaning.isNotBlank()) {
            Text(text = item.word.meaning, style = MaterialTheme.typography.bodyLarge)
        }
        if (item.word.example.isNotBlank()) {
            Text(
                text = item.word.example,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(text = "掌握状态：$statusLabel", style = MaterialTheme.typography.bodySmall)
        Text(text = "下次复习：$nextReviewText", style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = { if (!item.isInNewWords) onAddToNewWords() },
            enabled = !item.isInNewWords,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (item.isInNewWords) "已在生词本" else "加入生词本")
        }
    }
}
