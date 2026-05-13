package com.cpu.seamlessloopmobile.model

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    // --- 核心查询 (返回 POJO) ---

    @Transaction
    @Query("SELECT * FROM Songs WHERE IsAbPartB = 0 ORDER BY FileName ASC")
    suspend fun getAllSongs(): List<Song>

    @Transaction
    @Query("SELECT * FROM Songs WHERE IsAbPartB = 0 ORDER BY FileName ASC")
    fun getAllSongsFlow(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Songs ORDER BY FileName ASC")
    fun getAllSongsRawFlow(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Songs WHERE FileName = :name AND duration = :duration LIMIT 1")
    suspend fun getSongByFingerprint(name: String, duration: Long): Song?

    @Transaction
    @Query("SELECT * FROM Songs WHERE FileName = :name AND TotalSamples = :samples LIMIT 1")
    suspend fun getSongBySamples(name: String, samples: Long): Song?

    @Transaction
    @Query("SELECT * FROM Songs WHERE FileName = :name AND IsAbPartB = 0")
    suspend fun getSongsByName(name: String): List<Song>

    @Transaction
    @Query("SELECT * FROM Songs WHERE Id = :id LIMIT 1")
    suspend fun getSongById(id: Long): Song?

    @Transaction
    @Query("SELECT * FROM Songs WHERE FilePath = :path LIMIT 1")
    suspend fun getSongByPath(path: String): Song?

    @Transaction
    @Query("SELECT * FROM Songs")
    suspend fun getAllSongsRaw(): List<Song>

    @Transaction
    @Query("SELECT * FROM Songs WHERE FilePath LIKE :pathPrefix || '%'")
    suspend fun getSongsByPathPrefix(pathPrefix: String): List<Song>

    // --- 关联表专用操作 ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtist(artist: Artist): Long

    @Query("SELECT * FROM Artists WHERE Name = :name LIMIT 1")
    suspend fun getArtistByName(name: String): Artist?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: Album): Long

    @Query("SELECT * FROM Albums WHERE Name = :name LIMIT 1")
    suspend fun getAlbumByName(name: String): Album?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoopPoint(loopPoint: LoopPoint)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserRating(userRating: UserRating)

    @Query("UPDATE UserRatings SET Rating = :rating, LastModified = :now WHERE SongId = :songId")
    suspend fun updateSongRating(songId: Long, rating: Int, now: Long = System.currentTimeMillis())

    // --- 基础增删改 (针对 Entity) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongEntity(song: SongEntity): Long

    @Update
    suspend fun updateSongEntity(song: SongEntity): Int

    @Delete
    suspend fun deleteSongEntity(song: SongEntity): Int

    @Query("DELETE FROM Songs WHERE Id IN (:ids)")
    suspend fun deleteSongsByIds(ids: List<Long>): Int

    // --- 复杂逻辑：Artist/Album 处理 ---

    @Transaction
    suspend fun getOrCreateArtist(name: String?): Long? {
        if (name.isNullOrBlank()) return null
        val existing = getArtistByName(name)
        if (existing != null) return existing.id
        return insertArtist(Artist(name = name))
    }

    @Transaction
    suspend fun getOrCreateAlbum(name: String?): Long? {
        if (name.isNullOrBlank()) return null
        val existing = getAlbumByName(name)
        if (existing != null) return existing.id
        return insertAlbum(Album(name = name))
    }

    // --- 复杂逻辑：兼容旧版接口的 insertOrUpdateSong ---

    @Transaction
    suspend fun updateSyncMetadata(
        songId: Long, 
        start: Long, 
        end: Long, 
        total: Long, 
        rating: Int, 
        artist: String?, 
        album: String?, 
        displayName: String?, 
        coverPath: String?
    ) {
        val song = getSongById(songId)?.song ?: return
        
        // 1. 更新主表
        val artistId = getOrCreateArtist(artist)
        val albumId = getOrCreateAlbum(album)
        val updatedSong = song.copy(
            totalSamples = total,
            displayName = displayName,
            coverPath = coverPath,
            artistId = artistId,
            albumId = albumId
        )
        updateSongEntity(updatedSong)

        // 2. 更新关联表
        insertLoopPoint(LoopPoint(songId = songId, loopStart = start, loopEnd = end))
        insertUserRating(UserRating(songId = songId, rating = rating))
    }

    @Transaction
    suspend fun insertOrUpdateSong(
        songEntity: SongEntity, 
        loopStart: Long = 0, 
        loopEnd: Long = 0, 
        rating: Int = 0,
        artistName: String? = null,
        albumName: String? = null
    ): Long {
        val existingByFingerprint = getSongByFingerprint(songEntity.fileName, songEntity.duration)
        val existingBySamples = if (songEntity.totalSamples > 0) getSongBySamples(songEntity.fileName, songEntity.totalSamples) else null
        val existingByPath = if (songEntity.filePath.isNotBlank()) getSongByPath(songEntity.filePath) else null

        var targetId: Long = 0

        // 逻辑保持一致：指纹、采样数、路径优先级
        val match = existingByFingerprint ?: existingBySamples ?: existingByPath

        val artistId = getOrCreateArtist(artistName)
        val albumId = getOrCreateAlbum(albumName)

        if (match != null) {
            targetId = match.id
            val updatedEntity = songEntity.copy(
                id = targetId,
                mediaId = if (songEntity.mediaId != 0L) songEntity.mediaId else match.song.mediaId,
                filePath = if (songEntity.filePath.isNotBlank()) songEntity.filePath else match.song.filePath,
                artistId = artistId ?: match.song.artistId,
                albumId = albumId ?: match.song.albumId
            )
            updateSongEntity(updatedEntity)
        } else {
            targetId = insertSongEntity(songEntity.copy(artistId = artistId, albumId = albumId))
        }

        // 更新 1:1 关联数据
        insertLoopPoint(LoopPoint(songId = targetId, loopStart = loopStart, loopEnd = loopEnd))
        insertUserRating(UserRating(songId = targetId, rating = rating))

        return targetId
    }

    @Transaction
    suspend fun insertOrUpdateSong(song: Song): Long {
        return insertOrUpdateSong(
            songEntity = song.song,
            loopStart = song.loopStart,
            loopEnd = song.loopEnd,
            rating = song.rating,
            artistName = song.artist,
            albumName = song.album
        )
    }
}
