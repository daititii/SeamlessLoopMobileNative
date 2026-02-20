package com.cpu.seamlessloopmobile.model

import androidx.room.*

@Dao
interface SongDao {
    @Query("SELECT * FROM LoopPoints")
    suspend fun getAllSongs(): List<Song>

    @Query("SELECT * FROM LoopPoints WHERE FilePath = :path LIMIT 1")
    suspend fun getSongByPath(path: String): Song?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSong(song: Song): Long

    @Update
    suspend fun updateSong(song: Song)

    @Delete
    suspend fun deleteSong(song: Song)
}
