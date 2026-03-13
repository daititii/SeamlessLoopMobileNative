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
import androidx.compose.ui.tooling.preview.Preview
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
import com.cpu.seamlessloopmobile.ui.components.CentralizedDialogHost
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
    val audioPlayState by viewModel.audioPlayState.collectAsState()
    val isPlayingPanelVisible by viewModel.isPlayingPanelVisible.observeAsState(false)
    val syncStatus by viewModel.syncStatus.observeAsState("")
    val selectedFolders by viewModel.selectedFolders.observeAsState(emptySet())
    
    // --- 导航滚动位置记忆中心喵 ---
    // 我们把 LazyListState 留在 MainScreen 这里，这样当 AnimatedContent 切换内部页面时，
    // 即使旧页面被销毁了，只要 MainScreen 还在，状态就还在喵！
    val categoryScrollStates = remember { mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }
    val songListScrollStates = remember { mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }

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

    Box(modifier = Modifier.fillMaxSize()) {
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
                        val currentPlayingPath = currentPlaylist.getOrNull(currentSongIndex)?.filePath

                        CategoryScreen(
                            items = itemsToShow,
                            currentPlayingPath = currentPlayingPath,
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
                            onToggleFolderSelection = { folder -> viewModel.toggleFolderSelection(folder) },
                            listState = categoryScrollStates.getOrPut(state.title) { 
                                androidx.compose.foundation.lazy.LazyListState() 
                            }
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
                                val index = songsToShow.indexOf(song)
                                viewModel.updateCurrentPlaylist(songsToShow, index)
                                playSong(song)
                            },
                            onToggleSelection = { song ->
                                if (!isSelectionMode) viewModel.setSelectionMode(true)
                                viewModel.toggleSelection(song.filePath)
                            },
                            onShowMoreOptions = { song ->
                                // TODO
                            },
                            listState = songListScrollStates.getOrPut("${state.type}_${state.title}") {
                                androidx.compose.foundation.lazy.LazyListState()
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
                                IconButton(onClick = { 
                                    viewModel.showDialog(
                                        com.cpu.seamlessloopmobile.viewmodel.MusicDialog.ConfirmDeletePlaylist(
                                            playlist = com.cpu.seamlessloopmobile.model.Playlist(name = "选中的 ${selectedPlaylists.size} 个歌单"),
                                            onConfirm = { viewModel.deleteSelectedPlaylists() }
                                        )
                                    )
                                }) {
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
                                 IconButton(onClick = { 
                                    viewModel.showDialog(
                                        com.cpu.seamlessloopmobile.viewmodel.MusicDialog.ImportFoldersOptions(
                                            count = selectedFolders.size,
                                            onIndividual = { viewModel.importSelectedFoldersIndividually() },
                                            onMerge = { 
                                                viewModel.showDialog(
                                                    com.cpu.seamlessloopmobile.viewmodel.MusicDialog.MergeFoldersName { name ->
                                                        viewModel.importSelectedFoldersAsSinglePlaylist(name)
                                                    }
                                                )
                                            }
                                        )
                                    )
                                }) {
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
                                    val dialog = if (playlists.isNotEmpty()) {
                                        com.cpu.seamlessloopmobile.viewmodel.MusicDialog.AddToPlaylist(
                                            playlists = playlists,
                                            onAdd = { p -> viewModel.addSelectedToPlaylist(p.id) },
                                            onCreateNew = {
                                                viewModel.showDialog(
                                                    com.cpu.seamlessloopmobile.viewmodel.MusicDialog.CreatePlaylist { name ->
                                                        viewModel.createPlaylistWithSelected(name)
                                                    }
                                                )
                                            }
                                        )
                                    } else {
                                        com.cpu.seamlessloopmobile.viewmodel.MusicDialog.CreatePlaylist { name ->
                                            viewModel.createPlaylistWithSelected(name)
                                        }
                                    }
                                    viewModel.showDialog(dialog)
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
        }
    }
}

    // --- 全屏详情面板作为顶层 Overlay 喵 ---
    PlayingPanel(
        viewModel = viewModel,
        isVisible = isPlayingPanelVisible,
        onClose = { viewModel.setPlayingPanelVisible(false) },
        onPlayPause = { if (audioPlayState == com.cpu.seamlessloopmobile.audio.AudioPlayState.PLAYING) viewModel.pause() else viewModel.play() },
        onNext = { viewModel.skipToNext() },
        onPrev = { viewModel.skipToPrevious() }
    )

    // --- 全局对话框托管中心喵 ---
    CentralizedDialogHost(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MaterialTheme {
        // 在预览中，我们可以手动画一个相似的脚手架来观察布局喵！
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Seamless Loop (预览)") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("此处是 MiniPlayer 区域喵")
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("这里是主内容区域喵。\n\n您可以在 HomeScreen.kt 或 SongListScreen.kt \n中添加更具体的页面级预览！")
            }
        }
    }
}
