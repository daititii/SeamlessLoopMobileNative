package com.cpu.seamlessloopmobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cpu.seamlessloopmobile.jni.NativeAudio
import com.cpu.seamlessloopmobile.viewmodel.MainViewModel
import com.cpu.seamlessloopmobile.viewmodel.MusicDialog

@Composable
fun CentralizedDialogHost(viewModel: MainViewModel) {
    val currentDialog by viewModel.currentDialog.observeAsState()

    when (val dialog = currentDialog) {
        is MusicDialog.LoopEdit -> {
            var samplesValue by remember { mutableStateOf(dialog.initialSamples.toString()) }
            var timeValue by remember { 
                val sr = NativeAudio.getSampleRate()
                val seconds = dialog.initialSamples.toDouble() / if(sr > 0) sr else 44100
                mutableStateOf(String.format("%.3f", seconds))
            }

            LoopEditDialog(
                visible = true,
                isStart = dialog.isStart,
                samplesValue = samplesValue,
                timeValue = timeValue,
                onValueSamplesChange = { samplesValue = it; timeValue = "" },
                onValueTimeChange = { timeValue = it; samplesValue = "" },
                onDismiss = { viewModel.dismissDialog() },
                onConfirm = {
                    val sr = NativeAudio.getSampleRate()
                    val newSamples = samplesValue.toLongOrNull()
                    val newTime = timeValue.toDoubleOrNull()
                    
                    if (newSamples != null) {
                        dialog.onConfirm(newSamples)
                    } else if (newTime != null) {
                        dialog.onConfirm((newTime * sr).toLong())
                    }
                    viewModel.dismissDialog()
                }
            )
        }

        is MusicDialog.CreatePlaylist -> {
            var newPlaylistName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
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
                            viewModel.dismissDialog()
                        }
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) { Text("取消") }
                }
            )
        }

        is MusicDialog.AddToPlaylist -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text("添加到歌单") },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn {
                        item {
                            ListItem(
                                headlineContent = { Text("新建歌单...") },
                                leadingContent = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                                modifier = Modifier.clickable { dialog.onCreateNew() }
                            )
                        }
                        items(dialog.playlists) { playlist ->
                            ListItem(
                                headlineContent = { Text(playlist.name) },
                                leadingContent = { Icon(Icons.Default.QueueMusic, contentDescription = null) },
                                modifier = Modifier.clickable { 
                                    dialog.onAdd(playlist)
                                    viewModel.dismissDialog()
                                }
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) { Text("取消") }
                }
            )
        }

        is MusicDialog.ImportFoldersOptions -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text("导入文件夹 ${dialog.count} 个") },
                text = { Text("你要如何导入这些文件夹喵？") },
                confirmButton = {
                    Column {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                dialog.onIndividual()
                                viewModel.dismissDialog()
                            }
                        ) { Text("各文件夹导入各自歌单 (1:1)") }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { dialog.onMerge() }
                        ) { Text("全部合并为一个歌单") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) { Text("取消") }
                }
            )
        }

        is MusicDialog.MergeFoldersName -> {
            var mergePlaylistName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
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
                            dialog.onConfirm(mergePlaylistName)
                            viewModel.dismissDialog()
                        }
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) { Text("取消") }
                }
            )
        }

        is MusicDialog.ConfirmDeletePlaylist -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissDialog() },
                title = { Text("删除歌单") },
                text = { Text("真的要删除歌单《${dialog.playlist.name}》吗？这不会影响设备上的实际音频文件喵。") },
                confirmButton = {
                    Button(
                        onClick = {
                            dialog.onConfirm()
                            viewModel.dismissDialog()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("残忍删除") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissDialog() }) { Text("算了吧") }
                }
            )
        }

        null -> { /* 无弹窗显示 */ }
    }
}
