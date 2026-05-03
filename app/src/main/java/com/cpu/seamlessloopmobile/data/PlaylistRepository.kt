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

        // 2. 预处理：识别所有歌曲中的 B 段喵
        val nameMapInFolder = scannedSongs.associateBy { it.fileName.substringBeforeLast(".") }
        val bSuffixes = arrayOf("_B", "_b", "_loop", "_Loop")
        val aSuffixes = arrayOf("_A", "_a", "_intro", "_Intro")
        
        val processedSongs = scannedSongs.map { item ->
            var isB = false
            val nameNormal = item.fileName.substringBeforeLast(".")
            for (i in bSuffixes.indices) {
                if (nameNormal.endsWith(bSuffixes[i])) {
                    val baseName = nameNormal.substring(0, nameNormal.length - bSuffixes[i].length)
                    if (nameMapInFolder.containsKey(baseName + aSuffixes[i])) {
                        isB = true
                        break
                    }
                }
            }
            if (isB) item.copy(isAbPartB = true) else item
        }

        val total = processedSongs.size
        playlistDao.clearPlaylist(playlist.id)
        
        // --- 核心优化：同步前先搜集该路径下的旧魂灵喵 ---
        val folderSongsInDb = songDao.getSongsByPathPrefix(folderPath)
        val processedPathSet = processedSongs.map { it.filePath }.toSet()
        
        // 提前捞取一份数据库快照喵
        val allDbSongs = songDao.getAllSongsRaw()
        val dbSongsByFingerprint = allDbSongs.associateBy { "${it.fileName}|${it.duration}" }
        val pcDbSongs = allDbSongs.filter { it.totalSamples > 0 }

        processedSongs.forEachIndexed { index, item ->
            onProgress("正在同步: ${index + 1}/$total")
            val (accurateSamples, sampleRate) = AudioScanner.getAccurateMetadata(context, item.mediaId)
            
            val mobileSong = dbSongsByFingerprint["${item.fileName}|${item.duration}"]
            var pcSong: Song? = null
            val itemNameBase = item.fileName.substringBeforeLast(".")
            
            val nameMatchedPcSongs = pcDbSongs.filter { pc ->
                val pcNameBase = pc.fileName.substringBeforeLast(".")
                itemNameBase.equals(pcNameBase, ignoreCase = true) || 
                itemNameBase.contains(pcNameBase, ignoreCase = true) || 
                pcNameBase.contains(itemNameBase, ignoreCase = true)
            }
            
            if (nameMatchedPcSongs.isNotEmpty()) {
                val closestSong = nameMatchedPcSongs.minByOrNull { Math.abs(it.totalSamples - accurateSamples) }
                if (closestSong != null && (accurateSamples == 0L || Math.abs(closestSong.totalSamples - accurateSamples) < 20000)) {
                    pcSong = closestSong
                }
            }
            
            val song = item.copy(
                totalSamples = if (accurateSamples > 0) accurateSamples else (pcSong?.totalSamples ?: 0L),
                duration = item.duration,
                displayName = pcSong?.displayName ?: mobileSong?.displayName ?: item.displayName,
                loopStart = pcSong?.loopStart ?: mobileSong?.loopStart ?: 0L,
                loopEnd = pcSong?.loopEnd ?: mobileSong?.loopEnd ?: (if (accurateSamples > 0) accurateSamples else pcSong?.totalSamples ?: 0L),
                id = mobileSong?.id ?: pcSong?.id ?: 0,
                isAbPartB = item.isAbPartB // 应用隐身标记
            )
            
            val songId = songDao.insertOrUpdateSong(song)
            // 只有非 B 段的歌曲，才有资格进入歌单排队显示喵！
            if (songId > 0 && !song.isAbPartB) {
                playlistDao.addSongsToPlaylist(playlist.id, listOf(songId))
            }
        }

        // --- 核心优化：清理掉物理文件夹中已经消失，但数据库中还有的旧记录喵 ---
        val staleIds = folderSongsInDb
            .filter { it.filePath !in processedPathSet }
            .map { it.id }
        
        if (staleIds.isNotEmpty()) {
            songDao.deleteSongsByIds(staleIds)
        }
    }
}
