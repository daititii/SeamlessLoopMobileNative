package com.cpu.seamlessloopmobile.data

import android.content.Context
import com.cpu.seamlessloopmobile.jni.LoopPoint
import com.cpu.seamlessloopmobile.jni.NativeAudio
import com.cpu.seamlessloopmobile.model.Song
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.FileInputStream
import androidx.core.net.toUri

/**
 * 循环探测数据与底层业务仓库喵！
 * 莱芙把复制临时文件、调用 C++ JNI 分析、Gson 编解码都藏在这里啦，非常安静、听话！
 */
class LoopDetectionRepository(
    private val musicRepository: MusicRepository,
    private val context: Context
) {
    private val gson = Gson()

    /**
     * 反序列化缓存的候选循环点 JSON 字符串喵！
     */
    fun parseCachedCandidates(json: String?): List<LoopPoint>? {
        if (json.isNullOrEmpty()) return null
        return try {
            val type = object : TypeToken<List<LoopPoint>>() {}.type
            gson.fromJson<List<LoopPoint>>(json, type)
        } catch (e: Exception) {
            android.util.Log.w("LoopDetectionRepo", "⚠️ 缓存 JSON 反序列化失败: ${e.message}")
            null
        }
    }

    /**
     * 获取循环点候选列表喵！
     * 如果有缓存且不强制重探，则直接解析；否则将音频文件安全拷贝到私有缓存目录，并交给 NativeAudio JNI 层计算。
     */
    suspend fun getLoopCandidates(song: Song, forceReanalyze: Boolean = false): List<LoopPoint> = withContext(Dispatchers.IO) {
        // 1. 优先使用缓存
        if (!forceReanalyze) {
            val cached = parseCachedCandidates(song.loopCandidatesJson)
            if (!cached.isNullOrEmpty()) {
                android.util.Log.d("LoopDetectionRepo", "📦 命中缓存，直接返回 ${cached.size} 个循环点候选喵！")
                return@withContext cached
            }
        }

        // 2. 拷贝并分析
        var tempFile: File? = null
        try {
            // 提取文件的真实扩展名（优先使用 fileName 保证 content:// 等路径的真实格式不丢失喵！）
            var ext = File(song.fileName).extension
            if (ext.isEmpty()) {
                ext = File(song.filePath).extension
            }
            val extension = if (ext.isNotEmpty()) ".$ext" else ".mp3"
            val cacheDir = context.cacheDir
            val tFile = File(cacheDir, "loop_detect_${System.currentTimeMillis()}$extension")
            tempFile = tFile

            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null
            val copied = try {
                inputStream = if (song.filePath.startsWith("content://")) {
                    context.contentResolver.openInputStream(song.filePath.toUri())
                } else if (song.mediaId > 0) {
                    val mediaUri = android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        song.mediaId
                    )
                    try {
                        context.contentResolver.openInputStream(mediaUri)
                    } catch (me: Exception) {
                        android.util.Log.w("LoopDetectionRepo", "⚠️ MediaStore URI 打开失败，尝试回退物理路径: ${me.message}")
                        FileInputStream(File(song.filePath))
                    }
                } else {
                    FileInputStream(File(song.filePath))
                }

                if (inputStream != null) {
                    outputStream = FileOutputStream(tFile)
                    val buffer = ByteArray(1024 * 64) // 64KB 缓冲区
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                    true
                } else {
                    false
                }
            } catch (ioe: Exception) {
                android.util.Log.e("LoopDetectionRepo", "❌ 复制音频文件出错: ${ioe.message}")
                false
            } finally {
                try { inputStream?.close() } catch (_: Exception) {}
                try { outputStream?.close() } catch (_: Exception) {}
            }

            if (!copied) {
                throw java.io.IOException("无法成功复制源音频文件 (´w｀)")
            }

            // 将 fopen 完美可读的私有文件路径传给 Native 计算喵！
            val results = NativeAudio.analyzeLoopPoints(tFile.absolutePath, 5)
            if (results.isNullOrEmpty()) {
                emptyList()
            } else {
                results.toList()
            }
        } finally {
            // 确保临时文件已被彻底毁灭，杜绝磁盘残留垃圾喵！
            try {
                tempFile?.let {
                    if (it.exists()) {
                        it.delete()
                    }
                }
            } catch (de: Exception) {
                android.util.Log.e("LoopDetectionRepo", "❌ 删除临时分析文件失败: ${de.message}")
            }
        }
    }

    /**
     * 将候选列表序列化并存储至数据库，返回最新获取的 Song 对象喵！
     */
    suspend fun saveLoopCandidates(song: Song, candidates: List<LoopPoint>): Song = withContext(Dispatchers.IO) {
        val json = gson.toJson(candidates)
        musicRepository.updateLoopCandidatesJson(song, json)
    }

    /**
     * 更新数据库中的最终循环点数据喵！
     */
    suspend fun updateSongLoopPoints(song: Song, start: Long, end: Long): Song = withContext(Dispatchers.IO) {
        musicRepository.updateSongLoopPoints(song, start, end)
        // 重新获取 Song 对象，保持数据同步最新
        musicRepository.getSongById(song.id) ?: song.copy(loopStart = start, loopEnd = end)
    }
}
