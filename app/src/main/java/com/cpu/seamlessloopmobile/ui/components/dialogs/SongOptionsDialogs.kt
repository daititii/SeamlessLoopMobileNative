package com.cpu.seamlessloopmobile.ui.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cpu.seamlessloopmobile.viewmodel.MusicDialog
import com.cpu.seamlessloopmobile.model.Song
import java.util.Locale
import java.io.File

data class SongDetailedInfo(
    val path: String,
    val fileName: String,
    val fileSize: String,
    val duration: String,
    val sampleRate: String
)

private fun getFileSizeString(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun getSongDetailedInfo(context: android.content.Context, song: Song): SongDetailedInfo {
    var sizeBytes = 0L
    var sampleRate = 0
    
    val uri = if (song.filePath.startsWith("content://")) {
        android.net.Uri.parse(song.filePath)
    } else {
        android.net.Uri.fromFile(java.io.File(song.filePath))
    }
    
    // 1. 获取文件大小喵！
    try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            sizeBytes = pfd.statSize
        }
    } catch (t: Throwable) {
        t.printStackTrace()
    }
    
    if (sizeBytes == 0L) {
        val file = java.io.File(song.filePath)
        if (file.exists()) {
            sizeBytes = file.length()
        }
    }

    // 2. 拷贝到私有 cache 目录并利用 JNI 获取采样率（只在查看详情时，对单首歌曲执行）
    var tempFile: java.io.File? = null
    try {
        val ext = java.io.File(song.fileName).extension.let { if (it.isNotEmpty()) ".$it" else ".mp3" }
        val cacheDir = context.cacheDir
        val tFile = java.io.File(cacheDir, "sample_rate_temp_${System.currentTimeMillis()}$ext")
        tempFile = tFile
        
        var inputStream: java.io.InputStream? = null
        var outputStream: java.io.FileOutputStream? = null
        val copied = try {
            inputStream = if (song.filePath.startsWith("content://")) {
                context.contentResolver.openInputStream(uri)
            } else if (song.mediaId > 0) {
                val mediaUri = android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    song.mediaId
                )
                try {
                    context.contentResolver.openInputStream(mediaUri)
                } catch (me: Exception) {
                    java.io.FileInputStream(java.io.File(song.filePath))
                }
            } else {
                java.io.FileInputStream(java.io.File(song.filePath))
            }
            if (inputStream != null) {
                outputStream = java.io.FileOutputStream(tFile)
                val buffer = ByteArray(1024 * 64)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                outputStream.flush()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
        }
        
        if (copied) {
            android.os.ParcelFileDescriptor.open(tFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                sampleRate = com.cpu.seamlessloopmobile.jni.NativeAudio.getAudioFileSampleRate(pfd.fd, 0, tFile.length())
            }
        }
    } catch (t: Throwable) {
        t.printStackTrace()
    } finally {
        try {
            tempFile?.let {
                if (it.exists()) it.delete()
            }
        } catch (_: Exception) {}
    }
    
    val sizeStr = getFileSizeString(sizeBytes)
    val sampleRateStr = if (sampleRate > 0) "$sampleRate Hz" else "未知"
    val minutes = (song.duration / 1000) / 60
    val seconds = (song.duration / 1000) % 60
    val durationStr = String.format(Locale.US, "%02d:%02d", minutes, seconds)
    
    return SongDetailedInfo(
        path = song.filePath,
        fileName = song.fileName,
        fileSize = sizeStr,
        duration = "$durationStr (${song.duration} ms)",
        sampleRate = sampleRateStr
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMoreOptionsBottomSheet(
    song: Song,
    onDismissRequest: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDetectLoop: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)?,
    onShowInfo: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            
            ListItem(
                headlineContent = { Text("添加到歌单") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                modifier = Modifier.clickable {
                    onAddToPlaylist()
                }
            )
            
            ListItem(
                headlineContent = { Text("自动探测循环点") },
                leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDetectLoop()
                }
            )
            
            val isFavorite = song.rating > 0
            ListItem(
                headlineContent = { Text(if (isFavorite) "修改评分 (当前 ${song.rating} 星)" else "设为喜爱 (5 星)") },
                leadingContent = { 
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, 
                        contentDescription = null,
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                },
                modifier = Modifier.clickable {
                    onToggleFavorite()
                }
            )

            ListItem(
                headlineContent = { Text("歌曲详细信息") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                modifier = Modifier.clickable {
                    onShowInfo()
                }
            )
            
            if (onRemoveFromPlaylist != null) {
                ListItem(
                    headlineContent = { Text("从当前歌单移除", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { 
                        Icon(
                            imageVector = Icons.Default.Delete, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        ) 
                    },
                    modifier = Modifier.clickable {
                        onRemoveFromPlaylist()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkMoreOptionsBottomSheet(
    count: Int,
    onDismissRequest: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDetectLoop: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)?
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "已选择 $count 首歌曲",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            
            ListItem(
                headlineContent = { Text("批量添加到歌单") },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                modifier = Modifier.clickable {
                    onAddToPlaylist()
                }
            )
            
            ListItem(
                headlineContent = { Text("批量自动探测循环点") },
                leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
                modifier = Modifier.clickable {
                    onDetectLoop()
                }
            )
            
            ListItem(
                headlineContent = { Text("批量设为喜爱 (5 星)") },
                leadingContent = { Icon(Icons.Default.Star, contentDescription = null) },
                modifier = Modifier.clickable {
                    onToggleFavorite()
                }
            )
            
            if (onRemoveFromPlaylist != null) {
                ListItem(
                    headlineContent = { Text("从当前歌单批量移除", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { 
                        Icon(
                            imageVector = Icons.Default.Delete, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        ) 
                    },
                    modifier = Modifier.clickable {
                        onRemoveFromPlaylist()
                    }
                )
            }
        }
    }
}

@Composable
fun SongInfoDialog(
    context: android.content.Context,
    song: Song,
    onDismiss: () -> Unit
) {
    val info = remember(song) { getSongDetailedInfo(context, song) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("歌曲详细信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "文件名: ${info.fileName}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "路径: ${info.path}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "文件大小: ${info.fileSize}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "时长: ${info.duration}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "采样率: ${info.sampleRate}", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("关闭") }
        }
    )
}
