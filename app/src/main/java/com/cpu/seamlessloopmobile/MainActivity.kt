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
    private var currentPlaylist: List<Song> = emptyList()
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
            if (!isShowingFolders) {
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
                    }
                }
                kotlinx.coroutines.delay(50) // 20 FPS refresh rate
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
                             val currentFolder = folders.find { it.songs.any { s -> s.id == currentPlaylist.firstOrNull()?.id } }
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
            currentPlaylist = songs
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
        // 更新当前播放索引 (使用 ID 查找更稳健)
        val newIndex = currentPlaylist.indexOfFirst { it.id == song.id }
        if (newIndex != -1) {
            currentSongIndex = newIndex
        } else {
             android.util.Log.w("MainActivity", "Song not found in currentPlaylist!")
        }
        
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