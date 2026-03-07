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

                // 1. 获取本地所有歌曲并按文件名分组，极速查找喵！
                val localSongs = songDao.getAllSongs()
                val localSongsMap = localSongs.groupBy { it.fileName }

                if (cursor.moveToFirst()) {
                    do {
                        val fileName = cursor.getString(0) ?: ""
                        val total = cursor.getLong(1)
                        val start = cursor.getLong(2)
                        val end = cursor.getLong(3)
                        val name = cursor.getString(4)

                        val candidates = localSongsMap[fileName]
                        if (!candidates.isNullOrEmpty()) {
                            // 2. 核心算法：三重身份验证喵 (指纹优先 + 宽容时长)
                            // 优先通过 PC 端的 TotalSamples 指纹来认亲喵！
                            var matchedSong = candidates.find { it.totalSamples == total && total > 0 }

                            if (matchedSong == null) {
                                // 假设是 44.1k 或 48k，算出预估毫秒时长
                                val dur44 = (total / 44.1).toLong()
                                val dur48 = (total / 48.0).toLong()
                                val tolerance = 2000L // 遵命！容差已按大人指示缩小到 2 秒喵！

                                matchedSong = candidates.find { 
                                    val phoneDur = it.duration
                                    Math.abs(phoneDur - dur44) <= tolerance || 
                                    Math.abs(phoneDur - dur48) <= tolerance 
                                }
                            }
                            
                            // 兜底：如果没对上时长，但本地同名歌曲只有这一首，那也一定是它没跑了喵！
                            if (matchedSong == null && candidates.size == 1) {
                                matchedSong = candidates[0]
                            }

                            if (matchedSong != null) {
                                // 3. 抓到你了！只更新循环点，不准乱插队喵！
                                songDao.updateLoopPoints(matchedSong.id, start, end, total)
                                syncCount++
                            }
                        }
                    } while (cursor.moveToNext())
                }
                cursor.close()
                db.close()
                tempFile.delete()

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
