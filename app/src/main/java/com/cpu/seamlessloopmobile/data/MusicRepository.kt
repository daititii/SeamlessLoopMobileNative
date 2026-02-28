package com.cpu.seamlessloopmobile.data

import android.content.Context
import com.cpu.seamlessloopmobile.model.Playlist
import com.cpu.seamlessloopmobile.model.PlaylistDao
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao
import com.cpu.seamlessloopmobile.scanner.AudioScanner
import com.cpu.seamlessloopmobile.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MusicRepository (大管家)：
 * 负责应用的数据层逻辑。它屏蔽了数据来源（数据库、扫描器、JNI）的复杂性，
 * 为 ViewModel 提供“主线程安全”的数据接口。
 */
class MusicRepository(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao
) {

    // --- 基础查询喵 ---

    suspend fun getAllPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        playlistDao.getAllPlaylists()
    }

    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        songDao.getAllSongs()
    }

    suspend fun getSongsInPlaylist(playlistId: Int): List<Song> = withContext(Dispatchers.IO) {
        playlistDao.getSongsInPlaylist(playlistId)
    }

    suspend fun getSongByPath(path: String): Song? = withContext(Dispatchers.IO) {
        songDao.getSongByPath(path)
    }

    suspend fun getSongCountInPlaylist(playlistId: Int): Int = withContext(Dispatchers.IO) {
        playlistDao.getSongCountInPlaylist(playlistId)
    }

    /**
     * 应急修复：如果大人的歌曲 mediaId 丢了（比如 PC 导入或移动了位置），
     * 莱芙会尝试通过路径去系统库里重新帮它找回身份令牌喵！
     */
    suspend fun resolveMediaId(context: Context, song: Song): Song = withContext(Dispatchers.IO) {
        if (song.mediaId > 0) return@withContext song

        val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(android.provider.MediaStore.Audio.Media._ID)
        val selection = "${android.provider.MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = arrayOf(song.filePath)

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val newMediaId = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID))
                val updatedSong = song.copy(mediaId = newMediaId)
                songDao.insertOrUpdateSong(updatedSong)
                return@withContext updatedSong
            }
        }
        song
    }

    suspend fun updateSong(song: Song) = withContext(Dispatchers.IO) {
        songDao.updateSong(song)
    }

    suspend fun insertOrUpdateSong(song: Song): Long = withContext(Dispatchers.IO) {
        songDao.insertOrUpdateSong(song)
    }

    suspend fun insertPlaylist(playlist: Playlist): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(playlist)
    }

    suspend fun deletePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun addSongsToPlaylist(playlistId: Int, songIds: List<Long>): Int = withContext(Dispatchers.IO) {
        playlistDao.addSongsToPlaylist(playlistId, songIds)
    }

    suspend fun removeSongsFromPlaylist(playlistId: Int, songIds: List<Long>) = withContext(Dispatchers.IO) {
        playlistDao.removeSongsFromPlaylist(playlistId, songIds)
    }

    /**
     * 从本地媒体库快速扫描基础信息喵
     */
    suspend fun getInitialScannedSongs(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val scannedSongs = AudioScanner.scan(context)
        val dbSongs = songDao.getAllSongs().associateBy { it.fileName }
        
        scannedSongs.map { song ->
            val dbSong = dbSongs[song.fileName]
            dbSong?.let { 
                val updatedSong = song.copy(
                    id = it.id, 
                    loopStart = it.loopStart, 
                    loopEnd = it.loopEnd, 
                    totalSamples = it.totalSamples, 
                    displayName = it.displayName ?: song.displayName
                )
                // 关键点：如果 mediaId 变了，同步回数据库喵！
                if (it.mediaId != song.mediaId) {
                    songDao.insertOrUpdateSong(updatedSong)
                }
                updatedSong
            } ?: song
        }
    }

    /**
     * 针对特定关联文件夹的歌单进行“精准测量”同步喵
     */
    suspend fun syncFolderPlaylist(
        context: Context, 
        playlist: Playlist,
        onProgress: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val folderPath = playlist.folderPath ?: return@withContext
        
        onProgress("正在搜索文件...")
        
        // 1. 搜集文件 (逻辑从 ViewModel 搬迁过来喵)
        val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            android.provider.MediaStore.Audio.Media._ID,
            android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
            android.provider.MediaStore.Audio.Media.DATA
        )
        val selection = "${android.provider.MediaStore.Audio.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath/%")
        
        val audioItems = mutableListOf<Triple<Long, String, String>>()
        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext()) {
                val filePath = cursor.getString(dataCol)
                if (File(filePath).parent == folderPath) {
                    audioItems.add(Triple(cursor.getLong(idCol), cursor.getString(nameCol), filePath))
                }
            }
        }

        if (audioItems.isEmpty()) return@withContext

        // 2. 匹配 AB 逻辑 (这里保持纯粹的计算喵)
        val nameMapInFolder = audioItems.associateBy { it.second.substringBeforeLast(".") }
        val bSuffixes = arrayOf("_B", "_b", "_loop", "_Loop")
        val aSuffixes = arrayOf("_A", "_a", "_intro", "_Intro")
        val ignorePaths = mutableSetOf<String>()

        for (item in audioItems) {
            val nameNormal = item.second.substringBeforeLast(".")
            for (i in bSuffixes.indices) {
                if (nameNormal.endsWith(bSuffixes[i])) {
                    val baseName = nameNormal.substring(0, nameNormal.length - bSuffixes[i].length)
                    if (nameMapInFolder.containsKey(baseName + aSuffixes[i])) {
                        ignorePaths.add(item.third)
                        break
                    }
                }
            }
        }

        val targetItems = audioItems.filter { it.third !in ignorePaths }
        val total = targetItems.size

        // 3. 清空并开始写数据库喵
        playlistDao.clearPlaylist(playlist.id)
        val dbSongs = songDao.getAllSongs().associateBy { "${it.fileName}|${it.totalSamples}" }

        targetItems.forEachIndexed { index, item ->
            onProgress("正在同步: ${index + 1}/$total")
            
            // 精准测量
            val accurateSamples = AudioScanner.getAccurateSampleCount(context, item.first)
            val dbSong = dbSongs["${item.second}|$accurateSamples"]
            
            val song = Song(
                mediaId = item.first,
                fileName = item.second,
                filePath = item.third,
                totalSamples = accurateSamples,
                displayName = dbSong?.displayName ?: item.second.substringBeforeLast("."),
                loopStart = dbSong?.loopStart ?: 0L,
                loopEnd = dbSong?.loopEnd ?: accurateSamples,
                duration = TimeUtils.samplesToMillis(accurateSamples, 44100L), // 以后采样率可以动态获取喵
                id = 0
            )
            
            val songId = songDao.insertOrUpdateSong(song)
            playlistDao.addSongsToPlaylist(playlist.id, listOf(songId))
        }
    }

    /**
     * 发现 A-B 配对逻辑 喵
     */
    fun findAbPair(song: Song, allScannedSongs: List<Song>): Pair<Song, Song>? {
        val fileName = song.fileName.substringBeforeLast(".")
        val aSuffixes = arrayOf("_A", "_a", "_intro", "_Intro")
        val bSuffixes = arrayOf("_B", "_b", "_loop", "_Loop")

        for (i in aSuffixes.indices) {
            if (fileName.endsWith(aSuffixes[i])) {
                val baseName = fileName.substring(0, fileName.length - aSuffixes[i].length)
                val targetBName = baseName + bSuffixes[i]
                
                val partB = allScannedSongs.find { 
                    it.fileName.substringBeforeLast(".") == targetBName &&
                    File(it.filePath).parent == File(song.filePath).parent
                }
                if (partB != null) return Pair(song, partB)
            }
        }
        return null
    }

    suspend fun updateSongLoopPoints(song: Song, start: Long, end: Long) = withContext(Dispatchers.IO) {
        val newSong = song.copy(loopStart = start, loopEnd = end)
        songDao.insertOrUpdateSong(newSong)
        newSong
    }

    /**
     * 自动从数据库或系统的 MediaStore 补全缺失的 AB 对配对关系喵！
     * 即使用户把歌扔进了文件夹还没有建歌单，也能瞬间找出它的灵魂伴侣喵！
     */
    suspend fun findAbPairRobust(context: Context, song: Song): Pair<Song, Song>? = withContext(Dispatchers.IO) {
        val fileName = song.fileName.substringBeforeLast(".")
        val aSuffixes = arrayOf("_A", "_a", "_intro", "_Intro")
        val bSuffixes = arrayOf("_B", "_b", "_loop", "_Loop")

        var bNameWithoutExt: String? = null
        for (i in aSuffixes.indices) {
            if (fileName.endsWith(aSuffixes[i])) {
                val baseName = fileName.substring(0, fileName.length - aSuffixes[i].length)
                bNameWithoutExt = baseName + bSuffixes[i]
                break
            }
        }
        
        if (bNameWithoutExt == null) return@withContext null

        val parentDir = File(song.filePath).parent ?: return@withContext null

        // 1. 先尝试在数据库里找喵！
        val dbSongs = songDao.getAllSongs()
        val pB = dbSongs.find { 
            it.fileName.substringBeforeLast(".") == bNameWithoutExt &&
            File(it.filePath).parent == parentDir
        }
        if (pB != null) return@withContext Pair(song, pB)

        // 2. 数据库没有，去 MediaStore 搜 (解决还没来得及加歌单就直接点听的情况喵)
        var partB: Song? = null
        try {
            val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
                android.provider.MediaStore.Audio.Media.DATA
            )
            // 使用 LIKE 查询同一文件夹下的文件喵
            val selection = "${android.provider.MediaStore.Audio.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("$parentDir/%")
            
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol)
                    val path = cursor.getString(dataCol)
                    if (name.substringBeforeLast(".") == bNameWithoutExt && File(path).parent == parentDir) {
                        partB = Song(
                            mediaId = cursor.getLong(idCol),
                            fileName = name,
                            filePath = path,
                            totalSamples = 0,
                            displayName = name.substringBeforeLast("."),
                            loopStart = 0,
                            loopEnd = 0,
                            duration = 0,
                            id = 0
                        )
                        break
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        if (partB != null) Pair(song, partB!!) else null
    }
}
