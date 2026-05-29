package com.cpu.seamlessloopmobile.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.cpu.seamlessloopmobile.audio.MediaControlManager
import com.cpu.seamlessloopmobile.data.LoopDetectionRepository
import com.cpu.seamlessloopmobile.jni.LoopPoint
import com.cpu.seamlessloopmobile.jni.NativeAudio
import com.cpu.seamlessloopmobile.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 自动探测循环点状态及播放控制子管家喵！
 * 莱芙把弹窗状态外的自动探测、试听、以及 Loading 状态都在这里维护哦，干净利落！
 */
class LoopDetectionViewModel(
    private val repository: LoopDetectionRepository,
    private val mediaControlManager: MediaControlManager,
    private val scope: CoroutineScope
) : ViewModel() {

    private val _isDetectingLoop = MutableStateFlow(false)
    val isDetectingLoop: StateFlow<Boolean> = _isDetectingLoop

    private val _detectedLoopPoints = MutableStateFlow<List<LoopPoint>?>(null)
    val detectedLoopPoints: StateFlow<List<LoopPoint>?> = _detectedLoopPoints

    /**
     * 核心探测方法喵！
     * 优先查找缓存。若无缓存或强制重新计算，则交给底层 Repository 异步分析，并自动回写数据库缓存。
     * 计算成功后，在 Dispatchers.Main 线程中回调通知外部更新 UI。
     */
    fun detectLoopPoints(
        context: Context,
        song: Song,
        forceReanalyze: Boolean = false,
        onFinished: (Song, List<LoopPoint>, Int) -> Unit, // 带上 sampleRate 的安全回调
        onError: (String) -> Unit
    ) {
        // 1. 优先检查并读取缓存（非强制重探时）
        if (!forceReanalyze) {
            val cached = repository.parseCachedCandidates(song.loopCandidatesJson)
            if (!cached.isNullOrEmpty()) {
                _detectedLoopPoints.value = cached
                // 缓存命中时也异步获取 sampleRate，拒绝在主线程调用任何外部/JNI方法！
                scope.launch {
                    val sampleRate = withContext(Dispatchers.Default) {
                        NativeAudio.getSampleRate().let { if (it > 0) it else 44100 }
                    }
                    withContext(Dispatchers.Main) {
                        onFinished(song, cached, sampleRate)
                    }
                }
                return
            }
        }

        // 2. 无缓存或强制重新分析
        scope.launch {
            _isDetectingLoop.value = true
            _detectedLoopPoints.value = null

            try {
                // 异步探测（IO 线程在 repository.getLoopCandidates 中已被调度喵！）
                val results = repository.getLoopCandidates(song, forceReanalyze)
                
                _detectedLoopPoints.value = results

                // 异步获取 sampleRate 彻底避免 JNI 音频锁与 UI 线程锁争用！
                val sampleRate = withContext(Dispatchers.Default) {
                    NativeAudio.getSampleRate().let { if (it > 0) it else 44100 }
                }

                if (results.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onError("唔……CPU 大人，这首歌曲好像没有探测到合适的循环点呢 (´w｀)")
                    }
                } else {
                    // 将结果缓存到数据库中喵！
                    val updatedSong = repository.saveLoopCandidates(song, results)
                    withContext(Dispatchers.Main) {
                        onFinished(updatedSong, results, sampleRate)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LoopDetectionViewModel", "❌ 循环检测失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    onError("对不起，CPU 大人！探测过程中发生了一点小失误：${e.message} ㅠㅠ")
                }
            } finally {
                _isDetectingLoop.value = false
            }
        }
    }

    /**
     * 应用候选循环点，并跳转到循环终点前 3 秒进行衔接度试听播放喵！
     */
    fun applyAndListenToLoopFromEnd(
        song: Song,
        point: LoopPoint,
        onSongUpdated: (Song) -> Unit
    ) {
        scope.launch {
            // 1. 将 JNI 锁争用和计算任务彻底剥离到 Default 协程中执行！
            val seekTargetMs = withContext(Dispatchers.Default) {
                val sampleRate = NativeAudio.getSampleRate().toLong().let { if (it > 0) it else 44100L }
                val threeSecondsInSamples = sampleRate * 3
                val seekTargetSamples = (point.loopEnd - threeSecondsInSamples).coerceAtLeast(point.loopStart)
                seekTargetSamples * 1000L / sampleRate
            }

            // 2. 将数据库 UPDATE 写盘任务安全隔离在 IO 线程池中！
            val updatedSong = repository.updateSongLoopPoints(song, point.loopStart, point.loopEnd)
            
            // 3. 切回主线程更新内存 UI 状态
            withContext(Dispatchers.Main) {
                onSongUpdated(updatedSong)
            }

            // 4. 将具体的播放控制方法调度回 Main 线程执行，实现极致顺滑！
            withContext(Dispatchers.Main) {
                val bundle = android.os.Bundle().apply {
                    putLong("start_pos", point.loopStart)
                    putLong("end_pos", point.loopEnd)
                }
                mediaControlManager.sendCustomAction("APPLY_LOOP_POINTS", bundle)
                mediaControlManager.seekTo(seekTargetMs)
                mediaControlManager.play()
            }
        }
    }

    fun clearDetectedLoopPoints() {
        _detectedLoopPoints.value = null
        _isDetectingLoop.value = false
    }
}
