package com.cpu.seamlessloopmobile.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cpu.seamlessloopmobile.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 核心媒体运营中心喵！
 * 负责统一调度 MediaBrowser、MediaController 和后台 Service。
 * 它会把复杂的媒体会话逻辑封装成 UI 只需要订阅的数据流喵。
 */
class MediaControlManager(private val context: Context) {

    private var mediaBrowser: MediaBrowserCompat? = null
    private var mediaController: MediaControllerCompat? = null
    private var playbackService: PlaybackService? = null

    private val _playbackState = MutableStateFlow<PlaybackStateCompat?>(null)
    val playbackState = _playbackState.asStateFlow()

    private val _metadata = MutableStateFlow<MediaMetadataCompat?>(null)
    val metadata = _metadata.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration = _totalDuration.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _sessionEvent = MutableSharedFlow<Pair<String, Bundle?>>(extraBufferCapacity = 1)
    val sessionEvent = _sessionEvent.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionUpdateJob: Job? = null

    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val token = mediaBrowser?.sessionToken ?: return
            mediaController = MediaControllerCompat(context, token)
            mediaController?.registerCallback(controllerCallback)
            
            _playbackState.value = mediaController?.playbackState
            _metadata.value = mediaController?.metadata
            _isConnected.value = true
            
            startPositionUpdates()
        }

        override fun onConnectionSuspended() {
            _isConnected.value = false
            stopPositionUpdates()
        }

        override fun onConnectionFailed() {
            _isConnected.value = false
            stopPositionUpdates()
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            _playbackState.value = state
            if (state?.state == PlaybackStateCompat.STATE_PLAYING) {
                startPositionUpdates()
            } else {
                stopPositionUpdates()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            _metadata.value = metadata
            _totalDuration.value = playbackService?.playbackManager?.duration ?: 0L
        }

        override fun onSessionEvent(event: String?, extras: Bundle?) {
            event?.let { 
                scope.launch { _sessionEvent.emit(it to extras) }
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlaybackService.PlaybackBinder
            playbackService = binder.getService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
        }
    }

    fun connect() {
        if (mediaBrowser == null) {
            mediaBrowser = MediaBrowserCompat(
                context,
                ComponentName(context, PlaybackService::class.java),
                connectionCallback,
                null
            )
        }
        mediaBrowser?.connect()
        
        val intent = Intent(context, PlaybackService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun disconnect() {
        mediaBrowser?.disconnect()
        mediaController?.unregisterCallback(controllerCallback)
        try {
            context.unbindService(serviceConnection)
        } catch (e: Exception) { }
        _isConnected.value = false
        stopPositionUpdates()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                playbackService?.playbackManager?.let { pm ->
                    _currentPosition.value = pm.position
                    _totalDuration.value = pm.duration
                }
                delay(100)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    // --- 遥控器指令集喵 ---

    fun play() = mediaController?.transportControls?.play()
    fun pause() = mediaController?.transportControls?.pause()
    fun skipToNext() = mediaController?.transportControls?.skipToNext()
    fun skipToPrevious() = mediaController?.transportControls?.skipToPrevious()
    fun seekTo(pos: Long) = mediaController?.transportControls?.seekTo(pos)

    fun playFromMediaId(mediaId: String, extras: Bundle?) {
        mediaController?.transportControls?.playFromMediaId(mediaId, extras)
    }

    fun sendCustomAction(action: String, args: Bundle?) {
        mediaController?.transportControls?.sendCustomAction(action, args)
    }
}
