package com.cpu.seamlessloopmobile.ui.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cpu.seamlessloopmobile.viewmodel.MusicDialog
import com.cpu.seamlessloopmobile.model.Playlist

@Composable
fun CreatePlaylistDialog(
    dialog: MusicDialog.CreatePlaylist,
    onDismiss: () -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    dialog.onConfirm(newPlaylistName)
                    onDismiss()
                }
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    dialog: MusicDialog.AddToPlaylist,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到歌单") },
        text = {
            LazyColumn {
                item {
                    ListItem(
                        headlineContent = { Text("新建歌单...") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                        modifier = Modifier.clickable { dialog.onCreateNew() }
                    )
                }
                items(dialog.playlists) { playlist ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = playlist.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                        modifier = Modifier.clickable { 
                            dialog.onAdd(playlist)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun ConfirmDeletePlaylistDialog(
    dialog: MusicDialog.ConfirmDeletePlaylist,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除歌单") },
        text = { Text("真的要删除歌单《${dialog.playlist.name}》吗？这不会影响设备上的实际音频文件喵。") },
        confirmButton = {
            Button(
                onClick = {
                    dialog.onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("残忍删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("算了吧") }
        }
    )
}
