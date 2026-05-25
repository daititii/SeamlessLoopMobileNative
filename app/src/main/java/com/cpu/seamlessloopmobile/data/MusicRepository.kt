package com.cpu.seamlessloopmobile.data

import android.content.Context
import com.cpu.seamlessloopmobile.model.Playlist
import com.cpu.seamlessloopmobile.model.PlaylistDao
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao
import kotlinx.coroutines.flow.Flow

/**
 * MusicRepository (超级指挥官 - Facade)：
 * 为了兼容现有系统，它暂时作为 Song、Playlist 和 Scanner 仓库的聚合点。
 * 屏蔽了底层仓库拆分的复杂性，为现有 ViewModel 提供统一的数据接口。
 * 虽然目前它还在帮干将们“转发”请求，但未来您可以直接在相应地方注入特定的子仓库喵！
 */
class MusicRepository(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val playQueueDao: com.cpu.seamlessloopmobile.model.PlayQueueDao
) {

    // 内部持有的子干将们
    private val songRepository = SongRepository(songDao)
    private val playlistRepository = PlaylistRepository(playlistDao, songDao)
    private val musicScannerRepository = MusicScannerRepository(songDao)

    // --- 歌曲相关转发 (SongRepository) ---

    suspend fun getAllSongs(): List<Song> = songRepository.getAllSongs()

    suspend fun getAllSongsRaw(): List<Song> = songRepository.getAllSongsRaw()

    fun getAllSongsFlow(): Flow<List<Song>> = songDao.getAllSongsFlow()

    fun getAllSongsRawFlow(): Flow<List<Song>> = songDao.getAllSongsRawFlow()

    suspend fun getSongByPath(path: String): Song? = songRepository.getSongByPath(path)
    
    suspend fun getSongById(id: Long): Song? = songRepository.getSongById(id)

    suspend fun updateSong(song: Song) = songRepository.updateSong(song)

    suspend fun insertOrUpdateSong(song: Song): Long = songRepository.insertOrUpdateSong(song)

    suspend fun resolveMediaId(context: Context, song: Song): Song = songRepository.resolveMediaId(context, song)

    suspend fun updateSongLoopPoints(song: Song, start: Long, end: Long) = songRepository.updateSongLoopPoints(song, start, end)

    suspend fun updateSongRating(song: Song, rating: Int) = songRepository.updateSongRating(song, rating)

    suspend fun updateLoopCandidatesJson(song: Song, json: String?): Song = songRepository.updateLoopCandidatesJson(song, json)

    // --- 歌单相关转发 (PlaylistRepository) ---

    suspend fun getAllPlaylists(): List<Playlist> = playlistRepository.getAllPlaylists()

    suspend fun getPlaylistsWithCounts(): List<com.cpu.seamlessloopmobile.model.PlaylistDao.PlaylistWithCount> = playlistRepository.getPlaylistsWithCounts()

    fun getPlaylistsWithCountsFlow(): Flow<List<com.cpu.seamlessloopmobile.model.PlaylistDao.PlaylistWithCount>> = 
        playlistDao.getPlaylistsWithCountsFlow()

    suspend fun getSongsInPlaylist(playlistId: Int): List<Song> = playlistRepository.getSongsInPlaylist(playlistId)

    suspend fun insertPlaylist(playlist: Playlist): Long = playlistRepository.insertPlaylist(playlist)

    suspend fun deletePlaylist(playlist: Playlist) = playlistRepository.deletePlaylist(playlist)

    suspend fun addSongsToPlaylist(playlistId: Int, songIds: List<Long>): Int = playlistRepository.addSongsToPlaylist(playlistId, songIds)

    suspend fun removeSongsFromPlaylist(playlistId: Int, songIds: List<Long>) = playlistRepository.removeSongsFromPlaylist(playlistId, songIds)

    suspend fun getSongCountInPlaylist(playlistId: Int): Int = playlistRepository.getSongCountInPlaylist(playlistId)



    // --- 扫描与探测转发 (MusicScannerRepository) ---

    suspend fun getInitialScannedSongs(context: Context): List<Song> = musicScannerRepository.getInitialScannedSongs(context)
    
    suspend fun cleanupStaleSongs(context: Context): Int = musicScannerRepository.cleanupStaleSongs(context)

    fun findAbPair(song: Song, allScannedSongs: List<Song>): Pair<Song, Song>? = 
        musicScannerRepository.findAbPair(song, allScannedSongs)

    suspend fun findAbPairRobust(context: Context, song: Song): Pair<Song, Song>? = 
        musicScannerRepository.findAbPairRobust(context, song)

    // --- 播放队列状态持久化转发 ---
    
    suspend fun getPlayQueueSongs(): List<Song> = playQueueDao.getPlayQueueSongs()
    
    suspend fun replacePlayQueue(songIds: List<Long>) = playQueueDao.replacePlayQueue(songIds)
}
