package com.cpu.seamlessloopmobile.model

import androidx.room.*

@Dao
interface SongDao {
    @Query("SELECT * FROM LoopPoints ORDER BY FileName ASC")
    suspend fun getAllSongs(): List<Song>

    @Query("SELECT * FROM LoopPoints WHERE FileName = :name AND TotalSamples = :samples LIMIT 1")
    suspend fun getSongByFingerprint(name: String, samples: Long): Song?

    @Query("SELECT * FROM LoopPoints WHERE FileName = :name")
    suspend fun getSongsByName(name: String): List<Song>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSong(song: Song): Long

    @Update
    suspend fun updateSong(song: Song): Int

    @Query("SELECT * FROM LoopPoints WHERE FilePath = :path LIMIT 1")
    suspend fun getSongByPath(path: String): Song?

    @Transaction
    suspend fun insertOrUpdateSong(song: Song): Long {
        // 先按路径找，因为文件路径在安卓端也是唯一的喵！
        val existingByPath = getSongByPath(song.filePath)
        if (existingByPath != null) {
            // 路径对上了，直接更新指纹和数据喵
            updateSong(song.copy(
                id = existingByPath.id,
                mediaId = if (song.mediaId != 0L) song.mediaId else existingByPath.mediaId
            ))
            return existingByPath.id
        }

        // 再按指纹找，处理文件搬家的情况喵
        val existingByFingerprint = getSongByFingerprint(song.fileName, song.totalSamples)
        return if (existingByFingerprint != null) {
            updateSong(song.copy(
                id = existingByFingerprint.id, 
                mediaId = if (song.mediaId != 0L) song.mediaId else existingByFingerprint.mediaId,
                filePath = song.filePath // 更新到新家喵！
            ))
            existingByFingerprint.id
        } else {
            insertSong(song)
        }
    }

    @Delete
    suspend fun deleteSong(song: Song): Int
}
