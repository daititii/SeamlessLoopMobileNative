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
    private val playbackService: PlaybackService
) {
    private val mediaSession: MediaSessionCompat
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notify: Notify

    fun getSession() = mediaSession

    init {
        mediaSession = MediaSessionCompat(context, "SeamlessLoopMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    playbackService.playbackManager?.resume()
                }
                override fun onPause() {
                    playbackService.playbackManager?.pause()
                }
                override fun onStop() {
                    playbackService.playbackManager?.stop()
                    playbackService.stopForegroundCompletely()
                }
                override fun onSkipToNext() {
                    // TODO: 真正的切歌逻辑需要 Service 持有当前的 Playlist 喵！
                }
                override fun onSkipToPrevious() {
                }
                override fun onPlayFromMediaId(mediaId: String?, extras: android.os.Bundle?) {
                    val idLong = mediaId?.toLongOrNull() ?: return
                    playbackService.playbackManager?.playFromMediaId(idLong)
                }
                override fun onSeekTo(pos: Long) {
                    playbackService.playbackManager?.seekTo(pos)
                }
                override fun onCustomAction(action: String?, extras: android.os.Bundle?) {
                    if (action == PlaybackCommand.ACTION_SET_PLAY_MODE) {
                        val modeOrdinal = extras?.getInt(PlaybackCommand.EXTRA_PLAY_MODE) ?: return
                        val isSingleLoop = modeOrdinal == 1 // com.cpu.seamlessloopmobile.viewmodel.PlayMode.SINGLE_LOOP.ordinal
                        playbackService.playbackManager?.setLooping(isSingleLoop)
                    }
                }
            })
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
        }
        
        notify = NotifyImpl(context, mediaSession, notificationManager)
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

        notify.show(song, isPlaying)
    }

    fun release() {
        mediaSession.release()
        notify.cancel()
    }
}
