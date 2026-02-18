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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var songAdapter: SongAdapter
    private lateinit var folderAdapter: com.cpu.seamlessloopmobile.adapter.FolderAdapter
    
    // 状态管理
    private var allSongs: List<com.cpu.seamlessloopmobile.model.Song> = emptyList()
    private var folders: List<com.cpu.seamlessloopmobile.model.Folder> = emptyList()
    private var isShowingFolders = true
    
    private var updateProgressJob: kotlinx.coroutines.Job? = null
    private var isUserSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSeekBar()
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
        songAdapter.updateSongs(folder.songs)
        binding.rvSongs.adapter = songAdapter
        
        // 更新标题栏显示当前文件夹名
        binding.toolbar.title = folder.name
        // 显示返回按钮
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun showFolderList() {
        isShowingFolders = true
        binding.rvSongs.adapter = folderAdapter
        binding.toolbar.title = "Seamless Loop"
        binding.toolbar.navigationIcon = null
    }

    private fun playSong(song: com.cpu.seamlessloopmobile.model.Song) {
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
                    song.id
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
                
                // 启动 UI 更新
                withContext(Dispatchers.Main) {
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
        allSongs = AudioScanner.scan(this)
        
        // 按文件夹分组逻辑
        val folderMap = mutableMapOf<String, MutableList<com.cpu.seamlessloopmobile.model.Song>>()
        
        for (song in allSongs) {
            try {
                // 提取父目录路径
                val file = java.io.File(song.filePath)
                val parentPath = file.parent ?: "Unknown"
                
                if (!folderMap.containsKey(parentPath)) {
                    folderMap[parentPath] = mutableListOf()
                }
                folderMap[parentPath]?.add(song)
            } catch (e: Exception) {
                // 如果路径解析失败，放到 Unknown
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
                file.name // 只取最后一级目录名
            } catch (e: Exception) {
                path
            }
            com.cpu.seamlessloopmobile.model.Folder(folderName, path, songs.size, songs)
        }.sortedBy { it.name }
        
        folderAdapter.updateFolders(folders)
        showFolderList() // 默认显示文件夹列表
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

    // --- JNI 接口 ---
    external fun stringFromJNI(): String
    external fun startAudioEngine(fd: Int, offset: Long, length: Long)
    external fun stopAudioEngine()
    external fun setLoopPoints(start: Long, end: Long)
    external fun seekTo(frame: Long)
    external fun getCurrentPosition(): Long
    external fun getDuration(): Long
    external fun getSampleRate(): Int

    companion object {
        private const val REQUEST_CODE_PERMISSION = 1001

        init {
            System.loadLibrary("seamlessloopmobile")
        }
    }
}