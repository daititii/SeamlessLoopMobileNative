package com.cpu.seamlessloopmobile.dialogs

import android.app.Activity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import com.cpu.seamlessloopmobile.R
import com.cpu.seamlessloopmobile.jni.NativeAudio
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.utils.TimeUtils
import com.cpu.seamlessloopmobile.viewmodel.MainViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoopSettingsDialog(
    private val activity: Activity,
    private val originalSong: Song,
    private val viewModel: MainViewModel,
    private val coroutineScope: CoroutineScope,
    private val onPlayPauseClick: () -> Unit,
    private val onPrevClick: () -> Unit,
    private val onNextClick: () -> Unit
) {
    private var song = originalSong
    private val dialog = BottomSheetDialog(activity)
    private var dialogUpdateJob: Job? = null
    private var isDialogSeeking = false

    fun show() {
        val view = activity.layoutInflater.inflate(R.layout.dialog_loop_controls, null)
        dialog.setContentView(view)

        val etStartSamples = view.findViewById<EditText>(R.id.et_loop_start_samples)
        val etStartTime = view.findViewById<EditText>(R.id.et_loop_start_time)
        val etEndSamples = view.findViewById<EditText>(R.id.et_loop_end_samples)
        val etEndTime = view.findViewById<EditText>(R.id.et_loop_end_time)

        fun updateDisplay() {
            val sampleRate = NativeAudio.getSampleRate().toLong()
            val safeSampleRate = if (sampleRate > 0) sampleRate else 44100L
            
            if (!etStartSamples.hasFocus()) etStartSamples.setText(song.loopStart.toString())
            if (!etEndSamples.hasFocus()) etEndSamples.setText(song.loopEnd.toString())
            
            val startSec = song.loopStart.toDouble() / safeSampleRate
            val endSec = song.loopEnd.toDouble() / safeSampleRate
            if (!etStartTime.hasFocus()) etStartTime.setText(String.format("%.3f", startSec))
            if (!etEndTime.hasFocus()) etEndTime.setText(String.format("%.3f", endSec))
        }

        fun applyUpdate(start: Long, end: Long) {
            viewModel.updateSongLoopPoints(song, start, end)
            song = song.copy(loopStart = start, loopEnd = end)
            NativeAudio.setLoopPoints(start, end)
            updateDisplay()
        }

        updateDisplay()

        // --- 手动输入监听 ---
        val onSamplesChanged = { isStart: Boolean, text: String ->
            val value = text.toLongOrNull() ?: 0L
            if (isStart) applyUpdate(value, song.loopEnd)
            else applyUpdate(song.loopStart, value)
        }
        
        val onTimeChanged = { isStart: Boolean, text: String ->
            val sec = text.toDoubleOrNull() ?: 0.0
            val sampleRate = NativeAudio.getSampleRate()
            val value = (sec * sampleRate).toLong()
            if (isStart) applyUpdate(value, song.loopEnd)
            else applyUpdate(song.loopStart, value)
        }

        val focusListener = View.OnFocusChangeListener { v, hasFocus ->
            if (!hasFocus && v is EditText) {
                when (v.id) {
                    R.id.et_loop_start_samples -> onSamplesChanged(true, v.text.toString())
                    R.id.et_loop_end_samples -> onSamplesChanged(false, v.text.toString())
                    R.id.et_loop_start_time -> onTimeChanged(true, v.text.toString())
                    R.id.et_loop_end_time -> onTimeChanged(false, v.text.toString())
                }
            }
        }
        etStartSamples.onFocusChangeListener = focusListener
        etEndSamples.onFocusChangeListener = focusListener
        etStartTime.onFocusChangeListener = focusListener
        etEndTime.onFocusChangeListener = focusListener

        val editorListener = TextView.OnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                when (v.id) {
                    R.id.et_loop_start_samples -> onSamplesChanged(true, v.text.toString())
                    R.id.et_loop_end_samples -> onSamplesChanged(false, v.text.toString())
                    R.id.et_loop_start_time -> onTimeChanged(true, v.text.toString())
                    R.id.et_loop_end_time -> onTimeChanged(false, v.text.toString())
                }
                v.clearFocus()
                true
            } else false
        }
        etStartSamples.setOnEditorActionListener(editorListener)
        etEndSamples.setOnEditorActionListener(editorListener)
        etStartTime.setOnEditorActionListener(editorListener)
        etEndTime.setOnEditorActionListener(editorListener)

        // --- A 点控制 ---
        val adjustA = { deltaMs: Double ->
            val sampleRate = NativeAudio.getSampleRate()
            val deltaSamples = (sampleRate * deltaMs / 1000.0).toLong()
            val dur = NativeAudio.getDuration()
            val newStart = (song.loopStart + deltaSamples).coerceIn(0, if (song.loopEnd > 0) song.loopEnd else dur)
            applyUpdate(newStart, song.loopEnd)
        }

        view.findViewById<Button>(R.id.btn_a_min).setOnClickListener { applyUpdate(0, song.loopEnd) }
        view.findViewById<Button>(R.id.btn_a_max).setOnClickListener { applyUpdate(song.loopEnd.coerceAtLeast(0), song.loopEnd) }
        view.findViewById<Button>(R.id.btn_a_set_current).setOnClickListener { 
            val curr = NativeAudio.getCurrentPosition()
            if (curr < song.loopEnd || song.loopEnd == 0L) applyUpdate(curr, song.loopEnd)
        }
        view.findViewById<Button>(R.id.btn_a_dec_5s).setOnClickListener { adjustA(-5000.0) }
        view.findViewById<Button>(R.id.btn_a_dec_1s).setOnClickListener { adjustA(-1000.0) }
        view.findViewById<Button>(R.id.btn_a_inc_1s).setOnClickListener { adjustA(1000.0) }
        view.findViewById<Button>(R.id.btn_a_inc_5s).setOnClickListener { adjustA(5000.0) }
        view.findViewById<Button>(R.id.btn_a_dec_01s).setOnClickListener { adjustA(-100.0) }
        view.findViewById<Button>(R.id.btn_a_dec_001s).setOnClickListener { adjustA(-10.0) }
        view.findViewById<Button>(R.id.btn_a_inc_001s).setOnClickListener { adjustA(10.0) }
        view.findViewById<Button>(R.id.btn_a_inc_01s).setOnClickListener { adjustA(100.0) }

        // --- B 点控制 ---
        val adjustB = { deltaMs: Double ->
            val sampleRate = NativeAudio.getSampleRate()
            val deltaSamples = (sampleRate * deltaMs / 1000.0).toLong()
            val dur = NativeAudio.getDuration()
            val newEnd = (song.loopEnd + deltaSamples).coerceIn(song.loopStart, dur)
            applyUpdate(song.loopStart, newEnd)
        }

        view.findViewById<Button>(R.id.btn_b_min).setOnClickListener { applyUpdate(song.loopStart, song.loopStart) }
        view.findViewById<Button>(R.id.btn_b_max).setOnClickListener { applyUpdate(song.loopStart, NativeAudio.getDuration()) }
        view.findViewById<Button>(R.id.btn_b_set_current).setOnClickListener { 
            val curr = NativeAudio.getCurrentPosition()
            if (curr > song.loopStart) applyUpdate(song.loopStart, curr)
        }
        view.findViewById<Button>(R.id.btn_b_dec_5s).setOnClickListener { adjustB(-5000.0) }
        view.findViewById<Button>(R.id.btn_b_dec_1s).setOnClickListener { adjustB(-1000.0) }
        view.findViewById<Button>(R.id.btn_b_inc_1s).setOnClickListener { adjustB(1000.0) }
        view.findViewById<Button>(R.id.btn_b_inc_5s).setOnClickListener { adjustB(5000.0) }
        view.findViewById<Button>(R.id.btn_b_dec_01s).setOnClickListener { adjustB(-100.0) }
        view.findViewById<Button>(R.id.btn_b_dec_001s).setOnClickListener { adjustB(-10.0) }
        view.findViewById<Button>(R.id.btn_b_inc_001s).setOnClickListener { adjustB(10.0) }
        view.findViewById<Button>(R.id.btn_b_inc_01s).setOnClickListener { adjustB(100.0) }

        // --- 播放控制 ---
        val btnPlayPause = view.findViewById<ImageButton>(R.id.btn_dialog_play_pause)
        val btnPrev = view.findViewById<ImageButton>(R.id.btn_dialog_prev)
        val btnNext = view.findViewById<ImageButton>(R.id.btn_dialog_next)

        fun updatePlayPauseIcon() {
            val isPlaying = viewModel.isPlaying.value ?: false
            btnPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }
        updatePlayPauseIcon()

        btnPlayPause.setOnClickListener { onPlayPauseClick(); updatePlayPauseIcon() }
        btnPrev.setOnClickListener { onPrevClick() }
        btnNext.setOnClickListener { onNextClick() }

        // --- 进度条 ---
        val sbProgress = view.findViewById<SeekBar>(R.id.sb_dialog_progress)
        val tvCurrentTimeView = view.findViewById<TextView>(R.id.tv_dialog_current_time)
        val tvTotalTimeView = view.findViewById<TextView>(R.id.tv_dialog_total_time)
        
        dialogUpdateJob = coroutineScope.launch(Dispatchers.Main) {
            while (true) {
                if (!isDialogSeeking) {
                    val currentFrame = NativeAudio.getCurrentPosition()
                    val totalFrames = NativeAudio.getDuration()
                    val sampleRate = NativeAudio.getSampleRate().toLong()
                    
                    if (totalFrames > 0) {
                        sbProgress.max = totalFrames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        sbProgress.progress = currentFrame.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        tvCurrentTimeView.text = TimeUtils.formatTime(currentFrame, sampleRate)
                        tvTotalTimeView.text = TimeUtils.formatTime(totalFrames, sampleRate)
                        updatePlayPauseIcon()
                    }
                }
                delay(50)
            }
        }
        
        sbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { 
                if(f) tvCurrentTimeView.text = TimeUtils.formatTime(p.toLong(), NativeAudio.getSampleRate().toLong()) 
            }
            override fun onStartTrackingTouch(s: SeekBar?) { isDialogSeeking = true }
            override fun onStopTrackingTouch(s: SeekBar?) { s?.let { NativeAudio.seekTo(it.progress.toLong()); isDialogSeeking = false } }
        })
        
        fun forceSyncAll() {
            if (etStartTime.hasFocus()) onTimeChanged(true, etStartTime.text.toString())
            else onSamplesChanged(true, etStartSamples.text.toString())
            if (etEndTime.hasFocus()) onTimeChanged(false, etEndTime.text.toString())
            else onSamplesChanged(false, etEndSamples.text.toString())
        }

        val owner = activity as? androidx.lifecycle.LifecycleOwner
        val observer = androidx.lifecycle.Observer<Int> { index ->
            val pl = viewModel.currentPlaylist.value ?: emptyList()
            if (index in pl.indices) {
                val newSong = pl[index]
                if (song.filePath != newSong.filePath) {
                    song = newSong
                    // 切换歌曲时，清除焦点以强制刷新UI显示新歌曲的数值喵
                    etStartSamples.clearFocus()
                    etEndSamples.clearFocus()
                    etStartTime.clearFocus()
                    etEndTime.clearFocus()
                    updateDisplay()
                }
            }
        }
        owner?.let { viewModel.currentSongIndex.observe(it, observer) }

        dialog.setOnDismissListener { 
            dialogUpdateJob?.cancel() 
            owner?.let { viewModel.currentSongIndex.removeObserver(observer) }
        }
        
        view.findViewById<Button>(R.id.btn_audition).setOnClickListener {
            forceSyncAll()
            val sampleRate = NativeAudio.getSampleRate().toLong()
            val safeSampleRate = if (sampleRate > 0) sampleRate else 44100L
            val totalDur = NativeAudio.getDuration()
            val actualEnd = if (song.loopEnd > 0) song.loopEnd else totalDur
            val seekPos = (actualEnd - (safeSampleRate * 3)).coerceIn(0, actualEnd)
            NativeAudio.seekTo(seekPos)
            if (!(viewModel.isPlaying.value ?: false)) {
                onPlayPauseClick()
                updatePlayPauseIcon()
            }
        }
        
        view.findViewById<Button>(R.id.btn_close_dialog).setOnClickListener { 
            forceSyncAll()
            dialog.dismiss() 
        }
        dialog.show()
    }
}
