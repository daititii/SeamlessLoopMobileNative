package com.cpu.seamlessloopmobile.audio

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.cpu.seamlessloopmobile.jni.NativeAudio
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.data.MusicRepository
import android.support.v4.media.session.MediaSessionCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 听觉中枢：负责处理音频文件的加载、解码启动以及数据库时长采集。
 * 已完成“脑部移植”，现在它是 PlaybackService 的直属核心引擎喵！
 */
class PlaybackManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val repository: MusicRepository,
    private val mediaSession: MediaSessionCompat
) {
    // 专门给 Service 回调的钩子，用于通知 UI 更新
    var onPlaybackStatusChanged: ((isPlaying: Boolean, currentSong: Song?) -> Unit)? = null
    var onPlaybackError: ((String) -> Unit)? = null

    fun updateMediaSessionState(song: Song, isPlaying: Boolean, isAbMode: Boolean = false) {
        val metadata = android.support.v4.media.MediaMetadataCompat.Builder()
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, song.displayName ?: song.fileName)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist ?: "Unknown Artist")
            .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
            // 存入自定义标记，方便 UI 识别喵
            .putString("is_ab_mode", if (isAbMode) "true" else "false")
            .build()
        mediaSession.setMetadata(metadata)

        val stateBuilder = android.support.v4.media.session.PlaybackStateCompat.Builder()
            .setActions(
                android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY or
                android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE or
                android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE or
                android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING 
                else android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED,
                NativeAudio.getCurrentPosition(), 
                1.0f
            )
            // 通过 Bundle 传递更多秘密情报喵！
            .setExtras(android.os.Bundle().apply {
                putBoolean("is_ab_mode", isAbMode)
            })
            
        mediaSession.setPlaybackState(stateBuilder.build())
        
        onPlaybackStatusChanged?.invoke(isPlaying, song)
    }

    fun playFromMediaId(mediaId: Long, startPosition: Long = 0, startPaused: Boolean = false, isSingleLoop: Boolean = true) {
        coroutineScope.launch {
            val song = withContext(Dispatchers.IO) {
                 // 这里我们需要让 repository 支持通过 mediaId 找 Song 喵
                 // 但目前 repository 主要是通过 path 找。莱芙先去补一个喵！
                 repository.getAllSongs().find { it.mediaId == mediaId }
            }
            song?.let { 
                playSong(it, startPosition, startPaused, isSingleLoop)
            } ?: run {
                onPlaybackError?.invoke("找不着大人的这首歌了喵...")
            }
        }
    }

    fun playSong(song: Song, startPosition: Long = 0, startPaused: Boolean = false, isSingleLoop: Boolean = true) {
        // 先检查是否需要 AB 模式 (逻辑从 Activity 搬到了这里喵)
        // 注意：AB 模式的发现由于不再依赖 ViewModel，需要调用者提供同级歌曲列表
        // 或者我们可以让 Repository 负责寻找 AB 配对喵
        
        coroutineScope.launch {
            // 安全第一喵！如果这是一首刚从 PC 导入、身份未知的歌，莱芙现场申请一个 ID 令牌！
            val resolvedSong = if (song.mediaId <= 0) {
                repository.resolveMediaId(context, song)
            } else song

            val abPair = withContext(Dispatchers.IO) {
                repository.findAbPairRobust(context, resolvedSong)
            }
            if (abPair != null) {
                // AB 模式下，也得帮 B 段歌曲确认一下身份令牌喵！
                val resolvedB = if (abPair.second.mediaId <= 0) {
                    repository.resolveMediaId(context, abPair.second)
                } else abPair.second
                
                playAbSong(resolvedSong, resolvedB, startPosition, startPaused, isSingleLoop)
                return@launch
            }

            actuallyPlaySong(resolvedSong, startPosition, startPaused, isSingleLoop)
        }
    }

    private fun actuallyPlaySong(song: Song, startPosition: Long = 0, startPaused: Boolean = false, isSingleLoop: Boolean = true) {

        coroutineScope.launch(Dispatchers.IO) {
            NativeAudio.stopAudioEngine()
            
            try {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.mediaId)
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    val actualLength = if (afd.declaredLength < 0) afd.length else afd.declaredLength
                    NativeAudio.startAudioEngine(afd.parcelFileDescriptor.fd, afd.startOffset, actualLength)
                }

                if (song.loopEnd > 0) {
                    NativeAudio.setLoopPoints(song.loopStart, song.loopEnd)
                }
                NativeAudio.setLooping(isSingleLoop)

                if (startPosition > 0) {
                    NativeAudio.seekTo(startPosition)
                }
                
                if (startPaused) {
                    NativeAudio.pauseAudioEngine()
                }

                val durationFrames = NativeAudio.getDuration()
                var finalSong = song
                if (durationFrames > 0) {
                    finalSong = song.copy(
                        duration = durationFrames * 1000 / 44100,
                        totalSamples = if (song.totalSamples == 0L) durationFrames else song.totalSamples 
                    )
                    if (song.totalSamples == 0L && finalSong.id > 0) {
                        repository.updateSong(finalSong) 
                    }
                }

                withContext(Dispatchers.Main) {
                    updateMediaSessionState(finalSong, !startPaused)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onPlaybackError?.invoke("无法打开音频文件喵...")
                }
            }
        }
    }

    private fun playAbSong(introSong: Song, loopSong: Song, startPosition: Long = 0, startPaused: Boolean = false, isSingleLoop: Boolean = true) {
        coroutineScope.launch(Dispatchers.IO) {
            NativeAudio.stopAudioEngine()
            
            try {
                android.util.Log.d("PlaybackManager", "====== 🚀 准备启动 AB 无缝合体模式喵！======")
                android.util.Log.d("PlaybackManager", "🍒 A 段 (前奏): ${introSong.fileName} (ID=${introSong.mediaId})")
                android.util.Log.d("PlaybackManager", "🍓 B 段 (循环): ${loopSong.fileName} (ID=${loopSong.mediaId})")

                val uriA = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, introSong.mediaId)
                val uriB = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, loopSong.mediaId)
                
                context.contentResolver.openAssetFileDescriptor(uriA, "r")?.use { afdA ->
                    context.contentResolver.openAssetFileDescriptor(uriB, "r")?.use { afdB ->
                        val lenA = if (afdA.declaredLength < 0) afdA.length else afdA.declaredLength
                        val lenB = if (afdB.declaredLength < 0) afdB.length else afdB.declaredLength
                        
                        android.util.Log.d("PlaybackManager", "⚖️ 测量完毕！A段长度: $lenA 字节, B段长度: $lenB 字节")
                        
                        NativeAudio.startAbAudioEngine(
                            afdA.parcelFileDescriptor.fd, afdA.startOffset, lenA,
                            afdB.parcelFileDescriptor.fd, afdB.startOffset, lenB
                        )
                        android.util.Log.d("PlaybackManager", "✅ C++ AB 引擎指令已送达喵！")
                    }
                }

                // 在 AB 模式下，底层会自动把 B 段作为循环主体喵
                NativeAudio.setLooping(isSingleLoop)

                if (startPosition > 0) {
                    NativeAudio.seekTo(startPosition)
                }

                if (startPaused) {
                    NativeAudio.pauseAudioEngine()
                }

                val totalFrames = NativeAudio.getDuration()
                val totalDuration = totalFrames * 1000 / 44100
                
                android.util.Log.d("PlaybackManager", "📊 合体系统就绪！总帧数: $totalFrames, 总时长: $totalDuration ms")

                val abTitle = (introSong.displayName ?: introSong.fileName.substringBeforeLast(".")) + " [AB Loop]"
                val abSong = introSong.copy(displayName = abTitle, duration = totalDuration)
                
                withContext(Dispatchers.Main) {
                    updateMediaSessionState(abSong, !startPaused, isAbMode = true)
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaybackManager", "AB 启动失败喵: ${e.message}")
                withContext(Dispatchers.Main) {
                    onPlaybackError?.invoke("AB 播放失败: ${e.message}")
                }
            }
        }
    }
}
