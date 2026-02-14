package com.kaoyan.wordhelper.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaoyan.wordhelper.data.model.AIConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AISecureStorageInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun saveAndGetConfig_roundTripWorks() {
        val config = AIConfig(
            enabled = true,
            apiBaseUrl = "https://api.openai.com/v1",
            apiKey = "sk-test-123456",
            modelName = "gpt-3.5-turbo"
        )

        AISecureStorage.saveConfig(context, config)
        val restored = AISecureStorage.getConfig(context)

        assertTrue(restored.enabled)
        assertEquals("https://api.openai.com/v1/", restored.apiBaseUrl)
        assertEquals("sk-test-123456", restored.apiKey)
        assertEquals("gpt-3.5-turbo", restored.modelName)
    }

    @Test
    fun saveConfig_plainApiKeyIsNotStoredInPrefsXml() {
        val rawApiKey = "sk-plain-secret-value"
        AISecureStorage.saveConfig(
            context,
            AIConfig(
                enabled = true,
                apiBaseUrl = "https://api.openai.com/v1/",
                apiKey = rawApiKey,
                modelName = "gpt-3.5-turbo"
            )
        )

        val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/ai_secure_prefs.xml")
        assertTrue("encrypted prefs file should exist", prefsFile.exists())
        val xmlContent = prefsFile.readText()
        assertFalse("plaintext api key should not appear in encrypted prefs", xmlContent.contains(rawApiKey))
    }
}
