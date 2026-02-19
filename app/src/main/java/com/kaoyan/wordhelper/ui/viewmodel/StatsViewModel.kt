package com.kaoyan.wordhelper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaoyan.wordhelper.KaoyanWordApp
import com.kaoyan.wordhelper.data.entity.Book
import com.kaoyan.wordhelper.data.entity.MLModelState
import com.kaoyan.wordhelper.data.entity.TrainingSample
import com.kaoyan.wordhelper.data.model.DailyStatsAggregate
import com.kaoyan.wordhelper.data.repository.ForecastDataSource
import com.kaoyan.wordhelper.ml.features.FeatureVector
import com.kaoyan.wordhelper.ml.ui.MemoryHeatmapEntry
import com.kaoyan.wordhelper.util.PressureLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class StatsRange(val days: Long, val label: String) {
    WEEK(7, "近7天"),
    MONTH(30, "近30天")
}

enum class CalendarMode(val label: String) {
    HISTORY("历史模式"),
    FUTURE("未来模式")
}

data class LineChartEntry(
    val date: LocalDate,
    val newCount: Int,
    val reviewCount: Int
)

data class MasteryDistribution(
    val unlearned: Int,
    val learning: Int,
    val mastered: Int,
    val total: Int
)

data class HeatmapCell(
    val date: LocalDate,
    val count: Int,
    val level: Int,
    val inRange: Boolean
)

data class ForecastDayUi(
    val date: LocalDate,
    val reviewCount: Int,
    val newWordQuota: Int,
    val totalCount: Int,
    val pressureLevel: PressureLevel,
    val isToday: Boolean
)

data class ForecastWordPreview(
    val word: String,
    val meaning: String
)

data class GestureStatsSummary(
    val gestureEasyCount: Int = 0,
    val gestureNotebookCount: Int = 0
)

data class TodayRecognitionSummary(
    val fuzzyCount: Int = 0,
    val recognizedCount: Int = 0
)

private data class ForecastUiBundle(
    val forecast: List<ForecastDayUi>,
    val selectedDate: LocalDate?,
    val selectedWords: List<ForecastWordPreview>,
    val loadLabel: String
)

private data class MlInsights(
    val stabilityIndex: Float,
    val optimalHourStart: Int,
    val optimalHourEnd: Int,
    val heatmapData: List<MemoryHeatmapEntry>
)

data class StatsUiState(
    val range: StatsRange = StatsRange.WEEK,
    val lineData: List<LineChartEntry> = emptyList(),
    val mastery: MasteryDistribution = MasteryDistribution(0, 0, 0, 0),
    val heatmap: List<HeatmapCell> = emptyList(),
    val gestureEasyCount: Int = 0,
    val gestureNotebookCount: Int = 0,
    val todayFuzzyCount: Int = 0,
    val todayRecognizedCount: Int = 0,
    val calendarMode: CalendarMode = CalendarMode.HISTORY,
    val forecast: List<ForecastDayUi> = emptyList(),
    val selectedForecastDate: LocalDate? = null,
    val selectedForecastWords: List<ForecastWordPreview> = emptyList(),
    val forecastLoadLabel: String = "",
    val mlStabilityIndex: Float = 0f,
    val mlConfidence: Float = 0f,
    val mlSampleCount: Int = 0,
    val mlOptimalHourStart: Int = 9,
    val mlOptimalHourEnd: Int = 11,
    val mlHeatmapData: List<MemoryHeatmapEntry> = emptyList(),
    val mlEnabled: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as KaoyanWordApp).repository
    private val forecastRepository = (application as KaoyanWordApp).forecastRepository
    private val settingsRepository = (application as KaoyanWordApp).settingsRepository
    private val mlModelPersistence = (application as KaoyanWordApp).mlModelPersistence
    private val trainingSampleDao = (application as KaoyanWordApp).database.trainingSampleDao()
    private val zoneId = ZoneId.systemDefault()

    private val rangeFlow = MutableStateFlow(StatsRange.WEEK)
    private val calendarModeFlow = MutableStateFlow(CalendarMode.HISTORY)
    private val forecastFlow = MutableStateFlow<List<ForecastDayUi>>(emptyList())
    private val selectedForecastDateFlow = MutableStateFlow<LocalDate?>(null)
    private val selectedForecastWordsFlow = MutableStateFlow<List<ForecastWordPreview>>(emptyList())
    private val forecastLoadLabelFlow = MutableStateFlow("")
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val mlStateFlow: StateFlow<MLModelState?> = mlModelPersistence.observeModelState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val mlEnabledFlow = settingsRepository.settingsFlow.map { it.mlAdaptiveEnabled }
    private val trainingSampleCountFlow = trainingSampleDao.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private val mlInsightsFlow: StateFlow<MlInsights> = combine(
        trainingSampleDao.observeRecentSamples(ML_INSIGHTS_SAMPLE_LIMIT),
        mlStateFlow
    ) { samples, modelState ->
        buildMlInsights(
            samples = samples,
            fallbackRetention = modelState?.userBaseRetention ?: DEFAULT_RETENTION
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), defaultMlInsights(DEFAULT_RETENTION))

    private val lineDataFlow = rangeFlow.flatMapLatest { range ->
        val endDate = LocalDate.now(zoneId)
        val startDate = endDate.minusDays(range.days - 1)
        repository.getDailyStatsAggregated(startDate, endDate)
            .map { aggregates -> buildLineData(startDate, endDate, aggregates) }
    }

    private val masteryFlow = repository.getActiveBookFlow()
        .flatMapLatest { book ->
            if (book == null) {
                flowOf(MasteryDistribution(0, 0, 0, 0))
            } else {
                combine(
                    repository.getLearnedCount(book.id),
                    repository.getMasteredCount(book.id)
                ) { learned, mastered ->
                    buildMastery(book, learned, mastered)
                }
            }
        }

    private val heatmapRange = run {
        val end = LocalDate.now(zoneId)
        val start = end.minusWeeks(11).with(DayOfWeek.MONDAY)
        start to end
    }

    private val heatmapFlow = repository.getDailyStatsAggregated(
        heatmapRange.first,
        heatmapRange.second
    ).map { aggregates ->
        buildHeatmap(heatmapRange.first, heatmapRange.second, aggregates)
    }

    private val gestureStatsFlow = rangeFlow.flatMapLatest { range ->
        val endDate = LocalDate.now(zoneId)
        val startDate = endDate.minusDays(range.days - 1)
        repository.getDailyStatsAggregated(startDate, endDate)
            .map { aggregates ->
                GestureStatsSummary(
                    gestureEasyCount = aggregates.sumOf { it.gestureEasyCount },
                    gestureNotebookCount = aggregates.sumOf { it.gestureNotebookCount }
                )
            }
    }

    private val todayRecognitionFlow = repository.getDailyStatsAggregated(
        LocalDate.now(zoneId),
        LocalDate.now(zoneId)
    ).map { aggregates ->
        val todayAggregate = aggregates.firstOrNull()
        TodayRecognitionSummary(
            fuzzyCount = todayAggregate?.fuzzyWordsCount ?: 0,
            recognizedCount = todayAggregate?.recognizedWordsCount ?: 0
        )
    }

    private val baseUiFlow = combine(
        rangeFlow,
        lineDataFlow,
        masteryFlow,
        heatmapFlow,
        gestureStatsFlow
    ) { range, lineData, mastery, heatmap, gestureStats ->
        StatsUiState(
            range = range,
            lineData = lineData,
            mastery = mastery,
            heatmap = heatmap,
            gestureEasyCount = gestureStats.gestureEasyCount,
            gestureNotebookCount = gestureStats.gestureNotebookCount
        )
    }.combine(todayRecognitionFlow) { base, todayRecognition ->
        base.copy(
            todayFuzzyCount = todayRecognition.fuzzyCount,
            todayRecognizedCount = todayRecognition.recognizedCount
        )
    }

    private val forecastUiFlow = combine(
        forecastFlow,
        selectedForecastDateFlow,
        selectedForecastWordsFlow,
        forecastLoadLabelFlow
    ) { forecast, selectedDate, selectedWords, loadLabel ->
        ForecastUiBundle(
            forecast = forecast,
            selectedDate = selectedDate,
            selectedWords = selectedWords,
            loadLabel = loadLabel
        )
    }

    private val mlUiBaseFlow = combine(
        baseUiFlow,
        calendarModeFlow,
        forecastUiFlow,
        mlEnabledFlow,
        trainingSampleCountFlow
    ) { base, mode, forecastUi, mlEnabled, trainingSampleCount ->
        base.copy(
            calendarMode = mode,
            forecast = forecastUi.forecast,
            selectedForecastDate = forecastUi.selectedDate,
            selectedForecastWords = forecastUi.selectedWords,
            forecastLoadLabel = forecastUi.loadLabel,
            mlConfidence = computeMlConfidence(trainingSampleCount),
            mlSampleCount = trainingSampleCount,
            mlEnabled = mlEnabled
        )
    }

    val uiState: StateFlow<StatsUiState> = mlUiBaseFlow
        .combine(mlInsightsFlow) { state, mlInsights ->
            state.copy(
                mlStabilityIndex = mlInsights.stabilityIndex,
                mlOptimalHourStart = mlInsights.optimalHourStart,
                mlOptimalHourEnd = mlInsights.optimalHourEnd,
                mlHeatmapData = mlInsights.heatmapData
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    init {
        refreshForecast()
    }

    fun updateRange(range: StatsRange) {
        rangeFlow.value = range
    }

    fun updateCalendarMode(mode: CalendarMode) {
        calendarModeFlow.value = mode
        if (mode == CalendarMode.FUTURE && forecastFlow.value.isEmpty()) {
            refreshForecast()
        }
    }

    fun selectForecastDate(date: LocalDate) {
        selectedForecastDateFlow.value = date
        loadForecastPreview(date)
    }

    fun pullForwardForSelectedDate(moveCount: Int = 5) {
        val selectedDate = selectedForecastDateFlow.value ?: return
        if (!selectedDate.isAfter(LocalDate.now(zoneId))) return
        viewModelScope.launch {
            val moved = withContext(Dispatchers.IO) {
                forecastRepository.pullForwardReviews(
                    targetDate = selectedDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    count = moveCount
                )
            }
            _message.value = if (moved > 0) {
                "已提前消化 $moved 个复习任务"
            } else {
                "没有可前移的复习任务"
            }
            refreshForecast()
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun refreshForecast() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                forecastRepository.loadNext7DaysPressure()
            }
            val days = result.days.map { day ->
                val localDate = Instant.ofEpochMilli(day.date).atZone(zoneId).toLocalDate()
                ForecastDayUi(
                    date = localDate,
                    reviewCount = day.reviewCount,
                    newWordQuota = day.newWordQuota,
                    totalCount = day.totalCount,
                    pressureLevel = day.pressureLevel,
                    isToday = localDate == LocalDate.now(zoneId)
                )
            }
            forecastFlow.value = days
            forecastLoadLabelFlow.value = buildForecastLoadLabel(result.source, result.elapsedMillis)
            val selected = selectedForecastDateFlow.value
            val fallback = days.firstOrNull { it.isToday }?.date ?: days.firstOrNull()?.date
            val resolved = when {
                selected == null -> fallback
                days.any { it.date == selected } -> selected
                else -> fallback
            }
            selectedForecastDateFlow.value = resolved
            if (resolved != null) {
                loadForecastPreview(resolved)
            } else {
                selectedForecastWordsFlow.value = emptyList()
            }
        }
    }

    private fun loadForecastPreview(date: LocalDate) {
        viewModelScope.launch {
            val words = withContext(Dispatchers.IO) {
                forecastRepository.getWordsForDate(
                    date = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                    limit = 5
                )
            }
            selectedForecastWordsFlow.value = words.map { word ->
                ForecastWordPreview(
                    word = word.word,
                    meaning = word.meaning.ifBlank { "（无释义）" }
                )
            }
        }
    }

    private fun buildLineData(
        startDate: LocalDate,
        endDate: LocalDate,
        aggregates: List<DailyStatsAggregate>
    ): List<LineChartEntry> {
        val map = aggregates.mapNotNull { aggregate ->
            parseDate(aggregate.date)?.let { it to aggregate }
        }.toMap()
        val result = mutableListOf<LineChartEntry>()
        var cursor = startDate
        while (!cursor.isAfter(endDate)) {
            val aggregate = map[cursor]
            result.add(
                LineChartEntry(
                    date = cursor,
                    newCount = aggregate?.newWordsCount ?: 0,
                    reviewCount = aggregate?.reviewWordsCount ?: 0
                )
            )
            cursor = cursor.plusDays(1)
        }
        return result
    }

    private fun buildMastery(book: Book, learnedCount: Int, masteredCount: Int): MasteryDistribution {
        val total = book.totalCount
        val safeLearned = learnedCount.coerceAtMost(total).coerceAtLeast(0)
        val safeMastered = masteredCount.coerceAtMost(safeLearned).coerceAtLeast(0)
        val learning = (safeLearned - safeMastered).coerceAtLeast(0)
        val unlearned = (total - safeLearned).coerceAtLeast(0)
        return MasteryDistribution(
            unlearned = unlearned,
            learning = learning,
            mastered = safeMastered,
            total = total
        )
    }

    private fun buildHeatmap(
        startDate: LocalDate,
        endDate: LocalDate,
        aggregates: List<DailyStatsAggregate>
    ): List<HeatmapCell> {
        val map = aggregates.mapNotNull { aggregate ->
            parseDate(aggregate.date)?.let { it to aggregate }
        }.toMap()
        val maxCount = aggregates.maxOfOrNull { aggregate ->
            aggregate.newWordsCount + aggregate.reviewWordsCount + aggregate.spellPracticeCount
        } ?: 0
        val displayEnd = endDate.with(DayOfWeek.SUNDAY)
        val result = mutableListOf<HeatmapCell>()
        var cursor = startDate
        while (!cursor.isAfter(displayEnd)) {
            val inRange = !cursor.isAfter(endDate)
            val aggregate = if (inRange) map[cursor] else null
            val count = aggregate?.let {
                it.newWordsCount + it.reviewWordsCount + it.spellPracticeCount
            } ?: 0
            val level = if (inRange) computeLevel(count, maxCount) else 0
            result.add(
                HeatmapCell(
                    date = cursor,
                    count = count,
                    level = level,
                    inRange = inRange
                )
            )
            cursor = cursor.plusDays(1)
        }
        return result
    }

    private fun computeLevel(count: Int, maxCount: Int): Int {
        if (count <= 0 || maxCount <= 0) return 0
        val ratio = count.toFloat() / maxCount.toFloat()
        return when {
            ratio <= 0.25f -> 1
            ratio <= 0.5f -> 2
            ratio <= 0.75f -> 3
            else -> 4
        }
    }

    private fun parseDate(raw: String): LocalDate? {
        return runCatching { LocalDate.parse(raw, DATE_FORMATTER) }.getOrNull()
    }

    private fun buildForecastLoadLabel(source: ForecastDataSource, elapsedMillis: Long): String {
        val sourceLabel = when (source) {
            ForecastDataSource.CACHE -> "缓存命中"
            ForecastDataSource.CALCULATED -> "实时计算"
        }
        return "预测来源：$sourceLabel · 耗时 ${elapsedMillis.coerceAtLeast(0L)}ms"
    }

    private fun buildMlInsights(samples: List<TrainingSample>, fallbackRetention: Float): MlInsights {
        if (samples.isEmpty()) {
            return defaultMlInsights(fallbackRetention)
        }

        val totalMatrix = Array(7) { IntArray(24) }
        val rememberedMatrix = Array(7) { IntArray(24) }
        val hourTotals = IntArray(24)
        val hourRemembered = IntArray(24)

        var validSamples = 0
        var rememberedTotal = 0

        samples.forEach { sample ->
            val vector = runCatching { FeatureVector.fromJson(sample.featuresJson) }.getOrNull() ?: return@forEach
            val hour = decodeHour(vector)
            val day = decodeDay(vector)
            val remembered = sample.outcome == 0

            validSamples++
            if (remembered) rememberedTotal++

            totalMatrix[day][hour] += 1
            hourTotals[hour] += 1
            if (remembered) {
                rememberedMatrix[day][hour] += 1
                hourRemembered[hour] += 1
            }
        }

        if (validSamples == 0) {
            return defaultMlInsights(fallbackRetention)
        }

        val retentionRaw = rememberedTotal.toFloat() / validSamples.toFloat()
        val retentionBlend = (validSamples.toFloat() / RETENTION_CONFIDENCE_SAMPLES.toFloat()).coerceIn(0f, 1f)
        val stabilityIndex = (fallbackRetention * (1f - retentionBlend) + retentionRaw * retentionBlend)
            .coerceIn(0.05f, 0.99f)

        val hourScore = FloatArray(24) { hour ->
            if (hourTotals[hour] > 0) {
                hourRemembered[hour].toFloat() / hourTotals[hour].toFloat()
            } else {
                stabilityIndex
            }
        }

        val heatmapData = buildList(7 * 24) {
            for (day in 0..6) {
                for (hour in 0..23) {
                    val efficiency = if (totalMatrix[day][hour] > 0) {
                        rememberedMatrix[day][hour].toFloat() / totalMatrix[day][hour].toFloat()
                    } else {
                        hourScore[hour]
                    }
                    add(
                        MemoryHeatmapEntry(
                            dayOfWeek = day,
                            hour = hour,
                            efficiency = efficiency.coerceIn(0f, 1f)
                        )
                    )
                }
            }
        }

        val optimalStart = (0..21).maxByOrNull { hour ->
            (hourScore[hour] + hourScore[hour + 1]) / 2f
        } ?: DEFAULT_OPTIMAL_START_HOUR

        return MlInsights(
            stabilityIndex = stabilityIndex,
            optimalHourStart = optimalStart,
            optimalHourEnd = optimalStart + 2,
            heatmapData = heatmapData
        )
    }

    private fun decodeHour(vector: FeatureVector): Int {
        return (vector[1] * 23f).roundToInt().coerceIn(0, 23)
    }

    private fun decodeDay(vector: FeatureVector): Int {
        val calendarDay = (vector[2] * 6f).roundToInt().coerceIn(0, 6)
        return if (calendarDay == 0) 6 else calendarDay - 1
    }

    private fun computeMlConfidence(sampleCount: Int): Float {
        return when {
            sampleCount < 50 -> sampleCount.toFloat() / 50f * 0.3f
            sampleCount < 200 -> {
                val progress = (sampleCount - 50).toFloat() / 150f
                0.3f + progress * 0.5f
            }

            else -> (0.8f + (sampleCount - 200).toFloat() / 1000f).coerceAtMost(1f)
        }.coerceIn(0f, 1f)
    }

    private fun defaultMlInsights(fallbackRetention: Float): MlInsights {
        return MlInsights(
            stabilityIndex = fallbackRetention.coerceIn(0.05f, 0.99f),
            optimalHourStart = DEFAULT_OPTIMAL_START_HOUR,
            optimalHourEnd = DEFAULT_OPTIMAL_START_HOUR + 2,
            heatmapData = buildList(7 * 24) {
                for (day in 0..6) {
                    for (hour in 0..23) {
                        add(
                            MemoryHeatmapEntry(
                                dayOfWeek = day,
                                hour = hour,
                                efficiency = fallbackRetention.coerceIn(0f, 1f)
                            )
                        )
                    }
                }
            }
        )
    }

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
        private const val DEFAULT_RETENTION = 0.85f
        private const val ML_INSIGHTS_SAMPLE_LIMIT = 500
        private const val RETENTION_CONFIDENCE_SAMPLES = 80
        private const val DEFAULT_OPTIMAL_START_HOUR = 9
    }
}
