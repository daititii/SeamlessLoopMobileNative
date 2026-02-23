package com.cpu.seamlessloopmobile.ui

import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.cpu.seamlessloopmobile.adapter.LibraryAdapter
import com.cpu.seamlessloopmobile.adapter.SongAdapter
import com.cpu.seamlessloopmobile.model.Playlist
import com.cpu.seamlessloopmobile.model.PlaylistDao
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao
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
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val coroutineScope: CoroutineScope,
    private val uiCallback: SelectionUiCallback
) {

    interface SelectionUiCallback {
        fun onExitSelection()
        fun onExitPlaylistSelection()
        fun onReloadHomeView()
        fun onRefreshPlaylist(playlist: Playlist)
        val isInsidePlaylist: Boolean
        val currentOpenPlaylist: Playlist?
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
                        coroutineScope.launch(Dispatchers.IO) {
                            playlistDao.removeSongsFromPlaylist(playlist.id, selectedSongs.map { it.id })
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

    fun enterPlaylistSelectionMode(initialPlaylist: Playlist?) {
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
                        coroutineScope.launch(Dispatchers.IO) {
                            selected.forEach { playlistDao.deletePlaylist(it) }
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
            val playlists = withContext(Dispatchers.IO) { playlistDao.getAllPlaylists() }
            
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
                    coroutineScope.launch(Dispatchers.IO) {
                        val persistentSongIds = songs.map { song ->
                            songDao.insertOrUpdateSong(song)
                        }
                        
                        val newId = playlistDao.insertPlaylist(Playlist(name = name))
                        playlistDao.addSongsToPlaylist(newId.toInt(), persistentSongIds)
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "成功创建歌单: $name 喵!", Toast.LENGTH_SHORT).show()
                            exitSelectionMode()
                            uiCallback.onReloadHomeView()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun addSongsToExistingPlaylist(playlist: Playlist, songs: List<Song>) {
        coroutineScope.launch(Dispatchers.IO) {
            val persistentSongIds = songs.map { song ->
                songDao.insertOrUpdateSong(song)
            }
            
            playlistDao.addSongsToPlaylist(playlist.id, persistentSongIds)
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "已添加到 ${playlist.name} 喵!", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                uiCallback.onReloadHomeView()
            }
        }
    }
}
