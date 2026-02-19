package com.kaoyan.wordhelper.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kaoyan.wordhelper.data.model.PronunciationSource
import com.kaoyan.wordhelper.util.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DEFAULT_NEW_WORDS_LIMIT = 20
private const val DEFAULT_FONT_SCALE = 1.0f
private const val DEFAULT_REVIEW_PRESSURE_RELIEF_ENABLED = true
private const val DEFAULT_REVIEW_PRESSURE_DAILY_CAP = 120
private const val DEFAULT_SWIPE_GESTURE_GUIDE_SHOWN = false
private const val DEFAULT_ALGORITHM_V4_ENABLED = false
private const val DEFAULT_PRONUNCIATION_ENABLED = false
private const val DEFAULT_PRONUNCIATION_SOURCE = 0
private const val DEFAULT_RECOGNITION_AUTO_PRONOUNCE_ENABLED = false
private const val DEFAULT_NEW_WORDS_SHUFFLE_ENABLED = false
private const val DEFAULT_ML_ADAPTIVE_ENABLED = false
private const val DEFAULT_PLANNED_NEW_WORDS_ENABLED = false
private val Context.dataStore by preferencesDataStore(name = "user_settings")

data class UserSettings(
    val newWordsLimit: Int = DEFAULT_NEW_WORDS_LIMIT,
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
    val reviewPressureReliefEnabled: Boolean = DEFAULT_REVIEW_PRESSURE_RELIEF_ENABLED,
    val reviewPressureDailyCap: Int = DEFAULT_REVIEW_PRESSURE_DAILY_CAP,
    val swipeGestureGuideShown: Boolean = DEFAULT_SWIPE_GESTURE_GUIDE_SHOWN,
    val algorithmV4Enabled: Boolean = DEFAULT_ALGORITHM_V4_ENABLED,
    val pronunciationEnabled: Boolean = DEFAULT_PRONUNCIATION_ENABLED,
    val pronunciationSource: PronunciationSource = PronunciationSource.FREE_DICTIONARY,
    val recognitionAutoPronounceEnabled: Boolean = DEFAULT_RECOGNITION_AUTO_PRONOUNCE_ENABLED,
    val newWordsShuffleEnabled: Boolean = DEFAULT_NEW_WORDS_SHUFFLE_ENABLED,
    val mlAdaptiveEnabled: Boolean = DEFAULT_ML_ADAPTIVE_ENABLED,
    val plannedNewWordsEnabled: Boolean = DEFAULT_PLANNED_NEW_WORDS_ENABLED
)

data class TodayNewWordsPlan(
    val learningDate: String = "",
    val bookId: Long? = null,
    val wordIds: List<Long> = emptyList()
)

enum class DarkMode(val value: Int) {
    FOLLOW_SYSTEM(0),
    LIGHT(1),
    DARK(2);

    companion object {
        fun fromValue(value: Int): DarkMode {
            return entries.firstOrNull { it.value == value } ?: FOLLOW_SYSTEM
        }
    }
}

class SettingsRepository(private val context: Context) {
    private val dataStore = context.dataStore

    val todayNewWordsPlanFlow: Flow<TodayNewWordsPlan> = dataStore.data.map { prefs ->
        val rawDate = prefs[KEY_TODAY_NEW_WORDS_PLAN_DATE].orEmpty()
        val rawBookId = prefs[KEY_TODAY_NEW_WORDS_PLAN_BOOK_ID].orEmpty()
        val rawWordIds = prefs[KEY_TODAY_NEW_WORDS_PLAN_WORD_IDS].orEmpty()
        val parsedWordIds = rawWordIds
            .split(',')
            .mapNotNull { token -> token.trim().toLongOrNull() }
            .distinct()
        TodayNewWordsPlan(
            learningDate = rawDate,
            bookId = rawBookId.toLongOrNull(),
            wordIds = parsedWordIds
        )
    }

    val settingsFlow: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            newWordsLimit = prefs[KEY_NEW_WORDS_LIMIT] ?: DEFAULT_NEW_WORDS_LIMIT,
            fontScale = prefs[KEY_FONT_SCALE] ?: DEFAULT_FONT_SCALE,
            darkMode = DarkMode.fromValue(prefs[KEY_DARK_MODE] ?: DarkMode.FOLLOW_SYSTEM.value),
            reviewPressureReliefEnabled = prefs[KEY_REVIEW_PRESSURE_RELIEF_ENABLED]
                ?: DEFAULT_REVIEW_PRESSURE_RELIEF_ENABLED,
            reviewPressureDailyCap = (prefs[KEY_REVIEW_PRESSURE_DAILY_CAP] ?: DEFAULT_REVIEW_PRESSURE_DAILY_CAP)
                .coerceIn(10, 500),
            swipeGestureGuideShown = prefs[KEY_SWIPE_GESTURE_GUIDE_SHOWN] ?: DEFAULT_SWIPE_GESTURE_GUIDE_SHOWN,
            algorithmV4Enabled = prefs[KEY_ALGORITHM_V4_ENABLED] ?: DEFAULT_ALGORITHM_V4_ENABLED,
            pronunciationEnabled = prefs[KEY_PRONUNCIATION_ENABLED] ?: DEFAULT_PRONUNCIATION_ENABLED,
            pronunciationSource = PronunciationSource.fromValue(
                prefs[KEY_PRONUNCIATION_SOURCE] ?: DEFAULT_PRONUNCIATION_SOURCE
            ),
            recognitionAutoPronounceEnabled =
                prefs[KEY_RECOGNITION_AUTO_PRONOUNCE_ENABLED]
                    ?: DEFAULT_RECOGNITION_AUTO_PRONOUNCE_ENABLED,
            newWordsShuffleEnabled =
                prefs[KEY_NEW_WORDS_SHUFFLE_ENABLED] ?: DEFAULT_NEW_WORDS_SHUFFLE_ENABLED,
            mlAdaptiveEnabled =
                prefs[KEY_ML_ADAPTIVE_ENABLED] ?: DEFAULT_ML_ADAPTIVE_ENABLED,
            plannedNewWordsEnabled =
                prefs[KEY_PLANNED_NEW_WORDS_ENABLED] ?: DEFAULT_PLANNED_NEW_WORDS_ENABLED
        )
    }

    suspend fun updateNewWordsLimit(limit: Int) {
        dataStore.edit { prefs -> prefs[KEY_NEW_WORDS_LIMIT] = limit.coerceIn(1, 500) }
    }

    suspend fun updateFontScale(scale: Float) {
        dataStore.edit { prefs -> prefs[KEY_FONT_SCALE] = scale.coerceIn(0.85f, 1.3f) }
    }

    suspend fun updateDarkMode(mode: DarkMode) {
        dataStore.edit { prefs -> prefs[KEY_DARK_MODE] = mode.value }
    }

    suspend fun updateReviewPressureReliefEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_REVIEW_PRESSURE_RELIEF_ENABLED] = enabled }
    }

    suspend fun updateReviewPressureDailyCap(cap: Int) {
        dataStore.edit { prefs -> prefs[KEY_REVIEW_PRESSURE_DAILY_CAP] = cap.coerceIn(10, 500) }
    }

    suspend fun updateSwipeGestureGuideShown(shown: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_SWIPE_GESTURE_GUIDE_SHOWN] = shown }
    }

    suspend fun updateAlgorithmV4Enabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ALGORITHM_V4_ENABLED] = enabled }
    }

    suspend fun updatePronunciationEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_PRONUNCIATION_ENABLED] = enabled }
    }

    suspend fun updatePronunciationSource(source: PronunciationSource) {
        dataStore.edit { prefs -> prefs[KEY_PRONUNCIATION_SOURCE] = source.value }
    }

    suspend fun updateRecognitionAutoPronounceEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_RECOGNITION_AUTO_PRONOUNCE_ENABLED] = enabled }
    }

    suspend fun updateNewWordsShuffleEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_NEW_WORDS_SHUFFLE_ENABLED] = enabled }
    }

    suspend fun updateMlAdaptiveEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ML_ADAPTIVE_ENABLED] = enabled }
    }

    suspend fun updatePlannedNewWordsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_PLANNED_NEW_WORDS_ENABLED] = enabled }
    }

    suspend fun saveTodayNewWordsPlan(bookId: Long, wordIds: List<Long>) {
        val learningDate = DateUtils.currentLearningDate().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        val normalizedWordIds = wordIds.distinct().joinToString(separator = ",")
        dataStore.edit { prefs ->
            prefs[KEY_TODAY_NEW_WORDS_PLAN_DATE] = learningDate
            prefs[KEY_TODAY_NEW_WORDS_PLAN_BOOK_ID] = bookId.toString()
            prefs[KEY_TODAY_NEW_WORDS_PLAN_WORD_IDS] = normalizedWordIds
        }
    }

    suspend fun clearTodayNewWordsPlan() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_TODAY_NEW_WORDS_PLAN_DATE)
            prefs.remove(KEY_TODAY_NEW_WORDS_PLAN_BOOK_ID)
            prefs.remove(KEY_TODAY_NEW_WORDS_PLAN_WORD_IDS)
        }
    }

    companion object {

        private val KEY_NEW_WORDS_LIMIT = intPreferencesKey("new_words_limit")
        private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        private val KEY_DARK_MODE = intPreferencesKey("dark_mode")
        private val KEY_REVIEW_PRESSURE_RELIEF_ENABLED =
            booleanPreferencesKey("review_pressure_relief_enabled")
        private val KEY_REVIEW_PRESSURE_DAILY_CAP = intPreferencesKey("review_pressure_daily_cap")
        private val KEY_SWIPE_GESTURE_GUIDE_SHOWN = booleanPreferencesKey("swipe_gesture_guide_shown")
        private val KEY_ALGORITHM_V4_ENABLED = booleanPreferencesKey("algorithm_v4_enabled")
        private val KEY_PRONUNCIATION_ENABLED = booleanPreferencesKey("pronunciation_enabled")
        private val KEY_PRONUNCIATION_SOURCE = intPreferencesKey("pronunciation_source")
        private val KEY_RECOGNITION_AUTO_PRONOUNCE_ENABLED =
            booleanPreferencesKey("recognition_auto_pronounce_enabled")
        private val KEY_NEW_WORDS_SHUFFLE_ENABLED = booleanPreferencesKey("new_words_shuffle_enabled")
        private val KEY_ML_ADAPTIVE_ENABLED = booleanPreferencesKey("ml_adaptive_enabled")
        private val KEY_PLANNED_NEW_WORDS_ENABLED = booleanPreferencesKey("planned_new_words_enabled")
        private val KEY_TODAY_NEW_WORDS_PLAN_DATE = stringPreferencesKey("today_new_words_plan_date")
        private val KEY_TODAY_NEW_WORDS_PLAN_BOOK_ID = stringPreferencesKey("today_new_words_plan_book_id")
        private val KEY_TODAY_NEW_WORDS_PLAN_WORD_IDS = stringPreferencesKey("today_new_words_plan_word_ids")
    }
}
