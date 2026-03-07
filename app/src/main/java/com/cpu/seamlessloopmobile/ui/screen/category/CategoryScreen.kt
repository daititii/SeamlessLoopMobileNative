package com.cpu.seamlessloopmobile.ui.screen.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cpu.seamlessloopmobile.model.Folder
import com.cpu.seamlessloopmobile.ui.components.FolderListItem
import androidx.compose.ui.graphics.Color

@Composable
fun CategoryScreen(
    items: List<Folder>,
    onOpenFolder: (Folder) -> Unit,
    isSelectionMode: Boolean,
    selectedFolders: Set<Folder>,
    onToggleFolderSelection: (Folder) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(items) { folder ->
            val isSelected = selectedFolders.any { it.path == folder.path }
            FolderListItem(
                folder = folder,
                isSelected = isSelected,
                onClick = {
                    if (isSelectionMode) {
                        onToggleFolderSelection(folder)
                    } else {
                        onOpenFolder(folder)
                    }
                },
                onLongClick = { onToggleFolderSelection(folder) }
            )
        }
    }
}
