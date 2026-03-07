package com.cpu.seamlessloopmobile.data

import android.content.Context
import com.cpu.seamlessloopmobile.model.Playlist
import com.cpu.seamlessloopmobile.model.PlaylistDao
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao

/**
 * MusicRepository (超级指挥官 - Facade)：
 * 为了兼容现有系统，它暂时作为 Song、Playlist 和 Scanner 仓库的聚合点。
 * 屏蔽了底层仓库拆分的复杂性，为现有 ViewModel 提供统一的数据接口。
 * 虽然目前它还在帮干将们“转发”请求，但未来您可以直接在相应地方注入特定的子仓库喵！
 */
class MusicRepository(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao
) {

    // 内部持有的子干将们
    private val songRepository = SongRepository(songDao)
    private val playlistRepository = PlaylistRepository(playlistDao, songDao)
    private val musicScannerRepository = MusicScannerRepository(songDao)

    // --- 歌曲相关转发 (SongRepository) ---

    suspend fun getAllSongs(): List<Song> = songRepository.getAllSongs()

    suspend fun getSongByPath(path: String): Song? = songRepository.getSongByPath(path)
    
    suspend fun getSongById(id: Long): Song? = songRepository.getSongById(id)

    suspend fun updateSong(song: Song) = songRepository.updateSong(song)

    suspend fun insertOrUpdateSong(song: Song): Long = songRepository.insertOrUpdateSong(song)

    suspend fun resolveMediaId(context: Context, song: Song): Song = songRepository.resolveMediaId(context, song)

    suspend fun updateSongLoopPoints(song: Song, start: Long, end: Long) = songRepository.updateSongLoopPoints(song, start, end)

    // --- 歌单相关转发 (PlaylistRepository) ---

    suspend fun getAllPlaylists(): List<Playlist> = playlistRepository.getAllPlaylists()

    suspend fun getPlaylistsWithCounts(): List<com.cpu.seamlessloopmobile.model.PlaylistDao.PlaylistWithCount> = playlistRepository.getPlaylistsWithCounts()

    suspend fun getSongsInPlaylist(playlistId: Int): List<Song> = playlistRepository.getSongsInPlaylist(playlistId)

    suspend fun insertPlaylist(playlist: Playlist): Long = playlistRepository.insertPlaylist(playlist)

    suspend fun deletePlaylist(playlist: Playlist) = playlistRepository.deletePlaylist(playlist)

    suspend fun addSongsToPlaylist(playlistId: Int, songIds: List<Long>): Int = playlistRepository.addSongsToPlaylist(playlistId, songIds)

    suspend fun removeSongsFromPlaylist(playlistId: Int, songIds: List<Long>) = playlistRepository.removeSongsFromPlaylist(playlistId, songIds)

    suspend fun getSongCountInPlaylist(playlistId: Int): Int = playlistRepository.getSongCountInPlaylist(playlistId)

    suspend fun syncFolderPlaylist(context: Context, playlist: Playlist, onProgress: (String) -> Unit) = 
        playlistRepository.syncFolderPlaylist(context, playlist, onProgress)

    // --- 扫描与探测转发 (MusicScannerRepository) ---

    suspend fun getInitialScannedSongs(context: Context): List<Song> = musicScannerRepository.getInitialScannedSongs(context)

    fun findAbPair(song: Song, allScannedSongs: List<Song>): Pair<Song, Song>? = 
        musicScannerRepository.findAbPair(song, allScannedSongs)

    suspend fun findAbPairRobust(context: Context, song: Song): Pair<Song, Song>? = 
        musicScannerRepository.findAbPairRobust(context, song)
}
