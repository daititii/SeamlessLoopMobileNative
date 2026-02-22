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
import android.view.MenuItem
import com.cpu.seamlessloopmobile.adapter.SongAdapter
import com.cpu.seamlessloopmobile.databinding.ActivityMainBinding
import com.cpu.seamlessloopmobile.scanner.AudioScanner
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.Playlist
import com.cpu.seamlessloopmobile.model.LibraryItem
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.SeekBar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var songAdapter: SongAdapter
    private lateinit var libraryAdapter: com.cpu.seamlessloopmobile.adapter.LibraryAdapter
    
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
    private val playlistDao by lazy { database.playlistDao() }

    // 文件选择器喵
    private val dbPickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importFromPcDatabase(it) }
    }

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
                if (isSelectionMode) {
                    exitSelectionMode()
                } else if (isPlaylistSelectionMode) {
                    exitPlaylistSelectionMode()
                } else if (!isShowingFolders) {
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
        
        songAdapter.setOnLongClickListener { song ->
            enterSelectionMode()
            songAdapter.toggleSelection(song.id) // 顺便把长按这首也选上喵
        }
        
        // 初始化主库列表适配器 (包含歌单和文件夹)
        libraryAdapter = com.cpu.seamlessloopmobile.adapter.LibraryAdapter(
            emptyList(),
            onPlaylistClick = { playlist -> openPlaylist(playlist) },
            onFolderClick = { folder -> openFolder(folder) },
            onPlaylistLongClick = { playlist -> enterPlaylistSelectionMode(playlist) }
        )
        libraryAdapter.setOnSelectionChangedListener { count ->
            if (isPlaylistSelectionMode) {
                binding.toolbar.title = "已选择歌单: $count"
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
                
        binding.toolbar.menu.add("添加到歌单").apply {
            setIcon(android.R.drawable.ic_menu_add)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setOnMenuItemClickListener {
                showAddToPlaylistDialog()
                true
            }
        }
    }

    private fun openPlaylist(playlist: com.cpu.seamlessloopmobile.model.Playlist) {
        lifecycleScope.launch(Dispatchers.Main) {
            val songs = withContext(Dispatchers.IO) { playlistDao.getSongsInPlaylist(playlist.id) }
            isShowingFolders = false
            currentPlaylist = songs
            songAdapter.updateSongs(songs)
            binding.rvSongs.adapter = songAdapter
            
            binding.toolbar.title = "歌单: ${playlist.name}"
            binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
            binding.toolbar.setNavigationOnClickListener { showFolderList() }
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

    private fun showFolderList() {
        isShowingFolders = true
        binding.rvSongs.adapter = libraryAdapter
        binding.toolbar.title = "Seamless Loop"
        binding.toolbar.navigationIcon = null
        
        // 如果是从多选模式回来的，要恢复普通菜单喵
        invalidateOptionsMenu()
    }

    private fun playSong(song: com.cpu.seamlessloopmobile.model.Song) {
        // --- 莱芙的“自动合体”魔法 (仿电脑端) ---
        val abPair = findAbPair(song)
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
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    startProgressUpdater()
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
            stopAudioEngine()
            
            try {
                val uriA = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, introSong.mediaId)
                val uriB = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, loopSong.mediaId)
                
                contentResolver.openAssetFileDescriptor(uriA, "r")?.use { afdA ->
                    contentResolver.openAssetFileDescriptor(uriB, "r")?.use { afdB ->
                        val lenA = if (afdA.declaredLength < 0) afdA.length else afdA.declaredLength
                        val lenB = if (afdB.declaredLength < 0) afdB.length else afdB.declaredLength
                        
                        startAbAudioEngine(
                            afdA.parcelFileDescriptor.fd, afdA.startOffset, lenA,
                            afdB.parcelFileDescriptor.fd, afdB.startOffset, lenB
                        )
                    }
                }
                
                withContext(Dispatchers.Main) {
                    exitSelectionMode()
                    isPlaying = true
                    binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                    startProgressUpdater()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "AB 播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun findAbPair(song: com.cpu.seamlessloopmobile.model.Song): Pair<com.cpu.seamlessloopmobile.model.Song, com.cpu.seamlessloopmobile.model.Song>? {
        val fileName = song.fileName.substringBeforeLast(".")
        val aSuffixes = arrayOf("_A", "_a", "_intro", "_Intro")
        val bSuffixes = arrayOf("_B", "_b", "_loop", "_Loop")

        for (i in aSuffixes.indices) {
            if (fileName.endsWith(aSuffixes[i])) {
                val baseName = fileName.substring(0, fileName.length - aSuffixes[i].length)
                val targetBName = baseName + bSuffixes[i]
                
                // 在所有已扫描歌曲中寻找对应的 B 段喵
                val partB = allSongs.find { 
                    it.fileName.substringBeforeLast(".") == targetBName &&
                    java.io.File(it.filePath).parent == java.io.File(song.filePath).parent
                }
                if (partB != null) return Pair(song, partB)
            }
        }
        return null
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

            // 2. 从数据库找回循环点
            val updatedSongs = scannedSongs.map { song ->
                val dbSong = songDao.getSongByPath(song.filePath)
                if (dbSong != null) {
                    song.copy(
                        id = dbSong.id,
                        loopStart = dbSong.loopStart,
                        loopEnd = dbSong.loopEnd,
                        totalSamples = dbSong.totalSamples,
                        displayName = dbSong.displayName ?: song.displayName
                    )
                } else {
                    song
                }
            }

            // 3. 同时把大人的虚拟歌单也请出来喵
            val dbPlaylists = playlistDao.getAllPlaylists()

            withContext(Dispatchers.Main) {
                allSongs = updatedSongs

                // 4. 按文件夹分组 (保持原样)
                val folderMap = mutableMapOf<String, MutableList<com.cpu.seamlessloopmobile.model.Song>>()
                for (song in allSongs) {
                    val parentPath = java.io.File(song.filePath).parent ?: "Unknown"
                    folderMap.getOrPut(parentPath) { mutableListOf() }.add(song)
                }
                
                folders = folderMap.map { (path, songs) ->
                    val name = try { java.io.File(path).name } catch (e: Exception) { path }
                    com.cpu.seamlessloopmobile.model.Folder(name, path, songs.size, songs)
                }.sortedBy { it.name }

                // 5. 构建全新的混合列表 LibraryItems 喵！
                val libraryItems = mutableListOf<com.cpu.seamlessloopmobile.model.LibraryItem>()
                
                // 歌单放在最高位！
                if (dbPlaylists.isNotEmpty()) {
                    libraryItems.add(com.cpu.seamlessloopmobile.model.LibraryItem.Header("我的歌单"))
                    dbPlaylists.forEach { playlist ->
                        val count = withContext(Dispatchers.IO) { playlistDao.getSongCountInPlaylist(playlist.id) }
                        libraryItems.add(com.cpu.seamlessloopmobile.model.LibraryItem.PlaylistWrapper(playlist, count))
                    }
                }
                
                // 接着是文件夹
                libraryItems.add(com.cpu.seamlessloopmobile.model.LibraryItem.Header("本地目录"))
                folders.forEach { folder ->
                    libraryItems.add(com.cpu.seamlessloopmobile.model.LibraryItem.FolderWrapper(folder))
                }

                libraryAdapter.updateItems(libraryItems)
                showFolderList()
            }
        }
    }

    private var isSelectionMode = false

    private fun enterSelectionMode() {
        if (isSelectionMode) return
        isSelectionMode = true
        songAdapter.setSelectionMode(true)
        
        // 变换 Toolbar
        updateSelectionMenu(songAdapter.getSelectedSongIds().size)
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
        binding.toolbar.setNavigationOnClickListener {
            exitSelectionMode()
        }
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        songAdapter.setSelectionMode(false)
        
        if (isShowingFolders) {
            showFolderList()
        } else {
            // 恢复当前文件夹的标题
            val currentFolder = folders.find { it.songs.any { s -> s.id == currentPlaylist.firstOrNull()?.id } }
            binding.toolbar.title = currentFolder?.name ?: "Seamless Loop"
            binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert)
            binding.toolbar.setNavigationOnClickListener { showFolderList() }
        }
        
        // 恢复原有菜单
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
        
        binding.toolbar.menu.clear()
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
                            scanSongs()
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
        showFolderList() // 还原顶部菜单栏喵
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
                            scanSongs() // 莱芙立刻重新加载列表，让歌单露脸喵！
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
                scanSongs() // 同步刷新喵！
            }
        }
    }

    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else if (isPlaylistSelectionMode) {
            exitPlaylistSelectionMode()
        } else if (!isShowingFolders) {
            showFolderList()
        } else {
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
        var song = originalSong 
        
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_loop_controls, null)
        dialog.setContentView(view)

        val etStartSamples = view.findViewById<android.widget.EditText>(R.id.et_loop_start_samples)
        val etStartTime = view.findViewById<android.widget.EditText>(R.id.et_loop_start_time)
        val etEndSamples = view.findViewById<android.widget.EditText>(R.id.et_loop_end_samples)
        val etEndTime = view.findViewById<android.widget.EditText>(R.id.et_loop_end_time)

        // 辅助：更新显示（仿电脑端）
        fun updateDisplay() {
            val sampleRate = getSampleRate().toLong()
            val safeSampleRate = if (sampleRate > 0) sampleRate else 44100L
            
            // 采样数 (只有在没焦点时才更新，防止干扰大人输入喵)
            if (!etStartSamples.hasFocus()) etStartSamples.setText(song.loopStart.toString())
            if (!etEndSamples.hasFocus()) etEndSamples.setText(song.loopEnd.toString())
            
            // 秒数（带3位小数）
            val startSec = song.loopStart.toDouble() / safeSampleRate
            val endSec = song.loopEnd.toDouble() / safeSampleRate
            if (!etStartTime.hasFocus()) etStartTime.setText(String.format("%.3f", startSec))
            if (!etEndTime.hasFocus()) etEndTime.setText(String.format("%.3f", endSec))
        }
        
        fun applyUpdate(start: Long, end: Long) {
            val updatedSong = updateLoopPoints(song, start, end)
            if (updatedSong != null) {
                song = updatedSong
                updateDisplay()
            }
        }
        
        updateDisplay()

        // --- 手动输入监听逻辑喵 ---
        val onSamplesChanged = { isStart: Boolean, text: String ->
            val value = text.toLongOrNull() ?: 0L
            if (isStart) applyUpdate(value, song.loopEnd)
            else applyUpdate(song.loopStart, value)
        }
        
        val onTimeChanged = { isStart: Boolean, text: String ->
            val sec = text.toDoubleOrNull() ?: 0.0
            val sampleRate = getSampleRate()
            val value = (sec * sampleRate).toLong()
            if (isStart) applyUpdate(value, song.loopEnd)
            else applyUpdate(song.loopStart, value)
        }

        // 莱芙加上“离焦保存”魔法，只要大人点别的地方，我们就存一下喵！
        val focusListener = android.view.View.OnFocusChangeListener { v, hasFocus ->
            if (!hasFocus && v is android.widget.EditText) {
                when (v.id) {
                    R.id.et_loop_start_samples -> onSamplesChanged(true, v.text.toString())
                    R.id.et_loop_end_samples -> onSamplesChanged(false, v.text.toString())
                    R.id.et_loop_start_time -> onTimeChanged(true, v.text.toString())
                    R.id.et_loop_end_time -> onTimeChanged(false, v.text.toString())
                }
            }
        }
        etStartSamples.onFocusChangeListener = focusListener
        etEndSamples.onFocusChangeListener = focusListener
        etStartTime.onFocusChangeListener = focusListener
        etEndTime.onFocusChangeListener = focusListener

        etStartSamples.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                onSamplesChanged(true, v.text.toString()); v.clearFocus(); true
            } else false
        }
        etEndSamples.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                onSamplesChanged(false, v.text.toString()); v.clearFocus(); true
            } else false
        }
        etStartTime.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                onTimeChanged(true, v.text.toString()); v.clearFocus(); true
            } else false
        }
        etEndTime.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                onTimeChanged(false, v.text.toString()); v.clearFocus(); true
            } else false
        }

        // --- A 点 (Start) 逻辑 ---
        val adjustA = { deltaMs: Double ->
            val sampleRate = getSampleRate()
            val deltaSamples = (sampleRate * deltaMs / 1000.0).toLong()
            val dur = getDuration()
            val newStart = (song.loopStart + deltaSamples).coerceIn(0, if (song.loopEnd > 0) song.loopEnd else dur)
            applyUpdate(newStart, song.loopEnd)
        }

        view.findViewById<Button>(R.id.btn_a_min).setOnClickListener { applyUpdate(0, song.loopEnd) }
        view.findViewById<Button>(R.id.btn_a_max).setOnClickListener { applyUpdate(song.loopEnd.coerceAtLeast(0), song.loopEnd) } // A 追上 B
        view.findViewById<Button>(R.id.btn_a_set_current).setOnClickListener { 
            val curr = getCurrentPosition()
            if (curr < song.loopEnd || song.loopEnd == 0L) applyUpdate(curr, song.loopEnd)
        }

        view.findViewById<Button>(R.id.btn_a_dec_5s).setOnClickListener { adjustA(-5000.0) }
        view.findViewById<Button>(R.id.btn_a_dec_1s).setOnClickListener { adjustA(-1000.0) }
        view.findViewById<Button>(R.id.btn_a_inc_1s).setOnClickListener { adjustA(1000.0) }
        view.findViewById<Button>(R.id.btn_a_inc_5s).setOnClickListener { adjustA(5000.0) }
        
        view.findViewById<Button>(R.id.btn_a_dec_01s).setOnClickListener { adjustA(-100.0) }
        view.findViewById<Button>(R.id.btn_a_dec_001s).setOnClickListener { adjustA(-10.0) }
        view.findViewById<Button>(R.id.btn_a_inc_001s).setOnClickListener { adjustA(10.0) }
        view.findViewById<Button>(R.id.btn_a_inc_01s).setOnClickListener { adjustA(100.0) }

        // --- B 点 (End) 逻辑 ---
        val adjustB = { deltaMs: Double ->
            val sampleRate = getSampleRate()
            val deltaSamples = (sampleRate * deltaMs / 1000.0).toLong()
            val dur = getDuration()
            val newEnd = (song.loopEnd + deltaSamples).coerceIn(song.loopStart, dur)
            applyUpdate(song.loopStart, newEnd)
        }

        view.findViewById<Button>(R.id.btn_b_min).setOnClickListener { applyUpdate(song.loopStart, song.loopStart) } // B 追回 A
        view.findViewById<Button>(R.id.btn_b_max).setOnClickListener { applyUpdate(song.loopStart, getDuration()) }
        view.findViewById<Button>(R.id.btn_b_set_current).setOnClickListener { 
            val curr = getCurrentPosition()
            if (curr > song.loopStart) applyUpdate(song.loopStart, curr)
        }

        view.findViewById<Button>(R.id.btn_b_dec_5s).setOnClickListener { adjustB(-5000.0) }
        view.findViewById<Button>(R.id.btn_b_dec_1s).setOnClickListener { adjustB(-1000.0) }
        view.findViewById<Button>(R.id.btn_b_inc_1s).setOnClickListener { adjustB(1000.0) }
        view.findViewById<Button>(R.id.btn_b_inc_5s).setOnClickListener { adjustB(5000.0) }
        
        view.findViewById<Button>(R.id.btn_b_dec_01s).setOnClickListener { adjustB(-100.0) }
        view.findViewById<Button>(R.id.btn_b_dec_001s).setOnClickListener { adjustB(-10.0) }
        view.findViewById<Button>(R.id.btn_b_inc_001s).setOnClickListener { adjustB(10.0) }
        view.findViewById<Button>(R.id.btn_b_inc_01s).setOnClickListener { adjustB(100.0) }

        // --- 播放控制逻辑 ---
        val btnPlayPause = view.findViewById<ImageButton>(R.id.btn_dialog_play_pause)
        val btnPrev = view.findViewById<ImageButton>(R.id.btn_dialog_prev)
        val btnNext = view.findViewById<ImageButton>(R.id.btn_dialog_next)

        fun updatePlayPauseIcon() {
            btnPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }
        updatePlayPauseIcon()

        btnPlayPause.setOnClickListener { binding.btnPlayPause.performClick(); updatePlayPauseIcon() }
        btnPrev.setOnClickListener { binding.btnPrevious.performClick() }
        btnNext.setOnClickListener { binding.btnNext.performClick() }

        // --- 进度条逻辑 ---
        val sbProgress = view.findViewById<SeekBar>(R.id.sb_dialog_progress)
        val tvCurrentTimeView = view.findViewById<TextView>(R.id.tv_dialog_current_time)
        val tvTotalTimeView = view.findViewById<TextView>(R.id.tv_dialog_total_time)
        
        var dialogUpdateJob: kotlinx.coroutines.Job? = null
        var isDialogSeeking = false
        
        dialogUpdateJob = lifecycleScope.launch(Dispatchers.Main) {
            while (true) {
                if (!isDialogSeeking) {
                    val currentFrame = getCurrentPosition()
                    val totalFrames = getDuration()
                    val sampleRate = getSampleRate().toLong()
                    
                    if (totalFrames > 0) {
                        sbProgress.max = totalFrames.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        sbProgress.progress = currentFrame.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        tvCurrentTimeView.text = formatTime(currentFrame, sampleRate)
                        tvTotalTimeView.text = formatTime(totalFrames, sampleRate)
                        updatePlayPauseIcon()
                    }
                }
                kotlinx.coroutines.delay(50)
            }
        }
        
        sbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { if(f) tvCurrentTimeView.text = formatTime(p.toLong(), getSampleRate().toLong()) }
            override fun onStartTrackingTouch(s: SeekBar?) { isDialogSeeking = true }
            override fun onStopTrackingTouch(s: SeekBar?) { s?.let { seekTo(it.progress.toLong()); isDialogSeeking = false } }
        })
        
        // 莱芙的“临终遗愿”魔法，谁有焦点就救谁喵！
        fun forceSyncAll() {
            // A点
            if (etStartTime.hasFocus()) onTimeChanged(true, etStartTime.text.toString())
            else onSamplesChanged(true, etStartSamples.text.toString())
            
            // B点
            if (etEndTime.hasFocus()) onTimeChanged(false, etEndTime.text.toString())
            else onSamplesChanged(false, etEndSamples.text.toString())
        }

        dialog.setOnDismissListener { 
            dialogUpdateJob?.cancel() 
        }
        
        view.findViewById<Button>(R.id.btn_audition).setOnClickListener {
            // 先同步一下大人可能刚输入完还没回车的 B 点
            forceSyncAll()
            
            val sampleRate = getSampleRate().toLong()
            val safeSampleRate = if (sampleRate > 0) sampleRate else 44100L
            
            // 同步完数据后拿到底层最新的总长
            val totalDur = getDuration()
            val actualEnd = if (song.loopEnd > 0) song.loopEnd else totalDur
            
            // 计算终点前 3 秒的位置，确保不小于 0 也不大于实际终点喵
            val seekPos = (actualEnd - (safeSampleRate * 3)).coerceIn(0, actualEnd)
            
            seekTo(seekPos)
            
            // 如果大人现在没在听，莱芙帮您点下播放！
            if (!isPlaying) {
                binding.btnPlayPause.performClick()
                updatePlayPauseIcon()
            }
        }
        
        view.findViewById<Button>(R.id.btn_close_dialog).setOnClickListener { 
            // 按钮按下的瞬间，不管大人有没有点回车，莱芙抢先一步全拿走！
            forceSyncAll()
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
                    val pcFileName = pcRecord.first
                    val pcTotal = pcRecord.second
                    val pcSong = pcRecord.third

                    // 在本地找找看
                    val localMatch = currentSongs.find { 
                        val localFileName = java.io.File(it.filePath).name
                        localFileName == pcFileName && (it.totalSamples == pcTotal || it.totalSamples == 0L)
                    }

                    if (localMatch != null) {
                        val updated = localMatch.copy(
                            loopStart = pcSong.loopStart,
                            loopEnd = pcSong.loopEnd,
                            displayName = pcSong.displayName ?: localMatch.displayName,
                            totalSamples = if (localMatch.totalSamples == 0L) pcTotal else localMatch.totalSamples
                        )
                        songDao.insertOrUpdateSong(updated)
                        syncCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "同步完成喵！成功找回 $syncCount 条循环数据", Toast.LENGTH_LONG).show()
                    // 重新扫描以刷新 UI
                    scanSongs()
                }

            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Sync failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "同步失败了(>_<): ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- JNI 接口 ---
    external fun stringFromJNI(): String
    external fun startAudioEngine(fd: Int, offset: Long, length: Long)
    external fun startAbAudioEngine(fdA: Int, offsetA: Long, lengthA: Long, fdB: Int, offsetB: Long, lengthB: Long)
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