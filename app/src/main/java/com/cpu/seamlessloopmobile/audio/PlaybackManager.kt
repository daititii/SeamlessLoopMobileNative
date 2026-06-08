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

        // 绑定底层的“灵魂感应”器喵！
        NativeAudio.setEventListener(object : NativeAudio.NativeEventListener {
            override fun onEvent(type: Int) {
                when (type) {
                    NativeAudio.EVENT_EOS -> {
                        android.util.Log.d("PlaybackManager", "🏁 底层报告：歌曲播完了喵！")
                        coroutineScope.launch(Dispatchers.Main) {
                            onSongCompleted?.invoke()
                        }
                    }
                    NativeAudio.EVENT_LOOP_JUMP -> {
                        android.util.Log.d("PlaybackManager", "🔄 底层报告：完成了一次完美的循环跳转喵！")
                        // This event only means the decoder handover is done. System-visible
                        // progress is sampled by PlaybackService's 250ms sync job so the
                        // notification/lock screen never rewinds before that position is audible.
                        coroutineScope.launch(Dispatchers.Main) {
                            pendingDecoderLoopJumpCount++
                        }
                    }
                }
            }
        })
    }
    
    // 专门给 Service 回调的钩子，用于通知 UI 更新
    override var onPlaybackStatusChanged: ((isPlaying: Boolean, currentSong: Song?) -> Unit)? = null
    var onSongCompleted: (() -> Unit)? = null
    var onSeamlessLoopLimitReached: (() -> Unit)? = null
    override var onPlaybackError: ((String) -> Unit)? = { error ->
        _state.value = AudioPlayState.ERROR
        currentSong?.let { updateMediaSessionState(it, false, isAbMode, error) }
    }

    override var currentSong: Song? = null
        private set

    private var isAbMode = false
    private var seamlessLoopCountForCurrentSong = 0
    private var pendingDecoderLoopJumpCount = 0
    private var lastObservedPositionFrames = -1L

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
        val song = currentSong
        if (song == null) {
            android.util.Log.w("PlaybackManager", "resume ignored because no current song is loaded")
            _state.value = AudioPlayState.IDLE
            onPlaybackStatusChanged?.invoke(false, null)
            return
        }

        NativeAudio.resumeAudioEngine()
        _state.value = AudioPlayState.PLAYING
        updateMediaSessionState(song, true, isAbMode)
    }

    override fun stop() {
        NativeAudio.stopAudioEngine()
        _state.value = AudioPlayState.IDLE
        currentSong = null
        isAbMode = false
        resetSeamlessLoopCounter()
        // 不在这里更新 Session，通常由 Service 处理销毁
    }

    override fun release() {
        stop()
        // 这里目前没什么特别好放的，以后如果有 ExoPlayer 就在这里销毁引擎喵
    }

    override fun seekTo(position: Long) {
        // position 来自 MediaSession (通知栏等)，单位是 PlaybackState 中存的毫秒
        // 底层 NativeAudio.seekTo 需要帧数，这里做转换
        val sr = NativeAudio.getSampleRate().let { if (it > 0) it else 44100 }
        val framePos = position * sr / 1000L
        NativeAudio.seekTo(framePos)
        lastObservedPositionFrames = framePos
        pendingDecoderLoopJumpCount = 0
        currentSong?.let {
            updateMediaSessionState(it, isPlaying, isAbMode)
        }
    }

    fun seekToFrame(positionFrames: Long) {
        // Internal playback commands already operate in native sample frames.
        NativeAudio.seekTo(positionFrames)
        lastObservedPositionFrames = positionFrames
        pendingDecoderLoopJumpCount = 0
        currentSong?.let {
            updateMediaSessionState(it, isPlaying, isAbMode)
        }
    }

    override fun setLooping(looping: Boolean) {
        NativeAudio.setLooping(looping)
    }

    fun updatePosition() {
        if (_state.value != AudioPlayState.PLAYING) return
        val positionFrames = NativeAudio.getCurrentPosition()
        if (registerAudibleSeamlessLoopIfNeeded(positionFrames)) return
        // 底层返回帧数，但 PlaybackState 需存毫秒以与 METADATA_KEY_DURATION 同单位，
        // 否则通知栏进度条会因单位不一致而错位或溢出
        val posMs = framesToPlaybackPositionMs(positionFrames, NativeAudio.getSampleRate())
        // 从零构建 PlaybackState，不依赖 controller 的异步缓存，防止读到过时状态导致 setPlaybackState 被忽略喵！
        val extras = android.os.Bundle().apply {
            putBoolean("is_ab_mode", isAbMode)
            putInt("play_mode", settingsManager.playMode.ordinal)
        }
        val newState = android.support.v4.media.session.PlaybackStateCompat.Builder()
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
                android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING,
                posMs,
                1.0f
            )
            .setExtras(extras)
            .build()
        mediaSession.setPlaybackState(newState)
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

        // 底层 getCurrentPosition 返回帧数，PlaybackState 需存毫秒以与 METADATA_KEY_DURATION 保持一致
        val posMs = framesToPlaybackPositionMs(NativeAudio.getCurrentPosition(), NativeAudio.getSampleRate())

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
                posMs,
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

    private var playJob: kotlinx.coroutines.Job? = null

    private fun resetSeamlessLoopCounter() {
        seamlessLoopCountForCurrentSong = 0
        pendingDecoderLoopJumpCount = 0
        lastObservedPositionFrames = -1L
    }

    private fun registerAudibleSeamlessLoopIfNeeded(positionFrames: Long): Boolean {
        val previousPosition = lastObservedPositionFrames
        lastObservedPositionFrames = positionFrames

        if (pendingDecoderLoopJumpCount <= 0 || previousPosition < 0L || positionFrames >= previousPosition) {
            return false
        }

        pendingDecoderLoopJumpCount--
        seamlessLoopCountForCurrentSong++

        val limit = settingsManager.seamlessLoopCountLimit
        android.util.Log.d(
            "PlaybackManager",
            "🔁 当前歌曲无缝循环计数：$seamlessLoopCountForCurrentSong / ${if (limit == 0) "∞" else limit}"
        )
        if (limit > 0 && seamlessLoopCountForCurrentSong >= limit) {
            android.util.Log.d("PlaybackManager", "🔁 无缝循环次数达到上限 $limit，准备切换下一首")
            resetSeamlessLoopCounter()
            onSeamlessLoopLimitReached?.invoke()
            return true
        }
        return false
    }

    override fun play(song: Song, startPos: Long, startPaused: Boolean) {
        playSong(song, startPos, startPaused)
    }

    override fun playSong(song: Song, startPosition: Long, startPaused: Boolean, isSingleLoop: Boolean) {
        playJob?.cancel()
        playJob = coroutineScope.launch {
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

    private suspend fun actuallyPlaySong(song: Song, startPosition: Long = 0, startPaused: Boolean = false, isSingleLoop: Boolean = true) = withContext(Dispatchers.IO) {
        this@PlaybackManager.currentSong = song
        _state.value = AudioPlayState.PREPARING
        resetSeamlessLoopCounter()
        NativeAudio.stopAudioEngine()
            
            // --- 核心修复：防止内存对象过时喵！ ---
            // 莱芙在正式闭眼播放前，先去数据库里确认一下这首歌最新的“灵魂参数”喵
            var latestSong = repository.getSongByPath(song.filePath)
            android.util.Log.d("PlaybackManager", "🔍 第一次数据库查询(通过路径): ${if (latestSong != null) "成功" else "失败"}")
            
            // 如果通过路径没找到，但歌曲有有效ID，尝试通过ID找喵
            if (latestSong == null && song.id > 0) {
                latestSong = repository.getSongById(song.id)
                android.util.Log.d("PlaybackManager", "🔍 第二次数据库查询(通过ID ${song.id}): ${if (latestSong != null) "成功" else "失败"}")
            }
            
            // 如果还是没找到，使用原对象喵
            latestSong = latestSong ?: song
            android.util.Log.d("PlaybackManager", "🚀 准备开播：${latestSong.fileName}, 最终循环点: [${latestSong.loopStart}-${latestSong.loopEnd}]")
            android.util.Log.d("PlaybackManager", "📊 原内存对象循环点: [${song.loopStart}-${song.loopEnd}], 路径: ${song.filePath}, ID: ${song.id}")
            android.util.Log.d("PlaybackManager", "📊 数据库歌曲循环点: [${latestSong.loopStart}-${latestSong.loopEnd}], ID: ${latestSong.id}")
            android.util.Log.d("PlaybackManager", "🔍 数据库查询最终结果: ${if (latestSong == song) "未找到更新数据" else "已从数据库更新"}, 路径匹配: ${song.filePath == latestSong.filePath}")

            try {
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, latestSong.mediaId)
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    val actualLength = if (afd.declaredLength < 0) afd.length else afd.declaredLength
                    NativeAudio.startAudioEngine(afd.parcelFileDescriptor.fd, afd.startOffset, actualLength)
                }

                val durationFrames = NativeAudio.getDuration()
                var actualLoopStart = latestSong.loopStart
                var actualLoopEnd = latestSong.loopEnd
                
                // 如果循环点都是 0（新导入未设置），默认全曲首尾循环喵！
                if (actualLoopStart == 0L && actualLoopEnd == 0L && durationFrames > 0L) {
                    actualLoopEnd = durationFrames
                }
                
                // --- 🛡️ 安全边界检查：防止 PC 数据越界喵！ ---
                if (durationFrames > 0L) {
                    if (actualLoopEnd > durationFrames) {
                        android.util.Log.w("PlaybackManager", "⚠️ 警告：loopEnd ($actualLoopEnd) 超过了实测时长 ($durationFrames)，已强制截断喵！")
                        actualLoopEnd = durationFrames
                    }
                    if (actualLoopStart >= durationFrames) {
                        android.util.Log.e("PlaybackManager", "❌ 错误：loopStart ($actualLoopStart) 在时长之外，重置为 0 喵！")
                        actualLoopStart = 0L
                    }
                }

                // --- 无缝循环适配（与全局模式完全解耦）喵 ---
                val isSeamlessEnabled = settingsManager.isSeamlessLoopEnabled
                val hasCustomLoopPoints = actualLoopEnd > actualLoopStart

                if (isSeamlessEnabled && hasCustomLoopPoints) {
                    android.util.Log.d("PlaybackManager", "🎯 无缝开关开启：开启局部无缝循环 [$actualLoopStart-$actualLoopEnd]")
                    NativeAudio.setLoopPoints(actualLoopStart, actualLoopEnd)
                    NativeAudio.setLooping(true)
                } else {
                    android.util.Log.d("PlaybackManager", "⚠️ 无缝开关关闭（或无循环点）：播放至物理结尾，由切歌模式接管")
                    NativeAudio.setLooping(false)
                }

                if (startPosition > 0) {
                    NativeAudio.seekTo(startPosition)
                }
                
                if (startPaused) {
                    NativeAudio.pauseAudioEngine()
                } else {
                    NativeAudio.resumeAudioEngine()
                }

                var finalSong = latestSong
                if (durationFrames > 0) {
                    val actualSampleRate = NativeAudio.getSampleRate().let { if (it > 0) it else 44100 }
                    val newTotalSamples = if (latestSong.totalSamples <= 0L) durationFrames else latestSong.totalSamples
                    
                    // 同步将推断的循环点信息更新到数据库喵！
                    val newLoopEnd = if (latestSong.loopStart == 0L && latestSong.loopEnd == 0L) durationFrames else latestSong.loopEnd
                    
                    finalSong = latestSong.copy(
                        duration = durationFrames * 1000 / actualSampleRate,
                        totalSamples = newTotalSamples,
                        loopEnd = newLoopEnd
                    )
                    
                    // 如果这是第一次读取样本数或者这是首没循环点的新歌，就把它的档案补充完整！
                    if ((latestSong.totalSamples <= 0L || (latestSong.loopStart == 0L && latestSong.loopEnd == 0L)) && finalSong.id > 0) {
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

    private suspend fun playAbSong(introSong: Song, loopSong: Song, startPosition: Long = 0, startPaused: Boolean = false, isSingleLoop: Boolean = true) = withContext(Dispatchers.IO) {
        this@PlaybackManager.currentSong = introSong
        _state.value = AudioPlayState.PREPARING
        resetSeamlessLoopCounter()
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
                            afdB.parcelFileDescriptor.fd, afdB.startOffset, lenB,
                            settingsManager.isSeamlessLoopEnabled
                        )
                        android.util.Log.d("PlaybackManager", "✅ C++ AB 引擎指令已送达，无缝大循环开启状态: ${settingsManager.isSeamlessLoopEnabled} 喵！")
                    }
                }

                // --- A/B 无缝循环适配（与全局模式完全解耦）喵 ---
                val isSeamlessEnabled = settingsManager.isSeamlessLoopEnabled

                if (isSeamlessEnabled) {
                    android.util.Log.d("PlaybackManager", "🎯 AB 模式：无缝开关开启，主段洗脑循环")
                    NativeAudio.setLooping(true)
                } else {
                    android.util.Log.d("PlaybackManager", "⚠️ AB 模式：无缝开关关闭，前奏+主段平铺播放至物理结尾")
                    NativeAudio.setLooping(false)
                }

                if (startPosition > 0) {
                    NativeAudio.seekTo(startPosition)
                }

                if (startPaused) {
                    NativeAudio.pauseAudioEngine()
                } else {
                    NativeAudio.resumeAudioEngine()
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
