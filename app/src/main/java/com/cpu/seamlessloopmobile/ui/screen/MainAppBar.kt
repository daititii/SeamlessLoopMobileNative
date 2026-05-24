package com.cpu.seamlessloopmobile.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.cpu.seamlessloopmobile.ui.state.DataUiState

@Composable
private fun SyncStatusSmallText(syncStatus: DataUiState<String>) {
    when (syncStatus) {
        is DataUiState.Loading -> {
            Text("🔍 寻找新曲子中...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        is DataUiState.Error -> {
            Text("❌ 扫描失败: ${syncStatus.message}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        is DataUiState.Success -> {
            if (syncStatus.data.isNotEmpty()) {
                Text(syncStatus.data, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeAppBar(
    syncStatus: DataUiState<String>,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    TopAppBar(
        title = {
            SyncStatusSmallText(syncStatus = syncStatus)
        },
        navigationIcon = {
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "设置")
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "搜索")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongListAppBar(
    title: String,
    syncStatus: DataUiState<String>,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                SyncStatusSmallText(syncStatus = syncStatus)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Default.Search, contentDescription = "搜索")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionAppBar(
    selectedItemsCount: Int,
    selectedFoldersCount: Int,
    selectedPlaylistsCount: Int,
    onCloseSelectionClick: () -> Unit
) {
    val selectionTitle = when {
        selectedItemsCount > 0 -> "已选择 ${selectedItemsCount} 首歌曲"
        selectedFoldersCount > 0 -> "已选择 ${selectedFoldersCount} 个文件夹"
        selectedPlaylistsCount > 0 -> "已选择 ${selectedPlaylistsCount} 个歌单"
        else -> "已选择 ${selectedItemsCount + selectedFoldersCount + selectedPlaylistsCount} 项"
    }

    TopAppBar(
        title = {
            Text(selectionTitle, fontWeight = FontWeight.Bold)
        },
        navigationIcon = {
            IconButton(onClick = onCloseSelectionClick) {
                Icon(Icons.Default.Close, contentDescription = "取消选择")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

