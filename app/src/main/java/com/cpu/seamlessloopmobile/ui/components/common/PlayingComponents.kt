package com.cpu.seamlessloopmobile.ui.components.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
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
import com.cpu.seamlessloopmobile.utils.rememberHapticClick
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * 播放控制台的核心页面与子组件，已完美融入 CPU 大人的 SeamlessLoopTheme 主题系统喵！(๑•̀ㅂ•́)و✧
 */
@Composable
fun MainInfoPage(
    songItem: Song,
    isPlaying: Boolean,
    buttonHapticFeedbackEnabled: Boolean = true,
    onRatingClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        SongArtwork(
            coverPath = songItem.coverPath,
            contentDescription = songItem.displayName,
            modifier = Modifier.size(268.dp),
            shape = RoundedCornerShape(28.dp),
            iconSize = 88.dp,
            backgroundColor = SeamlessLoopColors.DarkBgGradientEnd.copy(alpha = 0.82f),
            iconTint = SeamlessLoopColors.PurpleAccent
        )

        Spacer(modifier = Modifier.height(28.dp))

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

        AudioFileInfoRow(songItem)

        Spacer(modifier = Modifier.height(14.dp))

        // --- 评分控制 (0-5 循环) ---
        Button(
            onClick = rememberHapticClick(buttonHapticFeedbackEnabled, onClick = onRatingClick),
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

@Composable
private fun AudioFileInfoRow(song: Song) {
    val mime = formatMimeType(song.mimeType)
        ?: formatMimeType(mimeFromFileName(song.fileName))
        ?: "UNKNOWN"
    val sampleRate = song.sampleRateHz?.takeIf { it > 0 }?.let { formatSampleRate(it) }
        ?: "-- kHz"
    val bitrate = song.bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" }
        ?: "-- kbps"

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "$mime  ·  $sampleRate | $bitrate",
        style = MaterialTheme.typography.labelMedium.copy(
            color = SeamlessLoopColors.LightGray.copy(alpha = 0.82f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
    )
}

private fun formatSampleRate(sampleRateHz: Int): String {
    val khz = sampleRateHz / 1000.0
    return if (sampleRateHz % 1000 == 0) {
        "${sampleRateHz / 1000} kHz"
    } else {
        String.format(Locale.US, "%.1f kHz", khz)
    }
}

private fun mimeFromFileName(fileName: String): String? {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg", "oga" -> "audio/ogg"
        else -> null
    }
}

private fun formatMimeType(mimeType: String?): String? {
    val trimmed = mimeType
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    val type = if (trimmed.startsWith("audio/", ignoreCase = true)) {
        trimmed.substringAfter('/')
    } else {
        trimmed
    }
    return type.uppercase(Locale.US)
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

    val displayFrame = sliderPosition?.toLong() ?: currentFrame
    val startTime = TimeUtils.formatTime(displayFrame, sampleRate)
    val totalTime = TimeUtils.formatTime(totalFrames, sampleRate)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = startTime,
            color = SeamlessLoopColors.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.Start
        )
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
            modifier = Modifier
                .weight(1f)
                .height(32.dp),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(SeamlessLoopColors.PurpleAccent)
                        .border(2.dp, SeamlessLoopColors.White.copy(alpha = 0.82f), CircleShape)
                )
            },
            track = { sliderState ->
                val fraction = if (sliderState.valueRange.endInclusive > sliderState.valueRange.start) {
                    (sliderState.value - sliderState.valueRange.start) / (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                } else 0f
                val markerColor = Color(0xFFFFD166)
                val loopStartFraction = if (totalFrames > 0L && song.loopStart > 0L) {
                    (song.loopStart.toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f)
                } else null
                val loopEndFraction = if (totalFrames > 0L && song.loopEnd > 0L && song.loopEnd < totalFrames) {
                    (song.loopEnd.toFloat() / totalFrames.toFloat()).coerceIn(0f, 1f)
                } else null

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SeamlessLoopColors.Gray.copy(alpha = 0.34f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SeamlessLoopColors.PurpleAccent)
                    )
                    loopStartFraction?.let { LoopMarker(it, markerColor) }
                    loopEndFraction?.let { LoopMarker(it, markerColor) }
                }
            }
        )
        Text(
            text = totalTime,
            color = SeamlessLoopColors.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun BoxScope.LoopMarker(
    fraction: Float,
    color: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction.coerceIn(0.002f, 0.998f))
            .height(18.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )
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
    buttonHapticFeedbackEnabled: Boolean = true,
    onTogglePlayMode: () -> Unit,
    onToggleSeamlessLoop: () -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = rememberHapticClick(buttonHapticFeedbackEnabled, onClick = onTogglePlayMode)) {
                val modeIcon = when(playMode) {
                    com.cpu.seamlessloopmobile.viewmodel.PlayMode.LIST_LOOP -> Icons.Default.Repeat
                    com.cpu.seamlessloopmobile.viewmodel.PlayMode.SINGLE_LOOP -> Icons.Default.RepeatOne
                    com.cpu.seamlessloopmobile.viewmodel.PlayMode.SHUFFLE -> Icons.Default.Shuffle
                }
                Icon(modeIcon, contentDescription = "播放模式", tint = SeamlessLoopColors.PurpleAccent, modifier = Modifier.size(24.dp))
            }
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = rememberHapticClick(buttonHapticFeedbackEnabled, onClick = onPrev)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", tint = SeamlessLoopColors.White, modifier = Modifier.size(32.dp))
            }
        }
 
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            FilledIconButton(
                onClick = rememberHapticClick(buttonHapticFeedbackEnabled) { if (!isPreparing) onPlayPause() },
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
        }
 
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = rememberHapticClick(buttonHapticFeedbackEnabled, onClick = onNext)) {
                Icon(Icons.Default.SkipNext, contentDescription = "下一首", tint = SeamlessLoopColors.White, modifier = Modifier.size(32.dp))
            }
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = rememberHapticClick(buttonHapticFeedbackEnabled, onClick = onToggleSeamlessLoop)) {
                Icon(
                    imageVector = Icons.Default.AllInclusive,
                    contentDescription = "单曲无缝循环",
                    tint = if (isSeamlessLoopEnabled) SeamlessLoopColors.PurpleAccent else SeamlessLoopColors.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
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
                onNext = {}
            )
        }
    }
}
