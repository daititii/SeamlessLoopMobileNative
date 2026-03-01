package com.cpu.seamlessloopmobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import com.cpu.seamlessloopmobile.jni.NativeAudio
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var songAdapter: SongAdapter
    private lateinit var libraryAdapter: LibraryAdapter
    
    private lateinit var viewModel: MainViewModel
    private lateinit var repository: com.cpu.seamlessloopmobile.data.MusicRepository
    private lateinit var database: com.cpu.seamlessloopmobile.db.AppDatabase

    private var rawScannedSongs: List<Song> = emptyList()
    private var allSongs: List<Song> = emptyList()
    private var folders: List<com.cpu.seamlessloopmobile.model.Folder> = emptyList()
    private var isShowingFolders = false
    private var isExploringLocal = false
    private var isInsidePlaylist = false
    private var displayedSongs: List<Song> = emptyList() // 专门负责显示的“看单”喵
    private var currentPlaylist: List<Song> = emptyList() // 专门负责播放的“听单”喵
    private var currentSongIndex: Int = -1
    private var isPlaying = false
    private var isAbModePlaying = false
    private var currentAbIntroSong: Song? = null
    private var currentOpenPlaylist: Playlist? = null
    private var updateProgressJob: kotlinx.coroutines.Job? = null
    private var isUserSeeking: Boolean = false
    private var isSelectionControllerInitialized = false
    private lateinit var selectionController: com.cpu.seamlessloopmobile.ui.SelectionController
    private lateinit var mediaBrowser: MediaBrowserCompat
    private var playbackService: com.cpu.seamlessloopmobile.audio.PlaybackService? = null
    
    // 专门抓捕耳机被拔掉瞬间的小广播喵！
    private val becomingNoisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            // 增加一点安全防御，等一切准备好（binding 就绪）再响应喵
            if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY && ::binding.isInitialized) {
                // 嘘！系统说现在声音可能外泄，我们要立刻暂停喵！
                playbackService?.playbackManager?.pause()
                viewModel.setPlaying(false)
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }
        }
    }

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

        // 中间层同步：把 ViewModel 的状态同步给本地冗余变量 (过渡用喵)
        viewModel.allSongs.observe(this) { 
            allSongs = it
            if ((viewModel.isExploringLocal.value ?: false) == false && (viewModel.isInsidePlaylist.value ?: false) == false) {
                loadHomeView()
            }
        }
        viewModel.folders.observe(this) { 
            folders = it
            if (isExploringLocal && isShowingFolders) {
                showFolderList()
            }
        }
        viewModel.isExploringLocal.observe(this) { isExploringLocal = it }
        viewModel.isShowingFolders.observe(this) { isShowingFolders = it }
        viewModel.isInsidePlaylist.observe(this) { isInsidePlaylist = it }
        viewModel.currentPlaylist.observe(this) { 
            currentPlaylist = it
            // 只要没在看文件夹列表，就刷新歌曲显示喵（包括歌单内和文件夹内）
            if (!isShowingFolders && (isInsidePlaylist || isExploringLocal)) {
                displayedSongs = it // 保持看单和听单同步喵！
                songAdapter.updateSongs(it)
            }
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
            if (index >= 0 && index < currentPlaylist.size) {
                songAdapter.setPlayingSong(currentPlaylist[index].filePath)
            } else {
                songAdapter.setPlayingSong(null)
            }
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
        viewModel.rawScannedSongs.observe(this) { rawScannedSongs = it }
        viewModel.playlists.observe(this) {
            if ((viewModel.isExploringLocal.value ?: false) == false && (viewModel.isInsidePlaylist.value ?: false) == false) {
                loadHomeView()
            }
        }
        viewModel.playMode.observe(this) { mode ->
            updatePlayModeIcon(mode)
            // 循环模式的变更也应该通过遥控器传给后台喵
            val controller = MediaControllerCompat.getMediaController(this)
            val bundle = android.os.Bundle().apply {
                putInt("play_mode", mode.ordinal)
            }
            controller?.transportControls?.sendCustomAction("SET_PLAY_MODE", bundle)
        }

        // 设置返回键逻辑喵 (现代安卓做法)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 增加安全性检查：万一初始化还没跑完大人就按返回键了喵！
                if (!isSelectionControllerInitialized) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                    return
                }

                if (selectionController.isSelectionMode) {
                    selectionController.exitSelectionMode()
                } else if (selectionController.isPlaylistSelectionMode) {
                    selectionController.exitPlaylistSelectionMode()
                } else if (isInsidePlaylist) {
                    isInsidePlaylist = false
                    loadHomeView()
                } else if (isExploringLocal) {
                    if (!isShowingFolders) {
                        showFolderList() // 从歌曲列表回退到文件夹列表喵
                    } else {
                        isExploringLocal = false
                        loadHomeView() // 从文件夹列表回退到主页喵
                    }
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
        checkPermissionsAndLoadHome()
        
        // 注册耳机监控喵 (考虑到安卓 13+ 的动态权限要求喵)
        // 莱芙使用了更安全的 ContextCompat 做法逻辑，避免直接引用可能不存在的常量喵
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                becomingNoisyReceiver, 
                android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                if (android.os.Build.VERSION.SDK_INT >= 33) 4 else 0 // 4 是 RECEIVER_NOT_EXPORTED 喵
            )
        } else {
            registerReceiver(
                becomingNoisyReceiver, 
                android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            )
        }

        // --- 官方遥控器：初始化 MediaBrowser 喵 ---
        mediaBrowser = MediaBrowserCompat(
            this,
            android.content.ComponentName(this, com.cpu.seamlessloopmobile.audio.PlaybackService::class.java),
            connectionCallbacks,
            null
        )
 
        // --- 绑定后台服务喵 ---
        val serviceIntent = Intent(this, com.cpu.seamlessloopmobile.audio.PlaybackService::class.java)
        bindService(serviceIntent, object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
                val binder = service as com.cpu.seamlessloopmobile.audio.PlaybackService.PlaybackBinder
                playbackService = binder.getService()
                // 现在主要通过 MediaBrowser 通信，Binder 仅作极少数兼容喵
                restoreLastPlayedSong()
            }
            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                playbackService = null
            }
        }, BIND_AUTO_CREATE)
    }

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            // 拿到令牌，制作遥控器喵！
            mediaBrowser.sessionToken.let { token ->
                val mediaController = MediaControllerCompat(this@MainActivity, token)
                MediaControllerCompat.setMediaController(this@MainActivity, mediaController)
                
                // 注册反馈监听喵！
                mediaController.registerCallback(controllerCallback)
                
                // 初次同步状态喵
                controllerCallback.onPlaybackStateChanged(mediaController.playbackState)
                controllerCallback.onMetadataChanged(mediaController.metadata)
            }
        }

        override fun onConnectionSuspended() {}
        override fun onConnectionFailed() {}
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: android.support.v4.media.session.PlaybackStateCompat?) {
            state?.let {
                val isPlaying = it.state == android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING
                viewModel.setPlaying(isPlaying)
                
                val isAbMode = it.extras?.getBoolean("is_ab_mode", false) ?: false
                viewModel.setAbModePlaying(isAbMode)
                
                if (isPlaying) {
                    startProgressUpdater()
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                } else {
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                }
            }
        }

        override fun onMetadataChanged(metadata: android.support.v4.media.MediaMetadataCompat?) {
            metadata?.let {
                val title = it.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE)
                val isAbMode = it.getString("is_ab_mode") == "true"
                
                binding.tvPlayingSongName.text = title ?: "未知歌曲"
                viewModel.setAbModePlaying(isAbMode)
                
                // 强制刷新一次总时长喵
                val totalFrames = playbackService?.playbackManager?.duration ?: 0L
                val sampleRate = playbackService?.playbackManager?.sampleRate?.toLong() ?: 44100L
                if (totalFrames > 0) {
                    binding.tvTotalTime.text = TimeUtils.formatTime(totalFrames, sampleRate)
                }
            }
        }
    }


    override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
    }

    override fun onStop() {
        super.onStop()
        // 断开连接前记得存一下进度喵！
        saveCurrentState()
        mediaBrowser.disconnect()
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
        try {
            unregisterReceiver(becomingNoisyReceiver)
        } catch (e: Exception) {
            // 预防万一，如果没注册成功也不要崩掉喵
        }
        updateProgressJob?.cancel()
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
                    // 通过遥控器下达 Seek 指令喵！
                    MediaControllerCompat.getMediaController(this@MainActivity)
                        ?.transportControls?.seekTo(it.progress.toLong())
                    isUserSeeking = false
                }
            }
        })
    }

    private fun setupPlaybackControls() {
        // 播放/暂停按钮
        binding.btnPlayPause.setOnClickListener {
            val controller = MediaControllerCompat.getMediaController(this)
            val state = controller?.playbackState?.state
            if (state == android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
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

    private fun startProgressUpdater() {
        updateProgressJob?.cancel()
        updateProgressJob = lifecycleScope.launch(Dispatchers.Main) {
            var lastObservedFrame = -1L
            var frameStallCount = 0
            
            while (true) {
                if (!isUserSeeking) {
                    val currentFrame = playbackService?.playbackManager?.position ?: 0L
                    val totalFrames = playbackService?.playbackManager?.duration ?: 0L
                    val sampleRate = playbackService?.playbackManager?.sampleRate?.toLong() ?: 44100L
                    
                    if (totalFrames > 0) {
                        if (isAbModePlaying && currentFrame % 100 == 0L) { // 每隔一会儿打印一下合体进度喵
                            android.util.Log.d("MainActivity", "AB进度观察: $currentFrame / $totalFrames")
                        }
                        
                        // 防止 SeekBar 溢出（Int.MAX_VALUE 限制）
                        val maxValue = totalFrames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        val progressValue = currentFrame.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        
                        binding.seekBar.max = maxValue
                        binding.seekBar.progress = progressValue
                        
                        binding.tvCurrentTime.text = TimeUtils.formatTime(currentFrame, sampleRate)
                        binding.tvTotalTime.text = TimeUtils.formatTime(totalFrames, sampleRate)

                        if (isPlaying) {
                            // 同步底层的断线休眠状态（比如拔耳机强行中止了播放）
                            if (playbackService?.playbackManager?.isPlaying == false) {
                                viewModel.setPlaying(false)
                                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                                continue
                            }

                            // 物理文件末尾检测机制喵：进度卡住不动了（说明物理文件真播完了），或者接近绝对末尾了
                            // 留出约 1/8 秒的提前量，让切歌听起来更紧凑喵！
                            val endThreshold = (sampleRate / 8).coerceAtLeast(1024L)
                            
                            if (currentFrame == lastObservedFrame && (totalFrames - currentFrame) < sampleRate * 2) {
                                frameStallCount++
                            } else {
                                frameStallCount = 0
                            }
                            lastObservedFrame = currentFrame

                            // 达到文件末尾提前量，或者卡住了（约 400ms 没进度更新）
                            if (currentFrame >= totalFrames - endThreshold || frameStallCount >= 2) {
                                frameStallCount = 0
                                lastObservedFrame = -1L
                                
                                val nextIndex = viewModel.getNextIndex()
                                val currentMode = viewModel.playMode.value ?: com.cpu.seamlessloopmobile.viewmodel.PlayMode.LIST_LOOP
                                
                                // 如果是单曲循环，并且原生引擎已经开启了无缝循环，
                                // UI 绝对不能横插一脚去重新 playSong，否则无缝就破功了喵！
                                if (currentMode == com.cpu.seamlessloopmobile.viewmodel.PlayMode.SINGLE_LOOP) {
                                    // 什么都不做，让影子大魔王（C++引擎）自己处理无缝衔接喵
                                } else if (nextIndex != -1) {
                                    playSong(currentPlaylist[nextIndex])
                                } else {
                                    playbackService?.playbackManager?.stop()
                                    viewModel.setPlaying(false)
                                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                                }
                            }
                        }
                    }
                }
                kotlinx.coroutines.delay(50) // 提高实时性喵！50ms 是丝滑的水平线喵
            }
        }
    }



    private fun setupRecyclerView() {
        // 初始化歌曲列表适配器
        songAdapter = SongAdapter(emptyList()) { song ->
            playSong(song)
        }
        
        songAdapter.setOnLongClickListener { song ->
            selectionController.enterSelectionMode()
            songAdapter.toggleSelection(song.filePath) // 顺便把长按这首也选上喵
        }
        
        // 初始化主库列表适配器 (包含歌单和文件夹)
        libraryAdapter = com.cpu.seamlessloopmobile.adapter.LibraryAdapter(
            emptyList(),
            onPlaylistClick = { playlist -> openPlaylist(playlist) },
            onFolderClick = { folder -> openFolder(folder) },
            onQuickActionClick = { title -> 
                if (title == "本地音乐") {
                    enterLocalMusic()
                }
            },
            onPlaylistLongClick = { playlist -> selectionController.enterPlaylistSelectionMode(playlist) },
            onFolderLongClick = { folder -> selectionController.showLinkFolderDialog(folder) }
        )
        libraryAdapter.setOnSelectionChangedListener { count ->
            if (selectionController.isPlaylistSelectionMode) {
                binding.toolbar.title = "已选择歌单: $count"
                selectionController.updatePlaylistSelectionMenu()
            }
        }

        binding.rvSongs.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.rvSongs.itemAnimator = null // 取消列表刷新的闪烁动画喵！
        // 默认显示主库（歌单+文件夹）
        binding.rvSongs.adapter = libraryAdapter

        songAdapter.setOnSelectionChangedListener { count ->
            if (selectionController.isSelectionMode) {
                selectionController.updateSelectionMenu(count)
            }
        }

        // --- 初始化 SelectionController 喵 ---
        selectionController = com.cpu.seamlessloopmobile.ui.SelectionController(
            context = this,
            toolbar = binding.toolbar,
            songAdapter = songAdapter,
            libraryAdapter = libraryAdapter,
            repository = repository,
            viewModel = viewModel,
            coroutineScope = lifecycleScope,
            uiCallback = object : com.cpu.seamlessloopmobile.ui.SelectionController.SelectionUiCallback {
                override fun onExitSelection() {
                    if (isInsidePlaylist) {
                        // 如果在歌单里，退回歌单视图喵
                        currentOpenPlaylist?.let { openPlaylist(it) } ?: loadHomeView()
                    } else if (isShowingFolders || isExploringLocal) {
                        if (!isShowingFolders) {
                             val currentFolder = folders.find { it.songs.any { s -> s.filePath == currentPlaylist.firstOrNull()?.filePath } }
                             binding.toolbar.title = currentFolder?.name ?: "本地音乐"
                             binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
                             binding.toolbar.setNavigationOnClickListener { showFolderList() }
                        } else {
                             showFolderList()
                        }
                    } else {
                        loadHomeView()
                    }
                    invalidateOptionsMenu()
                }

                override fun onExitPlaylistSelection() {
                    if (isExploringLocal) showFolderList() else loadHomeView()
                }

                override fun onReloadHomeView() {
                    loadHomeView()
                }

                override fun onRefreshPlaylist(playlist: com.cpu.seamlessloopmobile.model.Playlist) {
                    openPlaylist(playlist)
                }

                override val isInsidePlaylist: Boolean
                    get() = this@MainActivity.isInsidePlaylist

                override val currentOpenPlaylist: com.cpu.seamlessloopmobile.model.Playlist?
                    get() = this@MainActivity.currentOpenPlaylist
            }
        )
        isSelectionControllerInitialized = true
    }


    private fun openPlaylist(playlist: com.cpu.seamlessloopmobile.model.Playlist) {
        currentOpenPlaylist = playlist
        viewModel.setCurrentOpenPlaylist(playlist)
        viewModel.setInsidePlaylist(true)
        viewModel.setExploringLocal(false)
        viewModel.setShowingFolders(false)
        
        lifecycleScope.launch(Dispatchers.Main) {
            val songs = repository.getSongsInPlaylist(playlist.id)
            
            // 如果是关联文件夹的歌单，且里面没歌，才主动同步喵！
            // 如果已经有歌了，就让大人先听着，莱芙不乱插手喵！
            if (playlist.isFolderLinked == 1 && songs.isEmpty()) {
                Toast.makeText(this@MainActivity, "正在同步文件夹信息喵...", Toast.LENGTH_SHORT).show()
                viewModel.refreshFolderPlaylist(this@MainActivity, playlist)
            } else {
                displayedSongs = songs
                currentPlaylist = songs
                viewModel.updateCurrentPlaylist(songs) 
                songAdapter.updateSongs(songs)
            }
            
            if (binding.rvSongs.adapter != songAdapter) {
                binding.rvSongs.adapter = songAdapter
            }
            binding.toolbar.title = "歌单: ${playlist.name}"
            binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
            binding.toolbar.setNavigationOnClickListener { 
                isInsidePlaylist = false
                loadHomeView() 
            }
        }
    }

    private fun openFolder(folder: com.cpu.seamlessloopmobile.model.Folder) {
        viewModel.setShowingFolders(false)
        viewModel.setExploringLocal(true)
        viewModel.setInsidePlaylist(false)
        displayedSongs = folder.songs // 记录当前看到的列表
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

    private fun enterLocalMusic() {
        viewModel.setExploringLocal(true)
        viewModel.setInsidePlaylist(false)
        if (folders.isEmpty()) {
            viewModel.scanLibrary(this) // 如果还没扫过，就扫一下喵
        } else {
            showFolderList()
        }
    }

    private fun showFolderList() {
        viewModel.setShowingFolders(true)
        viewModel.setExploringLocal(true)
        viewModel.setInsidePlaylist(false)
        binding.rvSongs.adapter = libraryAdapter
        binding.toolbar.title = "本地音乐"
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
        binding.toolbar.setNavigationOnClickListener { 
            isExploringLocal = false
            loadHomeView() 
        }
        
        // 同步 UI
        val libraryItems = mutableListOf<com.cpu.seamlessloopmobile.model.LibraryItem>()
        libraryItems.add(com.cpu.seamlessloopmobile.model.LibraryItem.Header("本地目录"))
        folders.forEach { folder ->
            libraryItems.add(com.cpu.seamlessloopmobile.model.LibraryItem.FolderWrapper(folder))
        }
        libraryAdapter.updateItems(libraryItems)

        // 如果是从多选模式回来的，要恢复普通菜单喵
        invalidateOptionsMenu()
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
        
        // 官方遥控器：点火！
        MediaControllerCompat.getMediaController(this)?.transportControls
            ?.playFromMediaId(song.mediaId.toString(), null)
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
            loadHomeView()
            // 偷偷在后台扫一下，不打扰大人喵
            viewModel.scanLibrary(this)
        } else {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_CODE_PERMISSION)
        }
    }

    private fun loadHomeView() {
        viewModel.setShowingFolders(false)
        viewModel.setExploringLocal(false)
        viewModel.setInsidePlaylist(false)
        
        lifecycleScope.launch(Dispatchers.Main) {
            binding.rvSongs.adapter = libraryAdapter
            binding.toolbar.title = "Seamless Loop"
            binding.toolbar.navigationIcon = null
            
            // 1. 从数据库读取歌单喵
            val dbPlaylists = repository.getAllPlaylists()
            val playlistWithCounts = dbPlaylists.map { playlist ->
                var count = repository.getSongCountInPlaylist(playlist.id)
                    
                    // 如果是联动文件夹，且数据库还没同步完，莱芙先实地数数，不让大人看到 0 喵！
                    if (playlist.isFolderLinked == 1 && count == 0 && playlist.folderPath != null) {
                        try {
                            val folder = java.io.File(playlist.folderPath)
                            val files = folder.listFiles { file ->
                                val name = file.name.lowercase()
                                name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".ogg")
                            }
                            count = files?.size ?: 0
                        } catch (e: Exception) { /* 忽略错误喵 */ }
                    }
                    Pair(playlist, count)
                }
            
            // 2. 这里的本地音乐数量先从数据库里拿个大概，或者直接显示“去探索”喵
            val localCount = repository.getAllSongs().size

            // 3. 构建主页混合列表
            val libraryItems = mutableListOf<com.cpu.seamlessloopmobile.model.LibraryItem>()
            
            // 核心功能键
            libraryItems.add(com.cpu.seamlessloopmobile.model.LibraryItem.QuickAction(
                "本地音乐", 
                android.R.drawable.ic_menu_save, 
                localCount
            ))
            
            if (playlistWithCounts.isNotEmpty()) {
                libraryItems.add(com.cpu.seamlessloopmobile.model.LibraryItem.Header("我的歌单"))
                playlistWithCounts.forEach { (playlist, count) ->
                    libraryItems.add(com.cpu.seamlessloopmobile.model.LibraryItem.PlaylistWrapper(playlist, count))
                }
            } else {
                libraryItems.add(com.cpu.seamlessloopmobile.model.LibraryItem.Header("暂无歌单"))
            }

            libraryAdapter.updateItems(libraryItems)
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