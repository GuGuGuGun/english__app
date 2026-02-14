package com.kaoyan.wordhelper.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaoyan.wordhelper.data.entity.Book
import com.kaoyan.wordhelper.ui.component.MasteryDonutChart
import com.kaoyan.wordhelper.ui.component.MasteryLegend
import com.kaoyan.wordhelper.ui.viewmodel.BookManageViewModel
import com.kaoyan.wordhelper.ui.viewmodel.BookUiModel
import com.kaoyan.wordhelper.ui.viewmodel.EarlyReviewCandidate
import com.kaoyan.wordhelper.ui.viewmodel.EarlyReviewUiState
import com.kaoyan.wordhelper.ui.viewmodel.MasteryDistribution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookManageScreen(
    viewModel: BookManageViewModel = viewModel(),
    onStartSpelling: () -> Unit = {},
    onOpenBuildGuide: () -> Unit = {}
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val importPreview by viewModel.importPreview.collectAsStateWithLifecycle()
    val importBookName by viewModel.importBookName.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val earlyReviewState by viewModel.earlyReviewUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingDelete by remember { mutableStateOf<BookUiModel?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedBook by remember { mutableStateOf<BookUiModel?>(null) }
    var earlyReviewBook by remember { mutableStateOf<BookUiModel?>(null) }
    var pendingExport by remember { mutableStateOf<BookUiModel?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onImportFileSelected(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val target = pendingExport
        if (target != null) {
            viewModel.exportBook(target.book, uri)
        }
        pendingExport = null
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(text = "删除词书") },
            text = { Text(text = "确认删除 ${pendingDelete?.book?.name} 吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete?.let { viewModel.deleteBook(it.book) }
                    pendingDelete = null
                }) {
                    Text(text = "删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(text = "取消")
                }
            }
        )
    }

    if (selectedBook != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedBook = null },
            sheetState = sheetState
        ) {
            BookDetailSheet(
                item = selectedBook!!,
                onOpenEarlyReview = {
                    selectedBook?.let { target ->
                        selectedBook = null
                        earlyReviewBook = target
                        viewModel.openEarlyReviewSelector(target.book.id)
                    }
                },
                onExport = {
                    selectedBook?.let { target ->
                        pendingExport = target
                        exportLauncher.launch(viewModel.suggestExportFileName(target.book.name))
                    }
                }
            )
        }
    }

    if (earlyReviewBook != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                earlyReviewBook = null
                viewModel.closeEarlyReviewSelector()
            },
            sheetState = sheetState
        ) {
            EarlyReviewSheet(
                bookName = earlyReviewBook!!.book.name,
                state = earlyReviewState,
                onToggle = viewModel::toggleEarlyReviewSelection,
                onSelectAll = viewModel::selectAllEarlyReview,
                onClearAll = viewModel::clearAllEarlyReviewSelection,
                onConfirm = {
                    viewModel.confirmEarlyReviewSelection()
                    earlyReviewBook = null
                    viewModel.closeEarlyReviewSelector()
                }
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(text = "清空生词本") },
            text = { Text(text = "确认清空生词本内容吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearNewWords()
                    showClearDialog = false
                }) {
                    Text(text = "清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(text = "取消")
                }
            }
        )
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissImportPreview() },
            title = { Text(text = "预览词书") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = importBookName,
                        onValueChange = viewModel::updateImportBookName,
                        label = { Text(text = "词书名称") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "共 ${preview.drafts.size} 条，预览前 ${preview.previewLines.size} 条：",
                        style = MaterialTheme.typography.bodySmall
                    )
                    preview.previewLines.forEach { line ->
                        Text(text = line, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmImport) {
                    Text(text = "确认导入")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissImportPreview) {
                    Text(text = "取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "我的词库") },
                actions = {
                    TextButton(onClick = onOpenBuildGuide) {
                        Text(text = "教程")
                    }
                    TextButton(onClick = {
                        importLauncher.launch(arrayOf("text/plain", "text/csv", "text/*", "application/*"))
                    }) {
                        Text(text = "导入")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                importLauncher.launch(arrayOf("text/plain", "text/csv", "text/*", "application/*"))
            }) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "导入词书")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(books, key = { it.book.id }) { item ->
                    BookListItem(
                        item = item,
                        onSwitch = { viewModel.switchBook(item.book.id) },
                        onDelete = { pendingDelete = item },
                        onClearNewWords = { showClearDialog = true },
                        onStartSpelling = onStartSpelling,
                        onOpenEarlyReview = {
                            earlyReviewBook = item
                            viewModel.openEarlyReviewSelector(item.book.id)
                        },
                        onOpenDetail = { selectedBook = item },
                        onExport = {
                            pendingExport = item
                            exportLauncher.launch(viewModel.suggestExportFileName(item.book.name))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookDetailSheet(
    item: BookUiModel,
    onOpenEarlyReview: () -> Unit,
    onExport: () -> Unit
) {
    val mastery = buildMasteryDistribution(item)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = item.book.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(text = "共 ${item.total} 词", style = MaterialTheme.typography.bodySmall)
        val estimateText = when (item.estimatedFinishDays) {
            null -> "预计学完：-"
            0 -> "预计学完：今天"
            else -> "预计学完：${item.estimatedFinishDays} 天"
        }
        Text(
            text = estimateText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DetailStat(label = "已学习", value = item.learned)
            DetailStat(label = "已掌握", value = item.mastered)
            DetailStat(label = "待复习", value = item.dueCount)
        }
        if (item.book.type != Book.TYPE_NEW_WORDS) {
            Text(
                text = "提前复习队列：${item.earlyReviewCount} 词",
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onOpenEarlyReview) {
                Text(text = "提前复习（批量选择）")
            }
        }
        TextButton(onClick = onExport) {
            Text(text = "导出词书")
        }
        if (mastery.total > 0) {
            MasteryDonutChart(mastery = mastery, modifier = Modifier.height(200.dp))
            MasteryLegend()
            Text(
                text = "已掌握 ${mastery.mastered}/${mastery.total}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(text = "暂无词书数据", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DetailStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(text = "$value", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun buildMasteryDistribution(item: BookUiModel): MasteryDistribution {
    val total = item.total.coerceAtLeast(0)
    val learned = item.learned.coerceAtMost(total).coerceAtLeast(0)
    val mastered = item.mastered.coerceAtMost(learned).coerceAtLeast(0)
    val learning = (learned - mastered).coerceAtLeast(0)
    val unlearned = (total - learned).coerceAtLeast(0)
    return MasteryDistribution(
        unlearned = unlearned,
        learning = learning,
        mastered = mastered,
        total = total
    )
}

@Composable
private fun BookListItem(
    item: BookUiModel,
    onSwitch: () -> Unit,
    onDelete: () -> Unit,
    onClearNewWords: () -> Unit,
    onStartSpelling: () -> Unit,
    onOpenEarlyReview: () -> Unit,
    onOpenDetail: () -> Unit,
    onExport: () -> Unit
) {
    val progress = if (item.total > 0) item.learned.toFloat() / item.total else 0f
    val progressText = if (item.book.type == Book.TYPE_NEW_WORDS) {
        "已收录 ${item.total}"
    } else {
        "已学习 ${item.learned}/${item.total}"
    }
    val safeMastered = item.mastered.coerceAtMost(item.total).coerceAtLeast(0)
    val masteryRate = if (item.total > 0) (safeMastered * 100f / item.total).toInt() else 0
    val estimateText = when (item.estimatedFinishDays) {
        null -> "--"
        0 -> "今天"
        else -> "${item.estimatedFinishDays}天"
    }
    val statsText = "已掌握率 $masteryRate% · 待复习 ${item.dueCount} · 提前复习 ${item.earlyReviewCount} · 预计学完 $estimateText"

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenDetail
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.book.type == Book.TYPE_NEW_WORDS) Icons.Filled.Star else Icons.Outlined.LibraryBooks,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = item.book.name, fontWeight = FontWeight.SemiBold)
                Text(text = progressText, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = statsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item.book.isActive) {
                    AssistChip(onClick = {}, label = { Text(text = "使用中") })
                    if (item.book.type != Book.TYPE_NEW_WORDS) {
                        TextButton(onClick = onOpenEarlyReview) {
                            Text(text = "提前复习")
                        }
                    }
                    TextButton(onClick = onStartSpelling) {
                        Text(text = "拼写测试")
                    }
                } else {
                    OutlinedButton(onClick = onSwitch) {
                        Text(text = "切换")
                    }
                }
                TextButton(onClick = onExport) {
                    Text(text = "导出")
                }
                if (item.book.type == Book.TYPE_NEW_WORDS) {
                    TextButton(onClick = onClearNewWords) {
                        Text(text = "清空")
                    }
                } else {
                    TextButton(onClick = onDelete) {
                        Text(text = "删除")
                    }
                }
            }
        }
    }
}

@Composable
private fun EarlyReviewSheet(
    bookName: String,
    state: EarlyReviewUiState,
    onToggle: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "$bookName · 提前复习",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "批量选择已学习词条，加入本轮提前复习（不影响每日新学词数设置）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "已选择 ${state.selectedWordIds.size} 词",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSelectAll) {
                Text(text = "全选")
            }
            TextButton(onClick = onClearAll) {
                Text(text = "清空")
            }
            TextButton(onClick = onConfirm, enabled = !state.loading) {
                Text(text = "确认")
            }
        }
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.candidates.isEmpty()) {
            Text(
                text = "暂无可提前复习的词条（请先完成至少一次学习）。",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.candidates, key = { it.wordId }) { candidate ->
                    EarlyReviewCandidateItem(
                        item = candidate,
                        selected = state.selectedWordIds.contains(candidate.wordId),
                        onToggle = { onToggle(candidate.wordId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EarlyReviewCandidateItem(
    item: EarlyReviewCandidate,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Spacer(modifier = Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = item.word, style = MaterialTheme.typography.bodyLarge)
                if (item.phonetic.isNotBlank()) {
                    Text(text = item.phonetic, style = MaterialTheme.typography.bodySmall)
                }
                val meaning = item.meaning.ifBlank { "（无释义）" }
                Text(
                    text = meaning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${item.statusLabel} · 下次复习：${item.nextReviewText}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
