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

    fun updateMediaSessionState(song: Song, isPlaying: Boolean) {
        val metadata = android.support.v4.media.MediaMetadataCompat.Builder()
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, song.displayName ?: song.fileName)
            .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist ?: "Unknown Artist")
            .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
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
                NativeAudio.getCurrentPosition(), // 实时同步进度喵！
                1.0f
            )
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
            val abPair = withContext(Dispatchers.IO) {
                val allSongs = repository.getAllSongs() // 简单扫一下全库喵
                repository.findAbPair(song, allSongs)
            }
            if (abPair != null) {
                playAbSong(abPair.first, abPair.second, startPosition, startPaused, isSingleLoop)
                return@launch
            }

            actuallyPlaySong(song, startPosition, startPaused, isSingleLoop)
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
                NativeAudio.setLooping(isSingleLoop)

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
                }
                
                withContext(Dispatchers.Main) {
                    updateMediaSessionState(finalIntroSong, !startPaused)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onPlaybackError?.invoke("AB 播放失败: ${e.message}")
                }
            }
        }
    }
}
