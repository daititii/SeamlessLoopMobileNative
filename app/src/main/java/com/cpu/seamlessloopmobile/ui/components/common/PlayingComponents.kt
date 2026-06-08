package com.cpu.seamlessloopmobile.ui.components.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalInspectionMode
import com.cpu.seamlessloopmobile.jni.NativeAudio
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.utils.TimeUtils
import com.cpu.seamlessloopmobile.ui.theme.SeamlessLoopColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * 播放控制台的核心页面与子组件，已完美融入 CPU 大人的 SeamlessLoopTheme 主题系统喵！(๑•̀ㅂ•́)و✧
 */
@Composable
fun MainInfoPage(
    songItem: Song,
    isPlaying: Boolean,
    onRatingClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // --- 封面图旋转动效喵 ---
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
                .background(SeamlessLoopColors.DarkBgGradientEnd.copy(alpha = 0.8f))
                .padding(2.dp)
                .graphicsLayer { rotationZ = if (isPlaying) rotation else 0f }
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(64.dp),
                tint = SeamlessLoopColors.PurpleAccent
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- 歌曲信息 ---
        Text(
            text = songItem.displayName ?: songItem.fileName,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = SeamlessLoopColors.White,
                fontSize = 24.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = songItem.artist ?: "Unknown Artist",
            style = MaterialTheme.typography.bodyLarge.copy(
                color = SeamlessLoopColors.Gray,
                fontSize = 16.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- 评分控制 (0-5 循环) ---
        Button(
            onClick = onRatingClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = SeamlessLoopColors.White.copy(alpha = 0.05f),
                contentColor = if (songItem.rating > 0) Color(0xFFFFD700) else SeamlessLoopColors.Gray
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (songItem.rating > 0) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Rating",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (songItem.rating > 0) "${songItem.rating}" else "未评分",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackProgressBar(
    song: Song,
    fallbackPositionFrames: Long = 0L,
    onSeekComplete: (() -> Unit)? = null
) {
    val isPreview = LocalInspectionMode.current
    var currentFrame by remember(song.id) { mutableStateOf(0L) }
    var totalFrames by remember(song.id, song.totalSamples, song.duration) { mutableStateOf(0L) }
    var sampleRate by remember(song.id) { mutableStateOf(44100L) }
    var sliderPosition by remember(song.id) { mutableStateOf<Float?>(null) }
    var hasSeenNativeProgress by remember(song.id) { mutableStateOf(false) }
    val latestFallbackPositionFrames by rememberUpdatedState(fallbackPositionFrames)
    val coroutineScope = rememberCoroutineScope()

    fun fallbackTotalFrames(rate: Long): Long {
        return song.totalSamples.takeIf { it > 0L }
            ?: song.duration.takeIf { it > 0L }?.let { it * rate / 1000L }
            ?: 0L
    }

    LaunchedEffect(song.id, song.totalSamples, song.duration) {
        if (isPreview) {
            sampleRate = 44100L
            totalFrames = 1000000L
            currentFrame = 0L
            return@LaunchedEffect
        }

        while (true) {
            val nextSampleRate = runCatching { NativeAudio.getSampleRate() }
                .getOrDefault(44100)
                .takeIf { it > 0 }
                ?.toLong()
                ?: 44100L
            val nativeDuration = runCatching { NativeAudio.getDuration() }.getOrDefault(0L)
            val nextTotalFrames = nativeDuration.takeIf { it > 0L } ?: fallbackTotalFrames(nextSampleRate)

            sampleRate = nextSampleRate
            totalFrames = nextTotalFrames

            if (sliderPosition == null) {
                val nativePosition = if (nativeDuration > 0L) {
                    runCatching { NativeAudio.getCurrentPosition() }.getOrDefault(0L)
                } else {
                    0L
                }
                val nextPosition = when {
                    // 通知被清除会销毁 native engine，此时 NativeAudio 会回 0。
                    // 全屏播放页仍在前台时不要用这个 0 覆盖已有 UI 进度。
                    nativeDuration <= 0L && currentFrame > 0L -> currentFrame
                    nativeDuration <= 0L -> latestFallbackPositionFrames
                    nativePosition > 0L -> {
                        hasSeenNativeProgress = true
                        nativePosition
                    }
                    !hasSeenNativeProgress -> latestFallbackPositionFrames
                    else -> nativePosition
                }
                currentFrame = if (nextTotalFrames > 0L) {
                    nextPosition.coerceIn(0L, nextTotalFrames)
                } else {
                    nextPosition.coerceAtLeast(0L)
                }
            }
            delay(100)
        }
    }

    val sliderMax = totalFrames.toFloat().coerceAtLeast(1f)
    val displayedValue = (sliderPosition ?: currentFrame.toFloat()).coerceIn(0f, sliderMax)

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Slider(
            value = displayedValue,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = { 
                sliderPosition?.let { finalPos ->
                    if (!isPreview) {
                        hasSeenNativeProgress = true
                        currentFrame = finalPos.toLong()
                        NativeAudio.seekTo(finalPos.toLong())
                        
                        coroutineScope.launch {
                            delay(300)
                            sliderPosition = null
                            onSeekComplete?.invoke()
                        }
                    } else {
                        sliderPosition = null
                    }
                }
            },
            valueRange = 0f..sliderMax,
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
                            .background(SeamlessLoopColors.Gray.copy(alpha = 0.3f))
                    )
                    // 进度轨道喵
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(2.dp)
                            .background(SeamlessLoopColors.PurpleAccent)
                    )
                }
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayFrame = sliderPosition?.toLong() ?: currentFrame
            val startTime = TimeUtils.formatTime(displayFrame, sampleRate)
            val totalTime = TimeUtils.formatTime(totalFrames, sampleRate)
            Text(startTime, color = SeamlessLoopColors.Gray, fontSize = 9.sp, fontWeight = FontWeight.Light)
            Text(totalTime, color = SeamlessLoopColors.Gray, fontSize = 9.sp, fontWeight = FontWeight.Light)
        }
    }
}

@Composable
fun PlaybackControls(
    playMode: com.cpu.seamlessloopmobile.viewmodel.PlayMode,
    isSeamlessLoopEnabled: Boolean,
    isPlaying: Boolean,
    isPreparing: Boolean,
    isError: Boolean,
    showLoading: Boolean,
    onTogglePlayMode: () -> Unit,
    onToggleSeamlessLoop: () -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onTogglePlayMode) {
            val modeIcon = when(playMode) {
                com.cpu.seamlessloopmobile.viewmodel.PlayMode.LIST_LOOP -> Icons.Default.Repeat
                com.cpu.seamlessloopmobile.viewmodel.PlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
                com.cpu.seamlessloopmobile.viewmodel.PlayMode.SHUFFLE -> Icons.Default.Shuffle
            }
            Icon(modeIcon, contentDescription = "播放模式", tint = SeamlessLoopColors.PurpleAccent, modifier = Modifier.size(24.dp))
        }
        
        // 🆕 在旁边肩并肩并排放置无缝循环按钮！使用代表无限符号的平放8字（AllInclusive）表示喵！(๑•̀ㅂ•稳t)و✧
        IconButton(onClick = onToggleSeamlessLoop) {
            Icon(
                imageVector = Icons.Default.AllInclusive,
                contentDescription = "单曲无缝循环",
                tint = if (isSeamlessLoopEnabled) SeamlessLoopColors.PurpleAccent else SeamlessLoopColors.Gray,
                modifier = Modifier.size(24.dp)
            )
        }
        
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", tint = SeamlessLoopColors.White, modifier = Modifier.size(32.dp))
        }
 
        FilledIconButton(
            onClick = { if (!isPreparing) onPlayPause() },
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isError) MaterialTheme.colorScheme.error else SeamlessLoopColors.PurpleAccent,
                disabledContainerColor = SeamlessLoopColors.PurpleAccent.copy(alpha = 0.5f)
            ),
            enabled = !isPreparing
        ) {
            if (showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = SeamlessLoopColors.DarkBgGradientStart
                )
            } else {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "播放/暂停",
                    modifier = Modifier.size(36.dp),
                    tint = SeamlessLoopColors.DarkBgGradientStart
                )
            }
        }
 
        IconButton(onClick = onNext) {
            Icon(Icons.Default.SkipNext, contentDescription = "下一首", tint = SeamlessLoopColors.White, modifier = Modifier.size(32.dp))
        }
 
        IconButton(onClick = onMoreClick) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = SeamlessLoopColors.White, modifier = Modifier.size(24.dp))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E2E)
@Composable
fun MainInfoPagePreview() {
    MaterialTheme {
        MainInfoPage(
            songItem = Song(
                id = 1L,
                fileName = "test.wav",
                filePath = "",
                totalSamples = 0,
                displayName = "示例歌曲",
                artist = "莱芙"
            ),
            isPlaying = true
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E2E)
@Composable
fun PlaybackControlsPreview() {
    MaterialTheme {
        Box(modifier = Modifier.background(SeamlessLoopColors.DarkBgGradientStart).padding(16.dp)) {
            PlaybackControls(
                playMode = com.cpu.seamlessloopmobile.viewmodel.PlayMode.LIST_LOOP,
                isSeamlessLoopEnabled = true,
                isPlaying = false,
                isPreparing = false,
                isError = false,
                showLoading = false,
                onTogglePlayMode = {},
                onToggleSeamlessLoop = {},
                onPrev = {},
                onPlayPause = {},
                onNext = {},
                onMoreClick = {}
            )
        }
    }
}
