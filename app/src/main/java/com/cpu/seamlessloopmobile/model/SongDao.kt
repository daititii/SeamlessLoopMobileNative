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
        // 首先查找指纹，指纹才是歌曲的核心绑定（影响播放列表等喵）
        val existingByFingerprint = getSongByFingerprint(song.fileName, song.totalSamples)
        val existingByPath = if (song.filePath.isNotBlank()) getSongByPath(song.filePath) else null

        // 场景 1：既找到了指纹，又找到了路径，而且它们不是同一首歌
        // 这说明用户覆盖了同名文件，或者系统刷新了 MediaStore
        if (existingByFingerprint != null && existingByPath != null && existingByFingerprint.id != existingByPath.id) {
            // 果断删除旧路径占用的幽灵数据，给真身让路，否则会报 UNIQUE constraint failed 喵！
            deleteSong(existingByPath)
        }

        // 场景 2：指纹存在，直接更新指纹主体
        if (existingByFingerprint != null) {
            updateSong(song.copy(
                id = existingByFingerprint.id, 
                mediaId = if (song.mediaId != 0L) song.mediaId else existingByFingerprint.mediaId,
                filePath = if (song.filePath.isNotBlank()) song.filePath else existingByFingerprint.filePath
            ))
            return existingByFingerprint.id
        }

        // 场景 3：指纹不存在，但路径存在（说明这是一首新剪辑或被大幅改动的歌，仅仅占据了旧位置）
        if (existingByPath != null) {
            updateSong(song.copy(
                id = existingByPath.id,
                mediaId = if (song.mediaId != 0L) song.mediaId else existingByPath.mediaId
            ))
            return existingByPath.id
        }

        // 场景 4：是个彻头彻尾的新人，直接加进去喵
        return insertSong(song)
    }

    @Transaction
    suspend fun insertOrUpdateSongs(songs: List<Song>) {
        songs.forEach { insertOrUpdateSong(it) }
    }

    @Delete
    suspend fun deleteSong(song: Song): Int
}
