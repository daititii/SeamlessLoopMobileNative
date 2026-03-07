package com.cpu.seamlessloopmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.support.v4.media.session.PlaybackStateCompat
import com.cpu.seamlessloopmobile.viewmodel.MainViewModel
import com.cpu.seamlessloopmobile.viewmodel.PlayMode
import com.cpu.seamlessloopmobile.utils.TimeUtils

@Composable
fun MiniPlayer(
    viewModel: MainViewModel,
    onClick: () -> Unit
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val metadata by viewModel.metadata.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val totalDuration by viewModel.totalDuration.collectAsState()
    val audioPlayState by viewModel.audioPlayState.collectAsState()
    val playMode by viewModel.playMode.observeAsState(PlayMode.LIST_LOOP)
    
    val isPlaying = audioPlayState == com.cpu.seamlessloopmobile.audio.AudioPlayState.PLAYING
    val isPreparing = audioPlayState == com.cpu.seamlessloopmobile.audio.AudioPlayState.PREPARING
    val isError = audioPlayState == com.cpu.seamlessloopmobile.audio.AudioPlayState.ERROR
    val title = metadata?.description?.title ?: "未在播放"
    
    // 采样率暂定 44100，实际可从 PM 获取喵
    val sampleRate = 44100L 

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(bottom = 8.dp)
    ) {
        // 进度条喵
        LinearProgressIndicator(
            progress = if (totalDuration > 0) currentPosition.toFloat() / totalDuration else 0f,
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${TimeUtils.formatTime(currentPosition, sampleRate)} / ${TimeUtils.formatTime(totalDuration, sampleRate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { viewModel.togglePlayMode() }) {
                val icon = when (playMode) {
                    PlayMode.LIST_LOOP -> Icons.Default.Repeat
                    PlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
                    PlayMode.SHUFFLE -> Icons.Default.Shuffle
                }
                Icon(icon, contentDescription = "播放模式", tint = MaterialTheme.colorScheme.primary)
            }

            IconButton(onClick = { viewModel.skipToPrevious() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首")
            }

            IconButton(
                onClick = { 
                    if (isError) { /* 可以在这里加个重试或重置逻辑喵 */ }
                    else if (isPlaying) viewModel.pause() 
                    else viewModel.play() 
                },
                modifier = Modifier.size(48.dp),
                enabled = !isPreparing
            ) {
                if (isPreparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        contentDescription = "播放/暂停",
                        modifier = Modifier.size(40.dp),
                        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = { viewModel.skipToNext() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首")
            }
        }
    }
}
