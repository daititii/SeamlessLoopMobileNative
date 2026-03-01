package com.cpu.seamlessloopmobile.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.cpu.seamlessloopmobile.MainActivity
import com.cpu.seamlessloopmobile.model.Song

/**
 * Android 通用版本的通知栏实现，干活非常麻利喵！
 */
class NotifyImpl(
    private val context: Context,
    private val mediaSession: MediaSessionCompat,
    private val notificationManager: NotificationManager
) : Notify {

    private val channelId = "seamless_loop_playback"
    private val notificationId = 1

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "播放控制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "允许在通知栏控制音乐播放喵！"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC // 显式允许锁屏可见喵
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun createNotification(song: Song, isPlaying: Boolean): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "暂停",
                MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play, "播放",
                MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_PLAY)
            )
        }

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song.displayName ?: song.fileName)
            .setContentText(song.artist ?: "Unknown Artist")
            .setContentIntent(pendingIntent)
            .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW) // 避开吵闹的弹窗喵
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying) // 播放时不能划掉喵
            .addAction(NotificationCompat.Action(
                android.R.drawable.ic_media_previous, "上一首",
                MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            ))
            .addAction(playPauseAction)
            .addAction(NotificationCompat.Action(
                android.R.drawable.ic_media_next, "下一首",
                MediaButtonReceiver.buildMediaButtonPendingIntent(context, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            ))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    override fun show(song: Song, isPlaying: Boolean) {
        val notification = createNotification(song, isPlaying)
        notificationManager.notify(notificationId, notification)
    }

    override fun cancel() {
        notificationManager.cancel(notificationId)
    }
}
