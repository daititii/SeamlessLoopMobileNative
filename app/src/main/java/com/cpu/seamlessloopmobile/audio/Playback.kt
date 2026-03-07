package com.cpu.seamlessloopmobile.audio

import com.cpu.seamlessloopmobile.model.Song

/**
 * 播放器行为的灵魂契约喵！
 * 无论是 NDK 引擎还是系统 MediaPlayer，只要签了这个契约，就能被 Service 统一调度。
 */
interface Playback {
    /**
     * 当前是否正在放歌喵？
     */
    val isPlaying: Boolean

    /**
     * 正在伺候哪首曲子？
     */
    val currentSong: Song?

    /**
     * 当前播放进度 (以帧数为单位喵，保证精准度)
     */
    val position: Long

    /**
     * 总时长 (以帧数为单位喵)
     */
    val duration: Long

    /**
     * 采样率 (通常是 44100 喵)
     */
    val sampleRate: Int

    /**
     * 播放器的当前状态流喵，供 UI 订阅
     */
    val state: kotlinx.coroutines.flow.StateFlow<AudioPlayState>

    /**
     * 状态改变时的联络暗号 (保留旧的回调以便兼容，但建议改用 state 流)
     */
    var onPlaybackStatusChanged: ((isPlaying: Boolean, currentSong: Song?) -> Unit)?

    /**
     * 出错时的哭诉渠道
     */
    var onPlaybackError: ((String) -> Unit)?

    /**
     * 开始放歌！
     */
    fun playSong(song: Song, startPosition: Long = 0, startPaused: Boolean = false, isSingleLoop: Boolean = true)

    /**
     * 歇一会喵
     */
    fun pause()

    /**
     * 继续干活！
     */
    fun resume()

    /**
     * 彻底停下
     */
    fun stop()

    /**
     * 释放资源，告老还乡
     */
    fun release()

    /**
     * 跳到指定的时刻
     */
    fun seekTo(position: Long)

    /**
     * 设置是否循环播放喵
     */
    fun setLooping(looping: Boolean)

}
