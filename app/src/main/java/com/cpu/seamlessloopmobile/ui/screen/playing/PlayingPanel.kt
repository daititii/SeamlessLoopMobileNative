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
import androidx.compose.foundation.pager.*
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
    
    // 状态管理：使用 PagerState 实现水平滑动切换喵
    val pagerState = rememberPagerState(initialPage = 1) { 2 } // 0: 调节页, 1: 主页

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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> FineTunePage(songItem, viewModel) // 调节页喵
                    1 -> MainInfoPage(songItem, viewModel, onPlayPause, onNext, onPrev, isPlaying, playMode) // 主页喵
                }
            }

            // --- 顶部控制 (关闭按钮 & 指示器) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起", tint = Color.White)
                }
                
                // 页面指示器喵
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(2) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (pagerState.currentPage == index) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (pagerState.currentPage == index) Color(0xFFBB86FC) else Color.Gray)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainInfoPage(
    songItem: Song,
    viewModel: MainViewModel,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    isPlaying: Boolean,
    playMode: com.cpu.seamlessloopmobile.viewmodel.PlayMode
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // --- 封面图 ---
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

        // --- 进度条 ---
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
            
            // 提示大人可以右滑的图标喵
            IconButton(onClick = { /* 只是占位喵 */ }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "微调", tint = Color.Gray.copy(alpha = 0.5f))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun FineTunePage(song: Song, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        
        Text(
            "循环无缝微调喵",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFFBB86FC)
            )
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // A-B 点核心调节区，现在空间很大喵！
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            FineTuneSection(
                label = "循环起点 (A)",
                value = song.loopStart,
                accentColor = Color(0xFF8FBBD9),
                onValueChange = { newValue ->
                    viewModel.updateSongLoopPoints(song, newValue, song.loopEnd)
                    NativeAudio.setLoopPoints(newValue, song.loopEnd)
                },
                onAdjust = { deltaSamples ->
                    val dur = NativeAudio.getDuration()
                    val newStart = (song.loopStart + deltaSamples).coerceIn(0, if (song.loopEnd > 0) song.loopEnd else dur)
                    viewModel.updateSongLoopPoints(song, newStart, song.loopEnd)
                    NativeAudio.setLoopPoints(newStart, song.loopEnd)
                }
            )

            HorizontalDivider(color = Color.DarkGray, modifier = Modifier.padding(horizontal = 16.dp))

            FineTuneSection(
                label = "循环终点 (B)",
                value = song.loopEnd,
                accentColor = Color(0xFFF398AF),
                onValueChange = { newValue ->
                    viewModel.updateSongLoopPoints(song, song.loopStart, newValue)
                    NativeAudio.setLoopPoints(song.loopStart, newValue)
                },
                onAdjust = { deltaSamples ->
                    val dur = NativeAudio.getDuration()
                    val newEnd = (song.loopEnd + deltaSamples).coerceIn(song.loopStart, dur)
                    viewModel.updateSongLoopPoints(song, song.loopStart, newEnd)
                    NativeAudio.setLoopPoints(song.loopStart, newEnd)
                }
            )
        }

        Button(
            onClick = {
                val sampleRate = NativeAudio.getSampleRate().toLong()
                val totalDur = NativeAudio.getDuration()
                val actualEnd = if (song.loopEnd > 0) song.loopEnd else totalDur
                val seekPos = (actualEnd - (sampleRate * 3)).coerceIn(0, actualEnd)
                NativeAudio.seekTo(seekPos)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Hearing, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.size(8.dp))
            Text("试听临界 3 秒喵！", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun FineTuneSection(
    label: String,
    value: Long,
    accentColor: Color,
    onValueChange: (Long) -> Unit,
    onAdjust: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(accentColor))
            Spacer(modifier = Modifier.size(8.dp))
            Text(label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        
        Text(
            value.toString(),
            color = accentColor,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BigTuneButton("现", onClick = { onValueChange(NativeAudio.getCurrentPosition()) })
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val sampleRate = NativeAudio.getSampleRate()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigTuneButton("-1s", onClick = { onAdjust(-(sampleRate * 1.0).toLong()) })
                    BigTuneButton("+1s", onClick = { onAdjust((sampleRate * 1.0).toLong()) })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigTuneButton("-10ms", onClick = { onAdjust(-(sampleRate * 0.01).toLong()) })
                    BigTuneButton("+10ms", onClick = { onAdjust((sampleRate * 0.01).toLong()) })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigTuneButton("-1帧", onClick = { onAdjust(-1) })
                    BigTuneButton("+1帧", onClick = { onAdjust(1) })
                }
            }
        }
    }
}

@Composable
fun BigTuneButton(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 64.dp)
            .clickable { onClick() },
        color = Color(0xFF353545),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

