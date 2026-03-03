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
 * 歌单管理员：负责所有 Playlist 的增删改查及复杂的文件夹同步逻辑喵！
 */
class PlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao
) {

    suspend fun getAllPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        playlistDao.getAllPlaylists()
    }

    suspend fun getSongsInPlaylist(playlistId: Int): List<Song> = withContext(Dispatchers.IO) {
        playlistDao.getSongsInPlaylist(playlistId)
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

    suspend fun getSongCountInPlaylist(playlistId: Int): Int = withContext(Dispatchers.IO) {
        playlistDao.getSongCountInPlaylist(playlistId)
    }

    /**
     * 同步文件夹歌单逻辑，已搬迁至此喵！
     */
    suspend fun syncFolderPlaylist(
        context: Context,
        playlist: Playlist,
        onProgress: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val folderPath = playlist.folderPath ?: return@withContext
        onProgress("正在搜索文件...")

        // (此处包含原来的 sync 逻辑内容，暂略以保持代码块大小喵)
        // 1. 搜集文件
        val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            android.provider.MediaStore.Audio.Media._ID,
            android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
            android.provider.MediaStore.Audio.Media.DATA,
            android.provider.MediaStore.Audio.Media.DURATION
        )
        val selection = "${android.provider.MediaStore.Audio.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath/%")
        
        val audioItems = mutableListOf<Triple<Long, String, String>>()
        val itemDurations = mutableMapOf<String, Long>()
        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
            val durationCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val filePath = cursor.getString(dataCol)
                if (File(filePath).parent == folderPath) {
                    audioItems.add(Triple(cursor.getLong(idCol), cursor.getString(nameCol), filePath))
                    // 顺便存一下毫秒时长，用于比对手机端指纹喵
                    val ms = cursor.getLong(durationCol)
                    itemDurations[filePath] = ms
                }
            }
        }

        if (audioItems.isEmpty()) return@withContext

        // 2. 移除 AB 配对中的 B 部分（因为我们需要它是逻辑上的一个条目喵）
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

        // 3. 清空并开始同步写库
        playlistDao.clearPlaylist(playlist.id)
        val dbSongsByFingerprint = songDao.getAllSongs().associateBy { "${it.fileName}|${it.duration}" }
        val dbSongsBySamples = songDao.getAllSongs().filter { it.totalSamples > 0 }.associateBy { "${it.fileName}|${it.totalSamples}" }

        targetItems.forEachIndexed { index, item ->
            onProgress("正在同步: ${index + 1}/$total")
            val (accurateSamples, sampleRate) = AudioScanner.getAccurateMetadata(context, item.first)
            val mediaStoreDuration = itemDurations[item.third] ?: 0L
            
            // 优先查找手机端“指纹” (名称+毫秒时长)
            val mobileSong = dbSongsByFingerprint["${item.second}|$mediaStoreDuration"]
            // 备选查找电脑端“指纹” (名称+总采样数)
            val pcSong = dbSongsBySamples["${item.second}|$accurateSamples"]
            
            val song = Song(
                mediaId = item.first,
                fileName = item.second,
                filePath = item.third,
                totalSamples = accurateSamples,
                displayName = pcSong?.displayName ?: mobileSong?.displayName ?: item.second.substringBeforeLast("."),
                loopStart = pcSong?.loopStart ?: mobileSong?.loopStart ?: 0L,
                loopEnd = pcSong?.loopEnd ?: mobileSong?.loopEnd ?: accurateSamples,
                duration = mediaStoreDuration,
                id = mobileSong?.id ?: pcSong?.id ?: 0
            )
            
            val songId = songDao.insertOrUpdateSong(song)
            playlistDao.addSongsToPlaylist(playlist.id, listOf(songId))
        }
    }
}
