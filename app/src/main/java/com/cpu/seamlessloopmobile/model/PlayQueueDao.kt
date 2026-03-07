package com.cpu.seamlessloopmobile.model

import androidx.room.*

@Dao
interface PlayQueueDao {
    @Query("""
        SELECT s.* FROM LoopPoints s
        JOIN PlayQueue pq ON s.Id = pq.SongId
        ORDER BY pq.SortOrder ASC
    """)
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
