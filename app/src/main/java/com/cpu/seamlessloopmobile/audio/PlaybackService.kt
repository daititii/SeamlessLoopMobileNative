package com.cpu.seamlessloopmobile.audio

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import androidx.media.session.MediaButtonReceiver
import com.cpu.seamlessloopmobile.model.Song

/**
 * 影子司令部：负责在后台默默维持音频引擎的生命线喵！
 */
class PlaybackService : Service() {

    private val binder = PlaybackBinder()
    var mediaControlManager: MediaControlManager? = null
    
    // 专门给 PlaybackManager 实地调用的回调钩子喵
    var onMediaAction: ((String) -> Unit)? = null

    inner class PlaybackBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        // 服务启动时就准备好管家喵！
        mediaControlManager = MediaControlManager(
            context = this,
            onPlayPause = { onMediaAction?.invoke("PLAY_PAUSE") },
            onNext = { onMediaAction?.invoke("NEXT") },
            onPrevious = { onMediaAction?.invoke("PREVIOUS") }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 让系统发来的按键指令直接送达管家手里喵！
        mediaControlManager?.getSession()?.let { session ->
            MediaButtonReceiver.handleIntent(session, intent)
        }
        return START_STICKY
    }

    fun updateNotification(song: Song, isPlaying: Boolean) {
        mediaControlManager?.updatePlaybackState(song, isPlaying)
        
        // 关键喵：在这里打报告，告诉系统我们是正经的前台播放服务！
        val notification = mediaControlManager?.createNotification(song, isPlaying)
        if (notification != null) {
            startForeground(1, notification)
        }
    }

    fun stopForegroundCompletely() {
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaControlManager?.release()
    }
}
