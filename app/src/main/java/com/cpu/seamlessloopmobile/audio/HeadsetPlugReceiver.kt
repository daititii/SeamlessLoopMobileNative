package com.cpu.seamlessloopmobile.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * 耳机插拔与外放冲突检测器喵！
 * 负责在耳机被拔掉时，迅速停止播放，防止吵到别人。
 */
class HeadsetPlugReceiver(
    private val callbacks: Callbacks
) : BroadcastReceiver() {

    interface Callbacks {
        fun onHeadsetPlugged()
        fun onHeadsetUnplugged()
        fun onBecomingNoisy()
    }

    private var registered = false

    fun register(context: Context) {
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(Intent.ACTION_HEADSET_PLUG)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(this, filter)
            }
            registered = true
            Log.d("HeadsetPlugReceiver", "✅ 耳机状态监听器已启动喵！")
        }
    }

    fun unregister(context: Context) {
        if (registered) {
            context.unregisterReceiver(this)
            registered = false
            Log.d("HeadsetPlugReceiver", "💤 耳机状态监听器已休息喵~")
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                Log.d("HeadsetPlugReceiver", "⚠️ 即将外放，触发 BecomingNoisy 喵！")
                callbacks.onBecomingNoisy()
            }
            Intent.ACTION_HEADSET_PLUG -> {
                val state = intent.getIntExtra("state", -1)
                when (state) {
                    0 -> {
                        Log.d("HeadsetPlugReceiver", "🔌 耳机已拔出喵！")
                        callbacks.onHeadsetUnplugged()
                    }
                    1 -> {
                        Log.d("HeadsetPlugReceiver", "🎧 耳机已插入喵！")
                        callbacks.onHeadsetPlugged()
                    }
                }
            }
        }
    }
}
