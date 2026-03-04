package com.cpu.seamlessloopmobile.audio

import android.content.ComponentName
import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * 媒体浏览器助手机喵！
 * 负责帮 MainActivity 处理所有和 PlaybackService 打交道的脏活累活。
 */
class MediaBrowserHelper(
    private val context: Context,
    private val serviceClass: Class<*>
) {
    private lateinit var mediaBrowser: MediaBrowserCompat
    private var mediaController: MediaControllerCompat? = null

    private val _playbackState = MutableLiveData<PlaybackStateCompat?>()
    val playbackState: LiveData<PlaybackStateCompat?> = _playbackState

    private val _metadata = MutableLiveData<MediaMetadataCompat?>()
    val metadata: LiveData<MediaMetadataCompat?> = _metadata

    private val _isConnected = MutableLiveData<Boolean>(false)
    val isConnected: LiveData<Boolean> = _isConnected

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val token = mediaBrowser.sessionToken
            mediaController = MediaControllerCompat(context, token)
            mediaController?.registerCallback(controllerCallback)
            
            // 初始状态同步喵
            _playbackState.postValue(mediaController?.playbackState)
            _metadata.postValue(mediaController?.metadata)
            _isConnected.postValue(true)
        }

        override fun onConnectionSuspended() {
            _isConnected.postValue(false)
        }

        override fun onConnectionFailed() {
            _isConnected.postValue(false)
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            _playbackState.postValue(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            _metadata.postValue(metadata)
        }
    }

    fun connect() {
        mediaBrowser = MediaBrowserCompat(
            context,
            ComponentName(context, serviceClass),
            connectionCallbacks,
            null
        )
        mediaBrowser.connect()
    }

    fun disconnect() {
        mediaController?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()
        _isConnected.postValue(false)
    }

    fun getTransportControls(): MediaControllerCompat.TransportControls? {
        return mediaController?.transportControls
    }
    
    fun getController(): MediaControllerCompat? = mediaController
}
