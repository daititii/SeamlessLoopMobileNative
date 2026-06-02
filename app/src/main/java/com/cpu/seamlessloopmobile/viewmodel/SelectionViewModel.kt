package com.cpu.seamlessloopmobile.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.cpu.seamlessloopmobile.model.Folder
import com.cpu.seamlessloopmobile.model.Song

/**
 * 选妃大领班喵！
 * 专门负责管理界面上的多选状态，支持歌曲、歌单、文件夹的选择。
 */
class SelectionViewModel : ViewModel() {
    private val _isSelectionMode = MutableLiveData<Boolean>(false)
    val isSelectionMode: LiveData<Boolean> = _isSelectionMode

    private val _selectedItems = MutableLiveData<Set<String>>(emptySet())
    val selectedItems: LiveData<Set<String>> = _selectedItems

    private val _selectedPlaylists = MutableLiveData<Set<Int>>(emptySet())
    val selectedPlaylists: LiveData<Set<Int>> = _selectedPlaylists

    private val _selectedFolders = MutableLiveData<Set<Folder>>(emptySet())
    val selectedFolders: LiveData<Set<Folder>> = _selectedFolders

    fun setSelectionMode(enabled: Boolean) {
        _isSelectionMode.value = enabled
        if (!enabled) {
            _selectedItems.value = emptySet()
            _selectedPlaylists.value = emptySet()
            _selectedFolders.value = emptySet()
        }
    }

    fun toggleSelection(id: String) {
        val current = _selectedItems.value ?: emptySet()
        val next = if (current.contains(id)) current - id else current + id
        _selectedItems.value = next
        checkAutoExit()
    }

    fun togglePlaylistSelection(playlistId: Int) {
        val current = _selectedPlaylists.value ?: emptySet()
        val next = if (current.contains(playlistId)) current - playlistId else current + playlistId
        _selectedPlaylists.value = next
        if (next.isNotEmpty()) _isSelectionMode.value = true
        checkAutoExit()
    }

    fun toggleFolderSelection(folder: Folder) {
        val current = _selectedFolders.value ?: emptySet()
        val isAlreadySelected = current.any { it.path == folder.path }
        val next = if (isAlreadySelected) {
            current.filter { it.path != folder.path }.toSet()
        } else {
            current + folder
        }
        _selectedFolders.value = next
        if (next.isNotEmpty()) _isSelectionMode.value = true
        checkAutoExit()
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
        _selectedPlaylists.value = emptySet()
        _selectedFolders.value = emptySet()
        _isSelectionMode.value = false
    }

    fun selectAll(songs: List<Song>) {
        _selectedItems.value = songs.map { it.filePath }.toSet()
        _isSelectionMode.value = true
    }

    private fun checkAutoExit() {
        // 归零不自动退出多选模式，保留纯净的主动退出体验喵！(๑•̀ㅂ•́)و✧
    }
}
