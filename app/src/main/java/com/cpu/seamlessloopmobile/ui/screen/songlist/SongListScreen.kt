package com.cpu.seamlessloopmobile.ui.screen.songlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.ui.components.common.SongListItem
import kotlinx.coroutines.delay

/**
 * 歌曲大列表通用屏幕，现已完美兼职全屏搜索大页面喵！🚀
 * 架构最简、复用度最高，支持批量操作与完美的切歌循环喵！(๑•̀ㅂ•́)و✧
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SongListScreen(
    songs: List<Song>,
    currentPlayingSongPath: String?,
    isSelectionMode: Boolean,
    selectedItems: Set<String>,
    onPlaySong: (Song) -> Unit,
    onToggleSelection: (Song) -> Unit,
    onShowMoreOptions: (Song) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    // --- 极简复用搜索参数喵！🔍 ---
    isSearchType: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }

    // 进入页面时自动聚焦并弹起软键盘喵
    if (isSearchType) {
        LaunchedEffect(Unit) {
            delay(150) // 腾出少量时间给转场过渡动画喵
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 如果是搜索列表类型，在顶部渲染出精致又高级的输入框喵
        if (isSearchType) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("搜索歌名、歌手或专辑喵...", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "清除",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
            }
            Divider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }

        // 核心 LazyColumn 歌曲列表
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isSearchType && searchQuery.isBlank()) {
                // 搜索空输入提示页
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "今天想在乐库里寻找什么曲子呢？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "在这里打字，莱芙会自动为您防抖寻找喵 (´w｀)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else if (isSearchType && songs.isEmpty()) {
                // 搜索无结果提示页
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "ㅠㅠ",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "没有找到相符的歌曲喵",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "要不要换个关键词再试试看呢 (´w｀)...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 正常的歌曲列表展现
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 80.dp) // 预留底部 MiniPlayer 避让距离喵
                ) {
                    items(songs, key = { it.id }) { song ->
                        val isPlaying = song.filePath == currentPlayingSongPath
                        val isSelected = selectedItems.contains(song.filePath)

                        SongListItem(
                            song = song,
                            isPlaying = isPlaying,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) onToggleSelection(song)
                                else onPlaySong(song)
                            },
                            onLongClick = { onToggleSelection(song) },
                            onMoreClick = { onShowMoreOptions(song) }
                        )
                    }
                }
            }
        }
    }
}
