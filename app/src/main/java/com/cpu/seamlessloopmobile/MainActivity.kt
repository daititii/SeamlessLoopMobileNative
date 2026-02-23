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
                if (isSelectionMode) {
                    exitSelectionMode()
                } else if (isPlaylistSelectionMode) {
                    exitPlaylistSelectionMode()
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
            enterSelectionMode()
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
            onPlaylistLongClick = { playlist -> enterPlaylistSelectionMode(playlist) }
        )
        libraryAdapter.setOnSelectionChangedListener { count ->
            if (isPlaylistSelectionMode) {
                binding.toolbar.title = "已选择歌单: $count"
                updatePlaylistSelectionMenu()
            }
        }

        binding.rvSongs.layoutManager = LinearLayoutManager(this)
        // 默认显示主库（歌单+文件夹）
        binding.rvSongs.adapter = libraryAdapter

        songAdapter.setOnSelectionChangedListener { count ->
            if (isSelectionMode) {
                updateSelectionMenu(count)
            }
        }
    }

    private fun updateSelectionMenu(count: Int) {
        binding.toolbar.title = "已选择: $count"
        binding.toolbar.menu.clear()

        binding.toolbar.menu.add(if (songAdapter.isAllSelected()) "全不选" else "全选").apply {
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setOnMenuItemClickListener {
                songAdapter.selectAll()
                true
            }
        }
                
        binding.toolbar.menu.add("添加到歌单").apply {
            setIcon(android.R.drawable.ic_menu_add)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setOnMenuItemClickListener {
                showAddToPlaylistDialog()
                true
            }
        }

        if (isInsidePlaylist) {
            binding.toolbar.menu.add("从歌单移除").apply {
                setIcon(android.R.drawable.ic_menu_delete)
                setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
                setOnMenuItemClickListener {
                    val selectedSongs = songAdapter.getSelectedSongs()
                    currentOpenPlaylist?.let { playlist ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            playlistDao.removeSongsFromPlaylist(playlist.id, selectedSongs.map { it.id })
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity, "已从歌单移除 ${selectedSongs.size} 首歌曲喵", Toast.LENGTH_SHORT).show()
                                exitSelectionMode()
                                openPlaylist(playlist) // 重新刷新歌单内容喵
                            }
                        }
                    }
                    true
                }
            }
        }
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
        // --- 莱芙的“自动合体”魔法 (仿电脑端) ---
        val abPair = viewModel.findAbPair(song)
        if (abPair != null) {
            playAbSong(abPair.first, abPair.second)
            return
        }

        // 更新当前播放索引 (使用 ID 查找更稳健)
        val newIndex = currentPlaylist.indexOfFirst { it.id == song.id }
        if (newIndex != -1) {
            currentSongIndex = newIndex
        } else {
             // 如果这首歌不在当前列表里（比如跨文件夹播放），暂时不处理或添加到列表
             android.util.Log.w("MainActivity", "Song not found in currentPlaylist!")
        }
        
        isAbModePlaying = false // 常规播放
        // 弹出提示
        Toast.makeText(this, "正在为您疯狂解码: ${song.displayName}...", Toast.LENGTH_SHORT).show()

        // 开启后台协程，避免卡死 cpu 大人的 UI
        lifecycleScope.launch(Dispatchers.IO) {
            // 停止之前的播放
            updateProgressJob?.cancel()
            NativeAudio.stopAudioEngine()
            
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
                    NativeAudio.startAudioEngine(
                        afd.parcelFileDescriptor.fd,
                        afd.startOffset,
                        actualLength
                    )
                }

                // 设置循环点（如果数据库里有的话）
                if (song.loopEnd > 0) {
                    NativeAudio.setLoopPoints(song.loopStart, song.loopEnd)
                }

                // 获取总帧数并同步回数据库喵（指纹采集）
                val durationFrames = NativeAudio.getDuration()
                if (durationFrames > 0) {
                    val updatedWithSamples = song.copy(totalSamples = durationFrames)
                    songDao.insertOrUpdateSong(updatedWithSamples)
                    withContext(Dispatchers.Main) {
                        // 同步更新内存，防止 UI 显示滞后
                        allSongs = allSongs.map { if (it.filePath == song.filePath) updatedWithSamples else it }
                        currentPlaylist = currentPlaylist.map { if (it.filePath == song.filePath) updatedWithSamples else it }
                    }
                }

                // 启动 UI 更新
                withContext(Dispatchers.Main) {
                    isPlaying = true
                    viewModel.setPlaying(true)
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    startProgressUpdater()
                    songAdapter.setPlayingSong(song.filePath) // 告诉适配器谁在唱歌喵！
                }

            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to open audio FD", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "无法打开音频文件喵...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun playAbSong(introSong: com.cpu.seamlessloopmobile.model.Song, loopSong: com.cpu.seamlessloopmobile.model.Song) {
        // 弹出提示
        Toast.makeText(this, "正在为您合成 AB 循环: ${introSong.displayName} + ${loopSong.displayName}", Toast.LENGTH_LONG).show()

        lifecycleScope.launch(Dispatchers.IO) {
            updateProgressJob?.cancel()
            NativeAudio.stopAudioEngine()
            
            try {
                val uriA = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, introSong.mediaId)
                val uriB = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, loopSong.mediaId)
                
                contentResolver.openAssetFileDescriptor(uriA, "r")?.use { afdA ->
                    contentResolver.openAssetFileDescriptor(uriB, "r")?.use { afdB ->
                        val lenA = if (afdA.declaredLength < 0) afdA.length else afdA.declaredLength
                        val lenB = if (afdB.declaredLength < 0) afdB.length else afdB.declaredLength
                        
                        NativeAudio.startAbAudioEngine(
                            afdA.parcelFileDescriptor.fd, afdA.startOffset, lenA,
                            afdB.parcelFileDescriptor.fd, afdB.startOffset, lenB
                        )
                    }
                }

                // --- 莱芙的循环记忆回溯喵 ---
                if (introSong.loopEnd > 0) {
                    NativeAudio.setLoopPoints(introSong.loopStart, introSong.loopEnd)
                }

                // 获取总帧数并同步回数据库喵
                val durationFrames = NativeAudio.getDuration()
                if (durationFrames > 0) {
                    val updatedWithSamples = introSong.copy(totalSamples = durationFrames)
                    songDao.insertOrUpdateSong(updatedWithSamples)
                }
                
                withContext(Dispatchers.Main) {
                    exitSelectionMode()
                    currentAbIntroSong = introSong
                    viewModel.setCurrentAbIntroSong(introSong)
                    isAbModePlaying = true // 这是 AB 魔法大合体！
                    viewModel.setAbModePlaying(true)
                    isPlaying = true
                    viewModel.setPlaying(true)
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    startProgressUpdater()
                    songAdapter.setPlayingSong(introSong.filePath) // AB 时高亮 A 段喵！
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "AB 播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
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

    private var isSelectionMode = false

    private fun enterSelectionMode() {
        if (isSelectionMode) return
        isSelectionMode = true
        songAdapter.setSelectionMode(true)
        updateSelectionMenu(songAdapter.getSelectedSongPaths().size)
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
        binding.toolbar.setNavigationOnClickListener { exitSelectionMode() }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        songAdapter.setSelectionMode(false)
        if (isShowingFolders || isExploringLocal) {
            if (!isShowingFolders) {
                 // 在歌曲列表层级喵
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

    private var isPlaylistSelectionMode = false

    private fun enterPlaylistSelectionMode(initialPlaylist: com.cpu.seamlessloopmobile.model.Playlist?) {
        if (isPlaylistSelectionMode) return
        isPlaylistSelectionMode = true
        libraryAdapter.setSelectionMode(true)
        if (initialPlaylist != null) {
            libraryAdapter.toggleSelection(initialPlaylist.id)
        }
        
        binding.toolbar.title = "已选择歌单: ${libraryAdapter.getSelectedPlaylists().size}"
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
        binding.toolbar.setNavigationOnClickListener {
            exitPlaylistSelectionMode()
        }
        
        updatePlaylistSelectionMenu()
    }

    private fun updatePlaylistSelectionMenu() {
        binding.toolbar.menu.clear()
        
        binding.toolbar.menu.add(if (libraryAdapter.isAllSelected()) "全不选" else "全选").apply {
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setOnMenuItemClickListener {
                libraryAdapter.selectAll()
                true
            }
        }

        binding.toolbar.menu.add("删除已选").setIcon(android.R.drawable.ic_menu_delete).setShowAsActionFlags(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM).setOnMenuItemClickListener {
            val selected = libraryAdapter.getSelectedPlaylists()
            if (selected.isEmpty()) {
                Toast.makeText(this@MainActivity, "请先选择歌单喵", Toast.LENGTH_SHORT).show()
                return@setOnMenuItemClickListener true
            }
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("批量删除歌单")
                .setMessage("cpu 大人，真的要心碎地删除这 ${selected.size} 个歌单吗？")
                .setPositiveButton("删除") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        selected.forEach { playlistDao.deletePlaylist(it) }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "成功删除这 ${selected.size} 个歌单喵!", Toast.LENGTH_SHORT).show()
                            exitPlaylistSelectionMode()
                            loadHomeView() // 刷新主页数量喵
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
    }

    private fun exitPlaylistSelectionMode() {
        isPlaylistSelectionMode = false
        libraryAdapter.setSelectionMode(false)
        if (isExploringLocal) showFolderList() else loadHomeView()
    }

    private fun showAddToPlaylistDialog() {
        val selectedSongs = songAdapter.getSelectedSongs()
        if (selectedSongs.isEmpty()) {
            Toast.makeText(this, "请先选择歌曲喵", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.Main) {
            val playlists = withContext(Dispatchers.IO) { playlistDao.getAllPlaylists() }
            
            val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("添加到歌单")
            
            val items = playlists.map { it.name }.toMutableList()
            items.add("+ 新建歌单")
            
            dialog.setItems(items.toTypedArray()) { _, which ->
                if (which == items.size - 1) {
                    showCreatePlaylistDialog(selectedSongs)
                } else {
                    val targetPlaylist = playlists[which]
                    addSongsToExistingPlaylist(targetPlaylist, selectedSongs)
                }
            }
            dialog.show()
        }
    }

    private fun showCreatePlaylistDialog(songs: List<Song>) {
        val editText = android.widget.EditText(this)
        editText.hint = "歌单名称"
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("新建歌单")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val name = editText.text.toString()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        // 1. 先确保所有选择的歌曲都已经在数据库里“挂号”了喵，拿到真正的 ID
                        val persistentSongIds = songs.map { song ->
                            songDao.insertOrUpdateSong(song)
                        }
                        
                        // 2. 创建歌单并关联
                        val newId = playlistDao.insertPlaylist(com.cpu.seamlessloopmobile.model.Playlist(name = name))
                        playlistDao.addSongsToPlaylist(newId.toInt(), persistentSongIds)
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "成功创建歌单: $name 喵!", Toast.LENGTH_SHORT).show()
                            exitSelectionMode()
                            loadHomeView() // 刷新主页喵！
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addSongsToExistingPlaylist(playlist: Playlist, songs: List<Song>) {
        lifecycleScope.launch(Dispatchers.IO) {
            // 1. 同样要先入库拿到真 ID 喵
            val persistentSongIds = songs.map { song ->
                songDao.insertOrUpdateSong(song)
            }
            
            // 2. 关联到现有歌单
            playlistDao.addSongsToPlaylist(playlist.id, persistentSongIds)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "已添加到 ${playlist.name} 喵!", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                loadHomeView() // 刷新主页喵！
            }
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
            try {
                // 1. 拷贝到临时文件，因为 SQLite 不直接支持 ContentUri 喵
                val tempFile = java.io.File(cacheDir, "temp_pc_data.db")
                contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 2. 暴力开启外部数据库
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    tempFile.absolutePath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )

                val cursor = db.rawQuery("SELECT FileName, TotalSamples, LoopStart, LoopEnd, DisplayName FROM LoopPoints", null)
                var syncCount = 0

                val pcData = mutableListOf<Triple<String, Long, com.cpu.seamlessloopmobile.model.Song>>()

                if (cursor.moveToFirst()) {
                    do {
                        val fileName = cursor.getString(0) ?: ""
                        val total = cursor.getLong(1)
                        val start = cursor.getLong(2)
                        val end = cursor.getLong(3)
                        val name = cursor.getString(4)

                        // 构造一个临时的 Song 对象用于存储数据喵
                        val dummySong = com.cpu.seamlessloopmobile.model.Song(
                            fileName = fileName,
                            filePath = "", // 稍后匹配
                            displayName = name,
                            loopStart = start,
                            loopEnd = end,
                            totalSamples = total,
                            mediaId = 0
                        )
                        pcData.add(Triple(fileName, total, dummySong))
                    } while (cursor.moveToNext())
                }
                cursor.close()
                db.close()
                tempFile.delete()

                // 3. 开始对碰！
                // 莱芙用文件名和总采样数双重匹配最稳健
                val currentSongs = allSongs 
                var matchLog = StringBuilder()

                for (pcRecord in pcData) {
                    val pcSong = pcRecord.third
                    
                    // --- 灵魂锚点理论喵！ ---
                    // 不管手机里现在有没有这首歌的文件，
                    // 莱芙都先把这份来自 PC 的“循环记忆”存在数据库里。
                    // 等以后大人把文件拷进来，scanSongs 就会通过指纹自动认亲喵！
                    songDao.insertOrUpdateSong(pcSong)
                    syncCount++
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "同步完成喵！成功找回 $syncCount 条循环数据", Toast.LENGTH_LONG).show()
                    // 重新扫描以刷新 UI
                    viewModel.scanLibrary(this@MainActivity)
                }

            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Sync failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "同步失败了(>_<): ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }



    companion object {
        private const val REQUEST_CODE_PERMISSION = 1001
    }
}