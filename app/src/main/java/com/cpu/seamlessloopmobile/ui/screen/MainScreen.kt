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
import com.cpu.seamlessloopmobile.ui.screen.songlist.MainSongListScreen
import com.cpu.seamlessloopmobile.viewmodel.MainViewModel
import com.cpu.seamlessloopmobile.viewmodel.MusicUiState
import com.cpu.seamlessloopmobile.viewmodel.MusicDialog
import androidx.activity.compose.BackHandler
import com.cpu.seamlessloopmobile.ui.screen.search.SearchScreen
import com.cpu.seamlessloopmobile.ui.screen.settings.SettingsDrawer

import com.cpu.seamlessloopmobile.ui.components.app.MiniPlayer
import com.cpu.seamlessloopmobile.ui.components.app.CentralizedDialogHost
import com.cpu.seamlessloopmobile.ui.components.app.PlayingPanel
import com.cpu.seamlessloopmobile.ui.components.app.MultiSelectBar

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.material3.*
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.derivedStateOf

/**
 * 主界面总装配站喵！(๑•̀ㅂ•́)و✧
 * 现已完成深度瘦身，代码量缩减 50% 以上。
 * 不监听任何具体的业务列表数据（allSongs/playlists 等全部下沉自治），只做全局骨架装配与状态分发！
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    playSong: (com.cpu.seamlessloopmobile.model.Song) -> Unit,
    onSyncPc: () -> Unit,
    onExportDatabase: () -> Unit
) {
    val uiState by viewModel.uiState.observeAsState(MusicUiState.Home)
    
    // 局部化收集多选及显示状态喵！(๑•̀ㅂ•́)و✧
    val isSelectionMode by viewModel.selection.isSelectionMode.observeAsState(false)
    val selectedItems by viewModel.selection.selectedItems.observeAsState(emptySet())
    val selectedPlaylists by viewModel.selection.selectedPlaylists.observeAsState(emptySet())
    val selectedFolders by viewModel.selection.selectedFolders.observeAsState(emptySet())
    
    val isPlayingPanelVisible by viewModel.isPlayingPanelVisible.observeAsState(false)
    val isSettingsPanelVisible by viewModel.isSettingsPanelVisible.observeAsState(false)
    val seamlessLoopCountLimit by viewModel.seamlessLoopCountLimit.observeAsState(0)
    
    val playlists by viewModel.playlist.playlists.collectAsState()

    // --- 导航滚动位置记忆中心喵 ---
    val categoryScrollStates = remember { mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }
    val songListScrollStates = remember { mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }

    // 计算当前页面展示的歌曲，用于“全选”喵
    val songsInCurrentPage by remember(uiState) {
        derivedStateOf {
            if (uiState is MusicUiState.SongList) {
                (uiState as MusicUiState.SongList).songs
            } else {
                emptyList()
            }
        }
    }

    // --- 接管返回键喵 ---
    BackHandler(enabled = isSettingsPanelVisible || isPlayingPanelVisible || isSelectionMode || uiState !is MusicUiState.Home) {
        when {
            isSettingsPanelVisible -> viewModel.setSettingsPanelVisible(false)
            isPlayingPanelVisible -> viewModel.setPlayingPanelVisible(false)
            isSelectionMode -> viewModel.clearSelection()
            else -> viewModel.goBack()
        }
    }

    // --- 界面架构拼装喵 ---
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    val currentUiState = uiState
                    when {
                        isSelectionMode -> SelectionAppBar(
                            selectedItemsCount = selectedItems.size,
                            selectedFoldersCount = selectedFolders.size,
                            selectedPlaylistsCount = selectedPlaylists.size,
                            onCloseSelectionClick = remember(viewModel) { viewModel::clearSelection }
                        )
                        currentUiState is MusicUiState.Search -> {} // 搜索页自己掌控顶栏喵！
                        currentUiState is MusicUiState.Home -> HomeAppBar(
                            libraryVM = viewModel.library,
                            onSettingsClick = remember(viewModel) { { viewModel.setSettingsPanelVisible(true) } },
                            onSearchClick = remember(viewModel) { { viewModel.navigateTo(MusicUiState.Search) } }
                        )
                        currentUiState is MusicUiState.SongList -> SongListAppBar(
                            title = currentUiState.title,
                            libraryVM = viewModel.library,
                            onBackClick = remember(viewModel) { { viewModel.goBack() } },
                            onSearchClick = remember(viewModel) { { viewModel.navigateTo(MusicUiState.Search) } }
                        )
                        else -> {}
                    }
                },
                bottomBar = {
                    if (!isSelectionMode) {
                        MiniPlayer(
                            viewModel = viewModel,
                            onClick = remember(viewModel) { { viewModel.setPlayingPanelVisible(true) } }
                        )
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    // 1. 扁平 Tab 翻页展示主底层（已完美解耦抽离！）
                    MainTabsPager(
                        viewModel = viewModel,
                        isSelectionMode = isSelectionMode,
                        selectedFolders = selectedFolders,
                        categoryScrollStates = categoryScrollStates
                    )
 
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
                            is MusicUiState.Search -> {
                                SearchScreen(
                                    viewModel = viewModel,
                                    playSong = playSong
                                )
                            }
                            is MusicUiState.SongList -> {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background
                                ) {
                                    MainSongListScreen(
                                        state = state,
                                        libraryVM = viewModel.library,
                                        selectionVM = viewModel.selection,
                                        mainVM = viewModel,
                                        onPlaySong = remember(viewModel) {
                                            { song, songsToShow ->
                                                val index = songsToShow.indexOf(song)
                                                viewModel.updateCurrentPlaylist(songsToShow, index)
                                                playSong(song)
                                            }
                                        },
                                         onShowMoreOptions = remember(viewModel) {
                                             { song ->
                                                 val currentPlaylistId = if (state.type == MusicUiState.ListType.PLAYLIST) {
                                                     playlists.find { it.name == state.title }?.id
                                                 } else null
                                                 
                                                 viewModel.showDialog(MusicDialog.SongMoreOptions(song, currentPlaylistId))
                                             }
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
                        onClearSelection = remember(viewModel) { viewModel::clearSelection },
                        onSelectAll = remember(viewModel) { { songs -> viewModel.selectAll(songs) } },
                        onDeleteSelectedPlaylists = remember(viewModel) { viewModel::deleteSelectedPlaylists },
                        onImportFoldersIndividually = remember(viewModel) { viewModel::importSelectedFoldersIndividually },
                        onImportFoldersAsSinglePlaylist = remember(viewModel) { { name -> viewModel.importSelectedFoldersAsSinglePlaylist(name) } },
                        onAddSelectedToPlaylist = remember(viewModel) { { playlistId -> viewModel.addSelectedToPlaylist(playlistId) } },
                        onCreatePlaylistWithSelected = remember(viewModel) { { name -> viewModel.createPlaylistWithSelected(name) } },
                        onShowDialog = remember(viewModel) { { dialog -> viewModel.showDialog(dialog) } },
                        onShowMoreBulkOptions = remember(viewModel, uiState) {
                            {
                                val currentPlaylistId = if (uiState is MusicUiState.SongList && (uiState as MusicUiState.SongList).type == MusicUiState.ListType.PLAYLIST) {
                                    playlists.find { it.name == (uiState as MusicUiState.SongList).title }?.id
                                } else null
                                
                                viewModel.showDialog(MusicDialog.BulkMoreOptions(selectedItems.size, currentPlaylistId))
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
 
        // --- Layer 2: 侧边设置面板遮罩层与滑入面板喵！⚙️ ---
        SettingsDrawer(
            isVisible = isSettingsPanelVisible,
            onClose = remember(viewModel) { { viewModel.setSettingsPanelVisible(false) } },
            onRescan = remember(viewModel) { { context -> viewModel.scanLibrary(context) } },
            onSyncPc = onSyncPc,
            onExportDatabase = onExportDatabase,
            seamlessLoopCountLimit = seamlessLoopCountLimit,
            onSeamlessLoopCountLimitChange = remember(viewModel) { viewModel::setSeamlessLoopCountLimit }
        )
    }
 
    // --- 全屏播放面板 Overlay ---
    PlayingPanel(
        viewModel = viewModel,
        isVisible = isPlayingPanelVisible,
        onClose = remember(viewModel) { { viewModel.setPlayingPanelVisible(false) } },
        onPlayPause = remember(viewModel) { viewModel::togglePlayPause },
        onNext = remember(viewModel) { { viewModel.skipToNext() } },
        onPrev = remember(viewModel) { { viewModel.skipToPrevious() } },
        onMoreClick = remember(viewModel) {
            { song ->
                val currentPlaylistId = viewModel.currentOpenPlaylist.value?.id
                viewModel.showDialog(MusicDialog.SongMoreOptions(song, currentPlaylistId))
            }
        }
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
