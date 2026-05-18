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

}
