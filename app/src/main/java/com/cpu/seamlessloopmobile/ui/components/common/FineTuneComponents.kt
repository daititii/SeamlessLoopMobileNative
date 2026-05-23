package com.cpu.seamlessloopmobile.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalInspectionMode
import com.cpu.seamlessloopmobile.jni.LoopPoint
import com.cpu.seamlessloopmobile.jni.NativeAudio
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.ui.theme.SeamlessLoopColors

/**
 * 循环微调面板，已完美融入 CPU 大人的 SeamlessLoopTheme 主题系统喵！(๑•̀ㅂ•́)و✧
 */
@Composable
fun FineTunePage(
    song: Song, 
    tempLoopStart: Long,
    tempLoopEnd: Long,
    detectedPoints: List<LoopPoint>? = null,
    isDetecting: Boolean = false,
    onStartValueChange: (Long) -> Unit,
    onEndValueChange: (Long) -> Unit,
    onStartAdjustMs: (Double) -> Unit,
    onEndAdjustMs: (Double) -> Unit,
    onEditClick: (Boolean) -> Unit,
    onApplyAndListen: () -> Unit,
    onDetectClick: () -> Unit = {},
    onPointSelect: (LoopPoint) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "循环参数调节 (A-B)",
            style = MaterialTheme.typography.titleSmall.copy(color = SeamlessLoopColors.LightGray)
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        // 起点 A (使用 weight 1 分摊空间)
        Box(modifier = Modifier.weight(1f)) {
            TuneSectionBox(
                label = "循环起点 (A)",
                samples = tempLoopStart,
                accentColor = SeamlessLoopColors.PointAccentA,
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
                accentColor = SeamlessLoopColors.PointAccentB,
                onValueChange = onEndValueChange,
                onAdjustMs = onEndAdjustMs,
                onEditClick = { onEditClick(false) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 自动检测区域喵 ---
        if (isDetecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = SeamlessLoopColors.PurpleAccent,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            OutlinedButton(
                onClick = onDetectClick,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SeamlessLoopColors.PurpleAccent.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = SeamlessLoopColors.PurpleAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("自动探测循环点", color = SeamlessLoopColors.PurpleAccent, fontSize = 12.sp)
            }
            
            detectedPoints?.let { points ->
                if (points.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("探测结果 (点击应用):", color = SeamlessLoopColors.Gray, fontSize = 10.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            points.take(5).forEach { point ->
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onPointSelect(point) },
                                    color = SeamlessLoopColors.ComponentDarkBg,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            String.format("%.1f%%", point.score * 100),
                                            color = SeamlessLoopColors.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            "相似度",
                                            color = SeamlessLoopColors.Gray,
                                            fontSize = 8.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onApplyAndListen,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SeamlessLoopColors.ButtonDarkBg),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Default.Hearing, contentDescription = null, tint = SeamlessLoopColors.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(4.dp))
            Text("应用并试听", color = SeamlessLoopColors.White, fontSize = 13.sp)
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
    val isPreview = LocalInspectionMode.current
    val sampleRate = if (isPreview) 44100 else NativeAudio.getSampleRate()
    val seconds = samples.toDouble() / sampleRate

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SeamlessLoopColors.DarkBgGradientStart.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SeamlessLoopColors.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                label, 
                color = SeamlessLoopColors.LightGray, 
                fontSize = 11.sp, 
                modifier = Modifier.align(Alignment.Start)
            )
            
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
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    String.format("%.3fs", seconds),
                    color = SeamlessLoopColors.Gray,
                    fontSize = 15.sp,
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
                TuneGridButton("现在", modifier = Modifier.weight(1f)) { if(!isPreview) onValueChange(NativeAudio.getCurrentPosition()) }
                TuneGridButton("最大", modifier = Modifier.weight(1f)) { if(!isPreview) onValueChange(NativeAudio.getDuration()) }
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
        color = SeamlessLoopColors.ComponentDarkBg,
        shape = RoundedCornerShape(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = SeamlessLoopColors.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Normal)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1E1E2E)
@Composable
fun FineTunePagePreview() {
    Box(modifier = Modifier.background(Color(0xFF1E1E2E))) {
        FineTunePage(
            song = Song(
                id = 1L,
                fileName = "example.wav",
                filePath = "/sdcard/music/example.wav",
                totalSamples = 1000000L,
                displayName = "示例歌曲 - cpu 大人专用",
                artist = "莱芙",
                duration = 300000L,
                loopStart = 1000L,
                loopEnd = 5000L
            ),
            tempLoopStart = 1000L,
            tempLoopEnd = 5000L,
            onStartValueChange = {},
            onEndValueChange = {},
            onStartAdjustMs = {},
            onEndAdjustMs = {},
            onEditClick = {},
            onApplyAndListen = {}
        )
    }
}
