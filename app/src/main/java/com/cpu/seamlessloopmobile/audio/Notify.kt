package com.cpu.seamlessloopmobile.audio

import android.app.Notification
import com.cpu.seamlessloopmobile.model.Song

/**
 * 掌管通知栏的礼仪使者接口喵！
 * 负责定义在通知栏展示、更新、关闭音乐播放状态的契约。
 */
interface Notify {
    /**
     * 根据当前的歌曲和播放状态，构建出一个新鲜出炉的 Notification 喵！
     */
    fun createNotification(song: Song, isPlaying: Boolean): Notification

    /**
     * 更新并展示通知栏
     */
    fun show(song: Song, isPlaying: Boolean)

    /**
     * 释放通知栏资源
     */
    fun cancel()
}
