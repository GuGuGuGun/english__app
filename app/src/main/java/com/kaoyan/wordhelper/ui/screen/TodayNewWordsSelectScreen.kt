package com.kaoyan.wordhelper.ui.screen

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaoyan.wordhelper.ui.viewmodel.TodayNewWordCandidate
import com.kaoyan.wordhelper.ui.viewmodel.TodayNewWordsSelectUiState
import com.kaoyan.wordhelper.ui.viewmodel.TodayNewWordsSelectViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayNewWordsSelectScreen(
    viewModel: TodayNewWordsSelectViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.savedEvent.collectLatest {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "今日新词选择") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BookSelectorRow(
                uiState = uiState,
                onSelectBook = viewModel::selectBook
            )

            Text(
                text = "已选 ${uiState.selectedCount} 词 · 每日新学上限 ${uiState.dailyLimit}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(text = "搜索词条") },
                placeholder = { Text(text = "输入英文或中文释义") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::randomSelect) {
                    Text(text = "随机选取")
                }
                OutlinedButton(onClick = viewModel::clearSelection) {
                    Text(text = "清空选择")
                }
                TextButton(onClick = viewModel::savePlanAndSwitchBook) {
                    Text(text = "确认使用")
                }
            }

            CandidateList(
                uiState = uiState,
                onToggle = viewModel::toggleWord
            )
        }
    }
}

@Composable
private fun BookSelectorRow(
    uiState: TodayNewWordsSelectUiState,
    onSelectBook: (Long) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(uiState.books, key = { it.id }) { book ->
            FilterChip(
                selected = uiState.selectedBookId == book.id,
                onClick = { onSelectBook(book.id) },
                label = {
                    val isActive = uiState.activeBookId == book.id
                    val suffix = if (isActive) "（当前）" else ""
                    Text(text = "${book.name}$suffix")
                }
            )
        }
    }
}

@Composable
private fun CandidateList(
    uiState: TodayNewWordsSelectUiState,
    onToggle: (Long) -> Unit
) {
    if (uiState.loading) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.candidates.isEmpty()) {
        Text(
            text = "当前词书暂无可选新词（可能已全部学过）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(uiState.candidates, key = { it.wordId }) { candidate ->
            CandidateItem(
                item = candidate,
                selected = uiState.selectedWordIds.contains(candidate.wordId),
                onToggle = { onToggle(candidate.wordId) }
            )
        }
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CandidateItem(
    item: TodayNewWordCandidate,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Spacer(modifier = Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = item.word, style = MaterialTheme.typography.bodyLarge)
                if (item.phonetic.isNotBlank()) {
                    Text(text = item.phonetic, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = item.meaning.ifBlank { "（无释义）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
