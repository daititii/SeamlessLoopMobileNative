package com.cpu.seamlessloopmobile.ui.screen.songlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.ui.components.SongListItem
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListScreen(
    songs: List<Song>,
    currentPlayingSongPath: String?,
    isSelectionMode: Boolean,
    selectedItems: Set<String>,
    onPlaySong: (Song) -> Unit,
    onToggleSelection: (Song) -> Unit,
    onShowMoreOptions: (Song) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(songs, key = { it.filePath }) { song ->
            val isPlaying = song.filePath == currentPlayingSongPath
            val isSelected = selectedItems.contains(song.filePath)
            
            SongListItem(
                song = song,
                isPlaying = isPlaying,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onClick = { 
                    if (isSelectionMode) onToggleSelection(song)
                    else onPlaySong(song)
                },
                onLongClick = { onToggleSelection(song) },
                onMoreClick = { onShowMoreOptions(song) }
            )
        }
    }
}
