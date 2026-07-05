package com.cpu.seamlessloopmobile.ui.screen.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpu.seamlessloopmobile.data.SettingsManager
import com.cpu.seamlessloopmobile.data.ThemePreference
import com.cpu.seamlessloopmobile.utils.rememberHapticClick

private enum class SettingsPage {
    Appearance,
    Playback,
    Data
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRescan: (Context) -> Unit,
    onSyncPc: () -> Unit,
    onExportDatabase: () -> Unit,
    seamlessLoopCountLimit: Int,
    onSeamlessLoopCountLimitChange: (Int) -> Unit,
    buttonHapticFeedbackEnabled: Boolean,
    onButtonHapticFeedbackEnabledChange: (Boolean) -> Unit,
    onClearListenStats: () -> Unit,
    isDarkTheme: Boolean,
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier
) {
    var activePage by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val languages = remember { listOf("简体中文") }
    var selectedLanguage by remember { mutableStateOf(languages[0]) }
    var loopLimitText by remember(seamlessLoopCountLimit) { mutableStateOf(seamlessLoopCountLimit.toString()) }
    var loopLimitSavedMessage by remember { mutableStateOf<String?>(null) }
    val maxLoopLimit = SettingsManager.MAX_SEAMLESS_LOOP_COUNT_LIMIT
    val parsedLoopLimit = remember(loopLimitText) { loopLimitText.toIntOrNull() }
    val loopLimitError = remember(loopLimitText, parsedLoopLimit, maxLoopLimit) {
        when {
            loopLimitText.isBlank() -> "请输入 0 到 $maxLoopLimit 之间的整数"
            !loopLimitText.all { it.isDigit() } -> "仅允许输入非负整数，不能包含符号、小数点或空格"
            parsedLoopLimit == null -> "数字过大，请输入 0 到 $maxLoopLimit 之间的整数"
            parsedLoopLimit > maxLoopLimit -> "循环次数不能超过 $maxLoopLimit"
            else -> null
        }
    }

    BackHandler(enabled = activePage != null) {
        activePage = null
    }

    AnimatedContent(
        targetState = activePage,
        transitionSpec = {
            (scaleIn(
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                initialScale = 0.92f
            ) + fadeIn(animationSpec = tween(180))) togetherWith
                (scaleOut(
                    animationSpec = tween(160, easing = FastOutSlowInEasing),
                    targetScale = 1.04f
                ) + fadeOut(animationSpec = tween(120)))
        },
        label = "SettingsPageNavigation",
        modifier = modifier.fillMaxSize()
    ) { page ->
        if (page == null) {
            SettingsHomePage(
                buttonHapticFeedbackEnabled = buttonHapticFeedbackEnabled,
                onPageClick = { activePage = it }
            )
        } else {
            SettingsDetailPage(
                page = page,
                content = {
                    when (page) {
                        SettingsPage.Appearance -> AppearanceSettings(
                            isDarkTheme = isDarkTheme,
                            themePreference = themePreference,
                            onThemePreferenceChange = onThemePreferenceChange,
                            buttonHapticFeedbackEnabled = buttonHapticFeedbackEnabled,
                            onButtonHapticFeedbackEnabledChange = onButtonHapticFeedbackEnabledChange,
                            dropdownExpanded = dropdownExpanded,
                            onDropdownExpandedChange = { dropdownExpanded = it },
                            selectedLanguage = selectedLanguage,
                            languages = languages,
                            onLanguageSelected = { selectedLanguage = it }
                        )
                        SettingsPage.Playback -> PlaybackSettings(
                            loopLimitText = loopLimitText,
                            onLoopLimitTextChange = {
                                loopLimitText = it
                                loopLimitSavedMessage = null
                            },
                            loopLimitError = loopLimitError,
                            loopLimitSavedMessage = loopLimitSavedMessage,
                            maxLoopLimit = maxLoopLimit,
                            parsedLoopLimit = parsedLoopLimit,
                            buttonHapticFeedbackEnabled = buttonHapticFeedbackEnabled,
                            onSaveLoopLimit = { value ->
                                onSeamlessLoopCountLimitChange(value)
                                loopLimitSavedMessage = if (value == 0) {
                                    "已保存：无限循环"
                                } else {
                                    "已保存：循环 $value 次后切换下一首"
                                }
                            }
                        )
                        SettingsPage.Data -> DataSettings(
                            onClosePage = onBack,
                            onRescan = onRescan,
                            onSyncPc = onSyncPc,
                            onExportDatabase = onExportDatabase,
                            onClearListenStats = onClearListenStats,
                            buttonHapticFeedbackEnabled = buttonHapticFeedbackEnabled
                        )
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHomePage(
    buttonHapticFeedbackEnabled: Boolean,
    onPageClick: (SettingsPage) -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 176.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsGroupCard(
                    title = "界面",
                    pages = listOf(SettingsPage.Appearance),
                    buttonHapticFeedbackEnabled = buttonHapticFeedbackEnabled,
                    onPageClick = onPageClick
                )
            }
            item {
                SettingsGroupCard(
                    title = "播放与数据",
                    pages = listOf(SettingsPage.Playback, SettingsPage.Data),
                    buttonHapticFeedbackEnabled = buttonHapticFeedbackEnabled,
                    onPageClick = onPageClick
                )
            }
            item {
                FooterInfo()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDetailPage(
    page: SettingsPage,
    content: @Composable () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(page.title, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 176.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { content() }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    pages: List<SettingsPage>,
    buttonHapticFeedbackEnabled: Boolean,
    onPageClick: (SettingsPage) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
        ) {
            pages.forEachIndexed { index, page ->
                SettingsNavigationRow(
                    page = page,
                    buttonHapticFeedbackEnabled = buttonHapticFeedbackEnabled,
                    onClick = { onPageClick(page) }
                )
                if (index < pages.lastIndex) {
                    Divider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    page: SettingsPage,
    buttonHapticFeedbackEnabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = rememberHapticClick(buttonHapticFeedbackEnabled, onClick = onClick))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconBox(icon = page.icon)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = page.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSettings(
    isDarkTheme: Boolean,
    themePreference: ThemePreference,
    onThemePreferenceChange: (ThemePreference) -> Unit,
    buttonHapticFeedbackEnabled: Boolean,
    onButtonHapticFeedbackEnabledChange: (Boolean) -> Unit,
    dropdownExpanded: Boolean,
    onDropdownExpandedChange: (Boolean) -> Unit,
    selectedLanguage: String,
    languages: List<String>,
    onLanguageSelected: (String) -> Unit
) {
    SettingsSectionCard {
        ThemePreferenceSelector(
            themePreference = themePreference,
            isDarkTheme = isDarkTheme,
            buttonHapticFeedbackEnabled = buttonHapticFeedbackEnabled,
            onThemePreferenceChange = onThemePreferenceChange
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    HapticFeedbackSettings(
        enabled = buttonHapticFeedbackEnabled,
        onEnabledChange = onButtonHapticFeedbackEnabledChange
    )

    Spacer(modifier = Modifier.height(16.dp))

    SettingsSectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIconBox(icon = Icons.Default.Language)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "语言",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { onDropdownExpandedChange(!dropdownExpanded) }
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
                onDismissRequest = { onDropdownExpandedChange(false) }
            ) {
                languages.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language) },
                        onClick = {
                            onLanguageSelected(language)
                            onDropdownExpandedChange(false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HapticFeedbackSettings(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    SettingsSectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    onClick = rememberHapticClick(enabled) {
                        onEnabledChange(!enabled)
                    }
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconBox(icon = Icons.Default.Vibration)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "点击触感反馈",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "播放器和设置页按钮点击时提供轻微反馈",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = null
            )
        }
    }
}

@Composable
private fun ThemePreferenceSelector(
    themePreference: ThemePreference,
    isDarkTheme: Boolean,
    buttonHapticFeedbackEnabled: Boolean,
    onThemePreferenceChange: (ThemePreference) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SettingsIconBox(icon = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "显示模式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when (themePreference) {
                    ThemePreference.SYSTEM -> if (isDarkTheme) "跟随系统，当前为深色" else "跟随系统，当前为浅色"
                    ThemePreference.LIGHT -> "固定使用浅色"
                    ThemePreference.DARK -> "固定使用深色"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThemePreferenceButton(
            text = "跟随系统",
            selected = themePreference == ThemePreference.SYSTEM,
            buttonHapticFeedbackEnabled = buttonHapticFeedbackEnabled,
            onClick = { onThemePreferenceChange(ThemePreference.SYSTEM) },
            modifier = Modifier.weight(1f)
        )
        ThemePreferenceButton(
            text = "浅色",
            selected = themePreference == ThemePreference.LIGHT,
            buttonHapticFeedbackEnabled = buttonHapticFeedbackEnabled,
            onClick = { onThemePreferenceChange(ThemePreference.LIGHT) },
            modifier = Modifier.weight(1f)
        )
        ThemePreferenceButton(
            text = "深色",
            selected = themePreference == ThemePreference.DARK,
            buttonHapticFeedbackEnabled = buttonHapticFeedbackEnabled,
            onClick = { onThemePreferenceChange(ThemePreference.DARK) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ThemePreferenceButton(
    text: String,
    selected: Boolean,
    buttonHapticFeedbackEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = rememberHapticClick(buttonHapticFeedbackEnabled, onClick = onClick),
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = rememberHapticClick(buttonHapticFeedbackEnabled, onClick = onClick),
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PlaybackSettings(
    loopLimitText: String,
    onLoopLimitTextChange: (String) -> Unit,
    loopLimitError: String?,
    loopLimitSavedMessage: String?,
    maxLoopLimit: Int,
    parsedLoopLimit: Int?,
    buttonHapticFeedbackEnabled: Boolean,
    onSaveLoopLimit: (Int) -> Unit
) {
    SettingsSectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIconBox(icon = Icons.Default.RepeatOne)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "无缝循环行为",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "控制单曲循环达到指定次数后的调度方式",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
            value = loopLimitText,
            onValueChange = onLoopLimitTextChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("单曲无缝循环次数上限") },
            singleLine = true,
            isError = loopLimitError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = {
                Text(
                    text = loopLimitError
                        ?: loopLimitSavedMessage
                        ?: "0 表示无限循环；最大值：$maxLoopLimit",
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
            onClick = rememberHapticClick(buttonHapticFeedbackEnabled) { parsedLoopLimit?.let(onSaveLoopLimit) },
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

@Composable
private fun DataSettings(
    onClosePage: () -> Unit,
    onRescan: (Context) -> Unit,
    onSyncPc: () -> Unit,
    onExportDatabase: () -> Unit,
    onClearListenStats: () -> Unit,
    buttonHapticFeedbackEnabled: Boolean
) {
    val context = LocalContext.current
    var showClearStatsDialog by remember { mutableStateOf(false) }

    if (showClearStatsDialog) {
        AlertDialog(
            onDismissRequest = { showClearStatsDialog = false },
            title = { Text("清除播放统计") },
            text = { Text("这会清空所有歌曲的收听时长统计，不会删除音乐文件。") },
            confirmButton = {
                TextButton(
                    onClick = rememberHapticClick(buttonHapticFeedbackEnabled) {
                        onClearListenStats()
                        showClearStatsDialog = false
                    }
                ) {
                    Text("清除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = rememberHapticClick(buttonHapticFeedbackEnabled) {
                        showClearStatsDialog = false
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    SettingsSectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIconBox(icon = Icons.Default.Sync)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "数据同步与管理",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "扫描媒体库，导入或导出 PC 端数据库",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = rememberHapticClick(buttonHapticFeedbackEnabled) {
                onClosePage()
                onRescan(context)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("重新扫描库音乐", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = rememberHapticClick(buttonHapticFeedbackEnabled) {
                onClosePage()
                onSyncPc()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("导入 PC 端数据库", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = rememberHapticClick(buttonHapticFeedbackEnabled) {
                onClosePage()
                onExportDatabase()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("导出 PC 端数据库", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = rememberHapticClick(buttonHapticFeedbackEnabled) {
                showClearStatsDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("清除播放统计", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsSectionCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsIconBox(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun FooterInfo() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Seamless Loop Mobile v0.2.0",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "莱芙・泽诺为您倾情服务喵 (´w｀)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            lineHeight = 16.sp
        )
    }
}

private val SettingsPage.title: String
    get() = when (this) {
        SettingsPage.Appearance -> "外观"
        SettingsPage.Playback -> "播放"
        SettingsPage.Data -> "数据"
    }

private val SettingsPage.description: String
    get() = when (this) {
        SettingsPage.Appearance -> "语言、昼夜模式与触感反馈"
        SettingsPage.Playback -> "无缝循环次数与播放行为"
        SettingsPage.Data -> "扫描、导入和导出音乐数据"
    }

private val SettingsPage.icon: ImageVector
    get() = when (this) {
        SettingsPage.Appearance -> Icons.Default.DarkMode
        SettingsPage.Playback -> Icons.Default.RepeatOne
        SettingsPage.Data -> Icons.Default.Sync
    }
