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
import kotlinx.coroutines.flow.asStateFlow

/**
 * 听觉中枢：负责处理音频文件的加载、解码启动以及数据库时长采集。
 * 已完成“脑部移植”，现在它是 PlaybackService 的直属核心引擎喵！
 */
class PlaybackManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val repository: MusicRepository,
    private val mediaSession: MediaSessionCompat
) : Playback, IMultiPlayer {
    private val settingsManager = com.cpu.seamlessloopmobile.data.SettingsManager.getInstance(context)
    
    // 状态机核心喵！
    private val _state = kotlinx.coroutines.flow.MutableStateFlow(AudioPlayState.IDLE)
    override val state = _state.asStateFlow()
    
    init {
        // 初始化播放模式喵
        val mode = settingsManager.playMode.ordinal
        val state = mediaSession.controller.playbackState
        val builder = if (state != null) {
            android.support.v4.media.session.PlaybackStateCompat.Builder(state)
        } else {
            android.support.v4.media.session.PlaybackStateCompat.Builder()
        }
        
        val newState = builder.setExtras(android.os.Bundle().apply { 
                putInt("play_mode", mode)
            })
            .build()
        mediaSession.setPlaybackState(newState)
    }
    
    // 专门给 Service 回调的钩子，用于通知 UI 更新
    override var onPlaybackStatusChanged: ((isPlaying: Boolean, currentSong: Song?) -> Unit)? = null
    override var onPlaybackError: ((String) -> Unit)? = { error ->
        _state.value = AudioPlayState.ERROR
        currentSong?.let { updateMediaSessionState(it, false, isAbMode, error) }
    }

    override var currentSong: Song? = null
        private set

    private var isAbMode = false

    override val isPlaying: Boolean
        get() = mediaSession.controller.playbackState?.state == android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING

    override val position: Long
        get() = NativeAudio.getCurrentPosition()

    override val duration: Long
        get() = NativeAudio.getDuration()

    override val sampleRate: Int
        get() = NativeAudio.getSampleRate()

    override fun pause() {
        NativeAudio.pauseAudioEngine()
        _state.value = AudioPlayState.PAUSED
        currentSong?.let {
            updateMediaSessionState(it, false, isAbMode)
        }
    }

    override fun resume() {
        NativeAudio.resumeAudioEngine()
        _state.value = AudioPlayState.PLAYING
        currentSong?.let {
            updateMediaSessionState(it, true, isAbMode)
        }
    }

    override fun stop() {
        NativeAudio.stopAudioEngine()
        _state.value = AudioPlayState.IDLE
        currentSong = null
        isAbMode = false
        // 不在这里更新 Session，通常由 Service 处理销毁
    }

    override fun release() {
        stop()
        // 这里目前没什么特别好放的，以后如果有 ExoPlayer 就在这里销毁引擎喵
    }

    override fun seekTo(position: Long) {
        NativeAudio.seekTo(position)
        currentSong?.let {
            updateMediaSessionState(it, isPlaying, isAbMode)
        }
    }

    override fun setLooping(looping: Boolean) {
        NativeAudio.setLooping(looping)
    }

    fun updateMediaSessionState(song: Song, isPlaying: Boolean, isAbMode: Boolean = false, error: String? = null) {
        this.currentSong = song
        this.isAbMode = isAbMode
        
        val metadata = android.support.v4.media.MediaMetadataCompat.Builder()
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID, song.mediaId.toString())
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, song.displayName ?: song.fileName)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist ?: "Unknown Artist")
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_URI, song.filePath)
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
                android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO or
                android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP
            )
            .setState(
                if (isPlaying) android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING 
                else android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED,
                NativeAudio.getCurrentPosition(), 
                1.0f
            )
        
        error?.let {
            stateBuilder.setErrorMessage(android.support.v4.media.session.PlaybackStateCompat.ERROR_CODE_APP_ERROR, it)
        }
        
        // 通过 Bundle 传递更多秘密情报喵！
        val extras = mediaSession.controller.playbackState?.extras ?: android.os.Bundle()
        extras.putBoolean("is_ab_mode", isAbMode)
        stateBuilder.setExtras(extras)
            
        mediaSession.setPlaybackState(stateBuilder.build())
        
        // 莱芙帮大人记下来喵！
        settingsManager.lastSongPath = song.filePath
        settingsManager.lastPosition = NativeAudio.getCurrentPosition()
        settingsManager.isAbMode = isAbMode
        
        onPlaybackStatusChanged?.invoke(isPlaying, song)
    }

    override fun play(song: Song, startPos: Long, startPaused: Boolean) {
        playSong(song, startPos, startPaused)
    }

    override fun playSong(song: Song, startPosition: Long, startPaused: Boolean, isSingleLoop: Boolean) {
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
        this.currentSong = song
        coroutineScope.launch(Dispatchers.IO) {
            _state.value = AudioPlayState.PREPARING
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
                    val actualSampleRate = NativeAudio.getSampleRate().let { if (it > 0) it else 44100 }
                    finalSong = song.copy(
                        duration = durationFrames * 1000 / actualSampleRate,
                        totalSamples = if (song.totalSamples == 0L) durationFrames else song.totalSamples 
                    )
                    if (song.totalSamples == 0L && finalSong.id > 0) {
                        repository.updateSong(finalSong) 
                    }
                }

                withContext(Dispatchers.Main) {
                    _state.value = if (startPaused) AudioPlayState.PAUSED else AudioPlayState.PLAYING
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
        this.currentSong = introSong
        coroutineScope.launch(Dispatchers.IO) {
            _state.value = AudioPlayState.PREPARING
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
                val actualSampleRate = NativeAudio.getSampleRate().let { if (it > 0) it else 44100 }
                val totalDuration = totalFrames * 1000 / actualSampleRate
                
                android.util.Log.d("PlaybackManager", "📊 合体系统就绪！总帧数: $totalFrames, 总时长: $totalDuration ms")

                val abTitle = (introSong.displayName ?: introSong.fileName.substringBeforeLast(".")) + " [AB Loop]"
                val abSong = introSong.copy(displayName = abTitle, duration = totalDuration)
                
                withContext(Dispatchers.Main) {
                    _state.value = if (startPaused) AudioPlayState.PAUSED else AudioPlayState.PLAYING
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
