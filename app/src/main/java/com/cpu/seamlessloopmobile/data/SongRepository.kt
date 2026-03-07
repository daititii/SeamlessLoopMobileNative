package com.cpu.seamlessloopmobile.data

import android.content.Context
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 歌谱保管员：专注于“每一首歌”的基础数据存取喵！
 */
class SongRepository(private val songDao: SongDao) {

    suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        songDao.getAllSongs()
    }

    suspend fun getSongByPath(path: String): Song? = withContext(Dispatchers.IO) {
        songDao.getSongByPath(path)
    }

    suspend fun getSongById(id: Long): Song? = withContext(Dispatchers.IO) {
        songDao.getSongById(id)
    }

    suspend fun updateSong(song: Song) = withContext(Dispatchers.IO) {
        songDao.updateSong(song)
    }

    suspend fun insertOrUpdateSong(song: Song): Long = withContext(Dispatchers.IO) {
        songDao.insertOrUpdateSong(song)
    }

    suspend fun updateSongLoopPoints(song: Song, start: Long, end: Long): Song = withContext(Dispatchers.IO) {
        val newSong = song.copy(loopStart = start, loopEnd = end)
        songDao.insertOrUpdateSong(newSong)
        newSong
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
                val updatedSong = song.copy(mediaId = newMediaId)
                songDao.insertOrUpdateSong(updatedSong)
                return@withContext updatedSong
            }
        }
        song
    }
}
