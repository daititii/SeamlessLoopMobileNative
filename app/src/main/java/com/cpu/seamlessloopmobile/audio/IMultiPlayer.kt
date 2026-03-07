package com.cpu.seamlessloopmobile.audio

import com.cpu.seamlessloopmobile.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * APlayer 风格的统一播放器接口喵！
 * 它是对 Playback 接口的更高层次抽象，旨在屏蔽底层引擎（PCM/ExoPlayer）的差异。
 */
interface IMultiPlayer {
    /** 当前播放状态流 */
    val state: StateFlow<AudioPlayState>
    
    /** 当前正在播放的歌曲信息 */
    val currentSong: Song?
    
    /** 当前播放进度 (ms) */
    val position: Long
    
    /** 总时长 (ms) */
    val duration: Long
    
    /** 是否正在播放 */
    val isPlaying: Boolean

    /** 开始播放特定歌曲 */
    fun play(song: Song, startPos: Long = 0, startPaused: Boolean = false)
    
    /** 暂停播放 */
    fun pause()
    
    /** 恢复播放 */
    fun resume()
    
    /** 停止播放并释放部分资源 */
    fun stop()
    
    /** 跳进度 */
    fun seekTo(pos: Long)

    /** 设置循环模式 (由具体的逻辑决定是单曲还是列表循环喵) */
    fun setLooping(looping: Boolean)
    
    /** 资源释放 (告老还乡喵) */
    fun release()
}
