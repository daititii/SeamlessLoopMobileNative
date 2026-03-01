package com.cpu.seamlessloopmobile.data

import android.content.Context
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao
import com.cpu.seamlessloopmobile.scanner.AudioScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 歌曲搜索雷达：专门负责扫描媒体库并进行 A-B 对检测喵！
 */
class MusicScannerRepository(private val songDao: SongDao) {

    /**
     * 初始同步扫描
     */
    suspend fun getInitialScannedSongs(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val scannedSongs = AudioScanner.scan(context)
        val dbSongs = songDao.getAllSongs().associateBy { it.fileName }
        
        scannedSongs.map { song ->
            val dbSong = dbSongs[song.fileName]
            dbSong?.let { 
                val updatedSong = song.copy(
                    id = it.id, 
                    loopStart = it.loopStart, 
                    loopEnd = it.loopEnd, 
                    totalSamples = it.totalSamples, 
                    displayName = it.displayName ?: song.displayName
                )
                if (it.mediaId != song.mediaId) {
                    songDao.insertOrUpdateSong(updatedSong)
                }
                updatedSong
            } ?: song
        }
    }

    /**
     * 发现简单的 A-B 对
     */
    fun findAbPair(song: Song, allScannedSongs: List<Song>): Pair<Song, Song>? {
        val fileName = song.fileName.substringBeforeLast(".")
        val aSuffixes = arrayOf("_A", "_a", "_intro", "_Intro")
        val bSuffixes = arrayOf("_B", "_b", "_loop", "_Loop")

        for (i in aSuffixes.indices) {
            if (fileName.endsWith(aSuffixes[i])) {
                val baseName = fileName.substring(0, fileName.length - aSuffixes[i].length)
                val targetBName = baseName + bSuffixes[i]
                
                val partB = allScannedSongs.find { 
                    it.fileName.substringBeforeLast(".") == targetBName &&
                    File(it.filePath).parent == File(song.filePath).parent
                }
                if (partB != null) return Pair(song, partB)
            }
        }
        return null
    }

    /**
     * 强大的、健壮的 A-B 对搜索（即使没同步数据库也能找回灵魂伴侣喵）
     */
    suspend fun findAbPairRobust(context: Context, song: Song): Pair<Song, Song>? = withContext(Dispatchers.IO) {
        val fileName = song.fileName.substringBeforeLast(".")
        val aSuffixes = arrayOf("_A", "_a", "_intro", "_Intro")
        val bSuffixes = arrayOf("_B", "_b", "_loop", "_Loop")

        var bNameWithoutExt: String? = null
        for (i in aSuffixes.indices) {
            if (fileName.endsWith(aSuffixes[i])) {
                val baseName = fileName.substring(0, fileName.length - aSuffixes[i].length)
                bNameWithoutExt = baseName + bSuffixes[i]
                break
            }
        }
        
        if (bNameWithoutExt == null) return@withContext null
        val parentDir = File(song.filePath).parent ?: return@withContext null

        // 1. 同步数据库中寻找
        val dbSongs = songDao.getAllSongs()
        val pB = dbSongs.find { 
            it.fileName.substringBeforeLast(".") == bNameWithoutExt &&
            File(it.filePath).parent == parentDir
        }
        if (pB != null) return@withContext Pair(song, pB)

        // 2. 数据库没找到，直接去 MediaStore 搜
        var partB: Song? = null
        try {
            val uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
                android.provider.MediaStore.Audio.Media.DATA
            )
            val selection = "${android.provider.MediaStore.Audio.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("$parentDir/%")
            
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol)
                    val path = cursor.getString(dataCol)
                    if (name.substringBeforeLast(".") == bNameWithoutExt && File(path).parent == parentDir) {
                        partB = Song(
                            mediaId = cursor.getLong(idCol),
                            fileName = name,
                            filePath = path,
                            totalSamples = 0,
                            displayName = name.substringBeforeLast("."),
                            loopStart = 0,
                            loopEnd = 0,
                            duration = 0,
                            id = 0
                        )
                        break
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        
        if (partB != null) Pair(song, partB!!) else null
    }
}
