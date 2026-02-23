package com.cpu.seamlessloopmobile.utils

object TimeUtils {
    fun formatTime(frames: Long, sampleRate: Long): String {
        val safeSampleRate = if (sampleRate > 0) sampleRate else 44100L
        val totalSeconds = frames / safeSampleRate
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun formatTimeMs(frames: Long, sampleRate: Long): String {
        val safeSampleRate = if (sampleRate > 0) sampleRate else 44100L
        val totalMillis = (frames * 1000) / safeSampleRate
        val minutes = totalMillis / 60000
        val seconds = (totalMillis % 60000) / 1000
        val millis = totalMillis % 1000
        return String.format("%02d:%02d.%03d", minutes, seconds, millis)
    }
}
