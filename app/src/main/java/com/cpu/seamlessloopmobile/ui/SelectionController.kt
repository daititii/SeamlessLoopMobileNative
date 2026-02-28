package com.cpu.seamlessloopmobile.ui

import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.cpu.seamlessloopmobile.adapter.LibraryAdapter
import com.cpu.seamlessloopmobile.adapter.SongAdapter
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.data.MusicRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI 总管小助手喵！负责处理多选模式下复杂的 Toolbar 变换和所有的弹窗交互。
 * 将这部分繁杂逻辑从 MainActivity 中隔离，保持大人的活动文件清爽干净！
 */
class SelectionController(
    private val context: Context,
    private val toolbar: Toolbar,
    private val songAdapter: SongAdapter,
    private val libraryAdapter: LibraryAdapter,
    private val repository: MusicRepository,
    private val viewModel: com.cpu.seamlessloopmobile.viewmodel.MainViewModel,
    private val coroutineScope: CoroutineScope,
    private val uiCallback: SelectionUiCallback
) {

    interface SelectionUiCallback {
        fun onExitSelection()
        fun onExitPlaylistSelection()
        fun onReloadHomeView()
        fun onRefreshPlaylist(playlist: com.cpu.seamlessloopmobile.model.Playlist)
        val isInsidePlaylist: Boolean
        val currentOpenPlaylist: com.cpu.seamlessloopmobile.model.Playlist?
    }

    var isSelectionMode = false
        private set
    var isPlaylistSelectionMode = false
        private set

    // --- 歌曲多选模式喵 ---

    fun enterSelectionMode() {
        if (isSelectionMode) return
        isSelectionMode = true
        songAdapter.setSelectionMode(true)
        updateSelectionMenu(songAdapter.getSelectedSongPaths().size)
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
        toolbar.setNavigationOnClickListener { exitSelectionMode() }
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        songAdapter.setSelectionMode(false)
        uiCallback.onExitSelection()
    }

    fun updateSelectionMenu(count: Int) {
        toolbar.title = "已选择: $count"
        toolbar.menu.clear()

        toolbar.menu.add(if (songAdapter.isAllSelected()) "全不选" else "全选").apply {
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setOnMenuItemClickListener {
                songAdapter.selectAll()
                true
            }
        }
                
        toolbar.menu.add("添加到歌单").apply {
            setIcon(android.R.drawable.ic_menu_add)
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setOnMenuItemClickListener {
                showAddToPlaylistDialog()
                true
            }
        }

        if (uiCallback.isInsidePlaylist) {
            toolbar.menu.add("从歌单移除").apply {
                setIcon(android.R.drawable.ic_menu_delete)
                setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
                setOnMenuItemClickListener {
                    val selectedSongs = songAdapter.getSelectedSongs()
                    uiCallback.currentOpenPlaylist?.let { playlist ->
                        coroutineScope.launch {
                            repository.removeSongsFromPlaylist(playlist.id, selectedSongs.map { it.id })
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "已从歌单移除 ${selectedSongs.size} 首歌曲喵", Toast.LENGTH_SHORT).show()
                                exitSelectionMode()
                                uiCallback.onRefreshPlaylist(playlist)
                            }
                        }
                    }
                    true
                }
            }
        }
    }

    // --- 歌单多选模式喵 ---

    fun enterPlaylistSelectionMode(initialPlaylist: com.cpu.seamlessloopmobile.model.Playlist?) {
        if (isPlaylistSelectionMode) return
        isPlaylistSelectionMode = true
        libraryAdapter.setSelectionMode(true)
        if (initialPlaylist != null) {
            libraryAdapter.toggleSelection(initialPlaylist.id)
        }
        
        toolbar.title = "已选择歌单: ${libraryAdapter.getSelectedPlaylists().size}"
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
        toolbar.setNavigationOnClickListener {
            exitPlaylistSelectionMode()
        }
        
        updatePlaylistSelectionMenu()
    }

    fun updatePlaylistSelectionMenu() {
        toolbar.menu.clear()
        
        toolbar.menu.add(if (libraryAdapter.isAllSelected()) "全不选" else "全选").apply {
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setOnMenuItemClickListener {
                libraryAdapter.selectAll()
                true
            }
        }

        toolbar.menu.add("删除已选").setIcon(android.R.drawable.ic_menu_delete).apply {
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setOnMenuItemClickListener {
                val selected = libraryAdapter.getSelectedPlaylists()
                if (selected.isEmpty()) {
                    Toast.makeText(context, "请先选择歌单喵", Toast.LENGTH_SHORT).show()
                    return@setOnMenuItemClickListener true
                }
                MaterialAlertDialogBuilder(context)
                    .setTitle("批量删除歌单")
                    .setMessage("cpu 大人，真的要心碎地删除这 ${selected.size} 个歌单吗？")
                    .setPositiveButton("删除") { _, _ ->
                        coroutineScope.launch {
                            selected.forEach { repository.deletePlaylist(it) }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "成功删除这 ${selected.size} 个歌单喵!", Toast.LENGTH_SHORT).show()
                                exitPlaylistSelectionMode()
                                uiCallback.onReloadHomeView()
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }
        }
    }

    fun exitPlaylistSelectionMode() {
        isPlaylistSelectionMode = false
        libraryAdapter.setSelectionMode(false)
        uiCallback.onExitPlaylistSelection()
    }

    // --- 歌单操作弹窗喵 ---

    private fun showAddToPlaylistDialog() {
        val selectedSongs = songAdapter.getSelectedSongs()
        if (selectedSongs.isEmpty()) {
            Toast.makeText(context, "请先选择歌曲喵", Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch(Dispatchers.Main) {
            val playlists = repository.getAllPlaylists()
            
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle("添加到歌单")
            
            val items = playlists.map { it.name }.toMutableList()
            items.add("+ 新建歌单")
            
            dialog.setItems(items.toTypedArray()) { _, which ->
                if (which == items.size - 1) {
                    showCreatePlaylistDialog(selectedSongs)
                } else {
                    val targetPlaylist = playlists[which]
                    addSongsToExistingPlaylist(targetPlaylist, selectedSongs)
                }
            }
            dialog.show()
        }
    }

    private fun showCreatePlaylistDialog(songs: List<Song>) {
        val editText = EditText(context)
        editText.hint = "歌单名称"
        
        MaterialAlertDialogBuilder(context)
            .setTitle("新建歌单")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val name = editText.text.toString()
                if (name.isNotEmpty()) {
                    coroutineScope.launch {
                        val persistentSongIds = songs.map { song ->
                            repository.insertOrUpdateSong(song)
                        }
                        
                        val newId = repository.insertPlaylist(com.cpu.seamlessloopmobile.model.Playlist(name = name))
                        val count = repository.addSongsToPlaylist(newId.toInt(), persistentSongIds)
                        
                        withContext(Dispatchers.Main) {
                            val message = if (count > 0) {
                                "成功创建歌单: $name, 已添加 $count 首歌曲喵!"
                            } else {
                                "成功创建空歌单: $name 喵!"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            exitSelectionMode()
                            uiCallback.onReloadHomeView()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addSongsToExistingPlaylist(playlist: com.cpu.seamlessloopmobile.model.Playlist, songs: List<Song>) {
        coroutineScope.launch {
            val persistentSongIds = songs.map { song ->
                repository.insertOrUpdateSong(song)
            }
            
            val count = repository.addSongsToPlaylist(playlist.id, persistentSongIds)
            
            withContext(Dispatchers.Main) {
                val message = when {
                    count == persistentSongIds.size -> "已成功添加到 ${playlist.name} 喵!"
                    count > 0 -> "成功添加 $count 首，跳过 ${persistentSongIds.size - count} 首重复歌曲喵！"
                    else -> "这些歌曲已经在歌单里了喵！"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                uiCallback.onReloadHomeView()
            }
        }
    }

    // --- 文件夹联动魔法喵 ---

    fun showLinkFolderDialog(folder: com.cpu.seamlessloopmobile.model.Folder) {
        MaterialAlertDialogBuilder(context)
            .setTitle("文件夹联动")
            .setMessage("cpu 大人，要把文件夹『${folder.name}』导入为同步歌单吗？\n\n(此模式下歌曲会自动同步，且莱芙会开启 100% 精准识别喵！)")
            .setPositiveButton("同步导入") { _, _ ->
                coroutineScope.launch {
                    val newPlaylist = com.cpu.seamlessloopmobile.model.Playlist(
                        name = folder.name,
                        folderPath = folder.path,
                        isFolderLinked = 1 // 开启魔法开关喵！
                    )
                    val newId = repository.insertPlaylist(newPlaylist)
                    val playlistWithId = newPlaylist.copy(id = newId.toInt())

                    // 莱芙在这里立即帮大人开启后台同步！不让它闲着喵！
                    withContext(Dispatchers.Main) {
                         // 给大人汇报一下进度，并触发扫描
                         uiCallback.onRefreshPlaylist(playlistWithId)
                         viewModel.refreshFolderPlaylist(context, playlistWithId)
                         Toast.makeText(context, "已成功创建同步歌单：${folder.name} 喵！正在后台为您同步数据...", Toast.LENGTH_SHORT).show()
                         uiCallback.onReloadHomeView()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
