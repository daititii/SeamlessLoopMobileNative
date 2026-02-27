package com.cpu.seamlessloopmobile.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.cpu.seamlessloopmobile.MainActivity
import com.cpu.seamlessloopmobile.R
import com.cpu.seamlessloopmobile.model.Song

/**
 * 掌管多媒体控制权的大管家喵！
 * 负责让大人在锁屏、通知栏甚至耳机线控上都能随心所欲控制播放。
 */
class MediaControlManager(
    private val context: Context,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit
) {
    private val mediaSession: MediaSessionCompat
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "seamless_loop_playback"

    fun getSession() = mediaSession

    init {
        createNotificationChannel()

        mediaSession = MediaSessionCompat(context, "SeamlessLoopMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = onPlayPause()
                override fun onPause() = onPlayPause()
                override fun onSkipToNext() = onNext()
                override fun onSkipToPrevious() = onPrevious()
                // 我们不再拦截 onMediaButtonEvent，让 Service 统一分发喵！
            })
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
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

    fun updatePlaybackState(song: Song, isPlaying: Boolean) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.displayName ?: song.fileName)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist ?: "Unknown Artist")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
            .build()
        mediaSession.setMetadata(metadata)

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                1.0f
            )
        mediaSession.setPlaybackState(stateBuilder.build())

        val notification = createNotification(song, isPlaying)
        notificationManager.notify(1, notification)
    }

    fun createNotification(song: Song, isPlaying: Boolean): Notification {
        // ... (保持之前的 createNotification 逻辑，但调整 PendingIntent 喵)
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

    fun release() {
        mediaSession.release()
        notificationManager.cancel(1)
    }
}
