package com.cpu.seamlessloopmobile.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Converts native frame positions into the millisecond unit expected by MediaSession.
 * Keeping this in one place protects system progress from frame/ms unit drift.
 */
internal fun framesToPlaybackPositionMs(positionFrames: Long, sampleRate: Int): Long {
    val safeSampleRate = if (sampleRate > 0) sampleRate else 44100
    return positionFrames * 1000L / safeSampleRate
}

/**
 * Lightweight owner for notification/lock-screen progress refreshes.
 *
 * It intentionally samples the current native position on a steady 250ms service tick instead
 * of trusting EVENT_LOOP_JUMP. That event reports decoder handover completion, which can happen
 * before the rewound audio position is actually heard by the user.
 */
internal class SystemMediaProgressSyncController(
    private val scope: CoroutineScope,
    private val intervalMs: Long = 250L,
    private val onSyncTick: () -> Unit
) {
    private var syncJob: Job? = null

    val isRunning: Boolean
        get() = syncJob?.isActive == true

    fun onPlaybackStateChanged(state: AudioPlayState) {
        if (state == AudioPlayState.PLAYING) {
            startIfNeeded()
        } else {
            stop()
        }
    }

    fun dispose() {
        stop()
    }

    private fun startIfNeeded() {
        if (syncJob?.isActive == true) return

        syncJob = scope.launch {
            // Sync once immediately after resume, then stay within the chosen 250ms budget.
            onSyncTick()
            while (isActive) {
                delay(intervalMs)
                onSyncTick()
            }
        }
    }

    private fun stop() {
        syncJob?.cancel()
        syncJob = null
    }
}
