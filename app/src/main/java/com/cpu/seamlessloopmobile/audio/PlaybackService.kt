package com.cpu.seamlessloopmobile.audio

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import android.support.v4.media.session.MediaSessionCompat
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.data.MusicRepository
import com.cpu.seamlessloopmobile.db.AppDatabase
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.content.Intent

/**
 * 影子司令部：正式升级为官方“明星架构”——媒体浏览器服务喵！
 * 负责在后台默默维持音频引擎的生命线，并为系统和其他应用提供标准的控制接口。
 */
class PlaybackService : MediaBrowserServiceCompat() {

    private val binder = PlaybackBinder()
    private val serviceScope = MainScope()
    private lateinit var repository: MusicRepository
    var playbackManager: PlaybackManager? = null
    var mediaControlManager: MediaControlManager? = null

    inner class PlaybackBinder : android.os.Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        
        // 1. 初始化数据库与仓库喵
        val database = AppDatabase.getDatabase(this)
        repository = MusicRepository(database.songDao(), database.playlistDao())

        // 2. 准备官方管家喵
        mediaControlManager = MediaControlManager(
            context = this,
            playbackService = this
        )

        // 3. 关键：初始化大脑！
        mediaControlManager?.getSession()?.let { session ->
            sessionToken = session.sessionToken
            
            playbackManager = PlaybackManager(
                context = this,
                coroutineScope = serviceScope,
                repository = repository,
                mediaSession = session
            ).apply {
                onPlaybackStatusChanged = { isPlaying, song ->
                    if (song != null) {
                        updateNotification(song, isPlaying)
                    }
                }
            }
        }
    }

    // --- MediaBrowserServiceCompat 必须实现的接口喵 ---

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        // 放宽准入条件喵，不仅允许咱们自己，也允许系统或其他正经管家连进来，防止因为拒绝访问被强制杀进程喵！
        return BrowserRoot("root", null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        // 暂时不提供对外的媒体库浏览功能喵
        result.sendResult(null)
    }

    // 莱芙的兼容贴纸：虽然升级了架构，但为了不让 MainActivity 的旧连接断掉，我们还得支持 Binder 连接喵
    override fun onBind(intent: android.content.Intent?): android.os.IBinder? {
        return if (intent?.action == SERVICE_INTERFACE) {
            super.onBind(intent)
        } else {
            binder
        }
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
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(1, notification)
            }
        }
    }

    fun stopForegroundCompletely() {
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // 关掉大脑喵！
        mediaControlManager?.release()
    }
}
