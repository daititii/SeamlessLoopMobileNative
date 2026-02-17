package com.cpu.seamlessloopmobile.scanner

import android.content.ContentResolver
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
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val fileName = it.getString(nameColumn)
                val title = it.getString(titleColumn) ?: "Unknown"
                val artist = it.getString(artistColumn) ?: "Unknown Artist"
                val filePath = it.getString(pathColumn)
                val duration = it.getLong(durationColumn)

                // 检查物理文件是否真的存在
                val file = File(filePath)
                if (file.exists()) {
                    songs.add(
                        Song(
                            id = id,
                            fileName = fileName,
                            filePath = filePath,
                            displayName = title,
                            artist = artist,
                            duration = duration,
                            totalSamples = 0, // 初始设为 0，后续需要解析文件头获取准确采样数
                            isLoopEnabled = false // 默认关闭，等待匹配数据库中的循环点
                        )
                    )
                }
            }
        }
        return songs
    }
}
