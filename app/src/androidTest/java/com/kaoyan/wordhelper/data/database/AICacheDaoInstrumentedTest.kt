package com.kaoyan.wordhelper.data.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaoyan.wordhelper.data.entity.AICache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class AICacheDaoInstrumentedTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndQuery_returnsLatestByWordAndQuery() = runBlocking {
        val dao = database.aiCacheDao()
        dao.insert(
            AICache(
                wordId = 11L,
                queryContent = "abandon",
                type = "EXAMPLE",
                aiContent = "old-content",
                modelName = "m1",
                createdAt = 100L
            )
        )
        dao.insert(
            AICache(
                wordId = 11L,
                queryContent = "abandon",
                type = "EXAMPLE",
                aiContent = "new-content",
                modelName = "m2",
                createdAt = 200L
            )
        )

        val byWord = dao.getByWordIdAndType(11L, "EXAMPLE")
        val byQuery = dao.getByQueryAndType("abandon", "EXAMPLE")

        assertNotNull(byWord)
        assertNotNull(byQuery)
        assertEquals("new-content", byWord?.aiContent)
        assertEquals("new-content", byQuery?.aiContent)
    }

    @Test
    fun deleteOlderThan_removesExpiredRows() = runBlocking {
        val dao = database.aiCacheDao()
        dao.insert(
            AICache(
                wordId = 12L,
                queryContent = "sentence-a",
                type = "SENTENCE",
                aiContent = "old",
                modelName = "m",
                createdAt = 10L
            )
        )
        dao.insert(
            AICache(
                wordId = 12L,
                queryContent = "sentence-a",
                type = "SENTENCE",
                aiContent = "new",
                modelName = "m",
                createdAt = 1000L
            )
        )

        val deleted = dao.deleteOlderThan(100L)
        val remaining = dao.getByQueryAndType("sentence-a", "SENTENCE")

        assertEquals(1, deleted)
        assertEquals("new", remaining?.aiContent)
    }
}
