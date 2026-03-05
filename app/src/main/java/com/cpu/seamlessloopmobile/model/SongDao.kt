package com.cpu.seamlessloopmobile.model

import androidx.room.*

@Dao
interface SongDao {
    @Query("SELECT * FROM LoopPoints ORDER BY FileName ASC")
    suspend fun getAllSongs(): List<Song>

    @Query("SELECT * FROM LoopPoints WHERE FileName = :name AND duration = :duration LIMIT 1")
    suspend fun getSongByFingerprint(name: String, duration: Long): Song?

    @Query("SELECT * FROM LoopPoints WHERE FileName = :name AND TotalSamples = :samples LIMIT 1")
    suspend fun getSongBySamples(name: String, samples: Long): Song?

    @Query("SELECT * FROM LoopPoints WHERE FileName = :name")
    suspend fun getSongsByName(name: String): List<Song>

    @Query("UPDATE LoopPoints SET LoopStart = :start, LoopEnd = :end, TotalSamples = :total WHERE Id = :songId")
    suspend fun updateLoopPoints(songId: Long, start: Long, end: Long, total: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song): Long

    @Update
    suspend fun updateSong(song: Song): Int

    @Query("SELECT * FROM LoopPoints WHERE FilePath = :path LIMIT 1")
    suspend fun getSongByPath(path: String): Song?

    @Transaction
    suspend fun insertOrUpdateSong(song: Song): Long {
        // 场景 0：如果传入了 ID，且指纹匹配或指纹不存在，直接信任 ID 进行更新
        // 但为了严谨，我们还是走指纹发现流程喵

        val existingByFingerprint = getSongByFingerprint(song.fileName, song.duration)
        // 尝试通过采样数找（电脑同步过来的幽灵喵）
        val existingBySamples = if (song.totalSamples > 0) getSongBySamples(song.fileName, song.totalSamples) else null
        val existingByPath = if (song.filePath.isNotBlank()) getSongByPath(song.filePath) else null

        // 场景 1：冲突处理 - 如果指纹（时长）还没对上，但采样数对上了
        // 我们检查这个已存在的记录是不是没有路径且时长异常（0 或者 等于采样数）的“幽灵”
        if (existingByFingerprint == null && existingBySamples != null && 
            (existingBySamples.duration == 0L || existingBySamples.duration == existingBySamples.totalSamples) &&
            existingBySamples.filePath.isBlank()) {
             // 这里的幽灵就是我们要找的 PC 循环点，把它扶正！
             val mergedSong = song.copy(
                 id = existingBySamples.id,
                 loopStart = if (song.loopStart == 0L) existingBySamples.loopStart else song.loopStart,
                 loopEnd = if (song.loopEnd == song.totalSamples) existingBySamples.loopEnd else song.loopEnd
             )
             updateSong(mergedSong)
             return existingBySamples.id
        }

        // 场景 1.2：如果两者都存在且互斥，说明我们要把“扫描到的”和“同步来的”进行大合体喵
        if (existingByFingerprint != null && existingBySamples != null && existingByFingerprint.id != existingBySamples.id) {
            val finalSong = existingByFingerprint.copy(
                totalSamples = song.totalSamples,
                loopStart = if (existingByFingerprint.loopStart == 0L) existingBySamples.loopStart else existingByFingerprint.loopStart,
                loopEnd = if (existingByFingerprint.loopEnd == 0L || existingByFingerprint.loopEnd == existingByFingerprint.totalSamples) existingBySamples.loopEnd else existingByFingerprint.loopEnd
            )
            updateSong(finalSong)
            deleteSong(existingBySamples) // 删除电脑端的幽灵记录，它的使命完成了喵！
            return existingByFingerprint.id
        }

        // 场景 1.5：既找到了指纹，又找到了路径，而且它们不是同一首歌
        if (existingByFingerprint != null && existingByPath != null && existingByFingerprint.id != existingByPath.id) {
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
