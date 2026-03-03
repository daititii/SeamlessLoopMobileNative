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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.livedata.observeAsState
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
    
    // 状态管理：使用 VerticalPagerState 实现纵向滑动切换喵
    val pagerState = rememberPagerState(initialPage = 0) { 2 } // 0: 主页, 1: 调节页

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
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> MainInfoPage(songItem, viewModel, onPlayPause, onNext, onPrev, isPlaying, playMode) // 主页喵
                    1 -> FineTunePage(songItem, viewModel) // 调节页喵
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
                
                // 页面指示器改为纵向小点喵
                Column(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
            
            // 提示大人可以上滑的图标喵
            IconButton(onClick = { /* 只是占位喵 */ }) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "微调", tint = Color.Gray.copy(alpha = 0.5f))
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun FineTunePage(song: Song, viewModel: MainViewModel) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            "循环参数调节喵 (A-B)",
            style = MaterialTheme.typography.titleMedium.copy(color = Color.LightGray)
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // 起点 A
        TuneSectionBox(
            label = "循环起点 (A)",
            samples = song.loopStart,
            accentColor = Color(0xFF8FBBD9),
            onValueChange = { onStartValueChange(song, it, viewModel) },
            onAdjustMs = { onStartAdjustMs(song, it, viewModel) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 终点 B
        TuneSectionBox(
            label = "循环终点 (B)",
            samples = song.loopEnd,
            accentColor = Color(0xFFF398AF),
            onValueChange = { onEndValueChange(song, it, viewModel) },
            onAdjustMs = { onEndAdjustMs(song, it, viewModel) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val sampleRate = NativeAudio.getSampleRate().toLong()
                val totalDur = NativeAudio.getDuration()
                val actualEnd = if (song.loopEnd > 0) song.loopEnd else totalDur
                val seekPos = (actualEnd - (sampleRate * 3)).coerceIn(0, actualEnd)
                NativeAudio.seekTo(seekPos)
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF353545)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Hearing, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.size(8.dp))
            Text("最后 3 秒试听喵！", color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(64.dp))
    }
}

@Composable
fun TuneSectionBox(
    label: String,
    samples: Long,
    accentColor: Color,
    onValueChange: (Long) -> Unit,
    onAdjustMs: (Double) -> Unit
) {
    val sampleRate = NativeAudio.getSampleRate()
    val seconds = samples.toDouble() / sampleRate

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1E1E2E).copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = Color.White, fontSize = 14.sp)
            
            Text(
                samples.toString(),
                color = accentColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            HorizontalDivider(thickness = 2.dp, color = accentColor, modifier = Modifier.fillMaxWidth(0.9f))
            
            Text(
                String.format("%.3f", seconds),
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 第一排: 最小/现/最大
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TuneGridButton("最小", modifier = Modifier.weight(1f)) { onValueChange(0) }
                TuneGridButton("现在", modifier = Modifier.weight(1f)) { onValueChange(NativeAudio.getCurrentPosition()) }
                TuneGridButton("最大", modifier = Modifier.weight(1f)) { onValueChange(NativeAudio.getDuration()) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 第二排: 秒级调节
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TuneGridButton("-5s", modifier = Modifier.weight(1f)) { onAdjustMs(-5000.0) }
                TuneGridButton("-1s", modifier = Modifier.weight(1f)) { onAdjustMs(-1000.0) }
                TuneGridButton("+1s", modifier = Modifier.weight(1f)) { onAdjustMs(1000.0) }
                TuneGridButton("+5s", modifier = Modifier.weight(1f)) { onAdjustMs(5000.0) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 第三排: 毫秒级调节
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TuneGridButton("-0.1s", modifier = Modifier.weight(1f)) { onAdjustMs(-100.0) }
                TuneGridButton("-0.01s", modifier = Modifier.weight(1f)) { onAdjustMs(-10.0) }
                TuneGridButton("+0.01s", modifier = Modifier.weight(1f)) { onAdjustMs(10.0) }
                TuneGridButton("+0.1s", modifier = Modifier.weight(1f)) { onAdjustMs(100.0) }
            }
        }
    }
}

@Composable
fun TuneGridButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .height(36.dp)
            .clickable { onClick() },
        color = Color(0xFF2D2D3D),
        shape = RoundedCornerShape(6.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// 辅助逻辑函数喵
private fun onStartValueChange(song: Song, newValue: Long, viewModel: MainViewModel) {
    viewModel.updateSongLoopPoints(song, newValue, song.loopEnd)
    NativeAudio.setLoopPoints(newValue, song.loopEnd)
}

private fun onStartAdjustMs(song: Song, deltaMs: Double, viewModel: MainViewModel) {
    val sampleRate = NativeAudio.getSampleRate()
    val deltaSamples = (sampleRate * deltaMs / 1000.0).toLong()
    val dur = NativeAudio.getDuration()
    val newStart = (song.loopStart + deltaSamples).coerceIn(0, if (song.loopEnd > 0) song.loopEnd else dur)
    onStartValueChange(song, newStart, viewModel)
}

private fun onEndValueChange(song: Song, newValue: Long, viewModel: MainViewModel) {
    viewModel.updateSongLoopPoints(song, song.loopStart, newValue)
    NativeAudio.setLoopPoints(song.loopStart, newValue)
}

private fun onEndAdjustMs(song: Song, deltaMs: Double, viewModel: MainViewModel) {
    val sampleRate = NativeAudio.getSampleRate()
    val deltaSamples = (sampleRate * deltaMs / 1000.0).toLong()
    val dur = NativeAudio.getDuration()
    val newEnd = (song.loopEnd + deltaSamples).coerceIn(song.loopStart, dur)
    onEndValueChange(song, newEnd, viewModel)
}

@Composable
fun PlaybackProgressBar(song: Song) {
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
            val startTime = TimeUtils.formatTime(currentFrame, sampleRate)
            val totalTime = TimeUtils.formatTime(totalFrames, sampleRate)
            Text(startTime, color = Color.Gray, fontSize = 12.sp)
            Text(totalTime, color = Color.Gray, fontSize = 12.sp)
        }
    }
}


