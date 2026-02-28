package com.cpu.seamlessloopmobile.audio

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.cpu.seamlessloopmobile.jni.NativeAudio
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.data.MusicRepository
import com.cpu.seamlessloopmobile.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 听觉中枢：负责处理音频文件的加载、解码启动以及数据库时长采集。
 * 将 MainActivity 从繁重的音频 I/O 中解脱出来喵！
 */
class PlaybackManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val repository: MusicRepository,
    private val viewModel: MainViewModel,
    private val uiCallback: PlaybackUiCallback
) {
    var playbackService: PlaybackService? = null

    fun updateMediaSessionState(isPlaying: Boolean) {
        val currentSong = if (viewModel.isAbModePlaying.value == true) {
            viewModel.currentAbIntroSong.value
        } else {
            val songs = viewModel.currentPlaylist.value
            val index = viewModel.currentSongIndex.value ?: -1
            if (index in (songs?.indices ?: (0 until -1))) songs?.get(index) else null
        }
        
        currentSong?.let { 
            if (isPlaying) {
                playbackService?.updateNotification(it, true)
            } else {
                playbackService?.updateNotification(it, false)
                playbackService?.stopForegroundCompletely()
            }
        }
    }

    interface PlaybackUiCallback {
        fun onPrePlayback(songName: String)
        fun onPlaybackStarted(song: Song, isAbMode: Boolean)
        fun onPlaybackError(message: String)
    }

    fun playSong(song: Song, startPosition: Long = 0, startPaused: Boolean = false) {
        // --- 莱芙的“自动合体”魔法 (仿电脑端) ---
        val abPair = viewModel.findAbPair(song)
        if (abPair != null) {
            playAbSong(abPair.first, abPair.second, startPosition, startPaused)
            return
        }
        // ... (单曲逻辑保持不变喵)
        uiCallback.onPrePlayback("正在为您疯狂解码: ${song.displayName}...")

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
                NativeAudio.setLooping(viewModel.playMode.value == com.cpu.seamlessloopmobile.viewmodel.PlayMode.SINGLE_LOOP)

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
                    viewModel.updateSongInMemory(finalSong)
                }

                withContext(Dispatchers.Main) {
                    viewModel.setAbModePlaying(false)
                    viewModel.setPlaying(!startPaused)
                    uiCallback.onPlaybackStarted(finalSong, false)
                    if (!startPaused) {
                        playbackService?.updateNotification(finalSong, true)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uiCallback.onPlaybackError("无法打开音频文件喵...")
                }
            }
        }
    }

    private fun playAbSong(introSong: Song, loopSong: Song, startPosition: Long = 0, startPaused: Boolean = false) {
        uiCallback.onPrePlayback("正在为您合成 AB 循环: ${introSong.displayName} + ${loopSong.displayName}")

        coroutineScope.launch(Dispatchers.IO) {
            NativeAudio.stopAudioEngine()
            
            try {
                val uriA = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, introSong.mediaId)
                val uriB = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, loopSong.mediaId)
                
                context.contentResolver.openAssetFileDescriptor(uriA, "r")?.use { afdA ->
                    context.contentResolver.openAssetFileDescriptor(uriB, "r")?.use { afdB ->
                        val lenA = if (afdA.declaredLength < 0) afdA.length else afdA.declaredLength
                        val lenB = if (afdB.declaredLength < 0) afdB.length else afdB.declaredLength
                        
                        NativeAudio.startAbAudioEngine(
                            afdA.parcelFileDescriptor.fd, afdA.startOffset, lenA,
                            afdB.parcelFileDescriptor.fd, afdB.startOffset, lenB
                        )
                    }
                }

                if (introSong.loopEnd > introSong.totalSamples) {
                    NativeAudio.setLoopPoints(introSong.loopStart, introSong.loopEnd)
                }
                NativeAudio.setLooping(viewModel.playMode.value == com.cpu.seamlessloopmobile.viewmodel.PlayMode.SINGLE_LOOP)

                if (startPosition > 0) {
                    NativeAudio.seekTo(startPosition)
                }

                if (startPaused) {
                    NativeAudio.pauseAudioEngine()
                }

                val durationFrames = NativeAudio.getDuration()
                var finalIntroSong = introSong
                if (durationFrames > 0) {
                    finalIntroSong = introSong.copy(duration = durationFrames * 1000 / 44100)
                    viewModel.updateSongInMemory(finalIntroSong)
                }
                
                withContext(Dispatchers.Main) {
                    viewModel.setCurrentAbIntroSong(finalIntroSong)
                    viewModel.setAbModePlaying(true)
                    viewModel.setPlaying(!startPaused)
                    uiCallback.onPlaybackStarted(finalIntroSong, true)
                    if (!startPaused) {
                        playbackService?.updateNotification(finalIntroSong, true)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uiCallback.onPlaybackError("AB 播放失败: ${e.message}")
                }
            }
        }
    }
}
