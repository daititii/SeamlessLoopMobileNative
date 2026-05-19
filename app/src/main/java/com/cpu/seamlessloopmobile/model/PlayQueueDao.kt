package com.cpu.seamlessloopmobile.model

import androidx.room.*

@Dao
interface PlayQueueDao {
    @Transaction
    @Query("""
        SELECT Songs.* FROM Songs
        JOIN PlayQueue ON Songs.Id = PlayQueue.SongId
        ORDER BY PlayQueue.SortOrder ASC
    """
    )
    suspend fun getPlayQueueSongs(): List<Song>

    @Query("DELETE FROM PlayQueue")
    suspend fun clearPlayQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayQueueItems(items: List<PlayQueueItem>)

    @Transaction
    suspend fun replacePlayQueue(songIds: List<Long>) {
        clearPlayQueue()
        val items = songIds.mapIndexed { index, songId ->
            PlayQueueItem(songId = songId, sortOrder = index)
        }
        insertPlayQueueItems(items)
    }
}
