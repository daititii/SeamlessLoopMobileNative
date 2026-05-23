package com.cpu.seamlessloopmobile.ui.screen

import androidx.compose.animation.AnimatedContent
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
import com.cpu.seamlessloopmobile.ui.screen.songlist.SongListScreen
import com.cpu.seamlessloopmobile.ui.components.common.CategoryListItem
import com.cpu.seamlessloopmobile.viewmodel.MainViewModel
import com.cpu.seamlessloopmobile.viewmodel.MusicUiState
import androidx.activity.compose.BackHandler

import com.cpu.seamlessloopmobile.ui.components.app.MiniPlayer
import com.cpu.seamlessloopmobile.ui.components.app.CentralizedDialogHost
import com.cpu.seamlessloopmobile.ui.components.app.PlayingPanel
import com.cpu.seamlessloopmobile.ui.components.app.MultiSelectBar

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

// Grid & Pager imports
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager

import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    playSong: (com.cpu.seamlessloopmobile.model.Song) -> Unit,
    onSyncPc: () -> Unit
) {
    val uiState by viewModel.uiState.observeAsState(MusicUiState.Home)
    val allSongs by viewModel.allSongs.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val playlistsWithCounts by viewModel.playlistsWithCounts.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val currentSongIndex by viewModel.currentSongIndex.observeAsState(-1)
    val currentPlaylist by viewModel.currentPlaylist.observeAsState(emptyList())
    val isSelectionMode by viewModel.isSelectionMode.observeAsState(false)
    val selectedItems by viewModel.selectedItems.observeAsState(emptySet())
    val selectedPlaylists by viewModel.selectedPlaylists.observeAsState(emptySet())
    val audioPlayState by viewModel.audioPlayState.collectAsState()
    val isPlayingPanelVisible by viewModel.isPlayingPanelVisible.observeAsState(false)
    val syncStatus by viewModel.syncStatus.collectAsState()
    val selectedFolders by viewModel.selectedFolders.observeAsState(emptySet())
    
    // --- 导航滚动位置记忆中心喵 ---
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
                    MusicUiState.ListType.FAVORITES -> favorites
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
                    is MusicUiState.SongList -> state.title
                    else -> "Seamless Loop"
                }
                val showBack = uiState !is MusicUiState.Home

                TopAppBar(
                    title = { 
                        Column {
                            Text(titleStr)
                            when (val status = syncStatus) {
                                is com.cpu.seamlessloopmobile.ui.state.DataUiState.Loading -> {
                                    Text("🔍 寻找新曲子中...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                is com.cpu.seamlessloopmobile.ui.state.DataUiState.Error -> {
                                    Text("❌ 扫描失败: ${status.message}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                                is com.cpu.seamlessloopmobile.ui.state.DataUiState.Success -> {
                                    if (status.data.isNotEmpty()) {
                                        Text(status.data, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
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
            val coroutineScope = rememberCoroutineScope()
            
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                // 1. 扁平 Tab 导航底层 (在进入二级页时我们不销毁它以保持状态)
                Column(modifier = Modifier.fillMaxSize()) {
                    val tabs = listOf("歌单", "专辑", "歌手", "文件夹")
                    val pagerState = rememberPagerState { tabs.size }
                    
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        edgePadding = 16.dp,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                text = { Text(title, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                    
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) { page ->
                        when (page) {
                            0 -> { // 歌单 Tab (全部歌曲 + 已评分 + 自定义歌单列表)
                                val playlistPairs = playlistsWithCounts.map { it.playlist to it.songCount }
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    // 1. 全部歌曲
                                    item {
                                        CategoryListItem(
                                            title = "全部歌曲",
                                            subtitle = "${allSongs.size} 首歌曲",
                                            icon = Icons.Default.MusicNote,
                                            isSelected = false,
                                            onClick = {
                                                viewModel.openSongList("全部歌曲", allSongs, MusicUiState.ListType.ALL_SONGS)
                                            }
                                        )
                                    }
                                    
                                    // 2. 已评分
                                    item {
                                        CategoryListItem(
                                            title = "已评分",
                                            subtitle = "${favorites.size} 首歌曲",
                                            icon = Icons.Default.Star,
                                            isSelected = false,
                                            onClick = {
                                                viewModel.openSongList("已评分", favorites, MusicUiState.ListType.FAVORITES)
                                            }
                                        )
                                    }

                                    // 3. 自定义歌单列表
                                    items(playlistPairs) { (playlist, count) ->
                                        val isSelected = selectedPlaylists.contains(playlist.id)
                                        CategoryListItem(
                                            title = playlist.name,
                                            subtitle = "${count}首" + if (playlist.isFolderLinked == 1) " · 联动" else "",
                                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                                            isSelected = isSelected,
                                            isSelectionMode = isSelectionMode,
                                            onClick = {
                                                if (isSelectionMode) {
                                                    viewModel.togglePlaylistSelection(playlist.id)
                                                } else {
                                                    viewModel.openPlaylist(playlist)
                                                }
                                            },
                                            onLongClick = { viewModel.togglePlaylistSelection(playlist.id) }
                                        )
                                    }
                                }
                            }
                            1 -> { // 专辑 Tab
                                val currentPlayingPath = currentPlaylist.getOrNull(currentSongIndex)?.filePath
                                CategoryScreen(
                                    items = albums,
                                    currentPlayingPath = currentPlayingPath,
                                    onOpenFolder = { folder ->
                                        viewModel.openSongList(folder.name, folder.songs, MusicUiState.ListType.ALBUM, albums)
                                    },
                                    isSelectionMode = isSelectionMode,
                                    selectedFolders = selectedFolders,
                                    onToggleFolderSelection = { folder -> viewModel.toggleFolderSelection(folder) },
                                    listState = categoryScrollStates.getOrPut("专辑") { 
                                        androidx.compose.foundation.lazy.LazyListState() 
                                    }
                                )
                            }
                            2 -> { // 歌手 Tab
                                val currentPlayingPath = currentPlaylist.getOrNull(currentSongIndex)?.filePath
                                CategoryScreen(
                                    items = artists,
                                    currentPlayingPath = currentPlayingPath,
                                    onOpenFolder = { folder ->
                                        viewModel.openSongList(folder.name, folder.songs, MusicUiState.ListType.ARTIST, artists)
                                    },
                                    isSelectionMode = isSelectionMode,
                                    selectedFolders = selectedFolders,
                                    onToggleFolderSelection = { folder -> viewModel.toggleFolderSelection(folder) },
                                    listState = categoryScrollStates.getOrPut("歌手") { 
                                        androidx.compose.foundation.lazy.LazyListState() 
                                    }
                                )
                            }
                            3 -> { // 文件夹 Tab
                                val currentPlayingPath = currentPlaylist.getOrNull(currentSongIndex)?.filePath
                                CategoryScreen(
                                    items = folders,
                                    currentPlayingPath = currentPlayingPath,
                                    onOpenFolder = { folder ->
                                        viewModel.openSongList(folder.name, folder.songs, MusicUiState.ListType.FOLDER, folders)
                                    },
                                    isSelectionMode = isSelectionMode,
                                    selectedFolders = selectedFolders,
                                    onToggleFolderSelection = { folder -> viewModel.toggleFolderSelection(folder) },
                                    listState = categoryScrollStates.getOrPut("文件夹") { 
                                        androidx.compose.foundation.lazy.LazyListState() 
                                    }
                                )
                            }
                        }
                    }
                }

                // 2. 二级 SongList 页面 Overlay (使用 AnimatedContent 控制淡入淡出，盖在 Pager 上方)
                AnimatedContent(
                    targetState = uiState,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "MainScreenNavigation",
                    modifier = Modifier.fillMaxSize()
                ) { state ->
                    when (state) {
                        is MusicUiState.SongList -> {
                            val songsToShow = when (state.type) {
                                MusicUiState.ListType.ALL_SONGS -> allSongs
                                MusicUiState.ListType.FOLDER -> folders.find { it.name == state.title }?.songs ?: state.songs
                                MusicUiState.ListType.ALBUM -> albums.find { it.name == state.title }?.songs ?: state.songs
                                MusicUiState.ListType.ARTIST -> artists.find { it.name == state.title }?.songs ?: state.songs
                                MusicUiState.ListType.FAVORITES -> favorites
                                else -> state.songs
                            }

                            val currentPlayingPath = currentPlaylist.getOrNull(currentSongIndex)?.filePath
                            
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background
                            ) {
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
                                    onShowMoreOptions = { _ ->
                                        // TODO
                                    },
                                    listState = songListScrollStates.getOrPut("${state.type}_${state.title}") {
                                        androidx.compose.foundation.lazy.LazyListState()
                                    }
                                )
                            }
                        }
                        else -> {
                            Spacer(modifier = Modifier.fillMaxSize())
                        }
                    }
                }

                // --- 多选操作悬浮页喵 ---
                MultiSelectBar(
                    isSelectionMode = isSelectionMode,
                    selectedItems = selectedItems,
                    selectedPlaylists = selectedPlaylists,
                    selectedFolders = selectedFolders,
                    playlists = playlists,
                    songsInCurrentPage = songsInCurrentPage,
                    onClearSelection = { viewModel.clearSelection() },
                    onSelectAll = { songs -> viewModel.selectAll(songs) },
                    onDeleteSelectedPlaylists = { viewModel.deleteSelectedPlaylists() },
                    onImportFoldersIndividually = { viewModel.importSelectedFoldersIndividually() },
                    onImportFoldersAsSinglePlaylist = { name -> viewModel.importSelectedFoldersAsSinglePlaylist(name) },
                    onAddSelectedToPlaylist = { playlistId -> viewModel.addSelectedToPlaylist(playlistId) },
                    onCreatePlaylistWithSelected = { name -> viewModel.createPlaylistWithSelected(name) },
                    onShowDialog = { dialog -> viewModel.showDialog(dialog) },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    // --- 全屏播放面板 Overlay ---
    PlayingPanel(
        viewModel = viewModel,
        isVisible = isPlayingPanelVisible,
        onClose = { viewModel.setPlayingPanelVisible(false) },
        onPlayPause = { if (audioPlayState == com.cpu.seamlessloopmobile.audio.AudioPlayState.PLAYING) viewModel.pause() else viewModel.play() },
        onNext = { viewModel.skipToNext() },
        onPrev = { viewModel.skipToPrevious() }
    )

    // --- 全局对话框托管 ---
    CentralizedDialogHost(viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    MaterialTheme {
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
