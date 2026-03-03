package com.cpu.seamlessloopmobile.ui.screen.playing

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.viewmodel.MainViewModel
import com.cpu.seamlessloopmobile.utils.TimeUtils
import com.cpu.seamlessloopmobile.jni.NativeAudio

@Composable
fun PlayingPanel(
    viewModel: MainViewModel,
    isVisible: Boolean,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    val currentSongIndex by viewModel.currentSongIndex.observeAsState(-1)
    val playlist by viewModel.currentPlaylist.observeAsState(emptyList())
    val isPlaying by viewModel.isPlaying.observeAsState(false)
    val playMode by viewModel.playMode.observeAsState(com.cpu.seamlessloopmobile.viewmodel.PlayMode.SINGLE_LOOP)
    
    val playingSong = if (currentSongIndex in playlist.indices) playlist[currentSongIndex] else null
    
    // 状态管理：是否展开微调面板喵
    var isFineTuneVisible by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = isVisible && playingSong != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        val songItem = playingSong ?: return@AnimatedVisibility

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E1E2E),
                            Color(0xFF2E2E3E)
                        )
                    )
                )
        ) {
            // --- 顶部控制 (关闭按钮) ---
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起", tint = Color.White)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                // --- 封面图 (带有简单的旋转动效喵) ---
                val infiniteTransition = rememberInfiniteTransition(label = "rotate")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(20000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotation"
                )

                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3E3E4E))
                        .padding(2.dp)
                        .graphicsLayer { rotationZ = if (isPlaying) rotation else 0f }
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(64.dp),
                        tint = Color(0xFFBB86FC)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // --- 歌曲信息 ---
                Text(
                    text = songItem.displayName ?: songItem.fileName,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 24.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = songItem.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.Gray,
                        fontSize = 16.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.weight(1f))

                // --- 进度条 (主进度) ---
                PlaybackProgressBar(songItem)

                Spacer(modifier = Modifier.height(24.dp))

                // --- 播放核心控制按钮 ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.togglePlayMode() }) {
                        val modeIcon = when(playMode) {
                            com.cpu.seamlessloopmobile.viewmodel.PlayMode.LIST_LOOP -> Icons.Default.Repeat
                            com.cpu.seamlessloopmobile.viewmodel.PlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
                            com.cpu.seamlessloopmobile.viewmodel.PlayMode.SHUFFLE -> Icons.Default.Shuffle
                            else -> Icons.Default.Repeat
                        }
                        Icon(modeIcon, contentDescription = "播放模式", tint = Color(0xFFBB86FC))
                    }
                    
                    IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    FilledIconButton(
                        onClick = onPlayPause,
                        modifier = Modifier.size(80.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFBB86FC))
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "播放/暂停",
                            modifier = Modifier.size(48.dp),
                            tint = Color.Black
                        )
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "下一首", tint = Color.White, modifier = Modifier.size(36.dp))
                    }

                    IconButton(onClick = { isFineTuneVisible = !isFineTuneVisible }) {
                        Icon(
                            Icons.Default.Tune, 
                            contentDescription = "微调", 
                            tint = if (isFineTuneVisible) Color(0xFFBB86FC) else Color.LightGray
                        )
                    }
                }

                // --- 可折叠微调面板喵 ---
                AnimatedVisibility(
                    visible = isFineTuneVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    FineTunePanel(songItem, viewModel)
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun PlaybackProgressBar(song: Song) {
    // 这里的进度需要从 NativeAudio 获取，可能需要一个本地的 frame 计数器喵
    var currentFrame by remember { mutableStateOf(0L) }
    val totalFrames = NativeAudio.getDuration()
    val sampleRate = NativeAudio.getSampleRate().toLong()

    LaunchedEffect(Unit) {
        while(true) {
            currentFrame = NativeAudio.getCurrentPosition()
            kotlinx.coroutines.delay(100)
        }
    }

    Column {
        Slider(
            value = currentFrame.toFloat(),
            onValueChange = { NativeAudio.seekTo(it.toLong()) },
            valueRange = 0f..totalFrames.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFBB86FC),
                activeTrackColor = Color(0xFFBB86FC),
                inactiveTrackColor = Color.DarkGray
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(TimeUtils.formatTime(currentFrame, sampleRate), color = Color.Gray, fontSize = 12.sp)
            Text(TimeUtils.formatTime(totalFrames, sampleRate), color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun FineTunePanel(song: Song, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF252535))
            .padding(16.dp)
    ) {
        Text("循环微调 (A-B)", color = Color(0xFFBB86FC), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FineTuneColumn(
                label = "起点 (A)", 
                value = song.loopStart, 
                accentColor = Color(0xFF8FBBD9),
                modifier = Modifier.weight(1f),
                onValueChange = { newValue ->
                    viewModel.updateSongLoopPoints(song, newValue, song.loopEnd)
                    NativeAudio.setLoopPoints(newValue, song.loopEnd)
                },
                onAdjust = { deltaMs ->
                    val sampleRate = NativeAudio.getSampleRate()
                    val deltaSamples = (sampleRate * deltaMs / 1000.0).toLong()
                    val dur = NativeAudio.getDuration()
                    val newStart = (song.loopStart + deltaSamples).coerceIn(0, if (song.loopEnd > 0) song.loopEnd else dur)
                    viewModel.updateSongLoopPoints(song, newStart, song.loopEnd)
                    NativeAudio.setLoopPoints(newStart, song.loopEnd)
                }
            )
            
            FineTuneColumn(
                label = "终点 (B)", 
                value = song.loopEnd, 
                accentColor = Color(0xFFF398AF),
                modifier = Modifier.weight(1f),
                onValueChange = { newValue ->
                    viewModel.updateSongLoopPoints(song, song.loopStart, newValue)
                    NativeAudio.setLoopPoints(song.loopStart, newValue)
                },
                onAdjust = { deltaMs ->
                    val sampleRate = NativeAudio.getSampleRate()
                    val deltaSamples = (sampleRate * deltaMs / 1000.0).toLong()
                    val dur = NativeAudio.getDuration()
                    val newEnd = (song.loopEnd + deltaSamples).coerceIn(song.loopStart, dur)
                    viewModel.updateSongLoopPoints(song, song.loopStart, newEnd)
                    NativeAudio.setLoopPoints(song.loopStart, newEnd)
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = {
                val sampleRate = NativeAudio.getSampleRate().toLong()
                val totalDur = NativeAudio.getDuration()
                val actualEnd = if (song.loopEnd > 0) song.loopEnd else totalDur
                val seekPos = (actualEnd - (sampleRate * 3)).coerceIn(0, actualEnd)
                NativeAudio.seekTo(seekPos)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("最后 3 秒试听喵！", color = Color.Black)
        }
    }
}

@Composable
fun FineTuneColumn(
    label: String, 
    value: Long, 
    accentColor: Color,
    modifier: Modifier = Modifier,
    onValueChange: (Long) -> Unit,
    onAdjust: (Double) -> Unit
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value.toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 按钮行 1: 基础位置
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallTuneButton("首", onClick = { onValueChange(0) })
            SmallTuneButton("现", onClick = { onValueChange(NativeAudio.getCurrentPosition()) })
            SmallTuneButton("末", onClick = { onValueChange(NativeAudio.getDuration()) })
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // 按钮行 2: 秒级
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallTuneButton("-1s", onClick = { onAdjust(-1000.0) })
            SmallTuneButton("+1s", onClick = { onAdjust(1000.0) })
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // 按钮行 3: 毫秒级
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallTuneButton("-10ms", onClick = { onAdjust(-10.0) })
            SmallTuneButton("+10ms", onClick = { onAdjust(10.0) })
        }
    }
}

@Composable
fun SmallTuneButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .height(24.dp)
            .widthIn(min = 32.dp)
            .clickable { onClick() },
        color = Color(0xFF353545),
        shape = RoundedCornerShape(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(text, color = Color.White, fontSize = 10.sp)
        }
    }
}
