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
        playlistDao.getPlaylistsWithCounts().map { it.playlist }
    }

    suspend fun getPlaylistsWithCounts(): List<PlaylistDao.PlaylistWithCount> = withContext(Dispatchers.IO) {
        playlistDao.getPlaylistsWithCounts()
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
        val allScanned = AudioScanner.scan(context)
        val scannedSongs = allScanned.filter { File(it.filePath).parent == folderPath }
        
        if (scannedSongs.isEmpty()) return@withContext

        // 2. 移除 AB 配对中的 B 部分（因为我们需要它是逻辑上的一个条目喵）
        val nameMapInFolder = scannedSongs.associateBy { it.fileName.substringBeforeLast(".") }
        val bSuffixes = arrayOf("_B", "_b", "_loop", "_Loop")
        val aSuffixes = arrayOf("_A", "_a", "_intro", "_Intro")
        val ignorePaths = mutableSetOf<String>()

        for (item in scannedSongs) {
            val nameNormal = item.fileName.substringBeforeLast(".")
            for (i in bSuffixes.indices) {
                if (nameNormal.endsWith(bSuffixes[i])) {
                    val baseName = nameNormal.substring(0, nameNormal.length - bSuffixes[i].length)
                    if (nameMapInFolder.containsKey(baseName + aSuffixes[i])) {
                        ignorePaths.add(item.filePath)
                        break
                    }
                }
            }
        }

        val targetItems = scannedSongs.filter { it.filePath !in ignorePaths }
        val total = targetItems.size

        // 3. 清空并开始同步写库
        playlistDao.clearPlaylist(playlist.id)
        val allDbSongs = songDao.getAllSongs()
        val dbSongsByFingerprint = allDbSongs.associateBy { "${it.fileName}|${it.duration}" }
        // 提取出所有来自PC端数据的记录喵，它们应该有 totalSamples > 0 
        val pcDbSongs = allDbSongs.filter { it.totalSamples > 0 }

        targetItems.forEachIndexed { index, item ->
            onProgress("正在同步: ${index + 1}/$total")
            val (accurateSamples, sampleRate) = AudioScanner.getAccurateMetadata(context, item.mediaId)
            
            // 优先查找手机端“指纹” (名称|毫秒时长)
            val mobileSong = dbSongsByFingerprint["${item.fileName}|${item.duration}"]
            
            // 备选查找电脑端记录：兼顾手机和电脑MP3采样数的细微差异喵！
            var pcSong: Song? = null
            val itemNameBase = item.fileName.substringBeforeLast(".")
            
            // 第一步：先看有没有名字能包含或被包含的 PC 歌曲
            val nameMatchedPcSongs = pcDbSongs.filter { pc ->
                val pcNameBase = pc.fileName.substringBeforeLast(".")
                itemNameBase.equals(pcNameBase, ignoreCase = true) || 
                itemNameBase.contains(pcNameBase, ignoreCase = true) || 
                pcNameBase.contains(itemNameBase, ignoreCase = true)
            }
            
            if (nameMatchedPcSongs.isNotEmpty()) {
                // 第二步：在名字匹配的歌曲中，找一个采样数最接近的喵！
                // 允许的最大误差为 10000 采样（对于44.1kHz大约0.2秒），足够包容MP3的首尾填充差异了
                val closestSong = nameMatchedPcSongs.minByOrNull { Math.abs(it.totalSamples - accurateSamples) }
                if (closestSong != null && (accurateSamples == 0L || Math.abs(closestSong.totalSamples - accurateSamples) < 20000)) {
                    pcSong = closestSong
                }
            }
            
            val song = item.copy(
                totalSamples = if (accurateSamples > 0) accurateSamples else (pcSong?.totalSamples ?: 0L),
                duration = item.duration, // 明确显式继承 duration 喵！
                displayName = pcSong?.displayName ?: mobileSong?.displayName ?: item.displayName,
                loopStart = pcSong?.loopStart ?: mobileSong?.loopStart ?: 0L,
                loopEnd = pcSong?.loopEnd ?: mobileSong?.loopEnd ?: (if (accurateSamples > 0) accurateSamples else pcSong?.totalSamples ?: 0L),
                id = mobileSong?.id ?: pcSong?.id ?: 0
            )
            
            val songId = songDao.insertOrUpdateSong(song)
            if (songId > 0) {
                playlistDao.addSongsToPlaylist(playlist.id, listOf(songId))
            } else {
                android.util.Log.e("PlaylistRepo", "插入歌曲失败喵: ${song.fileName}")
            }
        }
    }
}
