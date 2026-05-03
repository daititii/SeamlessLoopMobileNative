package com.cpu.seamlessloopmobile.db

import android.content.Context
import android.net.Uri
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao
import com.cpu.seamlessloopmobile.model.PlaylistDao
import com.cpu.seamlessloopmobile.model.Playlist
import com.cpu.seamlessloopmobile.model.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 后勤搬运工：负责从 PC 端导出的 SQLite 数据库中提取循环数据，
 * 并在本地数据库中打下“灵魂锚点”。
 * 现在支持 PC 端最新的 3NF 架构喵！(๑•̀ㅂ•́)و✧
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
        playlistDao: PlaylistDao,
        callback: ImportCallback
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 1. 拷贝到临时文件
                val tempFile = File(context.cacheDir, "temp_pc_data.db")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 2. 开启外部数据库
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    tempFile.absolutePath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )

                // 3. 架构自适应探测喵！
                val tables = mutableListOf<String>()
                val tableCursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
                while (tableCursor.moveToNext()) {
                    tables.add(tableCursor.getString(0))
                }
                tableCursor.close()

                val is3NF = tables.contains("Tracks")
                android.util.Log.d("PcDatabaseImporter", "Detected schema: ${if (is3NF) "3NF (New)" else "Flat (Old)"}")
                
                var syncCount = 0

                // 4. 获取本地所有歌曲并按文件名分组
                val localSongs = songDao.getAllSongs()
                val localSongsMap = localSongs.groupBy { it.fileName }

                // 5. 曲目与元数据同步
                val query = if (is3NF) {
                    """
                    SELECT 
                        t.FileName, t.TotalSamples, lp.LoopStart, lp.LoopEnd, t.DisplayName,
                        ar.Name AS Artist, al.Name AS Album, ur.Rating, t.CoverPath, lp.LoopCandidatesJson
                    FROM Tracks t
                    LEFT JOIN LoopPoints lp ON t.Id = lp.TrackId
                    LEFT JOIN UserRatings ur ON t.Id = ur.TrackId
                    LEFT JOIN Artists ar ON t.ArtistId = ar.Id
                    LEFT JOIN Albums al ON t.AlbumId = al.Id
                    """.trimIndent()
                } else {
                    // 降级兼容旧版 Flat 架构
                    "SELECT FileName, TotalSamples, LoopStart, LoopEnd, DisplayName, Artist, Album, Rating, CoverPath, LoopCandidatesJson FROM LoopPoints"
                }

                val cursor = db.rawQuery(query, null)
                if (cursor.moveToFirst()) {
                    do {
                        val fileName = cursor.getString(0) ?: ""
                        val total = cursor.getLong(1)
                        val start = cursor.getLong(2)
                        val end = cursor.getLong(3)
                        val displayName = cursor.getString(4)
                        val artist = cursor.getString(5)
                        val album = cursor.getString(6)
                        val rating = cursor.getInt(7)
                        val coverPath = cursor.getString(8)
                        // val candidates = cursor.getString(9) // TODO: Handle candidates if needed

                        val candidatesList = localSongsMap[fileName]
                        if (!candidatesList.isNullOrEmpty()) {
                            // 匹配逻辑：采样数优先 + 时长容差
                            var matchedSong = candidatesList.find { it.totalSamples == total && total > 0 }
                            
                            if (matchedSong == null) {
                                val dur44 = (total / 44.1).toLong()
                                val dur48 = (total / 48.0).toLong()
                                val tolerance = 2000L
                                matchedSong = candidatesList.find { 
                                    val phoneDur = it.duration
                                    Math.abs(phoneDur - dur44) <= tolerance || Math.abs(phoneDur - dur48) <= tolerance 
                                }
                            }

                            if (matchedSong == null && candidatesList.size == 1) {
                                matchedSong = candidatesList[0]
                            }

                            if (matchedSong != null) {
                                songDao.updateSyncMetadata(
                                    songId = matchedSong.id,
                                    start = start,
                                    end = end,
                                    total = total,
                                    rating = rating,
                                    artist = artist,
                                    album = album,
                                    displayName = displayName,
                                    coverPath = coverPath
                                )
                                syncCount++
                            }
                        }
                    } while (cursor.moveToNext())
                }
                cursor.close()

                // 6. 歌单同步 (Experimental)
                if (tables.contains("Playlists")) {
                    val plCursor = db.rawQuery("SELECT Id, Name FROM Playlists", null)
                    while (plCursor.moveToNext()) {
                        val extPlId = plCursor.getInt(0)
                        val plName = plCursor.getString(1) ?: continue
                        
                        // 寻找或创建本地歌单
                        var localPl = playlistDao.getPlaylistByName(plName)
                        if (localPl == null) {
                            val newId = playlistDao.insertPlaylist(Playlist(name = plName))
                            localPl = Playlist(id = newId.toInt(), name = plName)
                        }

                        // 同步歌单内歌曲
                        val itemQuery = if (is3NF) {
                            """
                            SELECT t.FileName, t.TotalSamples 
                            FROM PlaylistItems pi 
                            JOIN Tracks t ON pi.SongId = t.Id 
                            WHERE pi.PlaylistId = ?
                            """.trimIndent()
                        } else {
                            "SELECT FileName, TotalSamples FROM PlaylistItems pi JOIN LoopPoints lp ON pi.SongId = lp.Id WHERE pi.PlaylistId = ?"
                        }
                        
                        val itemCursor = db.rawQuery(itemQuery, arrayOf(extPlId.toString()))
                        val songIdsToSync = mutableListOf<Long>()
                        
                        while (itemCursor.moveToNext()) {
                            val fName = itemCursor.getString(0) ?: ""
                            val tSamples = itemCursor.getLong(1)
                            
                            // 在本地库寻找这首歌
                            val localMatch = localSongsMap[fName]?.find { 
                                it.totalSamples == tSamples || Math.abs((it.totalSamples/44.1).toLong() - (tSamples/44.1).toLong()) < 2000
                            }
                            if (localMatch != null) {
                                songIdsToSync.add(localMatch.id)
                            }
                        }
                        itemCursor.close()
                        
                        if (songIdsToSync.isNotEmpty()) {
                            playlistDao.addSongsToPlaylist(localPl.id, songIdsToSync)
                        }
                    }
                    plCursor.close()
                }

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
