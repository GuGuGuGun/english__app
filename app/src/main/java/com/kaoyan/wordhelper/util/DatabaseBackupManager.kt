package com.kaoyan.wordhelper.util

import android.content.Context
import android.net.Uri
import com.kaoyan.wordhelper.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DatabaseBackupManager {
    private const val DB_NAME = "kaoyan_words.db"

    suspend fun exportDatabase(context: Context, database: AppDatabase, uri: Uri): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                checkpoint(database)
                val dbFile = context.getDatabasePath(DB_NAME)
                require(dbFile.exists()) { "数据库文件不存在" }
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("无法打开导出文件")
                dbFile.inputStream().use { input ->
                    output.use { out -> input.copyTo(out) }
                }
                Unit
            }
        }
    }

    suspend fun importDatabase(context: Context, database: AppDatabase, uri: Uri): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                database.close()
                val dbFile = context.getDatabasePath(DB_NAME)
                dbFile.parentFile?.mkdirs()
                cleanupWalFiles(dbFile)
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("无法打开导入文件")
                input.use { source ->
                    dbFile.outputStream().use { target -> source.copyTo(target) }
                }
                Unit
            }
        }
    }

    private fun checkpoint(database: AppDatabase) {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { }
    }

    private fun cleanupWalFiles(dbFile: File) {
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-shm").delete()
    }
}
