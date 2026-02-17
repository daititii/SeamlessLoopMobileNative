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
            Toast.makeText(this, "正在使用 Oboe 播放: ${song.displayName}", Toast.LENGTH_SHORT).show()
            
            // 停止之前的播放
            stopAudioEngine()
            
            // 启动引擎（目前是播放由于模拟的正弦波）
            startAudioEngine()
            
            // 设置循环点（如果数据库里有的话，现在先设个假的测试一下）
            // 假设循环点是 1秒 到 5秒 (44100Hz)
            if (song.loopEnd > 0) {
                setLoopPoints(song.loopStart, song.loopEnd)
            } else {
                setLoopPoints(44100, 44100 * 5)
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
    external fun startAudioEngine()
    external fun stopAudioEngine()
    external fun setLoopPoints(start: Long, end: Long)

    companion object {
        private const val REQUEST_CODE_PERMISSION = 1001

        init {
            System.loadLibrary("seamlessloopmobile")
        }
    }
}