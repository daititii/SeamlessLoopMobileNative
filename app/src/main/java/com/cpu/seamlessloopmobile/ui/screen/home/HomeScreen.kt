package com.cpu.seamlessloopmobile.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpu.seamlessloopmobile.model.Playlist

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
    onOpenPlaylist: (Playlist) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            SectionHeader(title = "分类")
        }
        item {
            HomeQuickActionItem(
                iconId = android.R.drawable.ic_media_play,
                title = "全部歌曲",
                count = localCount,
                onClick = onOpenAllSongs
            )
        }
        item {
            HomeQuickActionItem(
                iconId = android.R.drawable.ic_menu_gallery,
                title = "专辑",
                count = albumsCount,
                onClick = onOpenAlbums
            )
        }
        item {
            HomeQuickActionItem(
                iconId = android.R.drawable.ic_menu_myplaces,
                title = "歌手",
                count = artistsCount,
                onClick = onOpenArtists
            )
        }
        item {
            HomeQuickActionItem(
                iconId = android.R.drawable.ic_menu_save,
                title = "文件夹",
                count = foldersCount,
                onClick = onOpenFolders
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (playlists.isNotEmpty()) {
            item {
                SectionHeader(title = "我的歌单")
            }
            items(playlists) { (playlist, count) ->
                PlaylistListItem(
                    playlist = playlist,
                    count = count,
                    onClick = { onOpenPlaylist(playlist) }
                )
            }
        } else {
            item {
                SectionHeader(title = "暂无歌单")
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun HomeQuickActionItem(
    iconId: Int,
    title: String,
    count: Int,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            painter = painterResource(id = iconId),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    count: Int,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.QueueMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge
            )
            val desc = if (playlist.isFolderLinked == 1) "联动文件夹" else "普通歌单"
            Text(
                text = "$count 首 · $desc",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
