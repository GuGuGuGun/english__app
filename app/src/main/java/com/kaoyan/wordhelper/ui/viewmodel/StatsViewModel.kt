package com.kaoyan.wordhelper.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaoyan.wordhelper.KaoyanWordApp
import com.kaoyan.wordhelper.data.entity.Book
import com.kaoyan.wordhelper.data.model.DailyStatsAggregate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class StatsRange(val days: Long, val label: String) {
    WEEK(7, "近7天"),
    MONTH(30, "近30天")
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

data class StatsUiState(
    val range: StatsRange = StatsRange.WEEK,
    val lineData: List<LineChartEntry> = emptyList(),
    val mastery: MasteryDistribution = MasteryDistribution(0, 0, 0, 0),
    val heatmap: List<HeatmapCell> = emptyList()
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as KaoyanWordApp).repository

    private val rangeFlow = MutableStateFlow(StatsRange.WEEK)
    val range: StateFlow<StatsRange> = rangeFlow

    private val lineDataFlow = rangeFlow.flatMapLatest { range ->
        val endDate = LocalDate.now()
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
        val end = LocalDate.now()
        val start = end.minusWeeks(11).with(DayOfWeek.MONDAY)
        start to end
    }

    private val heatmapFlow = repository.getDailyStatsAggregated(
        heatmapRange.first,
        heatmapRange.second
    ).map { aggregates ->
        buildHeatmap(heatmapRange.first, heatmapRange.second, aggregates)
    }

    val uiState: StateFlow<StatsUiState> = combine(
        rangeFlow,
        lineDataFlow,
        masteryFlow,
        heatmapFlow
    ) { range, lineData, mastery, heatmap ->
        StatsUiState(range = range, lineData = lineData, mastery = mastery, heatmap = heatmap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun updateRange(range: StatsRange) {
        rangeFlow.value = range
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

    companion object {
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
