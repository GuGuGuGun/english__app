package com.kaoyan.wordhelper.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaoyan.wordhelper.KaoyanWordApp
import com.kaoyan.wordhelper.ui.component.AIGeneratedBadge
import com.kaoyan.wordhelper.ui.component.AnimatedStarToggle
import com.kaoyan.wordhelper.ui.viewmodel.SearchSentenceAiState
import com.kaoyan.wordhelper.ui.viewmodel.SearchViewModel
import com.kaoyan.wordhelper.ui.viewmodel.SearchWordAiState
import com.kaoyan.wordhelper.ui.viewmodel.SearchWordItem
import com.kaoyan.wordhelper.util.PronunciationPlayer
import com.kaoyan.wordhelper.util.rememberPronunciationPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)

@Composable
fun SearchScreen(viewModel: SearchViewModel = viewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val sentenceAiState by viewModel.sentenceAiState.collectAsStateWithLifecycle()
    val wordAiState by viewModel.wordAiState.collectAsStateWithLifecycle()
    val pronunciationEnabled by viewModel.pronunciationEnabled.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val pronunciationRepository = remember(context) {
        (context.applicationContext as KaoyanWordApp).pronunciationRepository
    }
    val pronunciationPlayer: PronunciationPlayer = rememberPronunciationPlayer()
    val uiScope = rememberCoroutineScope()
    var selectedWordId by remember { mutableStateOf<Long?>(null) }
    var contentVisible by remember { mutableStateOf(false) }
    var pronouncingWordId by remember { mutableStateOf<Long?>(null) }
    val selectedItem = results.firstOrNull { it.word.id == selectedWordId }

    fun playWordPronunciation(wordId: Long, wordText: String) {
        if (!pronunciationEnabled) return
        if (pronouncingWordId == wordId) return
        pronouncingWordId = wordId
        uiScope.launch {
            val result = withContext(Dispatchers.IO) {
                pronunciationRepository.getPronunciationAudioUrl(wordText)
            }
            result.onSuccess { audioUrl ->
                pronunciationPlayer.play(
                    url = audioUrl,
                    onError = { errMsg ->
                        Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                    }
                )
            }.onFailure { throwable ->
                Toast.makeText(context, throwable.message ?: "单词发音失败", Toast.LENGTH_SHORT).show()
            }
            pronouncingWordId = null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshSentenceAiAvailability()
        contentVisible = true
    }

    if (selectedItem != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                selectedWordId = null
                viewModel.clearWordAiState()
            },
            sheetState = sheetState
        ) {
            SearchDetailSheet(
                item = selectedItem,
                wordAiState = wordAiState,
                onAddToNewWords = { viewModel.toggleNewWord(selectedItem.word, selectedItem.isInNewWords) },
                onGenerateAi = { forceRefresh -> viewModel.requestWordMemoryAid(selectedItem.word, forceRefresh) },
                onPronounce = { playWordPronunciation(selectedItem.word.id, selectedItem.word.word) },
                showPronounceButton = pronunciationEnabled,
                pronouncing = pronouncingWordId == selectedItem.word.id
            )
        }
    }

    val pageBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
            MaterialTheme.colorScheme.background
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBrush)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedVisibility(
            visible = contentVisible,
            enter = fadeIn(animationSpec = tween(260)) +
                slideInVertically(initialOffsetY = { it / 4 }, animationSpec = tween(320))
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "查词 / 长句解析",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::updateQuery,
                        leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text(text = "搜索单词，或粘贴长难句（>20 字符自动解析）") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_input"),
                        singleLine = !sentenceAiState.isSentenceMode,
                        maxLines = if (sentenceAiState.isSentenceMode) 4 else 1,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = contentVisible && sentenceAiState.isSentenceMode,
            enter = fadeIn(animationSpec = tween(220)) +
                expandVertically(animationSpec = tween(300))
        ) {
            SearchSentenceAnalysisPanel(
                state = sentenceAiState,
                onRetry = { viewModel.retrySentenceAnalysis(forceRefresh = false) },
                onForceRefresh = { viewModel.retrySentenceAnalysis(forceRefresh = true) }
            )
        }

        if (!sentenceAiState.isSentenceMode && results.isEmpty() && query.isNotBlank()) {
            Text(
                text = "没有匹配结果",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(results, key = { _, item -> item.word.id }) { index, item ->
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn(animationSpec = tween(240, delayMillis = min(index * 30, 210))) +
                        slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = tween(320, delayMillis = min(index * 30, 210))
                        )
                ) {
                    SearchResultItem(
                        item = item,
                        onToggle = { viewModel.toggleNewWord(item.word, item.isInNewWords) },
                        onClick = {
                            viewModel.clearWordAiState()
                            selectedWordId = item.word.id
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchSentenceAnalysisPanel(
    state: SearchSentenceAiState,
    onRetry: () -> Unit,
    onForceRefresh: () -> Unit
) {
    if (!state.isSentenceMode) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_sentence_panel")
            .animateContentSize(animationSpec = tween(260)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        val panelScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(panelScrollState)
                .padding(14.dp)
                .testTag("search_sentence_panel_scroll"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ) {
                Text(
                    text = "句子解析模式",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Text(
                text = "输入超过 20 个字符后会自动触发 AI 解析，不影响单词检索。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                state.isLoading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .testTag("search_sentence_loading"),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "正在解析长难句...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                !state.error.isNullOrBlank() -> {
                    Text(
                        text = state.error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("search_sentence_error")
                    )
                    if (state.isAvailable) {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.testTag("search_sentence_retry")
                        ) {
                            Text(text = "重试")
                        }
                    }
                }

                state.analysis != null -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        AIGeneratedBadge(modifier = Modifier.testTag("search_sentence_ai_badge"))
                    }
                    SentenceAnalysisSectionCard(
                        title = "句子主干",
                        content = state.analysis.mainClause,
                        testTag = "search_sentence_main"
                    )
                    SentenceAnalysisSectionCard(
                        title = "语法成分标注",
                        content = state.analysis.grammarBreakdown,
                        testTag = "search_sentence_grammar"
                    )
                    SentenceAnalysisSectionCard(
                        title = "中文翻译",
                        content = state.analysis.chineseTranslation,
                        testTag = "search_sentence_translation"
                    )
                    Button(
                        onClick = onForceRefresh,
                        enabled = !state.isLoading,
                        modifier = Modifier.testTag("search_sentence_regenerate")
                    ) {
                        Text(text = "重新解析")
                    }
                    Text(
                        text = "内容由 AI 生成，请甄别参考。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    val hint = if (state.isAvailable) {
                        "等待解析结果..."
                    } else {
                        "请先在 AI 实验室启用并配置 API Key。"
                    }
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("search_sentence_hint")
                    )
                }
            }
        }
    }
}

@Composable
private fun SentenceAnalysisSectionCard(
    title: String,
    content: String,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = content, style = MaterialTheme.typography.bodyMedium)
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
            .animateContentSize(animationSpec = tween(220)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
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
    wordAiState: SearchWordAiState,
    onAddToNewWords: () -> Unit,
    onGenerateAi: (Boolean) -> Unit,
    onPronounce: () -> Unit,
    showPronounceButton: Boolean,
    pronouncing: Boolean
) {
    val currentAiState = if (wordAiState.wordId == item.word.id) {
        wordAiState
    } else {
        wordAiState.copy(wordId = item.word.id, isLoading = false, content = "", error = null)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.word.word,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold
            )
            if (showPronounceButton) {
                OutlinedButton(onClick = onPronounce, enabled = !pronouncing, shape = RoundedCornerShape(10.dp)) {
                    if (pronouncing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(text = "发音")
                    }
                }
            }
        }
        if (item.word.phonetic.isNotBlank()) {
            Text(text = item.word.phonetic, style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        if (item.word.meaning.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Text(
                    text = item.word.meaning,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
        if (item.word.example.isNotBlank()) {
            Text(
                text = item.word.example,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        Text(text = "AI 助记", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

        when {
            currentAiState.isLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(text = "正在生成助记...", style = MaterialTheme.typography.bodySmall)
                }
            }

            !currentAiState.error.isNullOrBlank() -> {
                Text(
                    text = currentAiState.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                if (currentAiState.isAvailable) {
                    OutlinedButton(onClick = { onGenerateAi(false) }) {
                        Text(text = "重试")
                    }
                }
            }

            currentAiState.content.isNotBlank() -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            AIGeneratedBadge()
                        }
                        Text(text = currentAiState.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedButton(onClick = { onGenerateAi(true) }) {
                    Text(text = "重新生成助记")
                }
            }

            else -> {
                val hint = if (currentAiState.isAvailable) {
                    "生成该单词的联想记忆与拆解提示。"
                } else {
                    "请先在 AI 实验室启用并配置 API Key。"
                }
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { onGenerateAi(false) },
                    enabled = currentAiState.isAvailable
                ) {
                    Text(text = "生成 AI 助记")
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { if (!item.isInNewWords) onAddToNewWords() },
                enabled = !item.isInNewWords,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = if (item.isInNewWords) "已在生词本" else "加入生词本")
            }
            Spacer(modifier = Modifier.width(8.dp))
            AnimatedStarToggle(
                checked = item.isInNewWords,
                onCheckedChange = { if (!item.isInNewWords) onAddToNewWords() }
            )
        }
    }
}
