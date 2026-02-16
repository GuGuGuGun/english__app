package com.kaoyan.wordhelper.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaoyan.wordhelper.KaoyanWordApp
import com.kaoyan.wordhelper.data.model.DailyStatsAggregate
import com.kaoyan.wordhelper.data.model.AIPresets
import com.kaoyan.wordhelper.data.repository.DarkMode
import com.kaoyan.wordhelper.data.repository.UserSettings
import com.kaoyan.wordhelper.util.DatabaseBackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ProfileStats(
    val streakDays: Int,
    val todayStudyCount: Int,
    val weekStudyCount: Int,
    val todaySpellCount: Int,
    val weekSpellCount: Int,
    val weekLineData: List<LineChartEntry>,
    val heatmap: List<HeatmapCell>
)

data class AiLabSummary(
    val status: String = "未配置 API Key",
    val enabled: Boolean = false,
    val isConfigured: Boolean = false
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as KaoyanWordApp).repository
    private val settingsRepository = (application as KaoyanWordApp).settingsRepository
    private val aiConfigRepository = (application as KaoyanWordApp).aiConfigRepository
    private val database = (application as KaoyanWordApp).database

    val settings: StateFlow<UserSettings> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val weekRange = run {
        val end = LocalDate.now()
        val start = end.minusDays(6)
        start to end
    }

    private val heatmapRange = run {
        val end = LocalDate.now()
        val start = end.minusWeeks(11).with(DayOfWeek.MONDAY)
        start to end
    }

    private val weekStatsFlow = repository.getDailyStatsAggregated(weekRange.first, weekRange.second)
    private val heatmapStatsFlow = repository.getDailyStatsAggregated(heatmapRange.first, heatmapRange.second)

    val stats: StateFlow<ProfileStats> = combine(weekStatsFlow, heatmapStatsFlow) { weekAggregates, heatmapAggregates ->
        buildStats(weekAggregates, heatmapAggregates)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyStats())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _aiLabSummary = MutableStateFlow(AiLabSummary())
    val aiLabSummary: StateFlow<AiLabSummary> = _aiLabSummary.asStateFlow()

    init {
        refreshAiSummary()
    }

    fun updateNewWordsLimit(limit: Int) {
        viewModelScope.launch {
            settingsRepository.updateNewWordsLimit(limit)
        }
    }

    fun updateFontScale(scale: Float) {
        viewModelScope.launch {
            settingsRepository.updateFontScale(scale)
        }
    }

    fun updateDarkMode(mode: DarkMode) {
        viewModelScope.launch {
            settingsRepository.updateDarkMode(mode)
        }
    }

    fun updateReviewPressureReliefEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateReviewPressureReliefEnabled(enabled)
        }
    }

    fun updateReviewPressureDailyCap(cap: Int) {
        viewModelScope.launch {
            settingsRepository.updateReviewPressureDailyCap(cap)
        }
    }

    fun updateAlgorithmV4Enabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAlgorithmV4Enabled(enabled)
            if (enabled) {
                repository.repairMasteredStatusForV4()
            }
        }
    }

    fun exportDatabase(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val result = DatabaseBackupManager.exportDatabase(getApplication(), database, uri)
            _message.value = if (result.isSuccess) {
                "备份完成"
            } else {
                "备份失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
            }
        }
    }

    fun importDatabase(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val result = DatabaseBackupManager.importDatabase(getApplication(), database, uri)
            _message.value = if (result.isSuccess) {
                "恢复完成，请重启应用"
            } else {
                "恢复失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun refreshAiSummary() {
        viewModelScope.launch {
            val config = aiConfigRepository.getConfig()
            val hasApiKey = config.apiKey.isNotBlank()
            val providerName = AIPresets.inferProviderName(config.apiBaseUrl)
            val status = when {
                !hasApiKey -> "未配置 API Key"
                !config.enabled -> "已配置（未启用）"
                providerName == AIPresets.CUSTOM_NAME -> "已启用"
                else -> "$providerName 已启用"
            }
            _aiLabSummary.value = AiLabSummary(
                status = status,
                enabled = config.enabled,
                isConfigured = hasApiKey
            )
        }
    }

    private fun buildStats(
        weekAggregates: List<DailyStatsAggregate>,
        heatmapAggregates: List<DailyStatsAggregate>
    ): ProfileStats {
        val today = LocalDate.now()
        val weekLineData = buildLineData(weekRange.first, weekRange.second, weekAggregates)
        val todayAggregate = weekAggregates.firstOrNull { parseDate(it.date) == today }
        val todayStudyCount = todayAggregate?.let { totalCount(it) } ?: 0
        val todaySpellCount = todayAggregate?.spellPracticeCount ?: 0
        val weekStudyCount = weekAggregates.sumOf { totalCount(it) }
        val weekSpellCount = weekAggregates.sumOf { it.spellPracticeCount }
        val heatmap = buildHeatmap(heatmapRange.first, heatmapRange.second, heatmapAggregates)
        val studyDates = heatmapAggregates.filter { totalCount(it) > 0 }
            .mapNotNull { parseDate(it.date) }
            .toSet()
        val streakDays = computeStreak(today, studyDates)
        return ProfileStats(
            streakDays = streakDays,
            todayStudyCount = todayStudyCount,
            weekStudyCount = weekStudyCount,
            todaySpellCount = todaySpellCount,
            weekSpellCount = weekSpellCount,
            weekLineData = weekLineData,
            heatmap = heatmap
        )
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

    private fun buildHeatmap(
        startDate: LocalDate,
        endDate: LocalDate,
        aggregates: List<DailyStatsAggregate>
    ): List<HeatmapCell> {
        val map = aggregates.mapNotNull { aggregate ->
            parseDate(aggregate.date)?.let { it to aggregate }
        }.toMap()
        val maxCount = aggregates.maxOfOrNull { aggregate -> totalCount(aggregate) } ?: 0
        val displayEnd = endDate.with(DayOfWeek.SUNDAY)
        val result = mutableListOf<HeatmapCell>()
        var cursor = startDate
        while (!cursor.isAfter(displayEnd)) {
            val inRange = !cursor.isAfter(endDate)
            val aggregate = if (inRange) map[cursor] else null
            val count = aggregate?.let { totalCount(it) } ?: 0
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

    private fun computeStreak(today: LocalDate, studyDates: Set<LocalDate>): Int {
        var streak = 0
        var cursor = today
        while (studyDates.contains(cursor)) {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun totalCount(aggregate: DailyStatsAggregate): Int {
        return aggregate.newWordsCount + aggregate.reviewWordsCount + aggregate.spellPracticeCount
    }

    private fun parseDate(raw: String): LocalDate? {
        return runCatching { LocalDate.parse(raw, DATE_FORMATTER) }.getOrNull()
    }

    private fun emptyStats(): ProfileStats {
        return ProfileStats(
            streakDays = 0,
            todayStudyCount = 0,
            weekStudyCount = 0,
            todaySpellCount = 0,
            weekSpellCount = 0,
            weekLineData = emptyList(),
            heatmap = emptyList()
        )
    }

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
