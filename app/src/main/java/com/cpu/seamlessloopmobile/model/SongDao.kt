package com.cpu.seamlessloopmobile.model

import androidx.room.*

@Dao
interface SongDao {
    @Query("SELECT * FROM LoopPoints")
    suspend fun getAllSongs(): List<Song>

    @Query("SELECT * FROM LoopPoints WHERE FilePath = :path LIMIT 1")
    suspend fun getSongByPath(path: String): Song?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSong(song: Song): Long

    @Update
    suspend fun updateSong(song: Song)

    @Transaction
    suspend fun insertOrUpdateSong(song: Song): Long {
        val id = insertSong(song)
        if (id == -1L) {
            // 冲突了，说明已存在，执行更新
            val existing = getSongByPath(song.filePath)
            if (existing != null) {
                // 保持原有的 ID 不变进行更新，这样就不会触发级联删除了喵！
                updateSong(song.copy(id = existing.id))
                return existing.id
            }
        }
        return id
    }

    @Delete
    suspend fun deleteSong(song: Song)
}
