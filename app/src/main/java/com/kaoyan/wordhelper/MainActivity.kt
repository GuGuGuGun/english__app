package com.kaoyan.wordhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.kaoyan.wordhelper.ui.navigation.AppNavigation
import com.kaoyan.wordhelper.ui.theme.KaoyanWordTheme
import com.kaoyan.wordhelper.data.repository.DarkMode
import com.kaoyan.wordhelper.data.repository.UserSettings
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides this@MainActivity) {
                val settingsRepository = (application as KaoyanWordApp).settingsRepository
                val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(
                    lifecycleOwner = this@MainActivity,
                    initialValue = UserSettings()
                )
                val darkTheme = when (settings.darkMode) {
                    DarkMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                    DarkMode.LIGHT -> false
                    DarkMode.DARK -> true
                }
                KaoyanWordTheme(darkTheme = darkTheme, fontScale = settings.fontScale) {
                    AppNavigation()
                }
            }
        }
    }
}
