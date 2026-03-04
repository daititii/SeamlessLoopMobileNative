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

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    playSong: (com.cpu.seamlessloopmobile.model.Song) -> Unit
) {
    val uiState by viewModel.uiState.observeAsState(MusicUiState.Home)

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
                    val localCount = viewModel.allSongs.value?.size ?: 0
                    val folders = viewModel.folders.value ?: emptyList()
                    val albums = viewModel.albums.value ?: emptyList()
                    val artists = viewModel.artists.value ?: emptyList()
                    val playlists = viewModel.playlists.value ?: emptyList()
                    
                    // 暂时这里不处理带数量的歌单，简单适配一下喵
                    val playlistPairs = playlists.map { it to 0 }

                    HomeScreen(
                        localCount = localCount,
                        albumsCount = albums.size,
                        artistsCount = artists.size,
                        foldersCount = folders.size,
                        playlists = playlistPairs,
                        onOpenAllSongs = {
                            viewModel.allSongs.value?.let { songs ->
                                viewModel.openSongList("全部歌曲", songs, MusicUiState.ListType.ALL_SONGS)
                            }
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
                            // 暂时使用空列表，实际应当由 ViewModel 提供数据后再跳转
                            // 这部分逻辑等替换 MainActivity 相关代码时再完善喵
                            viewModel.setCurrentOpenPlaylist(playlist)
                            viewModel.openSongList(playlist.name, emptyList(), MusicUiState.ListType.PLAYLIST)
                        }
                    )
                }
                is MusicUiState.CategoryFolders -> {
                    CategoryScreen(
                        items = state.items,
                        onOpenFolder = { folder ->
                            val type = when {
                                folder.path.startsWith("album_") -> MusicUiState.ListType.ALBUM
                                folder.path.startsWith("artist_") -> MusicUiState.ListType.ARTIST
                                else -> MusicUiState.ListType.FOLDER
                            }
                            viewModel.openSongList(folder.name, folder.songs, type, state.items)
                        }
                    )
                }
                is MusicUiState.SongList -> {
                    val currentPlayingPath = viewModel.currentSongIndex.value?.let { index ->
                        viewModel.currentPlaylist.value?.getOrNull(index)?.filePath
                    }
                    
                    SongListScreen(
                        songs = state.songs,
                        currentPlayingSongPath = currentPlayingPath,
                        onPlaySong = { song ->
                            playSong(song)
                        },
                        onShowMoreOptions = { song ->
                            // TODO 显示更多选项对话框
                        }
                    )
                }
            }
        }
    }
}
