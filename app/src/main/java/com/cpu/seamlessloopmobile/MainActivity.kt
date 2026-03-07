package com.cpu.seamlessloopmobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.cpu.seamlessloopmobile.viewmodel.MainViewModel
import com.cpu.seamlessloopmobile.viewmodel.MainViewModelFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.cpu.seamlessloopmobile.ui.screen.MainScreen
import com.cpu.seamlessloopmobile.data.SettingsManager

/**
 * 精简后的 MainActivity 喵！
 * 莱芙把它从 600 多行瘦身成功啦，现在它只负责：
 * 1. 权限申请 
 * 2. 媒体连接生命周期
 * 3. 开启 Compose 大门
 */
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    
    // PC 数据库文件选择器喵
    private val dbPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importFromPcDatabase(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化
        val database = com.cpu.seamlessloopmobile.db.AppDatabase.getDatabase(this)
        val factory = MainViewModelFactory(database.songDao(), database.playlistDao(), database.playQueueDao(), this)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        val settingsManager = SettingsManager.getInstance(this)
        viewModel.initSettings(settingsManager)

        setContent {
            val darkTheme = isSystemInDarkTheme()
            val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
            
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        playSong = { song -> viewModel.playSong(song) },
                        onSyncPc = { dbPickerLauncher.launch("application/octet-stream") }
                    )
                }
            }
        }

        checkPermissionsAndLoadHome()
        viewModel.connectMedia()
        viewModel.startObservation()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.disconnectMedia()
    }

    private fun checkPermissionsAndLoadHome() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val permissions = mutableListOf(permission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            viewModel.openHome()
            viewModel.loadSongsFromDatabase() // 从数据库快速加载
            viewModel.loadPlaylists() // 加载歌单
            viewModel.scanLibrary(this) // 后台继续扫描新歌曲
        } else {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1001)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkPermissionsAndLoadHome()
        } else if (requestCode == 1001) {
            Toast.makeText(this, "未获得权限喵，可能无法为您扫描音乐(>_<)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFromPcDatabase(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            com.cpu.seamlessloopmobile.db.PcDatabaseImporter.importFromPcDatabase(
                context = this@MainActivity,
                uri = uri,
                songDao = com.cpu.seamlessloopmobile.db.AppDatabase.getDatabase(this@MainActivity).songDao(),
                callback = object : com.cpu.seamlessloopmobile.db.PcDatabaseImporter.ImportCallback {
                    override fun onSuccess(syncCount: Int) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "同步完成喵！成功找回 $syncCount 条循环数据", Toast.LENGTH_LONG).show()
                            viewModel.scanLibrary(this@MainActivity)
                        }
                    }
                    override fun onError(message: String) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "同步失败了(>_<): $message", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }
}