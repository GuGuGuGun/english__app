package com.kaoyan.wordhelper.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DEFAULT_NEW_WORDS_LIMIT = 20
private const val DEFAULT_FONT_SCALE = 1.0f
private const val DEFAULT_REVIEW_PRESSURE_RELIEF_ENABLED = true
private const val DEFAULT_REVIEW_PRESSURE_DAILY_CAP = 120
private const val DEFAULT_SWIPE_GESTURE_GUIDE_SHOWN = false
private val Context.dataStore by preferencesDataStore(name = "user_settings")

data class UserSettings(
    val newWordsLimit: Int = DEFAULT_NEW_WORDS_LIMIT,
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val darkMode: DarkMode = DarkMode.FOLLOW_SYSTEM,
    val reviewPressureReliefEnabled: Boolean = DEFAULT_REVIEW_PRESSURE_RELIEF_ENABLED,
    val reviewPressureDailyCap: Int = DEFAULT_REVIEW_PRESSURE_DAILY_CAP,
    val swipeGestureGuideShown: Boolean = DEFAULT_SWIPE_GESTURE_GUIDE_SHOWN
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

    val settingsFlow: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            newWordsLimit = prefs[KEY_NEW_WORDS_LIMIT] ?: DEFAULT_NEW_WORDS_LIMIT,
            fontScale = prefs[KEY_FONT_SCALE] ?: DEFAULT_FONT_SCALE,
            darkMode = DarkMode.fromValue(prefs[KEY_DARK_MODE] ?: DarkMode.FOLLOW_SYSTEM.value),
            reviewPressureReliefEnabled = prefs[KEY_REVIEW_PRESSURE_RELIEF_ENABLED]
                ?: DEFAULT_REVIEW_PRESSURE_RELIEF_ENABLED,
            reviewPressureDailyCap = (prefs[KEY_REVIEW_PRESSURE_DAILY_CAP] ?: DEFAULT_REVIEW_PRESSURE_DAILY_CAP)
                .coerceIn(10, 500),
            swipeGestureGuideShown = prefs[KEY_SWIPE_GESTURE_GUIDE_SHOWN] ?: DEFAULT_SWIPE_GESTURE_GUIDE_SHOWN
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

    companion object {

        private val KEY_NEW_WORDS_LIMIT = intPreferencesKey("new_words_limit")
        private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        private val KEY_DARK_MODE = intPreferencesKey("dark_mode")
        private val KEY_REVIEW_PRESSURE_RELIEF_ENABLED =
            booleanPreferencesKey("review_pressure_relief_enabled")
        private val KEY_REVIEW_PRESSURE_DAILY_CAP = intPreferencesKey("review_pressure_daily_cap")
        private val KEY_SWIPE_GESTURE_GUIDE_SHOWN = booleanPreferencesKey("swipe_gesture_guide_shown")
    }
}
