package com.kaoyan.wordhelper.ui.screen

import androidx.compose.animation.AnimatedContent
import android.widget.Toast
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaoyan.wordhelper.KaoyanWordApp
import com.kaoyan.wordhelper.R
import com.kaoyan.wordhelper.data.entity.Word
import com.kaoyan.wordhelper.data.entity.Book
import com.kaoyan.wordhelper.data.model.AIContentType
import com.kaoyan.wordhelper.data.model.StudyRating
import com.kaoyan.wordhelper.ui.component.AIGeneratedBadge
import com.kaoyan.wordhelper.ui.component.AnimatedStarToggle
import com.kaoyan.wordhelper.ui.theme.AlertRed
import com.kaoyan.wordhelper.ui.theme.FuzzyYellow
import com.kaoyan.wordhelper.ui.theme.KnownGreen
import com.kaoyan.wordhelper.ui.viewmodel.LearningAiState
import com.kaoyan.wordhelper.ui.viewmodel.LearningViewModel
import com.kaoyan.wordhelper.util.PronunciationPlayer
import com.kaoyan.wordhelper.util.rememberPronunciationPlayer
import com.kaoyan.wordhelper.util.DateUtils
import com.kaoyan.wordhelper.util.GestureHandler
import com.kaoyan.wordhelper.util.Sm2Scheduler
import com.kaoyan.wordhelper.util.SwipeAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningScreen(
    viewModel: LearningViewModel = viewModel(),
    initialMode: LearningMode = LearningMode.RECOGNITION,
    onModeChange: (LearningMode) -> Unit = {}
) {
    val activeBook by viewModel.activeBook.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val currentWord by viewModel.currentWord.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val isInNewWords by viewModel.isInNewWords.collectAsStateWithLifecycle()
    val isNewWordsBook by viewModel.isNewWordsBook.collectAsStateWithLifecycle()
    val currentProgress by viewModel.currentProgress.collectAsStateWithLifecycle()
    val isAnswering by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val dueReviewCount by viewModel.dueReviewCount.collectAsStateWithLifecycle()
    val canRelieveReviewPressure by viewModel.canRelieveReviewPressure.collectAsStateWithLifecycle()
    val reviewPressureDailyCap by viewModel.reviewPressureDailyCap.collectAsStateWithLifecycle()
    val algorithmV4Enabled by viewModel.algorithmV4Enabled.collectAsStateWithLifecycle()
    val pronunciationEnabled by viewModel.pronunciationEnabled.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val cachedExampleContent by viewModel.cachedExampleContent.collectAsStateWithLifecycle()
    val cachedMemoryAidContent by viewModel.cachedMemoryAidContent.collectAsStateWithLifecycle()
    val memoryAidSuggestionWordId by viewModel.memoryAidSuggestionWordId.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val swipeSnackbarEvent by viewModel.swipeSnackbarEvent.collectAsStateWithLifecycle()
    val showSwipeGuide by viewModel.showSwipeGuide.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pronunciationRepository = remember(context) {
        (context.applicationContext as KaoyanWordApp).pronunciationRepository
    }
    val pronunciationPlayer: PronunciationPlayer = rememberPronunciationPlayer()
    val uiScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSheet by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var showAiAssistantPage by rememberSaveable { mutableStateOf(false) }
    var showAiGuideDialog by remember { mutableStateOf(false) }
    var isCardFlipped by remember { mutableStateOf(false) }
    var lastRating by remember { mutableStateOf<StudyRating?>(null) }
    var pronouncingWordId by remember { mutableStateOf<Long?>(null) }
    var learningMode by rememberSaveable { mutableStateOf(initialMode) }
    var hideSwipeGuideInSession by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(currentWord?.id) {
        showReviewDialog = false
        showAiGuideDialog = false
        showAiAssistantPage = false
        isCardFlipped = false
    }

    LaunchedEffect(activeBook?.id) {
        lastRating = null
    }

    LaunchedEffect(initialMode) {
        learningMode = initialMode
        if (initialMode != LearningMode.RECOGNITION) {
            isCardFlipped = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshAiAvailability()
    }

    LaunchedEffect(showAiAssistantPage, currentWord?.id) {
        if (showAiAssistantPage && currentWord != null) {
            viewModel.loadCachedAiForCurrentWord()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(swipeSnackbarEvent?.id) {
        val event = swipeSnackbarEvent ?: return@LaunchedEffect
        val result = if (event.undoToken != null) {
            val dismissJob = launch {
                delay(3_000)
                snackbarHostState.currentSnackbarData?.dismiss()
            }
            val snackbarResult = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                duration = SnackbarDuration.Indefinite
            )
            dismissJob.cancel()
            snackbarResult
        } else {
            snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = event.actionLabel,
                duration = SnackbarDuration.Short
            )
        }
        if (event.undoToken != null) {
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoSwipeTooEasy(event.undoToken)
            } else {
                viewModel.dismissSwipeUndo(event.undoToken)
            }
        }
        viewModel.consumeSwipeSnackbarEvent(event.id)
    }

    fun handleAnswer(rating: StudyRating, removeFromNewWords: Boolean = false) {
        if (isAnswering) return
        lastRating = rating
        if (removeFromNewWords) {
            viewModel.submitAnswerAndRemoveFromNewWords(rating)
        } else {
            viewModel.submitAnswer(rating)
        }
    }

    fun openAiEntry() {
        if (isAnswering) return
        if (aiState.isAvailable) {
            showAiAssistantPage = true
            showAiGuideDialog = false
        } else {
            showAiGuideDialog = true
            showAiAssistantPage = false
        }
    }

    fun playPronunciation(word: Word) {
        if (!pronunciationEnabled) return
        if (pronouncingWordId == word.id) return
        pronouncingWordId = word.id
        uiScope.launch {
            val result = withContext(Dispatchers.IO) {
                pronunciationRepository.getPronunciationAudioUrl(word.word)
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

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "切换词书", style = MaterialTheme.typography.titleLarge)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(books, key = { it.id }) { book ->
                        BookSheetItem(
                            book = book,
                            isActive = book.id == activeBook?.id,
                            onSwitch = {
                                viewModel.switchBook(book.id)
                                showSheet = false
                            }
                        )
                    }
                }
            }
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(text = "移出生词本") },
            text = { Text(text = "确认将该单词从生词本移出吗？") },
            confirmButton = {
                TextButton(onClick = {
                    handleAnswer(StudyRating.GOOD, removeFromNewWords = true)
                    showRemoveDialog = false
                }) {
                    Text(text = "移出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(text = "取消")
                }
            }
        )
    }

    if (showAiGuideDialog) {
        AlertDialog(
            onDismissRequest = { showAiGuideDialog = false },
            title = { Text(text = "AI 功能未配置") },
            text = { Text(text = "请先在“我的 - AI 实验室”配置 API Key 解锁智能助记。") },
            confirmButton = {
                TextButton(onClick = { showAiGuideDialog = false }) {
                    Text(text = "知道了", modifier = Modifier.testTag("learning_ai_guide_confirm"))
                }
            }
        )
    }

    if (
        showSwipeGuide &&
        !hideSwipeGuideInSession &&
        learningMode == LearningMode.RECOGNITION &&
        currentWord != null
    ) {
        SwipeGestureCoachMarkDialog(
            onDismiss = { hideSwipeGuideInSession = true },
            onNeverShowAgain = {
                hideSwipeGuideInSession = true
                viewModel.dismissSwipeGuide()
            }
        )
    }

    val nextReviewTime = currentProgress?.nextReviewTime ?: 0L
    val reviewTag = DateUtils.reviewTag(nextReviewTime)
    val reviewDescription = DateUtils.reviewDescription(nextReviewTime)
    val lastReviewTime = Sm2Scheduler.estimateLastReviewTime(currentProgress)
    val lastReviewText = lastReviewTime?.let { DateUtils.formatDateTime(it) } ?: "暂无"
    val nextReviewText = DateUtils.formatReviewDay(nextReviewTime).let { if (it == "无") "暂无" else it }

    if (showReviewDialog) {
        AlertDialog(
            onDismissRequest = { showReviewDialog = false },
            title = {
                Text(
                    text = "复习时间",
                    modifier = Modifier.testTag("review_dialog_title")
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "上次复习：$lastReviewText",
                        modifier = Modifier.testTag("review_last_review_time")
                    )
                    Text(
                        text = "下次复习：$nextReviewText",
                        modifier = Modifier.testTag("review_next_review_time")
                    )
                    Text(
                        text = "提示：$reviewDescription",
                        modifier = Modifier.testTag("review_hint")
                    )
                    Text(text = SM2_DESCRIPTION)
                }
            },
            confirmButton = {
                TextButton(onClick = { showReviewDialog = false }) {
                    Text(
                        text = "知道了",
                        modifier = Modifier.testTag("review_dialog_confirm")
                    )
                }
            }
        )
    }

    if (showAiAssistantPage && currentWord != null && learningMode == LearningMode.RECOGNITION) {
        RecognitionAiAssistantPage(
            word = currentWord!!,
            aiState = aiState,
            cachedExampleContent = cachedExampleContent,
            cachedMemoryAidContent = cachedMemoryAidContent,
            showMemoryAidSuggestion = memoryAidSuggestionWordId == currentWord!!.id,
            canGenerateExample = isCardFlipped,
            onBack = { showAiAssistantPage = false },
            onGenerateExample = { forceRefresh -> viewModel.requestAiExample(forceRefresh) },
            onGenerateMemoryAid = { forceRefresh -> viewModel.requestAiMemoryAid(forceRefresh) }
        )
        return
    }

    val pageBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            MaterialTheme.colorScheme.background
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.clickable { showSheet = true },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AnimatedContent(
                                    targetState = activeBook?.name ?: "暂无词书",
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(180))
                                    },
                                    label = "activeBookName"
                                ) { title ->
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.testTag("learning_book_title")
                                    )
                                }
                                Icon(imageVector = Icons.Filled.KeyboardArrowDown, contentDescription = null)
                            }
                        }

                    }

                    TabRow(
                        selectedTabIndex = learningMode.ordinal,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        divider = {},
                        indicator = {}
                    ) {
                        Tab(
                            selected = learningMode == LearningMode.RECOGNITION,
                            onClick = {
                                learningMode = LearningMode.RECOGNITION
                                onModeChange(learningMode)
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("learning_mode_recognition"),
                            text = {
                                Text(
                                    text = "认词模式",
                                    fontWeight = if (learningMode == LearningMode.RECOGNITION) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    }
                                )
                            }
                        )
                        Tab(
                            selected = learningMode == LearningMode.SPELLING,
                            onClick = {
                                learningMode = LearningMode.SPELLING
                                onModeChange(learningMode)
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("learning_mode_spelling"),
                            text = {
                                Text(
                                    text = "拼写模式",
                                    fontWeight = if (learningMode == LearningMode.SPELLING) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    }
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (canRelieveReviewPressure) {
                ReviewPressureReliefBanner(
                    dueReviewCount = dueReviewCount,
                    dailyCap = reviewPressureDailyCap,
                    onRelieve = viewModel::disperseReviewPressure,
                    enabled = !isAnswering
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (currentWord == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "暂无单词", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                when (learningMode) {
                    LearningMode.RECOGNITION -> {
                        val cardState = WordCardState(currentWord!!, isInNewWords)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(RECOGNITION_SECTION_SPACING)
                        ) {
                            RecognitionHintBanner()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = cardState,
                                    transitionSpec = { wordCardTransition(lastRating) },
                                    label = "wordCardTransition"
                                ) { state ->
                                    SwipeableWordCard(
                                        enabled = !isAnswering,
                                        onSwipeTooEasy = viewModel::onSwipeTooEasy,
                                        onSwipeAddToNotebook = viewModel::onSwipeAddToNotebook,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight()
                                            .testTag("learning_word_card")
                                    ) { swipeModifier ->
                                        WordCard(
                                            word = state.word,
                                            isInNewWords = state.isInNewWords,
                                            onToggleNewWords = viewModel::toggleNewWord,
                                            reviewTag = reviewTag,
                                            onReviewTagClick = { showReviewDialog = true },
                                            aiAvailable = aiState.isAvailable,
                                            aiSuggested = memoryAidSuggestionWordId == currentWord!!.id,
                                            onAiEntryClick = ::openAiEntry,
                                            onPronounceClick = { playPronunciation(state.word) },
                                            showPronounceButton = pronunciationEnabled,
                                            pronounceLoading = pronouncingWordId == state.word.id,
                                            onFlipChanged = { isCardFlipped = it },
                                            starEnabled = !isAnswering,
                                            modifier = swipeModifier.fillMaxHeight()
                                        )
                                    }
                                }
                            }
                            RecognitionActionPanel(
                                isAnswering = isAnswering,
                                isNewWordsBook = isNewWordsBook,
                                onAgain = { handleAnswer(StudyRating.AGAIN) },
                                onHard = { handleAnswer(StudyRating.HARD) },
                                onGood = {
                                    if (isNewWordsBook) {
                                        showRemoveDialog = true
                                    } else {
                                        handleAnswer(StudyRating.GOOD)
                                    }
                                }
                            )
                        }
                    }

                    LearningMode.SPELLING -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SpellingScreen(
                                word = currentWord,
                                presentationKey = currentIndex,
                                isSubmitting = isAnswering,
                                algorithmV4Enabled = algorithmV4Enabled,
                                onSpellingResolved = viewModel::submitSpellingOutcome,
                                onContinueAfterFailure = viewModel::continueAfterSpellingFailure,
                                aiAssistAvailable = aiState.isAvailable,
                                aiAssistLoading = aiState.isLoading && aiState.activeType == AIContentType.MEMORY_AID,
                                aiAssistText = if (aiState.activeType == AIContentType.MEMORY_AID) {
                                    aiState.content.ifBlank { null }
                                } else {
                                    null
                                },
                                aiAssistError = if (aiState.activeType == AIContentType.MEMORY_AID) {
                                    aiState.error
                                } else {
                                    null
                                },
                                onRequestAiAssist = { forceRefresh ->
                                    viewModel.requestAiMemoryAid(forceRefresh)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }
                    }
                }
            }

            BottomLearningProgressBar(
                currentIndex = currentIndex,
                totalCount = totalCount,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

private const val SM2_DESCRIPTION =
    "SM-2 调度说明：不认识会在会话内重试，模糊保守推进，认识按记忆因子增长间隔。"
private val RECOGNITION_SECTION_SPACING = 14.dp
private const val SWIPE_TRIGGER_RATIO = 0.3f
private const val SWIPE_ACTIVE_ZONE_RATIO = 0.8f

enum class LearningMode {
    RECOGNITION,
    SPELLING
}

private data class WordCardState(
    val word: Word,
    val isInNewWords: Boolean
)

private enum class SwipeDirection {
    LEFT,
    RIGHT,
    NONE
}

@Composable
private fun SwipeableWordCard(
    enabled: Boolean,
    onSwipeTooEasy: () -> Unit,
    onSwipeAddToNotebook: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var offsetX by remember { mutableStateOf(0f) }
    var allowDrag by remember { mutableStateOf(false) }
    var thresholdHapticTriggered by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val threshold = GestureHandler.triggerThreshold(widthPx, SWIPE_TRIGGER_RATIO)
        val edgeReserved = GestureHandler.edgeReservedWidth(widthPx, SWIPE_ACTIVE_ZONE_RATIO)
        val swipeProgress = (abs(offsetX) / threshold).coerceIn(0f, 1f)

        SwipeBackgroundIndicator(
            offsetX = offsetX,
            progress = swipeProgress,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(enabled, widthPx) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { startOffset ->
                            allowDrag = GestureHandler.isStartInActiveZone(
                                startX = startOffset.x,
                                widthPx = widthPx,
                                activeZoneRatio = SWIPE_ACTIVE_ZONE_RATIO
                            )
                            thresholdHapticTriggered = false
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (!allowDrag) return@detectHorizontalDragGestures
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(-widthPx, widthPx)
                            if (!thresholdHapticTriggered && abs(offsetX) >= threshold) {
                                thresholdHapticTriggered = true
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDragEnd = {
                            val finalOffset = offsetX
                            allowDrag = false
                            thresholdHapticTriggered = false
                            when (GestureHandler.resolveSwipeAction(finalOffset, threshold)) {
                                SwipeAction.TOO_EASY -> {
                                    offsetX = 0f
                                    onSwipeTooEasy()
                                }

                                SwipeAction.ADD_TO_NOTEBOOK -> {
                                    offsetX = 0f
                                    onSwipeAddToNotebook()
                                }

                                SwipeAction.NONE -> {
                                    scope.launch {
                                        animate(finalOffset, 0f) { value, _ ->
                                            offsetX = value
                                        }
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            allowDrag = false
                            thresholdHapticTriggered = false
                            val finalOffset = offsetX
                            scope.launch {
                                animate(finalOffset, 0f) { value, _ ->
                                    offsetX = value
                                }
                            }
                        }
                    )
                }
        ) {
            content(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SwipeBackgroundIndicator(
    offsetX: Float,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val direction = when {
        offsetX < 0f -> SwipeDirection.LEFT
        offsetX > 0f -> SwipeDirection.RIGHT
        else -> SwipeDirection.NONE
    }
    val backgroundColor = when (direction) {
        SwipeDirection.LEFT -> KnownGreen.copy(alpha = 0.08f + 0.22f * progress)
        SwipeDirection.RIGHT -> FuzzyYellow.copy(alpha = 0.10f + 0.24f * progress)
        SwipeDirection.NONE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.10f)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = backgroundColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentAlignment = when (direction) {
                SwipeDirection.LEFT -> Alignment.CenterStart
                SwipeDirection.RIGHT -> Alignment.CenterEnd
                SwipeDirection.NONE -> Alignment.Center
            }
        ) {
            when (direction) {
                SwipeDirection.LEFT -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_swipe_left),
                            contentDescription = null,
                            tint = KnownGreen.copy(alpha = 0.4f + 0.6f * progress)
                        )
                        Text(
                            text = "太简单（30天）",
                            color = KnownGreen.copy(alpha = 0.5f + 0.5f * progress),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                SwipeDirection.RIGHT -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_swipe_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f + 0.5f * progress)
                        )
                        Text(
                            text = "加入生词本",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f + 0.5f * progress),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                SwipeDirection.NONE -> {
                    Text(
                        text = "左滑太简单 · 右滑加入生词本",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeGestureCoachMarkDialog(
    onDismiss: () -> Unit,
    onNeverShowAgain: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "手势快捷操作") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "左滑超过 30%：标记“太简单”，30 天后复习。")
                Text(text = "右滑超过 30%：快速加入生词本，并切换到下一词。")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "知道了",
                    modifier = Modifier.testTag("learning_swipe_guide_confirm")
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onNeverShowAgain) {
                Text(
                    text = "不再提示",
                    modifier = Modifier.testTag("learning_swipe_guide_never_again")
                )
            }
        }
    )
}

@Composable
private fun BottomLearningProgressBar(
    currentIndex: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    val rawProgress = if (totalCount > 0) currentIndex.toFloat() / totalCount else 0f
    val progress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 260),
        label = "learningBottomProgress"
    )
    val progressText = if (totalCount > 0) "$currentIndex/$totalCount" else "0/0"

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "学习进度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = progressText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        )
    }
}

@Composable
private fun RecognitionHintBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        ),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    ) {
        Text(
            text = "先判断是否认识，再翻卡核对释义；左滑标记太简单，右滑快速入生词本。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun RecognitionAiAssistantPage(
    word: Word,
    aiState: LearningAiState,
    cachedExampleContent: String,
    cachedMemoryAidContent: String,
    showMemoryAidSuggestion: Boolean,
    canGenerateExample: Boolean,
    onBack: () -> Unit,
    onGenerateExample: (Boolean) -> Unit,
    onGenerateMemoryAid: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var exampleContent by remember(word.id) { mutableStateOf(cachedExampleContent) }
    var memoryAidContent by remember(word.id) { mutableStateOf(cachedMemoryAidContent) }
    val scrollState = rememberScrollState()
    val displayWord = remember(word.word) { word.word.adaptiveWordText() }

    LaunchedEffect(cachedExampleContent) {
        if (cachedExampleContent.isNotBlank()) {
            exampleContent = cachedExampleContent
        }
    }

    LaunchedEffect(cachedMemoryAidContent) {
        if (cachedMemoryAidContent.isNotBlank()) {
            memoryAidContent = cachedMemoryAidContent
        }
    }

    LaunchedEffect(aiState.activeType, aiState.content) {
        when (aiState.activeType) {
            AIContentType.EXAMPLE -> if (aiState.content.isNotBlank()) exampleContent = aiState.content
            AIContentType.MEMORY_AID -> if (aiState.content.isNotBlank()) memoryAidContent = aiState.content
            AIContentType.SENTENCE, null -> Unit
        }
    }

    val isExampleLoading = aiState.activeType == AIContentType.EXAMPLE && aiState.isLoading
    val isMemoryAidLoading = aiState.activeType == AIContentType.MEMORY_AID && aiState.isLoading
    val exampleError = if (aiState.activeType == AIContentType.EXAMPLE) aiState.error else null
    val memoryAidError = if (aiState.activeType == AIContentType.MEMORY_AID) aiState.error else null
    val hasExampleContent = exampleContent.isNotBlank()
    val hasMemoryAidContent = memoryAidContent.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("learning_ai_page"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, modifier = Modifier.testTag("learning_ai_page_back")) {
                Text(text = "返回认词")
            }
            Text(
                text = "AI 学习助手",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.size(8.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "当前单词",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = displayWord,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "例句和助记会在本页展示，生成内容仅供学习参考。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (showMemoryAidSuggestion) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = AlertRed.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "刚才答错了，建议优先生成助记技巧。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlertRed,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            if (!aiState.isAvailable) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                ) {
                    Text(
                        text = "AI 未启用或未配置，请先前往“我的 - AI 实验室”完成配置。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(12.dp)
                            .testTag("learning_ai_page_unavailable")
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "例句生成",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!canGenerateExample) {
                        Text(
                            text = "请先翻卡查看释义后再生成例句。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isExampleLoading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("learning_ai_page_example_loading")
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(text = "正在生成例句...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!exampleError.isNullOrBlank()) {
                        Text(
                            text = exampleError,
                            style = MaterialTheme.typography.bodySmall,
                            color = AlertRed,
                            modifier = Modifier.testTag("learning_ai_page_example_error")
                        )
                    }
                    if (hasExampleContent) {
                        AIContentResultCard(
                            title = "AI 生成例句",
                            content = exampleContent,
                            modifier = Modifier.testTag("learning_ai_page_example_content")
                        )
                    }
                    OutlinedButton(
                        onClick = { onGenerateExample(hasExampleContent) },
                        enabled = aiState.isAvailable && !aiState.isLoading && canGenerateExample,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("learning_ai_page_example_generate")
                    ) {
                        Text(text = if (hasExampleContent) "重新生成例句" else "生成例句")
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "助记技巧",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isMemoryAidLoading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("learning_ai_page_memory_loading")
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(text = "正在生成助记...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!memoryAidError.isNullOrBlank()) {
                        Text(
                            text = memoryAidError,
                            style = MaterialTheme.typography.bodySmall,
                            color = AlertRed,
                            modifier = Modifier.testTag("learning_ai_page_memory_error")
                        )
                    }
                    if (hasMemoryAidContent) {
                        AIContentResultCard(
                            title = "AI 助记技巧",
                            content = memoryAidContent,
                            modifier = Modifier.testTag("learning_ai_page_memory_content")
                        )
                    }
                    OutlinedButton(
                        onClick = { onGenerateMemoryAid(hasMemoryAidContent) },
                        enabled = aiState.isAvailable && !aiState.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("learning_ai_page_memory_generate")
                    ) {
                        Text(text = if (hasMemoryAidContent) "重新生成助记" else "生成助记")
                    }
                }
            }
        }
    }
}

@Composable
fun LearningAiActionSheetContent(
    isAvailable: Boolean,
    showExampleAction: Boolean,
    showMemoryAidSuggestion: Boolean,
    hasExampleContent: Boolean,
    hasMemoryContent: Boolean,
    isLoading: Boolean,
    onGenerateExample: () -> Unit,
    onRegenerateExample: () -> Unit,
    onGenerateMemoryAid: () -> Unit,
    onRegenerateMemoryAid: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("learning_ai_sheet"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "AI 助手", style = MaterialTheme.typography.titleLarge)
        if (!isAvailable) {
            Text(
                text = "AI 未启用或未配置，请先在“我的 - AI 实验室”完成设置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        if (showExampleAction) {
            OutlinedButton(
                onClick = { if (hasExampleContent) onRegenerateExample() else onGenerateExample() },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("learning_ai_action_example")
            ) {
                Text(text = if (hasExampleContent) "重新生成例句" else "生成例句")
            }
        } else {
            Text(
                text = "翻卡后可使用例句生成",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(
            onClick = { if (hasMemoryContent) onRegenerateMemoryAid() else onGenerateMemoryAid() },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("learning_ai_action_memory")
        ) {
            val text = when {
                showMemoryAidSuggestion && hasMemoryContent -> "重新生成助记（推荐）"
                showMemoryAidSuggestion -> "生成助记（推荐）"
                hasMemoryContent -> "重新生成助记"
                else -> "生成助记"
            }
            Text(text = text)
        }
    }
}

@Composable
fun LearningAiResultPanel(
    aiState: LearningAiState,
    showMemoryAidSuggestion: Boolean,
    onRetryExample: () -> Unit,
    onRetryMemoryAid: () -> Unit,
    onOpenAiEntry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExampleContext = aiState.activeType == AIContentType.EXAMPLE
    val isMemoryAidContext = aiState.activeType == AIContentType.MEMORY_AID
    val shouldShow = showMemoryAidSuggestion ||
        ((isExampleContext || isMemoryAidContext) && (
            aiState.isLoading ||
                aiState.content.isNotBlank() ||
                !aiState.error.isNullOrBlank()
            ))
    if (!shouldShow) return

    val loadingText = when (aiState.activeType) {
        AIContentType.EXAMPLE -> "正在生成例句..."
        AIContentType.MEMORY_AID -> "正在生成助记..."
        AIContentType.SENTENCE -> "正在生成内容..."
        null -> "正在请求 AI..."
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("learning_ai_panel"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showMemoryAidSuggestion && aiState.activeType == null && aiState.content.isBlank()) {
                Text(
                    text = "刚才答错了，点击灯泡生成 AI 助记。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AlertRed,
                    modifier = Modifier.testTag("learning_ai_suggestion")
                )
            }

            if (aiState.isLoading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("learning_ai_loading")
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(text = loadingText, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (!aiState.error.isNullOrBlank()) {
                Text(
                    text = aiState.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = AlertRed,
                    modifier = Modifier.testTag("learning_ai_error")
                )
                if (aiState.isAvailable && aiState.activeType != null && !aiState.isLoading) {
                    OutlinedButton(
                        onClick = {
                            when (aiState.activeType) {
                                AIContentType.EXAMPLE -> onRetryExample()
                                AIContentType.MEMORY_AID -> onRetryMemoryAid()
                                AIContentType.SENTENCE -> onOpenAiEntry()
                            }
                        },
                        modifier = Modifier.testTag("learning_ai_retry")
                    ) {
                        Text(text = "重试")
                    }
                }
            }

            if (aiState.content.isNotBlank() && aiState.activeType != null) {
                val title = when (aiState.activeType) {
                    AIContentType.EXAMPLE -> "AI 生成例句"
                    AIContentType.MEMORY_AID -> "AI 助记技巧"
                    AIContentType.SENTENCE -> "AI 内容"
                }
                if (aiState.activeType == AIContentType.EXAMPLE || aiState.activeType == AIContentType.MEMORY_AID) {
                    AIContentResultCard(title = title, content = aiState.content)
                }
            }
        }
    }
}

@Composable
fun AIContentResultCard(
    title: String,
    content: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("learning_ai_content")
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "内容由 AI 生成，请甄别参考。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AIGeneratedBadge(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .testTag("learning_ai_badge")
        )
    }
}

@Composable
private fun ReviewPressureReliefBanner(
    dueReviewCount: Int,
    dailyCap: Int,
    onRelieve: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
        ),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "今日待复习 $dueReviewCount，超过上限 $dailyCap",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "点击后会把超出部分均匀顺延到后续天。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.size(10.dp))
            OutlinedButton(onClick = onRelieve, enabled = enabled) {
                Text(text = "分散压力")
            }
        }
    }
}

@Composable
private fun RecognitionActionPanel(
    isAnswering: Boolean,
    isNewWordsBook: Boolean,
    onAgain: () -> Unit,
    onHard: () -> Unit,
    onGood: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
        ),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = onAgain,
                enabled = !isAnswering,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("learning_action_again"),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = AlertRed.copy(alpha = 0.18f),
                    contentColor = AlertRed
                )
            ) {
                Text(text = "不认识")
            }
            FilledTonalButton(
                onClick = onHard,
                enabled = !isAnswering,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("learning_action_hard"),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = FuzzyYellow.copy(alpha = 0.28f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(text = "模糊")
            }
            val goodText = if (isNewWordsBook) "认识并移出" else "认识"
            FilledTonalButton(
                onClick = onGood,
                enabled = !isAnswering,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("learning_action_good"),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = KnownGreen.copy(alpha = 0.22f),
                    contentColor = KnownGreen
                )
            ) {
                Text(text = goodText, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun WordCard(
    word: Word,
    isInNewWords: Boolean,
    onToggleNewWords: () -> Unit,
    reviewTag: String,
    onReviewTagClick: () -> Unit,
    aiAvailable: Boolean,
    aiSuggested: Boolean,
    onAiEntryClick: () -> Unit,
    onPronounceClick: () -> Unit,
    showPronounceButton: Boolean,
    pronounceLoading: Boolean,
    onFlipChanged: (Boolean) -> Unit,
    starEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var isFlipped by remember(word.id) { mutableStateOf(false) }
    val normalizedWordLength = word.word.trim().length
    val displayWord = remember(word.word) { word.word.adaptiveWordText() }
    val typography = MaterialTheme.typography
    val frontWordStyle = when {
        normalizedWordLength <= 12 -> typography.displayLarge
        normalizedWordLength <= 16 -> typography.displayMedium
        normalizedWordLength <= 20 -> typography.headlineLarge
        normalizedWordLength <= 24 -> typography.headlineMedium
        normalizedWordLength <= 32 -> typography.titleLarge
        normalizedWordLength <= 40 -> typography.titleMedium
        else -> typography.titleSmall
    }
    val backWordStyle = when {
        normalizedWordLength <= 16 -> typography.titleLarge
        normalizedWordLength <= 24 -> typography.titleMedium
        normalizedWordLength <= 32 -> typography.titleSmall
        else -> typography.bodyLarge
    }
    val wordMaxLines = when {
        normalizedWordLength <= 20 -> 1
        normalizedWordLength <= 32 -> 2
        normalizedWordLength <= 48 -> 3
        else -> 4
    }
    LaunchedEffect(word.id) {
        onFlipChanged(false)
    }
    val density = LocalDensity.current
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "wordCardRotation"
    )
    val showFront = rotation <= 90f

    val frontBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
            MaterialTheme.colorScheme.surface
        )
    )
    val backBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f),
            MaterialTheme.colorScheme.surface
        )
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.26f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = {
            isFlipped = !isFlipped
            onFlipChanged(isFlipped)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(if (showFront) frontBrush else backBrush)
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .heightIn(min = 220.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ReviewTimeTag(text = reviewTag, onClick = onReviewTagClick)
                    if (showPronounceButton) {
                        OutlinedButton(
                            onClick = onPronounceClick,
                            enabled = !pronounceLoading,
                            modifier = Modifier.testTag("learning_pronounce_button")
                        ) {
                            if (pronounceLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(text = "发音")
                            }
                        }
                    }
                }
                AnimatedStarToggle(
                    checked = isInNewWords,
                    onCheckedChange = { onToggleNewWords() },
                    enabled = starEnabled,
                    modifier = Modifier.testTag("learning_star_toggle")
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            rotationY = rotation
                            cameraDistance = 12f * density.density
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (showFront) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                        ) {
                            Text(
                                text = displayWord,
                                style = frontWordStyle,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = wordMaxLines,
                                overflow = TextOverflow.Clip
                            )
                            if (word.phonetic.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = word.phonetic,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = "轻触卡片查看释义与例句",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 12.dp)
                                .graphicsLayer { rotationY = 180f },
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = displayWord,
                                style = backWordStyle,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = wordMaxLines,
                                overflow = TextOverflow.Clip
                            )
                            if (word.phonetic.isNotBlank()) {
                                Text(
                                    text = word.phonetic,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f))
                            if (word.meaning.isNotBlank()) {
                                Text(
                                    text = "释义",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = word.meaning,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (word.example.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                ) {
                                    Text(
                                        text = "例句  ${word.example}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                if (aiAvailable) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .clickable(enabled = starEnabled, onClick = onAiEntryClick)
                            .testTag("learning_ai_bulb"),
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                tint = if (aiSuggested) AlertRed else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (aiSuggested) "助记推荐" else "AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun String.adaptiveWordText(): String {
    val trimmed = trim()
    if (trimmed.length <= 20) return trimmed
    return trimmed.toCharArray().joinToString(separator = "\u200B")
}

@Composable
private fun ReviewTimeTag(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .testTag("learning_review_tag")
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun AnimatedContentTransitionScope<WordCardState>.wordCardTransition(
    lastRating: StudyRating?
): ContentTransform {
    val defaultFadeIn = tween<Float>(durationMillis = 180)
    val defaultFadeOut = tween<Float>(durationMillis = 150)

    return when (lastRating) {
        StudyRating.GOOD -> {
            (slideInHorizontally(
                initialOffsetX = { it / 2 },
                animationSpec = tween(durationMillis = 220)
            ) +
                fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 30), initialAlpha = 0.42f) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(durationMillis = 220)))
                .togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { -it / 3 },
                        animationSpec = tween(durationMillis = 190)
                    ) +
                        fadeOut(animationSpec = tween(durationMillis = 170), targetAlpha = 0.2f) +
                        scaleOut(targetScale = 1.03f, animationSpec = tween(durationMillis = 190))
                )
        }

        StudyRating.AGAIN -> {
            (slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(durationMillis = 320)
            ) +
                fadeIn(animationSpec = tween(durationMillis = 220), initialAlpha = 0.28f) +
                scaleIn(initialScale = 0.9f, animationSpec = tween(durationMillis = 300)))
                .togetherWith(
                    slideOutHorizontally(
                        targetOffsetX = { it / 2 },
                        animationSpec = tween(durationMillis = 300)
                    ) +
                        fadeOut(animationSpec = tween(durationMillis = 220), targetAlpha = 0.16f) +
                        scaleOut(targetScale = 0.92f, animationSpec = tween(durationMillis = 280))
                )
        }

        StudyRating.HARD -> {
            (slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = tween(durationMillis = 260)
            ) +
                fadeIn(animationSpec = tween(durationMillis = 210), initialAlpha = 0.26f) +
                scaleIn(initialScale = 0.95f, animationSpec = tween(durationMillis = 240)))
                .togetherWith(
                    slideOutVertically(
                        targetOffsetY = { -it / 6 },
                        animationSpec = tween(durationMillis = 230)
                    ) +
                        fadeOut(animationSpec = tween(durationMillis = 200), targetAlpha = 0.2f) +
                        scaleOut(targetScale = 0.97f, animationSpec = tween(durationMillis = 220))
                )
        }

        else -> {
            fadeIn(animationSpec = defaultFadeIn) togetherWith fadeOut(animationSpec = defaultFadeOut)
        }
    }.using(SizeTransform(clip = false))
}

@Composable
private fun BookSheetItem(
    book: Book,
    isActive: Boolean,
    onSwitch: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = book.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = "共 ${book.totalCount} 词", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.size(8.dp))
        if (isActive) {
            Text(text = "使用中", style = MaterialTheme.typography.bodySmall)
        } else {
            OutlinedButton(onClick = onSwitch) {
                Text(text = "切换")
            }
        }
    }
}





