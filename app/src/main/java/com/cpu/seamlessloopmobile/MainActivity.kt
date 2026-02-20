package com.cpu.seamlessloopmobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.cpu.seamlessloopmobile.adapter.SongAdapter
import com.cpu.seamlessloopmobile.databinding.ActivityMainBinding
import com.cpu.seamlessloopmobile.scanner.AudioScanner
import com.cpu.seamlessloopmobile.model.Song
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.Button
import android.widget.TextView
import android.widget.SeekBar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var songAdapter: SongAdapter
    private lateinit var folderAdapter: com.cpu.seamlessloopmobile.adapter.FolderAdapter
    
    private var allSongs: List<com.cpu.seamlessloopmobile.model.Song> = emptyList()
    private var folders: List<com.cpu.seamlessloopmobile.model.Folder> = emptyList()
    private var isShowingFolders = true
    
    // 播放状态管理
    private var currentPlaylist: List<com.cpu.seamlessloopmobile.model.Song> = emptyList()
    private var currentSongIndex: Int = -1
    private var isPlaying = false
    
    private var updateProgressJob: kotlinx.coroutines.Job? = null
    private var isUserSeeking = false

    // 数据库相关
    private lateinit var database: com.cpu.seamlessloopmobile.db.AppDatabase
    private val songDao by lazy { database.songDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化数据库
        database = com.cpu.seamlessloopmobile.db.AppDatabase.getDatabase(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置 Toolbar 喵
        setSupportActionBar(binding.toolbar)

        // 设置返回键逻辑喵 (现代安卓做法)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isShowingFolders) {
                    showFolderList()
                } else {
                    isEnabled = false // 关掉拦截，执行默认返回
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true // 重新点开
                }
            }
        })

        setupRecyclerView()
        setupSeekBar()
        setupPlaybackControls()
        checkPermissionsAndScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateProgressJob?.cancel()
        stopAudioEngine()
    }

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val sampleRate = getSampleRate().toLong()
                    binding.tvCurrentTime.text = formatTime(progress.toLong(), sampleRate)
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                seekBar?.let {
                    // 调用 JNI seekTo
                    seekTo(it.progress.toLong())
                    isUserSeeking = false
                }
            }
        })
    }

    private fun setupPlaybackControls() {
        // 播放/暂停按钮
        binding.btnPlayPause.setOnClickListener {
            if (isPlaying) {
                pauseAudioEngine()
                isPlaying = false
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                resumeAudioEngine()
                isPlaying = true
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        // 循环设置按钮
        binding.btnLoopSettings.setOnClickListener {
            // 使用 ID 重新确认当前歌曲索引，防止对象更新后找不到
            if (currentPlaylist.isNotEmpty()) {
                if (currentSongIndex < 0 || currentSongIndex >= currentPlaylist.size) {
                    // 尝试恢复索引
                    // 这里假设 currentPlaylist 已经被 updateLoopPoints 更新过了
                    // 如果找不到，说明真的没在播放列表里
                }
                
                if (currentSongIndex >= 0 && currentSongIndex < currentPlaylist.size) {
                    showLoopSettingsDialog(currentPlaylist[currentSongIndex])
                } else {
                    Toast.makeText(this, "索引错乱喵(>_<)，请重新点歌", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "请先播放一首歌曲喵", Toast.LENGTH_SHORT).show()
            }
        }

        // 上一首
        binding.btnPrevious.setOnClickListener {
            if (currentPlaylist.isNotEmpty() && currentSongIndex > 0) {
                currentSongIndex--
                playSong(currentPlaylist[currentSongIndex])
            }
        }

        // 下一首
        binding.btnNext.setOnClickListener {
            if (currentPlaylist.isNotEmpty() && currentSongIndex < currentPlaylist.size - 1) {
                currentSongIndex++
                playSong(currentPlaylist[currentSongIndex])
            }
        }
    }

    private fun startProgressUpdater() {
        updateProgressJob?.cancel()
        updateProgressJob = lifecycleScope.launch(Dispatchers.Main) {
            while (true) {
                if (!isUserSeeking) {
                    val currentFrame = getCurrentPosition()
                    val totalFrames = getDuration()
                    val sampleRate = getSampleRate().toLong()
                    
                    if (totalFrames > 0) {
                        // 防止 SeekBar 溢出（Int.MAX_VALUE 限制）
                        val maxValue = totalFrames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        val progressValue = currentFrame.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        
                        binding.seekBar.max = maxValue
                        binding.seekBar.progress = progressValue
                        
                        binding.tvCurrentTime.text = formatTime(currentFrame, sampleRate)
                        binding.tvTotalTime.text = formatTime(totalFrames, sampleRate)
                    }
                }
                kotlinx.coroutines.delay(50) // 20 FPS refresh rate
            }
        }
    }

    private fun formatTime(frames: Long, sampleRate: Long): String {
        val safeSampleRate = if (sampleRate > 0) sampleRate else 44100L
        val totalSeconds = frames / safeSampleRate
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun setupRecyclerView() {
        // 初始化歌曲列表适配器
        songAdapter = SongAdapter(emptyList()) { song ->
            playSong(song)
        }
        
        // 初始化文件夹列表适配器
        folderAdapter = com.cpu.seamlessloopmobile.adapter.FolderAdapter(emptyList()) { folder ->
            openFolder(folder)
        }

        binding.rvSongs.layoutManager = LinearLayoutManager(this)
        // 默认显示文件夹
        binding.rvSongs.adapter = folderAdapter
    }

    private fun openFolder(folder: com.cpu.seamlessloopmobile.model.Folder) {
        isShowingFolders = false
        currentPlaylist = folder.songs // 记录当前播放列表
        songAdapter.updateSongs(folder.songs)
        binding.rvSongs.adapter = songAdapter
        
        // 更新标题栏显示当前文件夹名
        binding.toolbar.title = folder.name
        // 显示返回按钮
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
        binding.toolbar.setNavigationOnClickListener {
            showFolderList()
        }
    }

    private fun showFolderList() {
        isShowingFolders = true
        binding.rvSongs.adapter = folderAdapter
        binding.toolbar.title = "Seamless Loop"
        binding.toolbar.navigationIcon = null
    }

    private fun playSong(song: com.cpu.seamlessloopmobile.model.Song) {
        // 更新当前播放索引 (使用 ID 查找更稳健)
        val newIndex = currentPlaylist.indexOfFirst { it.id == song.id }
        if (newIndex != -1) {
            currentSongIndex = newIndex
        } else {
             // 如果这首歌不在当前列表里（比如跨文件夹播放），暂时不处理或添加到列表
             android.util.Log.w("MainActivity", "Song not found in currentPlaylist!")
        }
        
        // 弹出提示
        Toast.makeText(this, "正在为您疯狂解码: ${song.displayName}...", Toast.LENGTH_SHORT).show()

        // 开启后台协程，避免卡死 cpu 大人的 UI
        lifecycleScope.launch(Dispatchers.IO) {
            // 停止之前的播放
            updateProgressJob?.cancel()
            stopAudioEngine()
            
            // 使用 ContentResolver 以 FD 方式安全打开音频文件
            try {
                val uri = android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    song.mediaId
                )
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    val actualLength = if (afd.declaredLength < 0) afd.length else afd.declaredLength
                    
                    android.util.Log.d("MainActivity", "Opening FD: ${afd.parcelFileDescriptor.fd}, length: $actualLength")
                    
                    // 真正的解码和启动引擎
                    startAudioEngine(
                        afd.parcelFileDescriptor.fd,
                        afd.startOffset,
                        actualLength
                    )
                }

                // 设置循环点（如果数据库里有的话）
                if (song.loopEnd > 0) {
                    setLoopPoints(song.loopStart, song.loopEnd)
                }
                
                // 获取总帧数并同步回数据库喵（指纹采集）
                val durationFrames = getDuration()
                if (durationFrames > 0) {
                    val updatedWithSamples = song.copy(totalSamples = durationFrames)
                    lifecycleScope.launch(Dispatchers.IO) {
                        val dbSong = songDao.getSongByPath(updatedWithSamples.filePath)
                        songDao.insertOrUpdateSong(if (dbSong != null) updatedWithSamples.copy(id = dbSong.id) else updatedWithSamples)
                    }
                    withContext(Dispatchers.Main) {
                        // 同步更新内存，防止 UI 显示滞后
                        allSongs = allSongs.map { if (it.filePath == song.filePath) updatedWithSamples else it }
                        currentPlaylist = currentPlaylist.map { if (it.filePath == song.filePath) updatedWithSamples else it }
                    }
                }
                
                // 启动 UI 更新
                withContext(Dispatchers.Main) {
                    isPlaying = true
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    startProgressUpdater()
                }

            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to open audio FD", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "文件解析失败喵 (T_T)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkPermissionsAndScan() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            scanSongs()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_CODE_PERMISSION)
        }
    }

    private fun scanSongs() {
        lifecycleScope.launch(Dispatchers.IO) {
            // 1. 获取系统媒体库的原始列表
            val scannedSongs = AudioScanner.scan(this@MainActivity)
            

            // 2. 从咱们的数据库里找回大人的“回忆”
            val updatedSongs = scannedSongs.map { song ->
                val dbSong = songDao.getSongByPath(song.filePath)
                if (dbSong != null) {
                    // 如果大人的数据库里有这首歌，就用数据库里的数据喵！
                    // 尤其是 loopStart 和 loopEnd，绝对不能丢！
                    song.copy(
                        id = dbSong.id,
                        loopStart = dbSong.loopStart,
                        loopEnd = dbSong.loopEnd,
                        totalSamples = dbSong.totalSamples,
                        // 如果数据库里有显示名就用数据库的，否则用原来的
                        displayName = dbSong.displayName ?: song.displayName
                    )
                } else {
                    song
                }
            }

            withContext(Dispatchers.Main) {
                // 3. 再次确保这些数据是经过数据库“洗礼”的
                allSongs = updatedSongs

                
                // 按文件夹分组逻辑
                val folderMap = mutableMapOf<String, MutableList<com.cpu.seamlessloopmobile.model.Song>>()
                
                for (song in allSongs) {
                    try {
                        val file = java.io.File(song.filePath)
                        val parentPath = file.parent ?: "Unknown"
                        
                        if (!folderMap.containsKey(parentPath)) {
                            folderMap[parentPath] = mutableListOf()
                        }
                        folderMap[parentPath]?.add(song)
                    } catch (e: Exception) {
                        if (!folderMap.containsKey("Unknown")) {
                            folderMap["Unknown"] = mutableListOf()
                        }
                        folderMap["Unknown"]?.add(song)
                    }
                }
                
                // 转换为 Folder 对象列表
                folders = folderMap.map { (path, songs) ->
                    val folderName = try {
                        val file = java.io.File(path)
                        file.name 
                    } catch (e: Exception) {
                        path
                    }
                    com.cpu.seamlessloopmobile.model.Folder(folderName, path, songs.size, songs)
                }.sortedBy { it.name }
                
                folderAdapter.updateFolders(folders)
                showFolderList() // 默认显示文件夹列表
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!isShowingFolders) {
            // 如果在看歌，就退回文件夹列表
            showFolderList()
        } else {
            // 如果已经在文件夹列表，执行系统默认操作（退出）
            super.onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            scanSongs()
        } else {
            Toast.makeText(this, "需要权限才能扫描音乐哦", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showLoopSettingsDialog(originalSong: com.cpu.seamlessloopmobile.model.Song) {
        var song = originalSong // 使用 var 以便更新引用
        
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_loop_controls, null)
        dialog.setContentView(view)

        val tvStartVal = view.findViewById<TextView>(R.id.tv_loop_start_time)
        val tvStartSamples = view.findViewById<TextView>(R.id.tv_loop_start_samples)
        val tvEndVal = view.findViewById<TextView>(R.id.tv_loop_end_time)
        val tvEndSamples = view.findViewById<TextView>(R.id.tv_loop_end_samples)

        // 辅助函数：更新显示
        fun updateDisplay() {
            val sampleRate = getSampleRate().toLong()
            tvStartVal.text = formatTimeMs(song.loopStart, sampleRate)
            tvStartSamples.text = "Samples: ${song.loopStart}"
            
            tvEndVal.text = formatTimeMs(song.loopEnd, sampleRate)
            tvEndSamples.text = "Samples: ${song.loopEnd}"
        }
        
        // 辅助函数：更新数据
        fun applyUpdate(start: Long, end: Long) {
            val updatedSong = updateLoopPoints(song, start, end)
            if (updatedSong != null) {
                song = updatedSong
                updateDisplay()
            }
        }
        
        // 初始显示
        updateDisplay()

        // --- 手动输入功能喵 ---
        fun showManualInputDialog(isStart: Boolean) {
            val input = android.widget.EditText(this)
            input.inputType = android.view.inputmethod.EditorInfo.TYPE_CLASS_NUMBER
            input.setText(if (isStart) song.loopStart.toString() else song.loopEnd.toString())
            input.setSelection(input.text.length)

            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(if (isStart) "Set Start Sample" else "Set End Sample")
                .setView(input)
                .setPositiveButton("OK") { _, _ ->
                    val newVal = input.text.toString().toLongOrNull() ?: 0L
                    if (isStart) {
                        if (newVal < song.loopEnd || song.loopEnd == 0L) {
                            applyUpdate(newVal, song.loopEnd)
                        } else {
                            Toast.makeText(this, "起点不能在终点之后喵", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        if (newVal > song.loopStart) {
                            applyUpdate(song.loopStart, newVal)
                        } else {
                            Toast.makeText(this, "终点必须在起点之后喵", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        tvStartSamples.setOnClickListener { showManualInputDialog(true) }
        tvEndSamples.setOnClickListener { showManualInputDialog(false) }

        // --- A 点控制 ---
        view.findViewById<Button>(R.id.btn_set_start_current).setOnClickListener {
            val current = getCurrentPosition()
            if (current < song.loopEnd || song.loopEnd == 0L) {
                applyUpdate(current, song.loopEnd)
            } else {
                Toast.makeText(this, "起点不能晚于终点哦", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 微调 A
        val adjustStart = { deltaMs: Int ->
            val sampleRate = getSampleRate()
            val deltaSamples = (sampleRate * deltaMs / 1000).toLong()
            val newStart = (song.loopStart + deltaSamples).coerceAtLeast(0)
            if (newStart < song.loopEnd || song.loopEnd == 0L) {
                applyUpdate(newStart, song.loopEnd)
            }
        }
        view.findViewById<Button>(R.id.btn_dec_start_50ms).setOnClickListener { adjustStart(-50) }
        view.findViewById<Button>(R.id.btn_dec_start_10ms).setOnClickListener { adjustStart(-10) }
        view.findViewById<Button>(R.id.btn_inc_start_10ms).setOnClickListener { adjustStart(10) }
        view.findViewById<Button>(R.id.btn_inc_start_50ms).setOnClickListener { adjustStart(50) }

        // --- B 点控制 ---
        view.findViewById<Button>(R.id.btn_set_end_current).setOnClickListener {
            val current = getCurrentPosition()
            if (current > song.loopStart) {
                applyUpdate(song.loopStart, current)
            } else {
                Toast.makeText(this, "终点必须晚于起点哦", Toast.LENGTH_SHORT).show()
            }
        }

        // 微调 B
        val adjustEnd = { deltaMs: Int ->
            val sampleRate = getSampleRate()
            val deltaSamples = (sampleRate * deltaMs / 1000).toLong()
            val newEnd = (song.loopEnd + deltaSamples).coerceAtMost(getDuration())
            if (newEnd > song.loopStart) {
                applyUpdate(song.loopStart, newEnd)
            }
        }
        
        view.findViewById<Button>(R.id.btn_dec_end_50ms).setOnClickListener { adjustEnd(-50) }
        view.findViewById<Button>(R.id.btn_dec_end_10ms).setOnClickListener { adjustEnd(-10) }
        view.findViewById<Button>(R.id.btn_inc_end_10ms).setOnClickListener { adjustEnd(10) }
        view.findViewById<Button>(R.id.btn_inc_end_50ms).setOnClickListener { adjustEnd(50) }

        // 新增：初始化控制按钮
        val btnPlayPause = view.findViewById<android.widget.ImageButton>(R.id.btn_dialog_play_pause)
        val btnPrev = view.findViewById<android.widget.ImageButton>(R.id.btn_dialog_prev)
        val btnNext = view.findViewById<android.widget.ImageButton>(R.id.btn_dialog_next)

        // 统一更新播放暂停图标
        fun updatePlayPauseIcon() {
            if (isPlaying) {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }
        }
        updatePlayPauseIcon()

        btnPlayPause.setOnClickListener {
            // 直接触发主界面的播放/暂停按钮逻辑
            binding.btnPlayPause.performClick()
            updatePlayPauseIcon()
        }

        btnPrev.setOnClickListener {
            binding.btnPrevious.performClick()
            // 切歌后，对话框里显示的对象也需要更新（但这块逻辑比较复杂，暂时先保证按钮能点）
        }

        btnNext.setOnClickListener {
            binding.btnNext.performClick()
        }

        // 新增：初始化进度条
        val sbProgress = view.findViewById<SeekBar>(R.id.sb_dialog_progress)
        val tvCurrentTimeView = view.findViewById<TextView>(R.id.tv_dialog_current_time)
        val tvTotalTimeView = view.findViewById<TextView>(R.id.tv_dialog_total_time)
        
        // 进度更新 Job
        var dialogUpdateJob: kotlinx.coroutines.Job? = null
        var isDialogSeeking = false
        
        // 启动进度更新协程
        dialogUpdateJob = lifecycleScope.launch(Dispatchers.Main) {
            while (true) {
                if (!isDialogSeeking) {
                    val currentFrame = getCurrentPosition()
                    val totalFrames = getDuration()
                    val sampleRate = getSampleRate().toLong()
                    
                    if (totalFrames > 0) {
                        val maxValue = totalFrames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        val progressValue = currentFrame.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        
                        sbProgress.max = maxValue
                        sbProgress.progress = progressValue
                        
                        tvCurrentTimeView.text = formatTime(currentFrame, sampleRate)
                        tvTotalTimeView.text = formatTime(totalFrames, sampleRate)
                        
                        // 同时同步更新播放暂停图标（防止后台状态变化）
                        updatePlayPauseIcon()
                    }
                }
                kotlinx.coroutines.delay(50)
            }
        }
        
        // 设置拖动监听
        sbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val sampleRate = getSampleRate().toLong()
                    tvCurrentTimeView.text = formatTime(progress.toLong(), sampleRate)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isDialogSeeking = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    seekTo(it.progress.toLong())
                    isDialogSeeking = false
                }
            }
        })
        
        // 弹窗关闭时取消协程
        dialog.setOnDismissListener {
            dialogUpdateJob?.cancel()
        }

        view.findViewById<Button>(R.id.btn_close_dialog).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateLoopPoints(song: com.cpu.seamlessloopmobile.model.Song, start: Long, end: Long): com.cpu.seamlessloopmobile.model.Song? {
        // 1. 创建新对象
        val newSong = song.copy(loopStart = start, loopEnd = end)
        
        // 2. 写入数据库记忆喵！
        lifecycleScope.launch(Dispatchers.IO) {
            songDao.insertOrUpdateSong(newSong)
        }
        
        // 3. 全局数据同步，确保“按返回键”后再进入文件夹依然有效喵！
        allSongs = allSongs.map { if (it.filePath == song.filePath) newSong else it }
        folders = folders.map { folder ->
            val updatedSongs = folder.songs.map { if (it.filePath == song.filePath) newSong else it }
            folder.copy(songs = updatedSongs)
        }

        // 4. 更新当前播放列表
        val list = currentPlaylist.toMutableList()
        val index = list.indexOfFirst { it.filePath == song.filePath }
        
        if (index != -1) {
            list[index] = newSong
            currentPlaylist = list
            if (!isShowingFolders) {
                songAdapter.updateSongItem(index, newSong) 
            }
        }
        
        // 5. 更新 C++ JNI 引擎
        setLoopPoints(start, end)
        
        return newSong
    }

    private fun formatTimeMs(frames: Long, sampleRate: Long): String {
        val safeSampleRate = if (sampleRate > 0) sampleRate else 44100L
        val totalMillis = (frames * 1000) / safeSampleRate
        val minutes = totalMillis / 60000
        val seconds = (totalMillis % 60000) / 1000
        val millis = totalMillis % 1000
        return String.format("%02d:%02d.%03d", minutes, seconds, millis)
    }
    // --- JNI 接口 ---
    external fun stringFromJNI(): String
    external fun startAudioEngine(fd: Int, offset: Long, length: Long)
    external fun stopAudioEngine()
    external fun setLoopPoints(start: Long, end: Long)
    external fun seekTo(frame: Long)
    external fun getCurrentPosition(): Long
    external fun getDuration(): Long
    external fun getSampleRate(): Int
    external fun pauseAudioEngine()
    external fun resumeAudioEngine()

    companion object {
        private const val REQUEST_CODE_PERMISSION = 1001

        init {
            System.loadLibrary("seamlessloopmobile")
        }
    }
}