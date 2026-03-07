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

/**
 * 歌单管理员喵！
 * 专门负责歌单的创建、删除、导入以及歌曲关联逻辑。
 */
class PlaylistViewModel(
    private val repository: MusicRepository,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope
) {

    private val _playlists = MutableLiveData<List<Playlist>>(emptyList())
    val playlists: LiveData<List<Playlist>> = _playlists

    private val _playlistsWithCounts = MutableLiveData<List<PlaylistDao.PlaylistWithCount>>(emptyList())
    val playlistsWithCounts: LiveData<List<PlaylistDao.PlaylistWithCount>> = _playlistsWithCounts

    fun loadPlaylists() {
        coroutineScope.launch(Dispatchers.IO) {
            val list = repository.getPlaylistsWithCounts()
            val rawList = repository.getAllPlaylists()
            withContext(Dispatchers.Main) {
                _playlistsWithCounts.value = list
                _playlists.value = rawList
            }
        }
    }

    fun createPlaylist(name: String, songIds: List<Long>, onComplete: () -> Unit) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                val id = repository.insertPlaylist(Playlist(name = name))
                repository.addSongsToPlaylist(id.toInt(), songIds)
            }
            loadPlaylists()
            onComplete()
        }
    }

    fun addSongsToPlaylist(playlistId: Int, songIds: List<Long>, onComplete: () -> Unit) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                repository.addSongsToPlaylist(playlistId, songIds)
            }
            loadPlaylists()
            onComplete()
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        coroutineScope.launch(Dispatchers.IO) {
            repository.deletePlaylist(playlist)
            loadPlaylists()
        }
    }

    fun deleteMultiplePlaylists(ids: Set<Int>, onComplete: () -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            ids.forEach { id ->
                val p = _playlists.value?.find { it.id == id }
                if (p != null) repository.deletePlaylist(p)
            }
            loadPlaylists()
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun importFoldersIndividually(folders: List<com.cpu.seamlessloopmobile.model.Folder>) {
        coroutineScope.launch(Dispatchers.IO) {
            folders.forEach { folder ->
                val id = repository.insertPlaylist(com.cpu.seamlessloopmobile.model.Playlist(name = folder.name, folderPath = folder.path, isFolderLinked = 1))
                repository.addSongsToPlaylist(id.toInt(), folder.songs.map { it.id })
            }
            loadPlaylists()
        }
    }

    fun importFoldersAsSinglePlaylist(name: String, folders: List<com.cpu.seamlessloopmobile.model.Folder>) {
        coroutineScope.launch(Dispatchers.IO) {
            val id = repository.insertPlaylist(com.cpu.seamlessloopmobile.model.Playlist(name = name))
            val songIds = folders.flatMap { folder -> folder.songs.map { it.id } }.distinct()
            repository.addSongsToPlaylist(id.toInt(), songIds)
            loadPlaylists()
        }
    }
}
