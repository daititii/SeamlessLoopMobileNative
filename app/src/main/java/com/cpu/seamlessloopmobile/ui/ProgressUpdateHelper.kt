package com.cpu.seamlessloopmobile.ui

import android.widget.SeekBar
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import com.cpu.seamlessloopmobile.audio.PlaybackService
import com.cpu.seamlessloopmobile.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 进度条小管家喵！
 * 专门负责盯着播放进度，并刷新 UI 上的进度条和时间文字。
 */
class ProgressUpdateHelper(
    private val seekBar: SeekBar,
    private val tvCurrentTime: TextView,
    private val tvTotalTime: TextView,
    private val coroutineScope: LifecycleCoroutineScope,
    private val getPlaybackService: () -> PlaybackService?,
    private val isUserSeeking: () -> Boolean,
    private val onFileEnd: () -> Unit
) {
    private var updateJob: Job? = null

    fun start() {
        updateJob?.cancel()
        updateJob = coroutineScope.launch(Dispatchers.Main) {
            var lastObservedFrame = -1L
            var frameStallCount = 0
            
            while (true) {
                if (!isUserSeeking()) {
                    val service = getPlaybackService()
                    val currentFrame = service?.playbackManager?.position ?: 0L
                    val totalFrames = service?.playbackManager?.duration ?: 0L
                    val sampleRate = service?.playbackManager?.sampleRate?.toLong() ?: 44100L
                    
                    if (totalFrames > 0) {
                        val maxValue = totalFrames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        val progressValue = currentFrame.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        
                        seekBar.max = maxValue
                        seekBar.progress = progressValue
                        
                        tvCurrentTime.text = TimeUtils.formatTime(currentFrame, sampleRate)
                        tvTotalTime.text = TimeUtils.formatTime(totalFrames, sampleRate)

                        if (service?.playbackManager?.isPlaying == true) {
                            val endThreshold = (sampleRate / 8).coerceAtLeast(1024L)
                            
                            if (currentFrame == lastObservedFrame && (totalFrames - currentFrame) < sampleRate * 2) {
                                frameStallCount++
                            } else {
                                frameStallCount = 0
                            }
                            lastObservedFrame = currentFrame

                            if (currentFrame >= totalFrames - endThreshold || frameStallCount >= 2) {
                                frameStallCount = 0
                                lastObservedFrame = -1L
                                onFileEnd()
                            }
                        }
                    }
                }
                delay(50)
            }
        }
    }

    fun stop() {
        updateJob?.cancel()
        updateJob = null
    }
}
