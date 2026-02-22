package com.cpu.seamlessloopmobile.model

import androidx.room.*

@Dao
interface SongDao {
    @Query("SELECT * FROM LoopPoints")
    suspend fun getAllSongs(): List<Song>

    @Query("SELECT * FROM LoopPoints WHERE FileName = :name AND TotalSamples = :samples LIMIT 1")
    suspend fun getSongByFingerprint(name: String, samples: Long): Song?

    @Query("SELECT * FROM LoopPoints WHERE FileName = :name")
    suspend fun getSongsByName(name: String): List<Song>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSong(song: Song): Long

    @Update
    suspend fun updateSong(song: Song)

    @Transaction
    suspend fun insertOrUpdateSong(song: Song): Long {
        // 优先通过指纹（文件名+采样数）找人，不管它搬家到哪里了喵！
        val existing = getSongByFingerprint(song.fileName, song.totalSamples)
        return if (existing != null) {
            // 只要大人的数据比较新，或者是带了循环点的数据，就更新它
            if (song.lastModified >= existing.lastModified || existing.loopEnd == 0L) {
                // 巧妙地保留本地特有的 mediaId 和最新的 filePath
                updateSong(song.copy(
                    id = existing.id, 
                    mediaId = if (song.mediaId != 0L) song.mediaId else existing.mediaId,
                    filePath = if (song.filePath.isNotEmpty()) song.filePath else existing.filePath
                ))
            }
            existing.id
        } else {
            insertSong(song)
        }
    }

    @Delete
    suspend fun deleteSong(song: Song)
}
