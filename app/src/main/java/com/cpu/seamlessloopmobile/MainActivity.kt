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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var songAdapter: SongAdapter
    private lateinit var libraryAdapter: LibraryAdapter
    
    private lateinit var database: com.cpu.seamlessloopmobile.db.AppDatabase
    private val songDao by lazy { database.songDao() }
    private val playlistDao by lazy { database.playlistDao() }
    
    private lateinit var viewModel: MainViewModel

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
    private lateinit var playbackManager: com.cpu.seamlessloopmobile.audio.PlaybackManager
    private lateinit var selectionController: com.cpu.seamlessloopmobile.ui.SelectionController

    // 文件选择器喵
    private val dbPickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importFromPcDatabase(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 初始化数据库
        database = com.cpu.seamlessloopmobile.db.AppDatabase.getDatabase(this)
        
        // 逻辑大脑初始化喵！
        val factory = MainViewModelFactory(songDao, playlistDao)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        playbackManager = com.cpu.seamlessloopmobile.audio.PlaybackManager(
            context = this,
            coroutineScope = lifecycleScope,
            songDao = songDao,
            viewModel = viewModel,
            uiCallback = object : com.cpu.seamlessloopmobile.audio.PlaybackManager.PlaybackUiCallback {
                override fun onPrePlayback(message: String) {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                    updateProgressJob?.cancel()
                }

                override fun onPlaybackStarted(song: com.cpu.seamlessloopmobile.model.Song, isAbMode: Boolean) {
                    if (isAbMode) selectionController.exitSelectionMode()
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    binding.tvPlayingSongName.text = song.displayName ?: song.fileName // 报上名来喵！
                    startProgressUpdater()
                    songAdapter.setPlayingSong(song.filePath)
                }

                override fun onPlaybackError(message: String) {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 设置 Toolbar 喵
        setSupportActionBar(binding.toolbar)

        // 中间层同步：把 ViewModel 的状态同步给本地冗余变量 (过渡用喵)
        viewModel.allSongs.observe(this) { 
            allSongs = it
            if (!isExploringLocal && !isInsidePlaylist) {
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
                songAdapter.updateSongs(it)
            }
        }
        viewModel.currentSongIndex.observe(this) { currentSongIndex = it }
        viewModel.isPlaying.observe(this) { isPlaying = it }
        viewModel.isAbModePlaying.observe(this) { isAbModePlaying = it }
        viewModel.currentAbIntroSong.observe(this) { currentAbIntroSong = it }
        viewModel.currentOpenPlaylist.observe(this) { currentOpenPlaylist = it }
        viewModel.rawScannedSongs.observe(this) { rawScannedSongs = it }
        viewModel.playlists.observe(this) {
            if (!isExploringLocal && !isInsidePlaylist) {
                loadHomeView()
            }
        }
        viewModel.playMode.observe(this) { mode ->
            updatePlayModeIcon(mode)
            NativeAudio.setLooping(mode == PlayMode.SINGLE_LOOP)
        }

        // 设置返回键逻辑喵 (现代安卓做法)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        updateProgressJob?.cancel()
        NativeAudio.stopAudioEngine()
    }

    private fun setupSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val sampleRate = NativeAudio.getSampleRate().toLong()
                    binding.tvCurrentTime.text = TimeUtils.formatTime(progress.toLong(), sampleRate)
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                seekBar?.let {
                    // 调用 JNI seekTo
                    NativeAudio.seekTo(it.progress.toLong())
                    isUserSeeking = false
                }
            }
        })
    }

    private fun setupPlaybackControls() {
        // 播放/暂停按钮
        binding.btnPlayPause.setOnClickListener {
            if (isPlaying) {
                NativeAudio.pauseAudioEngine()
                isPlaying = false
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            } else {
                NativeAudio.resumeAudioEngine()
                isPlaying = true
                binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
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
            PlayMode.SEQUENCE -> android.R.drawable.ic_menu_sort_by_size // 暂代顺序
            PlayMode.LIST_LOOP -> android.R.drawable.ic_menu_rotate
            PlayMode.SINGLE_LOOP -> android.R.drawable.ic_menu_revert
            PlayMode.SHUFFLE -> android.R.drawable.ic_menu_share // 暂代随机
        }
        binding.btnPlayMode.setImageResource(iconRes)
        
        val modeName = when (mode) {
            PlayMode.SEQUENCE -> "顺序播放"
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
                    val currentFrame = NativeAudio.getCurrentPosition()
                    val totalFrames = NativeAudio.getDuration()
                    val sampleRate = NativeAudio.getSampleRate().toLong()
                    
                    if (totalFrames > 0) {
                        // 防止 SeekBar 溢出（Int.MAX_VALUE 限制）
                        val maxValue = totalFrames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        val progressValue = currentFrame.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        
                        binding.seekBar.max = maxValue
                        binding.seekBar.progress = progressValue
                        
                        binding.tvCurrentTime.text = TimeUtils.formatTime(currentFrame, sampleRate)
                        binding.tvTotalTime.text = TimeUtils.formatTime(totalFrames, sampleRate)

                        if (isPlaying) {
                            // 物理文件末尾检测机制喵：距离结尾没多远，而且进度卡住不动了（说明物理文件真播完了）
                            if (currentFrame == lastObservedFrame && (totalFrames - currentFrame) < sampleRate * 4) {
                                frameStallCount++
                            } else {
                                frameStallCount = 0
                            }
                            lastObservedFrame = currentFrame

                            // 达到文件末尾，或者卡住了（约 800ms 没进度更新）
                            if (currentFrame >= totalFrames - 512 || frameStallCount >= 4) {
                                frameStallCount = 0
                                lastObservedFrame = -1L
                                
                                val nextIndex = viewModel.getNextIndex()
                                if (nextIndex != -1) {
                                    playSong(currentPlaylist[nextIndex])
                                } else {
                                    NativeAudio.stopAudioEngine()
                                    viewModel.setPlaying(false)
                                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                                }
                            }
                        }
                    }
                }
                kotlinx.coroutines.delay(200) // 轮询频率稍微降低一点喵，省电！
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
            onPlaylistLongClick = { playlist -> selectionController.enterPlaylistSelectionMode(playlist) }
        )
        libraryAdapter.setOnSelectionChangedListener { count ->
            if (selectionController.isPlaylistSelectionMode) {
                binding.toolbar.title = "已选择歌单: $count"
                selectionController.updatePlaylistSelectionMenu()
            }
        }

        binding.rvSongs.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
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
            songDao = songDao,
            playlistDao = playlistDao,
            coroutineScope = lifecycleScope,
            uiCallback = object : com.cpu.seamlessloopmobile.ui.SelectionController.SelectionUiCallback {
                override fun onExitSelection() {
                    if (isShowingFolders || isExploringLocal) {
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
    }


    private fun openPlaylist(playlist: com.cpu.seamlessloopmobile.model.Playlist) {
        currentOpenPlaylist = playlist
        lifecycleScope.launch(Dispatchers.Main) {
            val songs = withContext(Dispatchers.IO) { playlistDao.getSongsInPlaylist(playlist.id) }
            isShowingFolders = false
            isExploringLocal = false
            isInsidePlaylist = true // 进入了歌单喵
            displayedSongs = songs // 先让大人看到
            currentPlaylist = songs // 歌单点开时，默认听单也载入
            viewModel.updateCurrentPlaylist(songs) 
            songAdapter.updateSongs(songs)
            binding.rvSongs.adapter = songAdapter
            
            binding.toolbar.title = "歌单: ${playlist.name}"
            binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
            binding.toolbar.setNavigationOnClickListener { 
                isInsidePlaylist = false
                loadHomeView() 
            }
        }
    }

    private fun openFolder(folder: com.cpu.seamlessloopmobile.model.Folder) {
        isShowingFolders = false
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
        isExploringLocal = true
        isInsidePlaylist = false
        if (folders.isEmpty()) {
            viewModel.scanLibrary(this) // 如果还没扫过，就扫一下喵
        } else {
            showFolderList()
        }
    }

    private fun showFolderList() {
        isShowingFolders = true
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
        // 如果是从 UI 点播，通常我们要把“看单”同步给“听单”喵！
        // 这样按下一首时，才会按照眼前的顺序播下去
        if (displayedSongs.contains(song)) {
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
        playbackManager.playSong(song)
    }

    private fun checkPermissionsAndLoadHome() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadHomeView()
            // 偷偷在后台扫一下，不打扰大人喵
            viewModel.scanLibrary(this)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_CODE_PERMISSION)
        }
    }

    private fun loadHomeView() {
        lifecycleScope.launch(Dispatchers.Main) {
            isShowingFolders = false
            isExploringLocal = false
            binding.rvSongs.adapter = libraryAdapter
            binding.toolbar.title = "Seamless Loop"
            binding.toolbar.navigationIcon = null
            
            // 1. 从数据库读取歌单喵
            val dbPlaylists = withContext(Dispatchers.IO) { playlistDao.getAllPlaylists() }
            val playlistWithCounts = withContext(Dispatchers.IO) {
                dbPlaylists.map { playlist ->
                    Pair(playlist, playlistDao.getSongCountInPlaylist(playlist.id))
                }
            }
            
            // 2. 这里的本地音乐数量先从数据库里拿个大概，或者直接显示“去探索”喵
            val localCount = withContext(Dispatchers.IO) { songDao.getAllSongs().size }

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
                songDao = songDao,
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