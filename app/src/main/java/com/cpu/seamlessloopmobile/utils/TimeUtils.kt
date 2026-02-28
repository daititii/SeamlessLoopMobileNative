package com.cpu.seamlessloopmobile.utils

object TimeUtils {
    private const val DEFAULT_SAMPLE_RATE = 44100L

    fun formatTime(frames: Long, sampleRate: Long): String {
        val safeSampleRate = if (sampleRate > 0) sampleRate else DEFAULT_SAMPLE_RATE
        val totalSeconds = frames / safeSampleRate
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun formatTimeMs(frames: Long, sampleRate: Long): String {
        val totalMillis = samplesToMillis(frames, sampleRate)
        val minutes = totalMillis / 60000
        val seconds = (totalMillis % 60000) / 1000
        val millis = totalMillis % 1000
        return String.format("%02d:%02d.%03d", minutes, seconds, millis)
    }

    fun samplesToMillis(samples: Long, sampleRate: Long): Long {
        val safeSampleRate = if (sampleRate > 0) sampleRate else DEFAULT_SAMPLE_RATE
        return (samples * 1000) / safeSampleRate
    }

    fun millisToSamples(millis: Long, sampleRate: Long): Long {
        val safeSampleRate = if (sampleRate > 0) sampleRate else DEFAULT_SAMPLE_RATE
        return (millis * safeSampleRate) / 1000
    }
}
