package com.cpu.seamlessloopmobile.scanner

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.cpu.seamlessloopmobile.model.Song
import java.io.File
import java.io.RandomAccessFile

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

        // 过滤条件：只找音乐类文件（移除原本 10 秒的时长限制，确保所有短曲目也能出现喵）
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
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
     * 快速估算！读文件头解析采样率，配合 duration 估算 totalSamples喵！
     * 纯 Kotlin 文件 I/O，不解码不调 native，<1ms/首喵～
     */
    fun getApproximateSamples(filePath: String, durationMs: Long): Long {
        return try {
            val file = File(filePath)
            if (!file.exists()) return 0L
            val sampleRate = detectSampleRate(file)
            if (sampleRate > 0 && durationMs > 0) (durationMs * sampleRate) / 1000L else 0L
        } catch (e: Exception) {
            0L
        }
    }
    private fun detectSampleRate(file: File): Int {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(8192)
                val bytesRead = raf.read(header)
                if (bytesRead < 4) return 44100

                // FLAC: "fLaC" at offset 0, sample rate at bytes 18-20 (20-bit BE)
                if (bytesRead >= 42 && header[0] == 0x66.toByte() && header[1] == 0x4C.toByte() &&
                    header[2] == 0x61.toByte() && header[3] == 0x43.toByte()) {
                    val sr = ((header[18].toInt() and 0xFF) shl 12) or
                             ((header[19].toInt() and 0xFF) shl 4) or
                             ((header[20].toInt() and 0xFF) shr 4)
                    if (sr > 0) return sr
                }

                // WAV: "RIFF" at offset 0, sample rate at bytes 24-27 (32-bit LE)
                if (bytesRead >= 28 && header[0] == 0x52.toByte() && header[1] == 0x49.toByte() &&
                    header[2] == 0x46.toByte() && header[3] == 0x46.toByte()) {
                    val sr = (header[24].toInt() and 0xFF) or
                             ((header[25].toInt() and 0xFF) shl 8) or
                             ((header[26].toInt() and 0xFF) shl 16) or
                             ((header[27].toInt() and 0xFF) shl 24)
                    if (sr > 0) return sr
                }

                // OGG: "OggS" at offset 0
                if (bytesRead >= 42 && header[0] == 0x4F.toByte() && header[1] == 0x67.toByte() &&
                    header[2] == 0x67.toByte() && header[3] == 0x53.toByte()) {
                    val segCount = header[26].toInt() and 0xFF
                    val pktStart = 27 + segCount
                    if (segCount == 1 && bytesRead >= pktStart + 15) {
                        // Vorbis ident header: type(1) + "vorbis"(6) + version(4) + sampleRate(4)
                        val sr = (header[pktStart + 11].toInt() and 0xFF) or
                                 ((header[pktStart + 12].toInt() and 0xFF) shl 8) or
                                 ((header[pktStart + 13].toInt() and 0xFF) shl 16) or
                                 ((header[pktStart + 14].toInt() and 0xFF) shl 24)
                        if (sr > 0) return sr
                    }
                }

                // MP3: find sync word 0xFFE0-0xFFFE
                var offset = 0
                if (bytesRead >= 10 && header[0] == 0x49.toByte() && header[1] == 0x44.toByte() &&
                    header[2] == 0x33.toByte()) {
                    val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                                  ((header[7].toInt() and 0x7F) shl 14) or
                                  ((header[8].toInt() and 0x7F) shl 7) or
                                  (header[9].toInt() and 0x7F)
                    offset = 10 + tagSize
                }

                while (offset + 3 < bytesRead) {
                    if ((header[offset].toInt() and 0xFF) == 0xFF &&
                        (header[offset + 1].toInt() and 0xE0) == 0xE0) {
                        val h = ((header[offset].toInt() and 0xFF) shl 24) or
                                ((header[offset + 1].toInt() and 0xFF) shl 16) or
                                ((header[offset + 2].toInt() and 0xFF) shl 8) or
                                (header[offset + 3].toInt() and 0xFF)
                        val srIndex = (h shr 10) and 0x03
                        val version = (h shr 19) and 0x03
                        return when (version) {
                            3 -> intArrayOf(44100, 48000, 32000, 0)[srIndex]
                            2 -> intArrayOf(22050, 24000, 16000, 0)[srIndex]
                            0 -> intArrayOf(11025, 12000, 8000, 0)[srIndex]
                            else -> 44100
                        }
                    }
                    offset++
                }
            }
            44100
        } catch (e: Exception) {
            44100
        }
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
