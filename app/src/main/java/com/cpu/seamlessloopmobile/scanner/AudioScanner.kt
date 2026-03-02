package com.cpu.seamlessloopmobile.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.cpu.seamlessloopmobile.model.Song
import java.io.File

/**
 * 极简音频扫描器
 * 基于 ContentResolver 从 Android 系统媒体库获取歌曲
 */
object AudioScanner {

    fun scan(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        val contentResolver: ContentResolver = context.contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        
        // 查询字段：ID, 文件名, 标题, 艺术家, 路径, 时长
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            "album_artist", // ALBUM_ARTIST in newer Android versions
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION
        )

        // 过滤条件：只找音乐类的，时长大于 10 秒的
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor = contentResolver.query(uri, projection, selection, null, sortOrder)

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = it.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val albumArtistColumn = it.getColumnIndex("album_artist")
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val fileName = it.getString(nameColumn)
                val title = it.getString(titleColumn) ?: "Unknown"
                val artist = it.getString(artistColumn) ?: "Unknown Artist"
                val album = if (albumColumn != -1) it.getString(albumColumn) ?: "Unknown Album" else "Unknown Album"
                val albumArtist = if (albumArtistColumn != -1) it.getString(albumArtistColumn) ?: artist else artist
                val filePath = it.getString(pathColumn)
                val duration = it.getLong(durationColumn)

                // 物理文件校验
                val file = File(filePath)
                if (file.exists()) {
                    // 先给个 0，让列表秒开喵！
                    songs.add(
                        Song(
                            id = 0,
                            mediaId = id,
                            fileName = fileName,
                            filePath = filePath,
                            displayName = title,
                            artist = artist,
                            album = album,
                            albumArtist = albumArtist,
                            duration = duration,
                            totalSamples = 0, 
                            isLoopEnabled = false 
                        )
                    )
                }
            }
        }
        return songs
    }

    /**
     * 实地测量！利用 C++ 底层解码器拿到绝对准确的总采样数和采样率喵！
     */
    fun getAccurateMetadata(context: Context, mediaId: Long): Pair<Long, Int> {
        return try {
            val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)
            context.contentResolver.openAssetFileDescriptor(contentUri, "r")?.use { afd ->
                val fd = afd.parcelFileDescriptor.fd
                val offset = afd.startOffset
                val length = if (afd.declaredLength < 0) afd.length else afd.declaredLength
                
                val frames = com.cpu.seamlessloopmobile.jni.NativeAudio.getAudioFileDuration(fd, offset, length)
                val sampleRate = com.cpu.seamlessloopmobile.jni.NativeAudio.getAudioFileSampleRate(fd, offset, length)
                Pair(frames, sampleRate)
            } ?: Pair(0L, 44100)
        } catch (e: Exception) {
            Pair(0L, 44100)
        }
    }

    /**
     * 旧接口兼容：获取准确的总采样数喵！
     */
    fun getAccurateSampleCount(context: Context, mediaId: Long): Long {
        return getAccurateMetadata(context, mediaId).first
    }
}
