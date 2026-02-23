package com.cpu.seamlessloopmobile.model

import androidx.room.*

@Dao
interface PlaylistDao {
    // --- 歌单管理 ---
    @Query("SELECT * FROM Playlists ORDER BY SortOrder ASC, CreatedAt DESC")
    suspend fun getAllPlaylists(): List<Playlist>

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
        WHERE pi.PlaylistId = :playlistId
        ORDER BY pi.SortOrder ASC
    """)
    suspend fun getSongsInPlaylist(playlistId: Int): List<Song>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistItem(item: PlaylistItem): Long

    @Transaction
    suspend fun addSongsToPlaylist(playlistId: Int, songIds: List<Long>): Int {
        val currentMaxOrder = getMaxSortOrder(playlistId) ?: 0
        songIds.forEachIndexed { index, songId ->
            insertPlaylistItem(PlaylistItem(
                playlistId = playlistId,
                songId = songId,
                sortOrder = currentMaxOrder + index + 1
            ))
        }
        return songIds.size
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

    @Query("SELECT COUNT(*) FROM PlaylistItems WHERE PlaylistId = :playlistId")
    suspend fun getSongCountInPlaylist(playlistId: Int): Int
}
