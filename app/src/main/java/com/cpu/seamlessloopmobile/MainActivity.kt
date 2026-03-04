package com.cpu.seamlessloopmobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ComponentName
import android.os.Build
import android.os.IBinder
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.ViewModelProvider
import com.cpu.seamlessloopmobile.viewmodel.MainViewModel
import com.cpu.seamlessloopmobile.viewmodel.MainViewModelFactory
import com.cpu.seamlessloopmobile.viewmodel.PlayMode
import com.cpu.seamlessloopmobile.utils.TimeUtils
import com.cpu.seamlessloopmobile.dialogs.LoopSettingsDialog
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.Playlist
import com.cpu.seamlessloopmobile.adapter.SongAdapter
import com.cpu.seamlessloopmobile.adapter.LibraryAdapter
import com.cpu.seamlessloopmobile.databinding.ActivityMainBinding
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue
import com.cpu.seamlessloopmobile.ui.screen.playing.PlayingPanel
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    private lateinit var viewModel: MainViewModel
    private lateinit var repository: com.cpu.seamlessloopmobile.data.MusicRepository
    private lateinit var database: com.cpu.seamlessloopmobile.db.AppDatabase

    private var displayedSongs: List<Song> = emptyList() // 专门负责显示的“看单”喵
    private var currentPlaylist: List<Song> = emptyList() // 专门负责播放的“听单”喵
    private var currentSongIndex: Int = -1
    private var isPlaying = false
    private var isAbModePlaying = false
    private var isUserSeeking: Boolean = false
    private var currentAbIntroSong: Song? = null
    private var currentOpenPlaylist: Playlist? = null
    private lateinit var mediaBrowserHelper: com.cpu.seamlessloopmobile.audio.MediaBrowserHelper
    private lateinit var progressUpdateHelper: com.cpu.seamlessloopmobile.ui.ProgressUpdateHelper
    private var playbackService: com.cpu.seamlessloopmobile.audio.PlaybackService? = null

    // 缓存数据，回退时有用喵
    private var folders: List<com.cpu.seamlessloopmobile.model.Folder> = emptyList()
    private var albums: List<com.cpu.seamlessloopmobile.model.Folder> = emptyList()
    private var artists: List<com.cpu.seamlessloopmobile.model.Folder> = emptyList()
    private var allSongs: List<com.cpu.seamlessloopmobile.model.Song> = emptyList()


    // 文件选择器喵
    private val dbPickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importFromPcDatabase(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化数据库
        database = com.cpu.seamlessloopmobile.db.AppDatabase.getDatabase(this)
        
        repository = com.cpu.seamlessloopmobile.data.MusicRepository(database.songDao(), database.playlistDao())
        
        // 逻辑大脑初始化喵！
        val factory = MainViewModelFactory(database.songDao(), database.playlistDao())
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置 Toolbar 喵
        setSupportActionBar(binding.toolbar)

        // 初始化 Compose 播放面板喵
        binding.composeViewPlayingPanel.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val isVisible by viewModel.isPlayingPanelVisible.observeAsState(false)
                
                PlayingPanel(
                    viewModel = viewModel,
                    isVisible = isVisible,
                    onClose = { viewModel.setPlayingPanelVisible(false) },
                    onPlayPause = {
                        mediaBrowserHelper.getTransportControls()?.let { controls ->
                            val state = mediaBrowserHelper.playbackState.value?.state
                            if (state == android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING) {
                                controls.pause()
                            } else {
                                controls.play()
                            }
                        }
                    },
                    onNext = {
                        val nextIndex = viewModel.getNextIndex()
                        if (nextIndex != -1) {
                            playSong(currentPlaylist[nextIndex])
                        }
                    },
                    onPrev = {
                        val prevIndex = viewModel.getPrevIndex()
                        if (prevIndex != -1) {
                            playSong(currentPlaylist[prevIndex])
                        }
                    }
                )
            }
        }

        // 核心修复：直接通过 Activity 观察可见性，控制 View 的生杀大权喵！
        viewModel.isPlayingPanelVisible.observe(this) { isVisible ->
            binding.composeViewPlayingPanel.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        // --- 核心 UI 导航大脑同步喵！ ---
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is com.cpu.seamlessloopmobile.viewmodel.MusicUiState.Home -> {
                    binding.toolbar.title = "Seamless Loop"
                    binding.toolbar.navigationIcon = null
                    displayedSongs = emptyList()
                }
                is com.cpu.seamlessloopmobile.viewmodel.MusicUiState.CategoryFolders -> {
                    binding.toolbar.title = state.title
                    binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
                    binding.toolbar.setNavigationOnClickListener { viewModel.goBack() }
                    displayedSongs = emptyList()
                }
                is com.cpu.seamlessloopmobile.viewmodel.MusicUiState.SongList -> {
                    binding.toolbar.title = state.title
                    binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
                    binding.toolbar.setNavigationOnClickListener { viewModel.goBack() }
                    displayedSongs = state.songs
                }
            }
            invalidateOptionsMenu()
        }

        viewModel.allSongs.observe(this) { allSongs = it }
        viewModel.folders.observe(this) { folders = it }
        viewModel.albums.observe(this) { albums = it }
        viewModel.artists.observe(this) { artists = it }

        viewModel.currentPlaylist.observe(this) { 
            currentPlaylist = it
        }


        viewModel.syncStatus.observe(this) { status ->
            if (status.isNotEmpty()) {
                // 在 Toolbar 字幕显示进度，这样最帅气喵！
                binding.toolbar.subtitle = status
            } else {
                binding.toolbar.subtitle = null
            }
        }
        viewModel.currentSongIndex.observe(this) { index ->
            currentSongIndex = index
        }
        viewModel.isPlaying.observe(this) { playing ->
            isPlaying = playing 
            if (playing) {
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }
        }
        viewModel.isAbModePlaying.observe(this) { isAbModePlaying = it }
        viewModel.currentAbIntroSong.observe(this) { currentAbIntroSong = it }
        viewModel.currentOpenPlaylist.observe(this) { currentOpenPlaylist = it }

        viewModel.playMode.observe(this) { mode ->
            updatePlayModeIcon(mode)
            // 循环模式的变更也应该通过遥控器传给后台喵
            val bundle = android.os.Bundle().apply {
                putInt("play_mode", mode.ordinal)
            }
            mediaBrowserHelper.getTransportControls()?.sendCustomAction("SET_PLAY_MODE", bundle)
        }

        // --- 初始化助手们喵 ---
        mediaBrowserHelper = com.cpu.seamlessloopmobile.audio.MediaBrowserHelper(this, com.cpu.seamlessloopmobile.audio.PlaybackService::class.java)
        progressUpdateHelper = com.cpu.seamlessloopmobile.ui.ProgressUpdateHelper(
            seekBar = binding.seekBar,
            tvCurrentTime = binding.tvCurrentTime,
            tvTotalTime = binding.tvTotalTime,
            coroutineScope = lifecycleScope,
            getPlaybackService = { playbackService },
            isUserSeeking = { isUserSeeking },
            onFileEnd = {
                val nextIndex = viewModel.getNextIndex()
                val currentMode = viewModel.playMode.value ?: com.cpu.seamlessloopmobile.viewmodel.PlayMode.LIST_LOOP
                if (currentMode != com.cpu.seamlessloopmobile.viewmodel.PlayMode.SINGLE_LOOP && nextIndex != -1) {
                    playSong(currentPlaylist[nextIndex])
                } else if (currentMode != com.cpu.seamlessloopmobile.viewmodel.PlayMode.SINGLE_LOOP) {
                    mediaBrowserHelper.getTransportControls()?.stop()
                    viewModel.setPlaying(false)
                }
            }
        )

        mediaBrowserHelper.playbackState.observe(this) { state ->
            state?.let {
                val isPlaying = it.state == android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
                viewModel.setPlaying(isPlaying)
                val isAbMode = it.extras?.getBoolean("is_ab_mode", false) ?: false
                viewModel.setAbModePlaying(isAbMode)
                
                if (isPlaying) {
                    progressUpdateHelper.start()
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                } else {
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                }
            }
        }

        mediaBrowserHelper.metadata.observe(this) { metadata ->
            metadata?.let {
                val title = it.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE)
                binding.tvPlayingSongName.text = title ?: "未知歌曲"
                
                val totalFrames = playbackService?.playbackManager?.duration ?: 0L
                val sampleRate = playbackService?.playbackManager?.sampleRate?.toLong() ?: 44100L
                if (totalFrames > 0) {
                    binding.tvTotalTime.text = TimeUtils.formatTime(totalFrames, sampleRate)
                }
            }
        }

        // 设置返回键逻辑喵 (现代安卓做法)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.isPlayingPanelVisible.value == true) {
                    viewModel.setPlayingPanelVisible(false)
                } else if (viewModel.goBack()) {
                    // 大脑已经成功回退，UI 也会自动响应喵！
                } else {
                    isEnabled = false // 关掉拦截，执行默认返回
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true // 重新点开
                }
            }
        })

        setupMainContent()
        setupSeekBar()
        setupPlaybackControls()
        checkPermissionsAndLoadHome()
 
        // --- 绑定后台服务喵 ---
        val serviceIntent = Intent(this, com.cpu.seamlessloopmobile.audio.PlaybackService::class.java)
        bindService(serviceIntent, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as com.cpu.seamlessloopmobile.audio.PlaybackService.PlaybackBinder
                playbackService = binder.getService()
                restoreLastPlayedSong()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                playbackService = null
            }
        }, BIND_AUTO_CREATE)
    }

    override fun onStart() {
        super.onStart()
        mediaBrowserHelper.connect()
    }

    override fun onStop() {
        super.onStop()
        saveCurrentState()
        mediaBrowserHelper.disconnect()
        progressUpdateHelper.stop()
    }

    private fun restoreLastPlayedSong() {
        val prefs = getSharedPreferences("last_state", MODE_PRIVATE)
        val lastPath = prefs.getString("last_song_path", null)
        val lastPos = prefs.getLong("last_position", 0L)

        if (lastPath != null) {
            lifecycleScope.launch {
                val baseSong = repository.getSongByPath(lastPath)
                if (baseSong != null) {
                    // 同步令牌身份喵！
                    val song = repository.resolveMediaId(this@MainActivity, baseSong)
                    withContext(Dispatchers.Main) {
                        // 把它装好，并同步进听单喵，这样进度条和切歌逻辑才能跑起来喵！
                        binding.tvPlayingSongName.text = song.displayName ?: song.fileName
                        
                        // 先指挥脑部进入暂停状态，防止观察者乱动喵
                        viewModel.setPlaying(false)
                        
                        // 先建立一个只包含这首歌的临时听单，防止切歌时崩溃喵
                        viewModel.updateCurrentPlaylist(listOf(song), 0)
                        
                        // 直接恢复到那个进度，并下达“初始暂停”的死命令喵！
                        // 注意：这里需要考虑 Service 是否已经由 MediaBrowser 连通喵
                        val controller = android.support.v4.media.session.MediaControllerCompat.getMediaController(this@MainActivity)
                        if (controller != null) {
                            val extras = android.os.Bundle().apply {
                                putLong("start_pos", lastPos)
                                putBoolean("start_paused", true)
                            }
                            controller.transportControls.playFromMediaId(song.mediaId.toString(), extras)
                        } else {
                            // TODO: 备选方案，或者等连通后再发喵
                        }
                    }
                }
            }
        }
    }

    private fun saveCurrentState() {
        val currentSong = if (currentSongIndex >= 0 && currentSongIndex < currentPlaylist.size) {
            currentPlaylist[currentSongIndex]
        } else null
        
        currentSong?.let { song ->
            val prefs = getSharedPreferences("last_state", MODE_PRIVATE)
            prefs.edit().apply {
                putString("last_song_path", song.filePath)
                try {
                    putLong("last_position", playbackService?.playbackManager?.position ?: 0L)
                } catch (e: Exception) {
                    putLong("last_position", 0L)
                }
                apply()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 既然已经有后台 Service 了，Activity 销毁时不该直接停掉引擎喵！
        // 由 Service 自行根据播放状态决定生死喵。
    }

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val sampleRate = playbackService?.playbackManager?.sampleRate?.toLong() ?: 44100L
                    binding.tvCurrentTime.text = TimeUtils.formatTime(progress.toLong(), sampleRate)
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                seekBar?.let {
                mediaBrowserHelper.getTransportControls()?.seekTo(it.progress.toLong())
                isUserSeeking = false
            }
        }
    })
}

private fun setupPlaybackControls() {
    binding.btnPlayPause.setOnClickListener {
        val state = mediaBrowserHelper.playbackState.value?.state
        if (state == android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING) {
            mediaBrowserHelper.getTransportControls()?.pause()
        } else {
            mediaBrowserHelper.getTransportControls()?.play()
        }
    }

        // 循环设置按钮
        binding.btnLoopSettings.setOnClickListener {
            if (isAbModePlaying) {
                currentAbIntroSong?.let { 
                    showLoopSettingsDialog(it) 
                } ?: Toast.makeText(this, "AB 歌曲信息缺失喵", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (currentPlaylist.isNotEmpty()) {
                viewModel.setPlayingPanelVisible(true)
            } else {
                Toast.makeText(this, "请先播放一首歌曲喵", Toast.LENGTH_SHORT).show()
            }
        }

        // 上一首
        binding.btnPrevious.setOnClickListener {
            val prevIndex = viewModel.getPrevIndex()
            if (prevIndex != -1) {
                playSong(currentPlaylist[prevIndex])
            }
        }

        // 下一首
        binding.btnNext.setOnClickListener {
            val nextIndex = viewModel.getNextIndex()
            if (nextIndex != -1) {
                playSong(currentPlaylist[nextIndex])
            }
        }

        // 播放模式切换
        binding.btnPlayMode.setOnClickListener {
            viewModel.togglePlayMode()
        }

        // 点击底部播放栏整体打开详情页喵
        binding.bottomPlayerBar.setOnClickListener {
            if (currentPlaylist.isNotEmpty()) {
                viewModel.setPlayingPanelVisible(true)
            }
        }
    }

    private fun updatePlayModeIcon(mode: PlayMode) {
        val iconRes = when (mode) {
            PlayMode.LIST_LOOP -> android.R.drawable.ic_menu_revert
            PlayMode.SINGLE_LOOP -> android.R.drawable.ic_menu_rotate
            PlayMode.SHUFFLE -> android.R.drawable.ic_menu_share // 暂代随机
        }
        binding.btnPlayMode.setImageResource(iconRes)
        
        val modeName = when (mode) {
            PlayMode.LIST_LOOP -> "列表循环"
            PlayMode.SINGLE_LOOP -> "单曲循环"
            PlayMode.SHUFFLE -> "随机播放"
        }
        Toast.makeText(this, "模式已切换为: $modeName 喵!", Toast.LENGTH_SHORT).show()
    }



    private fun setupMainContent() {
        binding.composeViewMain.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val darkTheme = isSystemInDarkTheme()
                val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
                
                MaterialTheme(colorScheme = colorScheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ) {
                        com.cpu.seamlessloopmobile.ui.screen.MainScreen(
                            viewModel = viewModel,
                            playSong = { song -> playSong(song) }
                        )
                    }
                }
            }
        }
    }


    private fun playSong(song: com.cpu.seamlessloopmobile.model.Song) {
        // 只有当点击的歌不在当前“听单”里，或者“听单”和“看单”不对应时才强制同步喵
        if (displayedSongs.contains(song) && currentPlaylist != displayedSongs) {
            currentPlaylist = displayedSongs
            viewModel.updateCurrentPlaylist(displayedSongs)
        }

        // 更新当前播放索引 (使用 filePath 查找，因为有些新歌 ID 还没登记喵)
        val newIndex = currentPlaylist.indexOfFirst { it.filePath == song.filePath }
        currentSongIndex = if (newIndex != -1) {
            newIndex
        } else {
             // 如果在当前列表没找到，就试图在全局列表找找
             val globalIndex = allSongs.indexOfFirst { it.filePath == song.filePath }
             if (globalIndex != -1) {
                 currentPlaylist = allSongs
                 viewModel.updateCurrentPlaylist(allSongs)
                 globalIndex
             } else {
                 android.util.Log.w("MainActivity", "Song not found anywhere: ${song.filePath}")
                 -1
             }
        }
        
        viewModel.updateSongIndex(currentSongIndex)
        
        // 助手点火喵！
        mediaBrowserHelper.getTransportControls()?.playFromMediaId(song.mediaId.toString(), null)
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
            // 偷偷在后台扫一下，不打扰大人喵
            viewModel.scanLibrary(this)
        } else {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_CODE_PERMISSION)
        }
    }





    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkPermissionsAndLoadHome()
        } else {
            Toast.makeText(this, "需要权限才能扫描音乐哦", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showLoopSettingsDialog(originalSong: com.cpu.seamlessloopmobile.model.Song) {
        LoopSettingsDialog(
            activity = this,
            originalSong = originalSong,
            viewModel = viewModel,
            coroutineScope = lifecycleScope,
            onPlayPauseClick = { binding.btnPlayPause.performClick() },
            onPrevClick = { binding.btnPrevious.performClick() },
            onNextClick = { binding.btnNext.performClick() }
        ).show()
    }


    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sync_pc -> {
                dbPickerLauncher.launch("application/octet-stream")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun importFromPcDatabase(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            com.cpu.seamlessloopmobile.db.PcDatabaseImporter.importFromPcDatabase(
                context = this@MainActivity,
                uri = uri,
                songDao = database.songDao(),
                callback = object : com.cpu.seamlessloopmobile.db.PcDatabaseImporter.ImportCallback {
                    override fun onSuccess(syncCount: Int) {
                        Toast.makeText(this@MainActivity, "同步完成喵！成功找回 $syncCount 条循环数据", Toast.LENGTH_LONG).show()
                        // 重新扫描以刷新 UI
                        viewModel.scanLibrary(this@MainActivity)
                    }

                    override fun onError(message: String) {
                        Toast.makeText(this@MainActivity, "同步失败了(>_<): $message", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }



    companion object {
        private const val REQUEST_CODE_PERMISSION = 1001
    }
}