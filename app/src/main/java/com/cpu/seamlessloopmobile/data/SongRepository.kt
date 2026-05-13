package com.cpu.seamlessloopmobile.data

import android.content.Context
import com.cpu.seamlessloopmobile.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 歌谱保管员：专注于“每一首歌”的基础数据存取喵！
 */
class SongRepository(private val songDao: SongDao) {

    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        songDao.getAllSongs()
    }

    suspend fun getAllSongsRaw(): List<Song> = withContext(Dispatchers.IO) {
        songDao.getAllSongsRaw()
    }

    suspend fun getSongByPath(path: String): Song? = withContext(Dispatchers.IO) {
        songDao.getSongByPath(path)
    }

    suspend fun getSongById(id: Long): Song? = withContext(Dispatchers.IO) {
        songDao.getSongById(id)
    }

    suspend fun updateSong(song: Song) = withContext(Dispatchers.IO) {
        songDao.updateSongEntity(song.song)
    }

    suspend fun insertOrUpdateSong(song: Song): Long = withContext(Dispatchers.IO) {
        songDao.insertOrUpdateSong(
            song.song,
            song.loopStart,
            song.loopEnd,
            song.rating
        )
    }

    suspend fun updateSongLoopPoints(song: Song, start: Long, end: Long): Song = withContext(Dispatchers.IO) {
        songDao.insertLoopPoint(LoopPoint(songId = song.id, loopStart = start, loopEnd = end))
        // 重新获取最新的 Song 对象以反映更改
        songDao.getSongById(song.id) ?: song
    }

    suspend fun updateSongRating(song: Song, rating: Int) = withContext(Dispatchers.IO) {
        songDao.updateSongRating(song.id, rating)
    }

    /**
     * 应急修复：通过路径去系统库找寻 MediaID。
     */
    suspend fun resolveMediaId(context: Context, song: Song): Song = withContext(Dispatchers.IO) {
        if (song.mediaId > 0) return@withContext song

        val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(android.provider.MediaStore.Audio.Media._ID)
        val selection = "${android.provider.MediaStore.Audio.Media.DATA} = ?"
        val selectionArgs = arrayOf(song.filePath)

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val newMediaId = cursor.getLong(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID))
                val updatedEntity = song.song.copy(mediaId = newMediaId)
                songDao.updateSongEntity(updatedEntity)
                return@withContext song.copy(song = updatedEntity)
            }
        }
        song
    }
}
