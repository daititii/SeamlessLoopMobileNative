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

                val durationFrames = NativeAudio.getDuration()
                var finalSong = song
                if (durationFrames > 0) {
                    finalSong = song.copy(totalSamples = durationFrames)
                    songDao.insertOrUpdateSong(finalSong)
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

                if (introSong.loopEnd > 0) {
                    NativeAudio.setLoopPoints(introSong.loopStart, introSong.loopEnd)
                }

                val durationFrames = NativeAudio.getDuration()
                var finalIntroSong = introSong
                if (durationFrames > 0) {
                    finalIntroSong = introSong.copy(totalSamples = durationFrames)
                    songDao.insertOrUpdateSong(finalIntroSong)
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
