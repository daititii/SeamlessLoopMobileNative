package com.cpu.seamlessloopmobile.model

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    // --- 歌单管理 ---
    @Query("""
        SELECT p.*, (SELECT COUNT(*) FROM PlaylistItems pi JOIN LoopPoints s ON pi.SongId = s.Id WHERE pi.PlaylistId = p.Id AND s.IsAbPartB = 0) as songCount 
        FROM Playlists p 
        ORDER BY p.SortOrder ASC, p.CreatedAt DESC
    """)
    suspend fun getPlaylistsWithCounts(): List<PlaylistWithCount>

    @Query("""
        SELECT p.*, (SELECT COUNT(*) FROM PlaylistItems pi JOIN LoopPoints s ON pi.SongId = s.Id WHERE pi.PlaylistId = p.Id AND s.IsAbPartB = 0) as songCount 
        FROM Playlists p 
        ORDER BY p.SortOrder ASC, p.CreatedAt DESC
    """)
    fun getPlaylistsWithCountsFlow(): Flow<List<PlaylistWithCount>>

    data class PlaylistWithCount(
        @Embedded val playlist: Playlist,
        val songCount: Int
    )

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist): Int

    @Delete
    suspend fun deletePlaylist(playlist: Playlist): Int

    // --- 歌单项管理 ---
    @Query("""
        SELECT s.* FROM LoopPoints s
        JOIN PlaylistItems pi ON s.Id = pi.SongId
        WHERE pi.PlaylistId = :playlistId AND s.IsAbPartB = 0
        ORDER BY pi.SortOrder ASC
    """)
    suspend fun getSongsInPlaylist(playlistId: Int): List<Song>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistItem(item: PlaylistItem): Long

    @Query("SELECT SongId FROM PlaylistItems WHERE PlaylistId = :playlistId")
    suspend fun getSongIdsInPlaylist(playlistId: Int): List<Long>

    @Transaction
    suspend fun addSongsToPlaylist(playlistId: Int, songIds: List<Long>): Int {
        val existingIds = getSongIdsInPlaylist(playlistId).toSet()
        val newSongIds = songIds.distinct().filter { it !in existingIds }
        
        if (newSongIds.isEmpty()) return 0

        val currentMaxOrder = getMaxSortOrder(playlistId) ?: 0
        newSongIds.forEachIndexed { index, songId ->
            insertPlaylistItem(PlaylistItem(
                playlistId = playlistId,
                songId = songId,
                sortOrder = currentMaxOrder + index + 1
            ))
        }
        return newSongIds.size
    }

    @Query("SELECT MAX(SortOrder) FROM PlaylistItems WHERE PlaylistId = :playlistId")
    suspend fun getMaxSortOrder(playlistId: Int): Int?

    @Query("DELETE FROM PlaylistItems WHERE PlaylistId = :playlistId AND SongId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Int, songId: Long): Int

    @Transaction
    suspend fun removeSongsFromPlaylist(playlistId: Int, songIds: List<Long>): Int {
        songIds.forEach { removeSongFromPlaylist(playlistId, it) }
        return songIds.size
    }

    @Query("SELECT * FROM PlaylistFolders WHERE PlaylistId = :playlistId")
    suspend fun getFoldersInPlaylist(playlistId: Int): List<PlaylistFolder>

    @Query("DELETE FROM PlaylistItems WHERE PlaylistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Int)

    @Transaction
    suspend fun clearAndSyncPlaylist(playlistId: Int, songIds: List<Long>) {
        clearPlaylist(playlistId)
        songIds.distinct().forEachIndexed { index, songId ->
            insertPlaylistItem(PlaylistItem(
                playlistId = playlistId,
                songId = songId,
                sortOrder = index + 1
            ))
        }
    }

    @Query("SELECT COUNT(*) FROM PlaylistItems pi JOIN LoopPoints s ON pi.SongId = s.Id WHERE pi.PlaylistId = :playlistId AND s.IsAbPartB = 0")
    suspend fun getSongCountInPlaylist(playlistId: Int): Int
}
