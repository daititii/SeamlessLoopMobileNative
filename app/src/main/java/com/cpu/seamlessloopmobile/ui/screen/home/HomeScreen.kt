package com.cpu.seamlessloopmobile.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.CheckCircle
import com.cpu.seamlessloopmobile.model.Playlist
import com.cpu.seamlessloopmobile.ui.components.CategoryCard
import com.cpu.seamlessloopmobile.ui.components.PlaylistCard
import com.cpu.seamlessloopmobile.ui.components.SectionHeader

@Composable
fun HomeScreen(
    localCount: Int,
    albumsCount: Int,
    artistsCount: Int,
    foldersCount: Int,
    playlists: List<Pair<Playlist, Int>>,
    onOpenAllSongs: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    isSelectionMode: Boolean,
    selectedPlaylists: Set<Int>,
    onTogglePlaylistSelection: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(2) }) {
            SectionHeader(title = "本地音乐库")
        }

        item {
            CategoryCard(
                iconId = android.R.drawable.ic_media_play,
                title = "全部歌曲",
                count = localCount,
                onClick = onOpenAllSongs
            )
        }
        item {
            CategoryCard(
                iconId = android.R.drawable.ic_menu_gallery,
                title = "专辑",
                count = albumsCount,
                onClick = onOpenAlbums
            )
        }
        item {
            CategoryCard(
                iconId = android.R.drawable.ic_menu_myplaces,
                title = "歌手",
                count = artistsCount,
                onClick = onOpenArtists
            )
        }
        item {
            CategoryCard(
                iconId = android.R.drawable.ic_menu_save,
                title = "文件夹",
                count = foldersCount,
                onClick = onOpenFolders
            )
        }

        if (playlists.isNotEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeader(title = "我的歌单")
            }
            items(playlists) { (playlist, count) ->
                val isSelected = selectedPlaylists.contains(playlist.id)
                PlaylistCard(
                    playlist = playlist,
                    count = count,
                    isSelected = isSelected,
                    onClick = { 
                        if (isSelectionMode) {
                            onTogglePlaylistSelection(playlist.id)
                        } else {
                            onOpenPlaylist(playlist) 
                        }
                    },
                    onLongClick = { onTogglePlaylistSelection(playlist.id) }
                )
            }
        }
    }
}
