package com.cpu.seamlessloopmobile.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * 焦点卫兵：专门负责与系统抢占或归还音频焦点喵！
 */
class AudioFocusManager(context: Context) {

    interface Callbacks {
        fun onFocusGained()
        fun onFocusLost()
        fun onFocusLostTransient()
        fun onFocusDuck()
    }

    private var callbacks: Callbacks? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> callbacks?.onFocusGained()
            AudioManager.AUDIOFOCUS_LOSS -> callbacks?.onFocusLost()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> callbacks?.onFocusLostTransient()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> callbacks?.onFocusDuck()
        }
    }

    private var focusRequest: AudioFocusRequest? = null

    /**
     * 申请音频焦点
     * @return 是否成功抢到焦点喵
     */
    fun requestFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (focusRequest == null) {
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
            }
            audioManager.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * 主动放弃焦点
     */
    fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    fun setCallbacks(callbacks: Callbacks?) {
        this.callbacks = callbacks
    }
}
