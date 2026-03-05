package com.cpu.seamlessloopmobile.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import com.cpu.seamlessloopmobile.ui.screen.category.CategoryScreen
import com.cpu.seamlessloopmobile.ui.screen.home.HomeScreen
import com.cpu.seamlessloopmobile.ui.screen.songlist.SongListScreen
import com.cpu.seamlessloopmobile.viewmodel.MainViewModel
import com.cpu.seamlessloopmobile.viewmodel.MusicUiState
import androidx.activity.compose.BackHandler

import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import com.cpu.seamlessloopmobile.ui.components.MiniPlayer
import com.cpu.seamlessloopmobile.ui.screen.playing.PlayingPanel
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    playSong: (com.cpu.seamlessloopmobile.model.Song) -> Unit,
    onSyncPc: () -> Unit
) {
    val uiState by viewModel.uiState.observeAsState(MusicUiState.Home)
    val allSongs by viewModel.allSongs.observeAsState(emptyList())
    val folders by viewModel.folders.observeAsState(emptyList())
    val albums by viewModel.albums.observeAsState(emptyList())
    val artists by viewModel.artists.observeAsState(emptyList())
    val playlistsWithCounts by viewModel.playlistsWithCounts.observeAsState(emptyList())
    val playlists by viewModel.playlists.observeAsState(emptyList())
    val currentSongIndex by viewModel.currentSongIndex.observeAsState(-1)
    val currentPlaylist by viewModel.currentPlaylist.observeAsState(emptyList())
    val isSelectionMode by viewModel.isSelectionMode.observeAsState(false)
    val selectedItems by viewModel.selectedItems.observeAsState(emptySet())
    val selectedPlaylists by viewModel.selectedPlaylists.observeAsState(emptySet())
    val playbackState by viewModel.playbackState.collectAsState()
    val isPlayingPanelVisible by viewModel.isPlayingPanelVisible.observeAsState(false)
    val syncStatus by viewModel.syncStatus.observeAsState("")
    val selectedFolders by viewModel.selectedFolders.observeAsState(emptySet())

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var playlistToDelete by remember { mutableStateOf<com.cpu.seamlessloopmobile.model.Playlist?>(null) }
    var showImportFoldersDialog by remember { mutableStateOf(false) }
    var showMergeFoldersNameDialog by remember { mutableStateOf(false) }
    var mergePlaylistName by remember { mutableStateOf("") }

    // 计算当前页面展示的歌曲，用于“全选”喵
    val songsInCurrentPage = remember(uiState, allSongs, folders, albums, artists) {
        when (val state = uiState) {
            is MusicUiState.SongList -> {
                when (state.type) {
                    MusicUiState.ListType.ALL_SONGS -> allSongs
                    MusicUiState.ListType.FOLDER -> folders.find { it.name == state.title }?.songs ?: state.songs
                    MusicUiState.ListType.ALBUM -> albums.find { it.name == state.title }?.songs ?: state.songs
                    MusicUiState.ListType.ARTIST -> artists.find { it.name == state.title }?.songs ?: state.songs
                    else -> state.songs
                }
            }
            else -> emptyList()
        }
    }

    // --- 接管返回键喵 ---
    BackHandler(enabled = isPlayingPanelVisible || isSelectionMode || uiState !is MusicUiState.Home) {
        if (isPlayingPanelVisible) {
            viewModel.setPlayingPanelVisible(false)
        } else if (isSelectionMode) {
            viewModel.clearSelection()
        } else {
            viewModel.goBack()
        }
    }

    Scaffold(

        topBar = {
            val titleStr = when (val state = uiState) {
                is MusicUiState.Home -> "Seamless Loop"
                is MusicUiState.CategoryFolders -> state.title
                is MusicUiState.SongList -> state.title
            }
            val showBack = uiState !is MusicUiState.Home

            TopAppBar(
                title = { 
                    Column {
                        Text(titleStr)
                        if (syncStatus.isNotEmpty()) {
                            Text(syncStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = { viewModel.goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (uiState is MusicUiState.Home) {
                        IconButton(onClick = onSyncPc) {
                            Icon(Icons.Default.Sync, contentDescription = "同步电脑端")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            if (!isSelectionMode) {
                MiniPlayer(
                    viewModel = viewModel,
                    onClick = { viewModel.setPlayingPanelVisible(true) }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            AnimatedContent(
                targetState = uiState,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "MainScreenNavigation"
            ) { state ->
                when (state) {
                    is MusicUiState.Home -> {
                        // 使用带真实数量的歌单列表喵
                        val playlistPairs = playlistsWithCounts.map { it.playlist to it.songCount }

                        HomeScreen(
                            localCount = allSongs.size,
                            albumsCount = albums.size,
                            artistsCount = artists.size,
                            foldersCount = folders.size,
                            playlists = playlistPairs,
                            onOpenAllSongs = {
                                viewModel.openSongList("全部歌曲", allSongs, MusicUiState.ListType.ALL_SONGS)
                            },
                            onOpenAlbums = {
                                viewModel.openCategory("专辑", albums)
                            },
                            onOpenArtists = {
                                viewModel.openCategory("歌手", artists)
                            },
                            onOpenFolders = {
                                viewModel.openCategory("文件夹", folders)
                            },
                            onOpenPlaylist = { playlist ->
                                viewModel.openPlaylist(playlist)
                            },
                            isSelectionMode = isSelectionMode,
                            selectedPlaylists = selectedPlaylists,
                            onTogglePlaylistSelection = { id -> viewModel.togglePlaylistSelection(id) }
                        )
                    }
                    is MusicUiState.CategoryFolders -> {
                        val itemsToShow = when (state.title) {
                            "专辑" -> albums
                            "歌手" -> artists
                            "文件夹" -> folders
                            else -> state.items
                        }
                        CategoryScreen(
                            items = itemsToShow,
                            onOpenFolder = { folder ->
                                val type = when {
                                    folder.path.startsWith("album_") -> MusicUiState.ListType.ALBUM
                                    folder.path.startsWith("artist_") -> MusicUiState.ListType.ARTIST
                                    else -> MusicUiState.ListType.FOLDER
                                }
                                viewModel.openSongList(folder.name, folder.songs, type, itemsToShow)
                            },
                            isSelectionMode = isSelectionMode,
                            selectedFolders = selectedFolders,
                            onToggleFolderSelection = { folder -> viewModel.toggleFolderSelection(folder) }
                        )
                    }
                    is MusicUiState.SongList -> {
                        val songsToShow = when (state.type) {
                            MusicUiState.ListType.ALL_SONGS -> allSongs
                            MusicUiState.ListType.FOLDER -> folders.find { it.name == state.title }?.songs ?: state.songs
                            MusicUiState.ListType.ALBUM -> albums.find { it.name == state.title }?.songs ?: state.songs
                            MusicUiState.ListType.ARTIST -> artists.find { it.name == state.title }?.songs ?: state.songs
                            else -> state.songs
                        }

                        val currentPlayingPath = currentPlaylist.getOrNull(currentSongIndex)?.filePath
                        
                        SongListScreen(
                            songs = songsToShow,
                            currentPlayingSongPath = currentPlayingPath,
                            isSelectionMode = isSelectionMode,
                            selectedItems = selectedItems,
                            onPlaySong = { song ->
                                playSong(song)
                            },
                            onToggleSelection = { song ->
                                if (!isSelectionMode) viewModel.setSelectionMode(true)
                                viewModel.toggleSelection(song.filePath)
                            },
                            onShowMoreOptions = { song ->
                                // TODO
                            }
                        )
                    }
                }
            }

            // --- 多选操作悬浮窗喵 ---
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedPlaylists.isNotEmpty()) {
                                Text(
                                    text = "已选 ${selectedPlaylists.size} 个歌单",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(end = 16.dp)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.deleteSelectedPlaylists() }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除歌单")
                                }
                                IconButton(onClick = { viewModel.clearSelection() }) {
                                    Icon(Icons.Default.Close, contentDescription = "取消选择")
                                }
                            } else if (selectedFolders.isNotEmpty()) {
                                Text(
                                    text = "已选 ${selectedFolders.size} 个文件夹",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(end = 16.dp)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { showImportFoldersDialog = true }) {
                                    Icon(Icons.Default.PlaylistAdd, contentDescription = "导入文件夹")
                                }
                                IconButton(onClick = { viewModel.clearSelection() }) {
                                    Icon(Icons.Default.Close, contentDescription = "取消选择")
                                }
                            } else {
                                Text(
                                    text = "已选 ${selectedItems.size} 首",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(end = 16.dp)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { 
                                    if (playlists.isNotEmpty()) {
                                        showAddToPlaylistDialog = true
                                    } else {
                                        showCreatePlaylistDialog = true
                                    }
                                }) {
                                    Icon(Icons.Default.PlaylistAdd, contentDescription = "添加到歌单")
                                }

                                IconButton(onClick = { 
                                    viewModel.selectAll(songsInCurrentPage)
                                }) {
                                    Icon(Icons.Default.SelectAll, contentDescription = "全选")
                                }
                                
                                IconButton(onClick = { 
                                    viewModel.clearSelection()
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "取消选择")
                                }
                            }
                        }
                    }
                }
            }

            // --- 全屏详情面板作为 Overlay 喵 ---
            if (isPlayingPanelVisible) {
                PlayingPanel(
                    viewModel = viewModel,
                    isVisible = true,
                    onClose = { viewModel.setPlayingPanelVisible(false) },
                    onPlayPause = { if (playbackState?.state == PlaybackStateCompat.STATE_PLAYING) viewModel.pause() else viewModel.play() },
                    onNext = { viewModel.skipToNext() },
                    onPrev = { viewModel.skipToPrevious() }
                )
            }

            // --- 创建歌单对话框喵 ---
            if (showCreatePlaylistDialog) {
                AlertDialog(
                    onDismissRequest = { showCreatePlaylistDialog = false },
                    title = { Text("创建新歌单") },
                    text = {
                        TextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("请输入歌单名称") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                viewModel.createPlaylistWithSelected(newPlaylistName)
                                newPlaylistName = ""
                                showCreatePlaylistDialog = false
                            }
                        }) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreatePlaylistDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }

            // --- 添加到现有歌单对话框喵 ---
            if (showAddToPlaylistDialog) {
                AlertDialog(
                    onDismissRequest = { showAddToPlaylistDialog = false },
                    title = { Text("添加到歌单") },
                    text = {
                        androidx.compose.foundation.lazy.LazyColumn {
                            item {
                                ListItem(
                                    headlineContent = { Text("新建歌单...") },
                                    leadingContent = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                                    modifier = Modifier.clickable {
                                        showAddToPlaylistDialog = false
                                        showCreatePlaylistDialog = true
                                    }
                                )
                            }
                            items(playlists) { playlist ->
                                ListItem(
                                    headlineContent = { Text(playlist.name) },
                                    leadingContent = { Icon(Icons.Default.QueueMusic, contentDescription = null) },
                                    modifier = Modifier.clickable {
                                        viewModel.addSelectedToPlaylist(playlist.id)
                                        showAddToPlaylistDialog = false
                                    }
                                )
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { showAddToPlaylistDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }

            // --- 导入文件夹选择对话框喵 ---
            if (showImportFoldersDialog) {
                AlertDialog(
                    onDismissRequest = { showImportFoldersDialog = false },
                    title = { Text("导入文件夹 ${selectedFolders.size} 个") },
                    text = { Text("你要如何导入这些文件夹喵？") },
                    confirmButton = {
                        Column {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    viewModel.importSelectedFoldersIndividually()
                                    showImportFoldersDialog = false
                                }
                            ) { Text("各文件夹导入各自歌单 (1:1)") }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    showImportFoldersDialog = false
                                    showMergeFoldersNameDialog = true
                                }
                            ) { Text("全部合并为一个歌单") }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportFoldersDialog = false }) { Text("取消") }
                    }
                )
            }

            // --- 合并文件夹命名对话框喵 ---
            if (showMergeFoldersNameDialog) {
                AlertDialog(
                    onDismissRequest = { showMergeFoldersNameDialog = false },
                    title = { Text("合并导入歌单名称") },
                    text = {
                        TextField(
                            value = mergePlaylistName,
                            onValueChange = { mergePlaylistName = it },
                            placeholder = { Text("请输入新歌单名称") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (mergePlaylistName.isNotBlank()) {
                                viewModel.importSelectedFoldersAsSinglePlaylist(mergePlaylistName)
                                mergePlaylistName = ""
                                showMergeFoldersNameDialog = false
                            }
                        }) { Text("确定") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showMergeFoldersNameDialog = false }) { Text("取消") }
                    }
                )
            }

            // --- 删除歌单的再确定弹窗喵 ---
            playlistToDelete?.let { targetPlaylist ->
                AlertDialog(
                    onDismissRequest = { playlistToDelete = null },
                    title = { Text("删除歌单") },
                    text = { Text("真的要删除歌单《${targetPlaylist.name}》吗？这不会影响设备上的实际音频文件喵。") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deletePlaylist(targetPlaylist)
                                playlistToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("残忍删除")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { playlistToDelete = null }) {
                            Text("算了吧")
                        }
                    }
                )
            }
        }
    }
}
