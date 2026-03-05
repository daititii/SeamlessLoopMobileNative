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
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    playSong: (com.cpu.seamlessloopmobile.model.Song) -> Unit
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

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

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

    Box(modifier = Modifier.fillMaxSize()) {
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
                        }
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
                        }
                    )
                }
                is MusicUiState.SongList -> {
                    // 核心修复：如果数据更新了，我们要从最新的分类里捞数据，而不是死守着旧快照喵！
                    val songsToShow = when (state.type) {
                        MusicUiState.ListType.ALL_SONGS -> allSongs
                        MusicUiState.ListType.FOLDER -> folders.find { it.name == state.title }?.songs ?: state.songs
                        MusicUiState.ListType.ALBUM -> albums.find { it.name == state.title }?.songs ?: state.songs
                        MusicUiState.ListType.ARTIST -> artists.find { it.name == state.title }?.songs ?: state.songs
                        else -> state.songs // 歌单等暂时用快照，或者未来也支持动态加载喵
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
                            // TODO 显示更多选项对话框
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
                        Text(
                            text = "已选 ${selectedItems.size} 项",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        
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
    }
}
