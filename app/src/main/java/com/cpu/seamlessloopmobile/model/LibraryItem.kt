package com.cpu.seamlessloopmobile.model

/**
 * 列表项包装类，用于在同一个 RecyclerView 中混合显示标题、歌单和文件夹
 */
sealed class LibraryItem {
    data class Header(val title: String) : LibraryItem()
    data class PlaylistWrapper(val playlist: Playlist, val songCount: Int) : LibraryItem()
    data class FolderWrapper(val folder: Folder) : LibraryItem()
    data class QuickAction(val title: String, val iconRes: Int, val count: Int) : LibraryItem()
}
