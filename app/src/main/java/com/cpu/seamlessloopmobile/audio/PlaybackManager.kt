package com.cpu.seamlessloopmobile.audio

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.cpu.seamlessloopmobile.jni.NativeAudio
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao
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
    private val songDao: SongDao,
    private val viewModel: MainViewModel,
    private val uiCallback: PlaybackUiCallback
) {

    interface PlaybackUiCallback {
        fun onPrePlayback(songName: String)
        fun onPlaybackStarted(song: Song, isAbMode: Boolean)
        fun onPlaybackError(message: String)
    }

    fun playSong(song: Song) {
        // --- 莱芙的“自动合体”魔法 (仿电脑端) ---
        val abPair = viewModel.findAbPair(song)
        if (abPair != null) {
            playAbSong(abPair.first, abPair.second)
            return
        }

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

                val durationFrames = NativeAudio.getDuration()
                var finalSong = song
                if (durationFrames > 0) {
                    finalSong = song.copy(
                        duration = durationFrames * 1000 / 44100,
                        totalSamples = if (song.totalSamples == 0L) durationFrames else song.totalSamples 
                    )
                    if (song.totalSamples == 0L && finalSong.id > 0) {
                        // 如果之前没量过长度，才硬写入数据库更新它喵！
                        songDao.updateSong(finalSong) 
                    }
                    viewModel.updateSongInMemory(finalSong)
                }

                withContext(Dispatchers.Main) {
                    viewModel.setAbModePlaying(false)
                    viewModel.setPlaying(true)
                    uiCallback.onPlaybackStarted(finalSong, false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uiCallback.onPlaybackError("无法打开音频文件喵...")
                }
            }
        }
    }

    private fun playAbSong(introSong: Song, loopSong: Song) {
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

                // AB 模式下，底层 loadAbAudioSource 已经默认设置了 [lenA, lenA + lenB] 的完美循环喵！
                // 除非大人手动在弹窗里设置过特殊的跨越点，否则我们不应该用单文件的 loopEnd 去覆盖它喵。
                // 如果大人之前保存过 AB 循环点（通常 loopEnd 会超过 A 段长度），我们才应用。
                if (introSong.loopEnd > introSong.totalSamples) {
                    NativeAudio.setLoopPoints(introSong.loopStart, introSong.loopEnd)
                }
                NativeAudio.setLooping(viewModel.playMode.value == com.cpu.seamlessloopmobile.viewmodel.PlayMode.SINGLE_LOOP)

                val durationFrames = NativeAudio.getDuration()
                var finalIntroSong = introSong
                if (durationFrames > 0) {
                    // AB 模式下，底层返回的是 A+B 的总长度！
                    // 我们绝对不能把它写进 A 的 totalSamples 里存进数据库，那会破坏 A 自己的指纹并触发冲突喵！
                    // 只要更新一下毫秒 duration 喂给进度条就可以了喵！
                    finalIntroSong = introSong.copy(duration = durationFrames * 1000 / 44100)
                    viewModel.updateSongInMemory(finalIntroSong)
                }
                
                withContext(Dispatchers.Main) {
                    viewModel.setCurrentAbIntroSong(finalIntroSong)
                    viewModel.setAbModePlaying(true)
                    viewModel.setPlaying(true)
                    uiCallback.onPlaybackStarted(finalIntroSong, true)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    uiCallback.onPlaybackError("AB 播放失败: ${e.message}")
                }
            }
        }
    }
}
