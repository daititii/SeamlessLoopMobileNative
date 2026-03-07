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
    val audioPlayState by viewModel.audioPlayState.collectAsState()
    val isPlaying = audioPlayState == com.cpu.seamlessloopmobile.audio.AudioPlayState.PLAYING
    val isPreparing = audioPlayState == com.cpu.seamlessloopmobile.audio.AudioPlayState.PREPARING
    val isError = audioPlayState == com.cpu.seamlessloopmobile.audio.AudioPlayState.ERROR
    val playMode by viewModel.playMode.observeAsState(com.cpu.seamlessloopmobile.viewmodel.PlayMode.SINGLE_LOOP)
    
    var showLoading by remember { mutableStateOf(false) }

    LaunchedEffect(isPreparing) {
        if (isPreparing) {
            kotlinx.coroutines.delay(2000)
            showLoading = true
        } else {
            showLoading = false
        }
    }
    
    val playingSong = if (currentSongIndex in playlist.indices) playlist[currentSongIndex] else null
    
    // 状态管理：使用 VerticalPagerState 实现纵向滑动切换喵
    val pagerState = rememberPagerState(initialPage = 0) { 2 } // 0: 主页, 1: 调节页
    
    // --- 编辑弹窗状态 ---
    var showEditDialog by remember { mutableStateOf(false) }
    var editIsStart by remember { mutableStateOf(true) }
    var editValueSamples by remember { mutableStateOf("") }
    var editValueTime by remember { mutableStateOf("") }

    var tempLoopStart by remember(playingSong?.id) { mutableStateOf(playingSong?.loopStart ?: 0L) }
    var tempLoopEnd by remember(playingSong?.id) { mutableStateOf(playingSong?.loopEnd ?: 0L) }

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
            Column(modifier = Modifier.fillMaxSize()) {
                // --- 顶部控制 (关闭按钮 & 指示器) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "收起", tint = Color.White)
                    }
                    
                    // 页面指示器改为横向小点
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(2) { index ->
                            Box(
                                modifier = Modifier
                                    .size(if (pagerState.currentPage == index) 8.dp else 4.dp)
                                    .clip(CircleShape)
                                    .background(if (pagerState.currentPage == index) Color(0xFFBB86FC) else Color.Gray)
                            )
                        }
                    }
                }

                // --- 分页内容 (权重 1 占据剩余空间) ---
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    beyondViewportPageCount = 1
                ) { page ->
                    when (page) {
                        0 -> MainInfoPage(songItem, isPlaying) // 主页只剩封面和歌曲信息
                        1 -> FineTunePage(
                            song = songItem,
                            tempLoopStart = tempLoopStart,
                            tempLoopEnd = tempLoopEnd,
                            onStartValueChange = { tempLoopStart = it },
                            onEndValueChange = { tempLoopEnd = it },
                            onStartAdjustMs = { deltaMs ->
                                val sampleRate = NativeAudio.getSampleRate()
                                val deltaSamples = (sampleRate * deltaMs / 1000.0).toLong()
                                val dur = NativeAudio.getDuration()
                                val actualEnd = if (tempLoopEnd > 0) tempLoopEnd else dur
                                tempLoopStart = (tempLoopStart + deltaSamples).coerceIn(0, actualEnd)
                            },
                            onEndAdjustMs = { deltaMs ->
                                val sampleRate = NativeAudio.getSampleRate()
                                val deltaSamples = (sampleRate * deltaMs / 1000.0).toLong()
                                val dur = NativeAudio.getDuration()
                                tempLoopEnd = (tempLoopEnd + deltaSamples).coerceIn(tempLoopStart, dur)
                            },
                            onEditClick = { isStart ->
                                editIsStart = isStart
                                editValueSamples = (if(isStart) tempLoopStart else tempLoopEnd).toString()
                                val sr = NativeAudio.getSampleRate()
                                editValueTime = String.format("%.3f", (if(isStart) tempLoopStart else tempLoopEnd).toDouble() / if(sr>0) sr else 44100)
                                showEditDialog = true
                            },
                            onApplyAndListen = {
                                viewModel.updateSongLoopPoints(songItem, tempLoopStart, tempLoopEnd)
                                NativeAudio.setLoopPoints(tempLoopStart, tempLoopEnd)
                                
                                val sampleRate = NativeAudio.getSampleRate().toLong()
                                val totalDur = NativeAudio.getDuration()
                                val actualEnd = if (tempLoopEnd > 0) tempLoopEnd else totalDur
                                val seekPos = (actualEnd - (sampleRate * 3)).coerceIn(0, actualEnd)
                                NativeAudio.seekTo(seekPos)
                            }
                        )
                    }
                }

                // --- 固定在底部的进度条与控制部分 ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. 紧凑型进度条
                    PlaybackProgressBar(songItem)

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. 紧凑型播放控制栏
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
                            Icon(modeIcon, contentDescription = "播放模式", tint = Color(0xFFBB86FC), modifier = Modifier.size(24.dp))
                        }
                        
                        IconButton(onClick = onPrev) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        FilledIconButton(
                            onClick = { if (!isPreparing) onPlayPause() },
                            modifier = Modifier.size(64.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isError) MaterialTheme.colorScheme.error else Color(0xFFBB86FC),
                                disabledContainerColor = Color(0xFFBB86FC).copy(alpha = 0.5f)
                            ),
                            enabled = !isPreparing
                        ) {
                            if (showLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp,
                                    color = Color.Black
                                )
                            } else {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "播放/暂停",
                                    modifier = Modifier.size(36.dp),
                                    tint = Color.Black
                                )
                            }
                        }

                        IconButton(onClick = onNext) {
                            Icon(Icons.Default.SkipNext, contentDescription = "下一首", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        // 示例：可以放个音量或者别的，这里放个占位
                        IconButton(onClick = { /* 更多控制 */ }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = Color.Gray, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

        // --- 简单的编辑弹窗喵 ---
        if (showEditDialog) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text(if (editIsStart) "修改循环起点 (A)" else "修改循环终点 (B)", color = Color.White) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = editValueSamples,
                            onValueChange = { editValueSamples = it },
                            label = { Text("采样数 (Samples)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editValueTime,
                            onValueChange = { editValueTime = it },
                            label = { Text("时间 (Seconds)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val newSamples = editValueSamples.toLongOrNull()
                        val newTime = editValueTime.toDoubleOrNull()
                        val sr = NativeAudio.getSampleRate()
                        
                        // 优先按采样数改，如果采样数没变但时间变了，按时间改喵
                        if (newSamples != null) {
                            if (editIsStart) tempLoopStart = newSamples
                            else tempLoopEnd = newSamples
                        } else if (newTime != null) {
                            val calculatedSamples = (newTime * sr).toLong()
                            if (editIsStart) tempLoopStart = calculatedSamples
                            else tempLoopEnd = calculatedSamples
                        }
                        showEditDialog = false
                    }) {
                        Text("确定", color = Color(0xFFBB86FC))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) {
                        Text("取消", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF1E1E2E)
            )
        }
    }
}

@Composable
fun MainInfoPage(
    songItem: Song,
    isPlaying: Boolean
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
    }
}

@Composable
fun FineTunePage(
    song: Song, 
    tempLoopStart: Long,
    tempLoopEnd: Long,
    onStartValueChange: (Long) -> Unit,
    onEndValueChange: (Long) -> Unit,
    onStartAdjustMs: (Double) -> Unit,
    onEndAdjustMs: (Double) -> Unit,
    onEditClick: (Boolean) -> Unit,
    onApplyAndListen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 标题稍微写小点喵
        Text(
            "循环参数调节 (A-B)",
            style = MaterialTheme.typography.titleSmall.copy(color = Color.LightGray)
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        // 起点 A (使用 weight 1 分摊空间)
        Box(modifier = Modifier.weight(1f)) {
            TuneSectionBox(
                label = "循环起点 (A)",
                samples = tempLoopStart,
                accentColor = Color(0xFF8FBBD9),
                onValueChange = onStartValueChange,
                onAdjustMs = onStartAdjustMs,
                onEditClick = { onEditClick(true) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 终点 B (使用 weight 1 分摊空间)
        Box(modifier = Modifier.weight(1f)) {
            TuneSectionBox(
                label = "循环终点 (B)",
                samples = tempLoopEnd,
                accentColor = Color(0xFFF398AF),
                onValueChange = onEndValueChange,
                onAdjustMs = onEndAdjustMs,
                onEditClick = { onEditClick(false) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onApplyAndListen,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF353545)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Default.Hearing, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(4.dp))
            Text("应用并试听", color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
fun TuneSectionBox(
    label: String,
    samples: Long,
    accentColor: Color,
    onValueChange: (Long) -> Unit,
    onAdjustMs: (Double) -> Unit,
    onEditClick: () -> Unit
) {
    val sampleRate = NativeAudio.getSampleRate()
    val seconds = samples.toDouble() / sampleRate

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1E1E2E).copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 标签靠左侧喵
            Text(
                label, 
                color = Color.LightGray, 
                fontSize = 11.sp, 
                modifier = Modifier.align(Alignment.Start)
            )
            
            // 采样数与时间点击区域
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditClick() }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    samples.toString(),
                    color = accentColor,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(6.dp)) // 增加间距喵！
                
                Text(
                    String.format("%.3fs", seconds),
                    color = Color.Gray,
                    fontSize = 15.sp, // 比原来的 11.sp 大多了喵！
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(
                thickness = 1.dp, 
                color = accentColor.copy(alpha = 0.3f), 
                modifier = Modifier.fillMaxWidth(0.5f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            // 第一排: 最小/现/最大
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TuneGridButton("最小", modifier = Modifier.weight(1f)) { onValueChange(0) }
                TuneGridButton("现在", modifier = Modifier.weight(1f)) { onValueChange(NativeAudio.getCurrentPosition()) }
                TuneGridButton("最大", modifier = Modifier.weight(1f)) { onValueChange(NativeAudio.getDuration()) }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 第二排: 秒级调节
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TuneGridButton("-5s", modifier = Modifier.weight(1f)) { onAdjustMs(-5000.0) }
                TuneGridButton("-1s", modifier = Modifier.weight(1f)) { onAdjustMs(-1000.0) }
                TuneGridButton("+1s", modifier = Modifier.weight(1f)) { onAdjustMs(1000.0) }
                TuneGridButton("+5s", modifier = Modifier.weight(1f)) { onAdjustMs(5000.0) }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 第三排: 毫秒级调节
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
            .height(30.dp)
            .clickable { onClick() },
        color = Color(0xFF2D2D3D),
        shape = RoundedCornerShape(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Normal)
        }
    }
}
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PlaybackProgressBar(song: Song) {
        var currentFrame by remember { mutableStateOf(0L) }
        val totalFrames = NativeAudio.getDuration()
        val sampleRate = NativeAudio.getSampleRate().toLong()

        LaunchedEffect(Unit) {
            while (true) {
                currentFrame = NativeAudio.getCurrentPosition()
                kotlinx.coroutines.delay(100)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Slider(
                value = currentFrame.toFloat(),
                onValueChange = { NativeAudio.seekTo(it.toLong()) },
                valueRange = 0f..totalFrames.toFloat().coerceAtLeast(1f),
                modifier = Modifier.height(12.dp),
                thumb = {},
                track = { sliderState ->
                    val fraction = if (sliderState.valueRange.endInclusive > sliderState.valueRange.start) {
                        (sliderState.value - sliderState.valueRange.start) / (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                    } else 0f
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // 背景轨道喵
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Color.Gray.copy(alpha = 0.3f))
                        )
                        // 进度轨道喵
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(2.dp)
                                .background(Color(0xFFBB86FC))
                        )
                    }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val startTime = TimeUtils.formatTime(currentFrame, sampleRate)
                val totalTime = TimeUtils.formatTime(totalFrames, sampleRate)
                Text(startTime, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Light)
                Text(totalTime, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Light)
            }
        }
    }


