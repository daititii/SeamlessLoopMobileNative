package com.cpu.seamlessloopmobile.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cpu.seamlessloopmobile.data.MusicRepository
import com.cpu.seamlessloopmobile.model.Playlist
import com.cpu.seamlessloopmobile.model.PlaylistDao
import com.cpu.seamlessloopmobile.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import com.cpu.seamlessloopmobile.data.SettingsManager

/**
 * 歌单管理员喵！
 * 专门负责歌单的创建、删除、导入以及歌曲关联逻辑。
 */
class PlaylistViewModel(
    private val repository: MusicRepository,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope,
    private val settingsManager: SettingsManager? = null
) {

    private val _playlistsWithCounts = MutableStateFlow<List<PlaylistDao.PlaylistWithCount>>(emptyList())
    val playlistsWithCounts: StateFlow<List<PlaylistDao.PlaylistWithCount>> = _playlistsWithCounts

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    init {
        // APlayer 秘籍：双重驱动，瞬间灌满喵！🚀
        coroutineScope.launch {
            repository.getPlaylistsWithCountsFlow().collect { list ->
                android.util.Log.d("PlaylistViewModel", "🚀 收到歌单水流，更新 ${list.size} 个歌单及其数量喵！")
                _playlistsWithCounts.value = list
                _playlists.value = list.map { it.playlist }
                
                // 歌单变动也立刻记在小本本上喵！🚀
                settingsManager?.let { manager ->
                    val currentStats = manager.lastLibraryStats ?: SettingsManager.LibraryStats()
                    val newPlaylistStats = list.associate { it.playlist.name to it.songCount }
                    manager.lastLibraryStats = currentStats.copy(playlistNamesWithCounts = newPlaylistStats)
                }
            }
        }
    }


    fun createPlaylist(name: String, songIds: List<Long>, onComplete: () -> Unit) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val id = repository.insertPlaylist(Playlist(name = name))
                repository.addSongsToPlaylist(id.toInt(), songIds)
            }
            // 响应式 Flow 会自动感应并刷新，不需要莱芙操心了喵！🚀
            onComplete()
        }
    }

    fun addSongsToPlaylist(playlistId: Int, songIds: List<Long>, onComplete: () -> Unit) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.addSongsToPlaylist(playlistId, songIds)
            }
            // 响应式刷新喵！
            onComplete()
        }
    }

    fun removeSongsFromPlaylist(playlistId: Int, songIds: List<Long>, onComplete: () -> Unit = {}) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.removeSongsFromPlaylist(playlistId, songIds)
            }
            onComplete()
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        coroutineScope.launch(Dispatchers.IO) {
            repository.deletePlaylist(playlist)
        }
    }

    fun deleteMultiplePlaylists(ids: Set<Int>, onComplete: () -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            ids.forEach { id ->
                val p = playlists.value.find { it.id == id }
                if (p != null) repository.deletePlaylist(p)
            }
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun importFoldersIndividually(folders: List<com.cpu.seamlessloopmobile.model.Folder>) {
        coroutineScope.launch(Dispatchers.IO) {
            folders.forEach { folder ->
                val id = repository.insertPlaylist(com.cpu.seamlessloopmobile.model.Playlist(name = folder.name, folderPath = folder.path, isFolderLinked = 1))
                repository.addSongsToPlaylist(id.toInt(), folder.songs.map { it.id })
            }
        }
    }

    fun importFoldersAsSinglePlaylist(name: String, folders: List<com.cpu.seamlessloopmobile.model.Folder>) {
        coroutineScope.launch(Dispatchers.IO) {
            val id = repository.insertPlaylist(com.cpu.seamlessloopmobile.model.Playlist(name = name))
            val songIds = folders.flatMap { folder -> folder.songs.map { it.id } }.distinct()
            repository.addSongsToPlaylist(id.toInt(), songIds)
        }
    }
}
