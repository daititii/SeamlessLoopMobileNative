package com.cpu.seamlessloopmobile.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpu.seamlessloopmobile.data.SettingsManager

/**
 * 设置侧边栏面板喵！⚙️
 * 优雅美观，包含语言选择（壳子）、库重新扫描、PC 数据库导入与 PC 兼容数据库导出喵！(๑•̀ㅂ•́)و✧
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onRescan: () -> Unit,
    onSyncPc: () -> Unit,
    onExportDatabase: () -> Unit,
    seamlessLoopCountLimit: Int,
    onSeamlessLoopCountLimitChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var dropdownExpanded by remember { mutableStateOf(false) }
    val languages = listOf("简体中文")
    var selectedLanguage by remember { mutableStateOf(languages[0]) }
    var loopLimitText by remember(seamlessLoopCountLimit) { mutableStateOf(seamlessLoopCountLimit.toString()) }
    var loopLimitSavedMessage by remember { mutableStateOf<String?>(null) }
    val maxLoopLimit = SettingsManager.MAX_SEAMLESS_LOOP_COUNT_LIMIT
    val parsedLoopLimit = remember(loopLimitText) {
        loopLimitText.toIntOrNull()
    }
    val loopLimitError = remember(loopLimitText, parsedLoopLimit, maxLoopLimit) {
        when {
            loopLimitText.isBlank() -> "请输入 0 到 $maxLoopLimit 之间的整数"
            !loopLimitText.all { it.isDigit() } -> "仅允许输入非负整数，不能包含符号、小数点或空格"
            parsedLoopLimit == null -> "数字过大，请输入 0 到 $maxLoopLimit 之间的整数"
            parsedLoopLimit > maxLoopLimit -> "循环次数不能超过 $maxLoopLimit"
            else -> null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // --- 顶部标题与返回控制 ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Divider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- 1. 语言设置（壳子） ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "语言与显示",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedLanguage,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        languages.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(text = selectionOption) },
                                onClick = {
                                    selectedLanguage = selectionOption
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "* 目前仅支持简体中文喵，更多语言敬请期待 (´w｀)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 2. 播放行为设置 ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.RepeatOne,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "无缝循环行为",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = loopLimitText,
                    onValueChange = { newValue ->
                        loopLimitText = newValue
                        loopLimitSavedMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("单曲无缝循环次数上限") },
                    singleLine = true,
                    isError = loopLimitError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            text = loopLimitError
                                ?: loopLimitSavedMessage
                                ?: "0 表示无限循环；达到上限后自动切换下一首。最大值：$maxLoopLimit",
                            color = when {
                                loopLimitError != null -> MaterialTheme.colorScheme.error
                                loopLimitSavedMessage != null -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        parsedLoopLimit?.let { value ->
                            onSeamlessLoopCountLimitChange(value)
                            loopLimitSavedMessage = if (value == 0) {
                                "已保存：无限循环"
                            } else {
                                "已保存：循环 $value 次后切换下一首"
                            }
                        }
                    },
                    enabled = loopLimitError == null && parsedLoopLimit != null,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存循环次数设置", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. 数据与同步中心 ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "数据同步与管理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 重新扫描按钮
                Button(
                    onClick = {
                        onClose()
                        onRescan()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("重新扫描库音乐", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // PC 导入按钮
                OutlinedButton(
                    onClick = {
                        onClose()
                        onSyncPc()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导入 PC 端数据库", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // PC 兼容数据库导出按钮
                // 注意：这里导出的不是手机 Room 原始库，而是转换后的 PC 端 3NF 数据库喵。
                // 用系统文件保存器选择导出位置，避免硬编码下载目录，也不用额外申请存储写权限。
                OutlinedButton(
                    onClick = {
                        onClose()
                        onExportDatabase()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出 PC 端数据库", fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "提示：重新扫描会自动发现设备上的所有音频文件喵！若有在电脑端编辑好的无缝循环数据，可以直接选择 PC 数据库文件进行同步；手机端修改过的循环点、评分和歌单也可以导出为 PC 端可识别的数据库喵。(๑•̀ㅂ•́)و✧",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        // --- 底部版权信息喵 ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Seamless Loop Mobile v1.0",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "莱芙・泽诺为您倾情服务喵 (´w｀)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}
