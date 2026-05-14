package com.cpu.seamlessloopmobile.db

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.cpu.seamlessloopmobile.model.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 后勤搬运工：负责从 PC 端导出的 SQLite 数据库中提取循环数据， 并在本地数据库中打下“灵魂锚点”。
 *
 * 性能优化版：
 * 1. 内存预热：批量加载本地数据。
 * 2. 零查询循环：循环内不触发任何数据库 IO。
 * 3. 分层批量提交：主表逐行局部更新 + 关联表两次批量写入。
 * 4. Zip ID 回填：安全高效地处理新歌手/专辑关联。
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
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }

                // 2. 开启外部数据库
                val extDb =
                        android.database.sqlite.SQLiteDatabase.openDatabase(
                                tempFile.absolutePath,
                                null,
                                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                        )

                // 3. 架构自适应探测喵！
                val tables = mutableListOf<String>()
                val tableCursor =
                        extDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
                while (tableCursor.moveToNext()) {
                    tables.add(tableCursor.getString(0))
                }
                tableCursor.close()

                val is3NF = tables.contains("Tracks")

                // 4. 内存预热 (Pre-loading)
                // 一次性加载本地所有歌曲到内存中，按文件名（小写）分组加速匹配
                val localSongsMap = songDao.getAllSongs().groupBy { it.fileName.lowercase() }

                // 5. 数据收集阶段
                val missingArtists = mutableSetOf<String>()
                val missingAlbums = mutableSetOf<String>()

                val query =
                        if (is3NF) {
                            """
                    SELECT 
                        t.FileName, t.TotalSamples, lp.LoopStart, lp.LoopEnd, t.DisplayName,
                        ar.Name AS Artist, al.Name AS Album, ur.Rating, t.CoverPath
                    FROM Tracks t
                    LEFT JOIN LoopPoints lp ON t.Id = lp.TrackId
                    LEFT JOIN UserRatings ur ON t.Id = ur.TrackId
                    LEFT JOIN Artists ar ON t.ArtistId = ar.Id
                    LEFT JOIN Albums al ON t.AlbumId = al.Id
                    """.trimIndent()
                        } else {
                            "SELECT FileName, TotalSamples, LoopStart, LoopEnd, DisplayName, Artist, Album, Rating, CoverPath FROM LoopPoints"
                        }

                val cursor = extDb.rawQuery(query, null)
                val extData = mutableListOf<ExtSongData>()
                if (cursor.moveToFirst()) {
                    do {
                        val data =
                                ExtSongData(
                                        fileName = cursor.getString(0) ?: "",
                                        total = cursor.getLong(1),
                                        start = cursor.getLong(2),
                                        end = cursor.getLong(3),
                                        displayName = cursor.getString(4),
                                        artist = cursor.getString(5),
                                        album = cursor.getString(6),
                                        rating = cursor.getInt(7),
                                        coverPath = cursor.getString(8)
                                )
                        extData.add(data)
                        // 预收集所有可能缺失的分类名
                        data.artist?.let { if (it.isNotBlank()) missingArtists.add(it) }
                        data.album?.let { if (it.isNotBlank()) missingAlbums.add(it) }
                    } while (cursor.moveToNext())
                }
                cursor.close()

                // 6. 原子级批量同步 (Atomic Transaction)
                val appDb = AppDatabase.getDatabase(context)
                var syncCount = 0

                appDb.withTransaction {
                    // Stage 4.1: Artist/Album ID 回填 (CPU 大人的 Zip 映射法)

                    // --- 补全歌手缓存 ---
                    val finalArtistMap = mutableMapOf<String, Long>()
                    missingArtists.forEach { name ->
                        songDao.getArtistByName(name)?.let {
                            finalArtistMap[name.lowercase()] = it.id
                        }
                    }
                    val actualMissingArtists =
                            missingArtists.filter { finalArtistMap[it.lowercase()] == null }
                    if (actualMissingArtists.isNotEmpty()) {
                        val newIds =
                                songDao.insertArtistsBatch(
                                        actualMissingArtists.map { Artist(name = it) }
                                )
                        // Room 保证返回的 ID 顺序与输入列表一致喵！
                        actualMissingArtists.zip(newIds).forEach { (name, id) ->
                            finalArtistMap[name.lowercase()] = id
                        }
                    }

                    // --- 补全专辑缓存 ---
                    val finalAlbumMap = mutableMapOf<String, Long>()
                    missingAlbums.forEach { name ->
                        songDao.getAlbumByName(name)?.let {
                            finalAlbumMap[name.lowercase()] = it.id
                        }
                    }
                    val actualMissingAlbums =
                            missingAlbums.filter { finalAlbumMap[it.lowercase()] == null }
                    if (actualMissingAlbums.isNotEmpty()) {
                        val newIds =
                                songDao.insertAlbumsBatch(
                                        actualMissingAlbums.map { Album(name = it) }
                                )
                        actualMissingAlbums.zip(newIds).forEach { (name, id) ->
                            finalAlbumMap[name.lowercase()] = id
                        }
                    }

                    // Stage 4.2: 匹配并构建批量更新列表
                    val songUpdates = mutableListOf<SongMetadataUpdate>()
                    extData.forEach { data ->
                        val candidates = localSongsMap[data.fileName.lowercase()]
                        if (!candidates.isNullOrEmpty()) {
                            // 匹配逻辑：采样数优先 + 时长容差 (10000 采样数以内 ≈ 0.2s)
                            var matchedSong =
                                    candidates.find {
                                        it.totalSamples == data.total && data.total > 0
                                    }
                            if (matchedSong == null) {
                                val tolerance = 10000L
                                matchedSong =
                                        candidates.find {
                                            Math.abs(it.totalSamples - data.total) <= tolerance
                                        }
                            }
                            // 采样率交叉校验：针对本地采样数为 0 的情况喵
                            if (matchedSong == null && data.total > 0) {
                                matchedSong =
                                        candidates.find { cand ->
                                            val ms441 = data.total / 44.1
                                            val ms480 = data.total / 48.0
                                            Math.abs(ms441 - cand.duration) < 200 ||
                                                    Math.abs(ms480 - cand.duration) < 200
                                        }
                            }
                            // 智能兜底：仅当双方至少有一侧采样数未知（=0）时才允许唯一候选回退
                            // 但即便是回退，也要进行“理性校验”：时长差距不能太离谱喵！
                            if (matchedSong == null && candidates.size == 1) {
                                val single = candidates[0]
                                val bothKnown = data.total > 0 && single.totalSamples > 0
                                if (!bothKnown) {
                                    val pcMs441 = data.total / 44.1
                                    val pcMs480 = data.total / 48.0
                                    val durationRational =
                                            data.total <= 0 ||
                                                    single.duration <= 0 ||
                                                    Math.abs(pcMs441 - single.duration) < 200 ||
                                                    Math.abs(pcMs480 - single.duration) < 200
                                    if (durationRational) matchedSong = single
                                }
                            }

                            if (matchedSong != null) {
                                songUpdates.add(
                                        SongMetadataUpdate(
                                                songId = matchedSong.id,
                                                start = data.start,
                                                end = data.end,
                                                total =
                                                        if (data.total > 0) data.total
                                                        else matchedSong.totalSamples,
                                                rating = data.rating,
                                                artistId =
                                                        data.artist?.lowercase()?.let {
                                                            finalArtistMap[it]
                                                        },
                                                albumId =
                                                        data.album?.lowercase()?.let {
                                                            finalAlbumMap[it]
                                                        },
                                                displayName = data.displayName,
                                                coverPath = data.coverPath,
                                                isAbPartB = matchedSong.isAbPartB // 同步时保持原有的 B 段标记喵
                                        )
                                )
                                syncCount++
                            }
                        }
                    }

                    // Stage 4.3: 批量执行元数据更新 (分层分块)
                    songDao.updateSongsMetadataBatch(songUpdates)

                    // Stage 4.4: 歌单同步 (批量化重构)
                    if (tables.contains("Playlists")) {
                        // 关键修复：元数据更新后刷新内存快照，避免用过期的 totalSamples 匹配失败喵！
                        val refreshedSongsMap =
                                songDao.getAllSongs().groupBy { it.fileName.lowercase() }

                        val plCursor = extDb.rawQuery("SELECT Id, Name FROM Playlists", null)
                        while (plCursor.moveToNext()) {
                            val extPlId = plCursor.getInt(0)
                            val plName = plCursor.getString(1) ?: continue

                            var localPl = playlistDao.getPlaylistByName(plName)
                            if (localPl == null) {
                                val newId = playlistDao.insertPlaylist(Playlist(name = plName))
                                localPl = Playlist(id = newId.toInt(), name = plName)
                            }

                            val itemQuery =
                                    if (is3NF) {
                                        "SELECT t.FileName, t.TotalSamples FROM PlaylistItems pi JOIN Tracks t ON pi.SongId = t.Id WHERE pi.PlaylistId = ?"
                                    } else {
                                        "SELECT FileName, TotalSamples FROM PlaylistItems pi JOIN LoopPoints lp ON pi.SongId = lp.Id WHERE pi.PlaylistId = ?"
                                    }

                            val itemCursor = extDb.rawQuery(itemQuery, arrayOf(extPlId.toString()))
                            val itemsToInsert = mutableListOf<PlaylistItem>()
                            var order = playlistDao.getMaxSortOrder(localPl.id) ?: 0

                            // 获取本地歌单已有的歌曲 ID，防止重复同步喵！
                            val existingSongIds =
                                    playlistDao.getSongIdsInPlaylist(localPl.id).toSet()

                            while (itemCursor.moveToNext()) {
                                val fName = itemCursor.getString(0) ?: ""
                                val tSamples = itemCursor.getLong(1)
                                val candidates = refreshedSongsMap[fName.lowercase()]
                                // 匹配逻辑与歌曲元数据同步保持一致：精确 → 容差 → 唯一候选兜底
                                var match =
                                        candidates?.find {
                                            it.totalSamples == tSamples && tSamples > 0
                                        }
                                if (match == null) {
                                    val tolerance = 10000L
                                    match =
                                            candidates?.find {
                                                Math.abs(it.totalSamples - tSamples) <= tolerance
                                            }
                                }
                                // 采样率交叉校验：针对本地采样数为 0 的情况喵
                                if (match == null && tSamples > 0) {
                                    match =
                                            candidates?.find { cand ->
                                                val ms441 = tSamples / 44.1
                                                val ms480 = tSamples / 48.0
                                                Math.abs(ms441 - cand.duration) < 200 ||
                                                        Math.abs(ms480 - cand.duration) < 200
                                            }
                                }
                                // 智能兜底：仅当双方至少有一侧采样数未知（=0）时才允许唯一候选回退
                                // 但即便是回退，也要进行“理性校验”：时长差距不能太离谱喵！
                                if (match == null && candidates?.size == 1) {
                                    val single = candidates[0]
                                    val bothKnown = tSamples > 0 && single.totalSamples > 0
                                    if (!bothKnown) {
                                        val pcMs441 = tSamples / 44.1
                                        val pcMs480 = tSamples / 48.0
                                        val durationRational =
                                                tSamples <= 0 ||
                                                        single.duration <= 0 ||
                                                        Math.abs(pcMs441 - single.duration) < 200 ||
                                                        Math.abs(pcMs480 - single.duration) < 200
                                        if (durationRational) match = single
                                    }
                                }

                                if (match != null && !existingSongIds.contains(match.id)) {
                                    itemsToInsert.add(
                                            PlaylistItem(
                                                    playlistId = localPl.id,
                                                    songId = match.id,
                                                    sortOrder = ++order
                                            )
                                    )
                                }
                            }
                            itemCursor.close()
                            // 一次性写入歌单项
                            playlistDao.insertPlaylistItemsBatch(itemsToInsert)
                        }
                        plCursor.close()
                    }
                }

                extDb.close()
                tempFile.delete()

                withContext(Dispatchers.Main) { callback.onSuccess(syncCount) }
            } catch (e: Exception) {
                android.util.Log.e("PcDatabaseImporter", "Sync failed", e)
                withContext(Dispatchers.Main) { callback.onError(e.message ?: "未知错误") }
            }
        }
    }

    private data class ExtSongData(
            val fileName: String,
            val total: Long,
            val start: Long,
            val end: Long,
            val displayName: String?,
            val artist: String?,
            val album: String?,
            val rating: Int,
            val coverPath: String?
    )
}
