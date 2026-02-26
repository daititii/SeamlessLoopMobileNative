package com.cpu.seamlessloopmobile.db

import android.content.Context
import android.net.Uri
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 后勤搬运工：负责从 PC 端导出的 SQLite 数据库中提取循环数据，
 * 并在本地数据库中打下“灵魂锚点”。
 */
object PcDatabaseImporter {

    interface ImportCallback {
        fun onSuccess(syncCount: Int)
        fun onError(message: String)
    }

    suspend fun importFromPcDatabase(
        context: Context,
        uri: Uri,
        songDao: SongDao,
        callback: ImportCallback
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 1. 拷贝到临时文件，因为 SQLite 不直接支持 ContentUri 喵
                val tempFile = File(context.cacheDir, "temp_pc_data.db")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 2. 暴力开启外部数据库
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    tempFile.absolutePath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )

                val cursor = db.rawQuery("SELECT FileName, TotalSamples, LoopStart, LoopEnd, DisplayName FROM LoopPoints", null)
                var syncCount = 0

                val pcData = mutableListOf<Song>()

                if (cursor.moveToFirst()) {
                    do {
                        val fileName = cursor.getString(0) ?: ""
                        val total = cursor.getLong(1)
                        val start = cursor.getLong(2)
                        val end = cursor.getLong(3)
                        val name = cursor.getString(4)

                        // 构造一个临时的 Song 对象用于存储数据喵
                        val dummySong = Song(
                            fileName = fileName,
                            filePath = "", // 稍后匹配
                            displayName = name,
                            loopStart = start,
                            loopEnd = end,
                            totalSamples = total,
                            mediaId = 0
                        )
                        pcData.add(dummySong)
                    } while (cursor.moveToNext())
                }
                cursor.close()
                db.close()
                tempFile.delete()

                // 3. 开始一锅端！
                songDao.insertOrUpdateSongs(pcData)
                syncCount = pcData.size

                withContext(Dispatchers.Main) {
                    callback.onSuccess(syncCount)
                }

            } catch (e: Exception) {
                android.util.Log.e("PcDatabaseImporter", "Sync failed", e)
                withContext(Dispatchers.Main) {
                    callback.onError(e.message ?: "未知错误")
                }
            }
        }
    }
}
