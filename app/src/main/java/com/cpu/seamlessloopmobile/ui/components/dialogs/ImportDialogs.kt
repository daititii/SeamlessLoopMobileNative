package com.cpu.seamlessloopmobile.ui.components.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cpu.seamlessloopmobile.viewmodel.MusicDialog

@Composable
fun ImportFoldersOptionsDialog(
    dialog: MusicDialog.ImportFoldersOptions,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入文件夹 ${dialog.count} 个") },
        text = { Text("你要如何导入这些文件夹喵？") },
        confirmButton = {
            Column {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        dialog.onIndividual()
                        onDismiss()
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
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun MergeFoldersNameDialog(
    dialog: MusicDialog.MergeFoldersName,
    onDismiss: () -> Unit
) {
    var mergePlaylistName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    onDismiss()
                }
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
