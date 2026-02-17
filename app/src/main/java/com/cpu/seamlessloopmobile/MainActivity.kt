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
    private lateinit var adapter: SongAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        checkPermissionsAndScan()
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(emptyList()) { song ->
            // 弹出提示
            Toast.makeText(this, "正在为您疯狂解码: ${song.displayName}...", Toast.LENGTH_SHORT).show()

            // 开启后台协程，避免卡死 cpu 大人的 UI
            lifecycleScope.launch(Dispatchers.IO) {
                // 停止之前的播放
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
                    // 删掉了这里强制 1-5 秒预览的代码喵！

                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to open audio FD", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "文件解析失败喵 (T_T)", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        binding.rvSongs.layoutManager = LinearLayoutManager(this)
        binding.rvSongs.adapter = adapter
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
        val songs = AudioScanner.scan(this)
        adapter.updateSongs(songs)
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

    companion object {
        private const val REQUEST_CODE_PERMISSION = 1001

        init {
            System.loadLibrary("seamlessloopmobile")
        }
    }
}