package com.cpu.seamlessloopmobile.db

import android.content.Context
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import com.cpu.seamlessloopmobile.model.PlayQueueDao
import com.cpu.seamlessloopmobile.model.PlaylistDao
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 手机端 → PC 端数据库导出器喵！
 *
 * 重要：这里不是简单复制 Room 数据库。手机端主表名是 Songs，而 PC 端 3NF 数据库使用 Tracks，
 * LoopPoints/UserRatings 外键列也叫 TrackId。为了让电脑端能够直接打开/同步，必须生成一份
 * PC 端可识别的 SQLite 文件，再写入用户通过 SAF 选择的导出位置。
 */
object PcDatabaseExporter {

    data class ExportResult(
        val trackCount: Int,
        val playlistCount: Int,
        val bytes: Long
    )

    var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    suspend fun exportToPcDatabase(
        context: Context,
        uri: Uri,
        songDao: SongDao,
        playlistDao: PlaylistDao,
        playQueueDao: PlayQueueDao
    ): ExportResult = withContext(ioDispatcher) {
        val tempFile = File(context.cacheDir, "export_pc_data_${System.currentTimeMillis()}.db")

        try {
            if (tempFile.exists()) tempFile.delete()

            val allSongs = songDao.getAllSongsRaw()
                .sortedWith(compareBy<Song> { it.fileName.lowercase() }.thenBy { it.id })
            val playlists = playlistDao.getPlaylistsWithCounts().map { it.playlist }
            val playQueueSongs = playQueueDao.getPlayQueueSongs()
            val exportedPcTrackIds = mutableSetOf<Long>()

            val exportDb = SQLiteDatabase.openOrCreateDatabase(tempFile, null)
            var transactionStarted = false
            try {
                exportDb.execSQL("PRAGMA foreign_keys = ON")
                exportDb.beginTransaction()
                transactionStarted = true
                createPcSchema(exportDb)

                // PC 端通过 Tracks.Id 串起 LoopPoints/UserRatings/PlaylistItems。
                // 为了避免手机端 Room 自增 ID 的空洞或冲突影响 PC，导出时重新分配连续 TrackId。
                val mobileSongIdToPcTrackId = mutableMapOf<Long, Long>()
                val artistNameToPcId = mutableMapOf<String, Long>()
                val albumNameToPcId = mutableMapOf<String, Long>()

                allSongs.forEachIndexed { index, song ->
                    val artistId = insertArtistIfNeeded(exportDb, song, artistNameToPcId)
                    val albumId = insertAlbumIfNeeded(exportDb, song, albumNameToPcId)
                    val track = insertTrack(exportDb, song, artistId, albumId)
                    mobileSongIdToPcTrackId[song.id] = track.id

                    // PC 端 Tracks 有 UNIQUE(FileName, TotalSamples)。如果手机端存在同名同采样数的重复记录，
                    // PC schema 无法一比一表达，只能合并到第一条导出的 Track 上，避免导出直接失败。
                    if (track.inserted) {
                        exportedPcTrackIds.add(track.id)
                        insertLoopPoint(exportDb, song, track.id)
                        insertUserRating(exportDb, song, track.id)
                    }

                    // 顺便把手机端歌曲所在文件夹写入 MusicFolders，方便 PC 端初次打开时有扫描根目录参考。
                    insertMusicFolderIfNeeded(exportDb, song)

                    if ((index + 1) % 200 == 0) {
                        android.util.Log.d("PcDatabaseExporter", "已导出 ${index + 1}/${allSongs.size} 首歌曲喵")
                    }
                }

                val usedPlaylistNames = mutableSetOf<String>()
                playlists.forEach { playlist ->
                    val pcPlaylistName = makeUniquePlaylistName(playlist.name, usedPlaylistNames)
                    val pcPlaylistId = insertPlaylist(exportDb, pcPlaylistName, playlist.sortOrder, playlist.createdAt)
                    val songsInPlaylist = playlistDao.getSongsInPlaylist(playlist.id)
                    songsInPlaylist.forEachIndexed { index, song ->
                        val pcTrackId = mobileSongIdToPcTrackId[song.id] ?: return@forEachIndexed
                        insertPlaylistItem(exportDb, pcPlaylistId, pcTrackId, index + 1)
                    }
                }

                playQueueSongs.forEachIndexed { index, song ->
                    val pcTrackId = mobileSongIdToPcTrackId[song.id] ?: return@forEachIndexed
                    insertQueuedTrack(exportDb, pcTrackId, index)
                }

                insertDefaultAppSettings(exportDb)
                exportDb.setTransactionSuccessful()
            } finally {
                if (transactionStarted) exportDb.endTransaction()
                exportDb.close()
            }

            val exportedBytes = tempFile.length()
            tempFile.inputStream().use { input ->
                // "wt" = write + truncate：如果用户选了一个已存在的文件，就直接覆盖写入喵。
                val output = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IllegalStateException("无法打开导出文件")
                output.use { target ->
                    input.copyTo(target)
                    target.flush()
                }
            }

            ExportResult(
                trackCount = exportedPcTrackIds.size,
                playlistCount = playlists.size,
                bytes = exportedBytes
            )
        } finally {
            // 临时 PC 数据库只作为中转文件使用，写入 SAF 目标后必须立即清掉喵。
            tempFile.delete()
        }
    }

    private fun createPcSchema(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE Artists (
                Id        INTEGER PRIMARY KEY AUTOINCREMENT,
                Name      TEXT NOT NULL UNIQUE,
                CoverPath TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE Albums (
                Id        INTEGER PRIMARY KEY AUTOINCREMENT,
                Name      TEXT NOT NULL,
                CoverPath TEXT,
                UNIQUE(Name)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE Tracks (
                Id           INTEGER PRIMARY KEY AUTOINCREMENT,
                FileName     TEXT NOT NULL,
                FilePath     TEXT,
                DisplayName  TEXT,
                TotalSamples INTEGER DEFAULT 0,
                LastModified DATETIME,
                CoverPath    TEXT,
                AlbumId      INTEGER,
                ArtistId     INTEGER,
                FOREIGN KEY(AlbumId) REFERENCES Albums(Id) ON DELETE SET NULL,
                FOREIGN KEY(ArtistId) REFERENCES Artists(Id) ON DELETE SET NULL,
                UNIQUE(FileName, TotalSamples)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE LoopPoints (
                TrackId              INTEGER PRIMARY KEY,
                LoopStart            INTEGER DEFAULT 0,
                LoopEnd              INTEGER DEFAULT 0,
                LoopCandidatesJson   TEXT,
                AnalysisLastModified DATETIME,
                FOREIGN KEY(TrackId) REFERENCES Tracks(Id) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE UserRatings (
                TrackId      INTEGER PRIMARY KEY,
                Rating       INTEGER DEFAULT 0,
                LastModified DATETIME,
                FOREIGN KEY(TrackId) REFERENCES Tracks(Id) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE Playlists (
                Id        INTEGER PRIMARY KEY AUTOINCREMENT,
                Name      TEXT NOT NULL,
                SortOrder INTEGER DEFAULT 0,
                CreatedAt DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE PlaylistItems (
                PlaylistId INTEGER,
                SongId     INTEGER,
                SortOrder  INTEGER DEFAULT 0,
                PRIMARY KEY(PlaylistId, SongId),
                FOREIGN KEY(PlaylistId) REFERENCES Playlists(Id) ON DELETE CASCADE,
                FOREIGN KEY(SongId)     REFERENCES Tracks(Id)    ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("CREATE TABLE MusicFolders (Id INTEGER PRIMARY KEY AUTOINCREMENT, FolderPath TEXT NOT NULL UNIQUE, AddedAt DATETIME DEFAULT CURRENT_TIMESTAMP)")
        db.execSQL("CREATE TABLE AppSettings (Key TEXT PRIMARY KEY, Value TEXT)")
        db.execSQL("CREATE TABLE QueuedTracks (Id INTEGER PRIMARY KEY AUTOINCREMENT, TrackId INTEGER, SortOrder INTEGER)")

        db.execSQL("CREATE INDEX idx_tracks_albumid ON Tracks(AlbumId)")
        db.execSQL("CREATE INDEX idx_tracks_artistid ON Tracks(ArtistId)")
        db.execSQL("CREATE UNIQUE INDEX idx_playlists_name ON Playlists(Name)")
        db.execSQL("CREATE INDEX idx_playlistitems_songid ON PlaylistItems(SongId)")
    }

    private fun insertArtistIfNeeded(
        db: SQLiteDatabase,
        song: Song,
        cache: MutableMap<String, Long>
    ): Long? {
        val name = song.artist.takeUnless { it.isBlank() || it == "Unknown Artist" } ?: return null
        return cache.getOrPut(name.lowercase()) {
            val values = android.content.ContentValues().apply {
                put("Name", name)
                put("CoverPath", song.artistEntity?.coverPath)
            }
            db.insertWithOnConflict("Artists", null, values, SQLiteDatabase.CONFLICT_IGNORE).let { insertedId ->
                if (insertedId != -1L) insertedId else queryIdByName(db, "Artists", name)
            }
        }
    }

    private fun insertAlbumIfNeeded(
        db: SQLiteDatabase,
        song: Song,
        cache: MutableMap<String, Long>
    ): Long? {
        val name = song.album.takeUnless { it.isBlank() || it == "Unknown Album" } ?: return null
        return cache.getOrPut(name.lowercase()) {
            val values = android.content.ContentValues().apply {
                put("Name", name)
                put("CoverPath", song.albumEntity?.coverPath ?: song.coverPath)
            }
            db.insertWithOnConflict("Albums", null, values, SQLiteDatabase.CONFLICT_IGNORE).let { insertedId ->
                if (insertedId != -1L) insertedId else queryIdByName(db, "Albums", name)
            }
        }
    }

    private data class TrackInsertResult(
        val id: Long,
        val inserted: Boolean
    )

    private fun insertTrack(db: SQLiteDatabase, song: Song, artistId: Long?, albumId: Long?): TrackInsertResult {
        val values = android.content.ContentValues().apply {
            put("FileName", song.fileName)
            put("FilePath", song.filePath)
            put("DisplayName", song.displayName)
            put("TotalSamples", song.totalSamples)
            put("LastModified", toPcDateTime(song.lastModified))
            put("CoverPath", song.coverPath)
            if (albumId != null) put("AlbumId", albumId) else putNull("AlbumId")
            if (artistId != null) put("ArtistId", artistId) else putNull("ArtistId")
        }
        val insertedId = db.insertWithOnConflict("Tracks", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        if (insertedId != -1L) return TrackInsertResult(insertedId, inserted = true)

        db.rawQuery(
            "SELECT Id FROM Tracks WHERE FileName = ? AND TotalSamples = ? LIMIT 1",
            arrayOf(song.fileName, song.totalSamples.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) return TrackInsertResult(cursor.getLong(0), inserted = false)
        }

        throw IllegalStateException("无法写入或定位导出的曲目：${song.fileName}")
    }

    private fun insertLoopPoint(db: SQLiteDatabase, song: Song, trackId: Long) {
        val values = android.content.ContentValues().apply {
            put("TrackId", trackId)
            put("LoopStart", song.loopStart)
            put("LoopEnd", song.loopEnd)
            // 手机端 Gson 字段是 loopStart/loopEnd/noteDiff/score，PC 样本库使用
            // LoopStart/LoopEnd/NoteDifference/Score。这里顺手转成 PC 端更容易识别的键名喵。
            put("LoopCandidatesJson", toPcLoopCandidatesJson(song.loopCandidatesJson))
            putNull("AnalysisLastModified")
        }
        db.insertWithOnConflict("LoopPoints", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun insertUserRating(db: SQLiteDatabase, song: Song, trackId: Long) {
        val values = android.content.ContentValues().apply {
            put("TrackId", trackId)
            put("Rating", song.rating)
            put("LastModified", toPcDateTime(song.userRating?.lastModified ?: song.lastModified))
        }
        db.insertWithOnConflict("UserRatings", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun insertPlaylist(db: SQLiteDatabase, name: String, sortOrder: Int, createdAt: Long): Long {
        val values = android.content.ContentValues().apply {
            put("Name", name)
            put("SortOrder", sortOrder)
            put("CreatedAt", toPcDateTime(createdAt))
        }
        return db.insertOrThrow("Playlists", null, values)
    }

    private fun makeUniquePlaylistName(rawName: String, usedNames: MutableSet<String>): String {
        val baseName = rawName.takeIf { it.isNotBlank() } ?: "未命名歌单"
        var candidate = baseName
        var suffix = 2
        while (!usedNames.add(candidate)) {
            candidate = "$baseName ($suffix)"
            suffix++
        }
        return candidate
    }

    private fun insertPlaylistItem(db: SQLiteDatabase, playlistId: Long, trackId: Long, sortOrder: Int) {
        val values = android.content.ContentValues().apply {
            put("PlaylistId", playlistId)
            put("SongId", trackId)
            put("SortOrder", sortOrder)
        }
        db.insertWithOnConflict("PlaylistItems", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun insertQueuedTrack(db: SQLiteDatabase, trackId: Long, sortOrder: Int) {
        val values = android.content.ContentValues().apply {
            put("TrackId", trackId)
            put("SortOrder", sortOrder)
        }
        db.insert("QueuedTracks", null, values)
    }

    private fun insertMusicFolderIfNeeded(db: SQLiteDatabase, song: Song) {
        val parent = File(song.filePath).parent ?: return
        if (parent.isBlank()) return
        val values = android.content.ContentValues().apply {
            put("FolderPath", parent)
            put("AddedAt", toPcDateTime(System.currentTimeMillis()))
        }
        db.insertWithOnConflict("MusicFolders", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun insertDefaultAppSettings(db: SQLiteDatabase) {
        // 只写入最基础的版本标记，避免伪造 PC 端播放状态导致打开后跳到不存在的分类或曲目。
        val values = android.content.ContentValues().apply {
            put("Key", "MobileExport.Schema")
            put("Value", "pc_3nf_v1")
        }
        db.insertWithOnConflict("AppSettings", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun queryIdByName(db: SQLiteDatabase, table: String, name: String): Long {
        db.rawQuery("SELECT Id FROM $table WHERE Name = ? LIMIT 1", arrayOf(name)).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        throw IllegalStateException("无法读取 $table 中的 $name")
    }

    private fun toPcLoopCandidatesJson(json: String?): String? {
        val source = json?.takeIf { it.isNotBlank() && it != "[]" && it != "{}" } ?: return json

        return try {
            val input = JSONArray(source)
            val output = JSONArray()
            for (i in 0 until input.length()) {
                val item = input.optJSONObject(i) ?: continue
                val pcItem = JSONObject().apply {
                    put("LoopStart", readLong(item, "LoopStart", "loopStart"))
                    put("LoopEnd", readLong(item, "LoopEnd", "loopEnd"))
                    put("Score", readDouble(item, "Score", "score"))
                    put("NoteDifference", readDouble(item, "NoteDifference", "noteDiff"))

                    // PC 旧样本不一定带 LoudnessDifference，但移动端有 loudnessDiff；保留下来不影响兼容。
                    if (hasAny(item, "LoudnessDifference", "loudnessDiff")) {
                        put("LoudnessDifference", readDouble(item, "LoudnessDifference", "loudnessDiff"))
                    }
                }
                output.put(pcItem)
            }
            output.toString()
        } catch (_: Exception) {
            // 若将来 JSON 结构又变了，至少不要破坏原始缓存，原样交给 PC 端自己尝试解析喵。
            json
        }
    }

    private fun readLong(item: JSONObject, vararg keys: String): Long {
        keys.forEach { key ->
            if (item.has(key) && !item.isNull(key)) return item.optLong(key)
        }
        return 0L
    }

    private fun readDouble(item: JSONObject, vararg keys: String): Double {
        keys.forEach { key ->
            if (item.has(key) && !item.isNull(key)) return item.optDouble(key)
        }
        return 0.0
    }

    private fun hasAny(item: JSONObject, vararg keys: String): Boolean {
        return keys.any { key -> item.has(key) && !item.isNull(key) }
    }

    private fun toPcDateTime(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
    }
}
