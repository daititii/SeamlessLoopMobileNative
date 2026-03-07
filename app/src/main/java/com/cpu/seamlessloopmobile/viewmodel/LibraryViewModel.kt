package com.cpu.seamlessloopmobile.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cpu.seamlessloopmobile.data.MusicRepository
import com.cpu.seamlessloopmobile.model.Folder
import com.cpu.seamlessloopmobile.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 乐库管理员喵！
 * 专门负责歌曲扫描、文件夹整理和数据库同步，把 MainViewModel 从繁重的 IO 工作中解放出来。
 */
class LibraryViewModel(
    private val repository: com.cpu.seamlessloopmobile.data.MusicRepository,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope
) {

    private val _allSongs = MutableLiveData<List<Song>>(emptyList())
    val allSongs: LiveData<List<Song>> = _allSongs

    private val _folders = MutableLiveData<List<Folder>>(emptyList())
    val folders: LiveData<List<Folder>> = _folders

    private val _albums = MutableLiveData<List<Folder>>(emptyList())
    val albums: LiveData<List<Folder>> = _albums

    private val _artists = MutableLiveData<List<Folder>>(emptyList())
    val artists: LiveData<List<Folder>> = _artists

    private val _syncStatus = MutableLiveData<String>("")
    val syncStatus: LiveData<String> = _syncStatus

    fun loadSongsFromDatabase() {
        coroutineScope.launch(Dispatchers.IO) {
            val songs = repository.getAllSongs()
            withContext(Dispatchers.Main) {
                _allSongs.value = songs
                rebuildLibrary(songs)
            }
        }
    }

    /**
     * 核心扫描逻辑喵！
     */
    fun scanLibrary(context: Context) {
        coroutineScope.launch {
            _syncStatus.value = "🔍 寻找新曲子中..."
            withContext(Dispatchers.IO) {
                val songs = repository.getInitialScannedSongs(context)
                withContext(Dispatchers.Main) {
                    _allSongs.value = songs
                    rebuildLibrary(songs)
                    _syncStatus.value = ""
                }
            }
        }
    }

    private fun rebuildLibrary(songs: List<Song>) {
        coroutineScope.launch(Dispatchers.Default) {
            val folderMap = mutableMapOf<String, MutableList<Song>>()
            val albumMap = mutableMapOf<String, MutableList<Song>>()
            val artistMap = mutableMapOf<String, MutableList<Song>>()

            songs.forEach { song ->
                // 文件夹分类喵
                val parent = java.io.File(song.filePath).parent ?: "未知目录"
                folderMap.getOrPut(parent) { mutableListOf() }.add(song)

                // 专辑分类喵
                val album = song.album ?: "未知专辑"
                albumMap.getOrPut(album) { mutableListOf() }.add(song)

                // 歌手分类喵
                val artist = song.artist ?: "未知歌手"
                artistMap.getOrPut(artist) { mutableListOf() }.add(song)
            }

            val folders = folderMap.map { Folder(java.io.File(it.key).name, it.key, it.value.size, it.value) }.sortedBy { it.name }
            val albums = albumMap.map { Folder(it.key, "album_${it.key}", it.value.size, it.value) }.sortedBy { it.name }
            val artists = artistMap.map { Folder(it.key, "artist_${it.key}", it.value.size, it.value) }.sortedBy { it.name }

            withContext(Dispatchers.Main) {
                _folders.value = folders
                _albums.value = albums
                _artists.value = artists
            }
        }
    }

    fun updateSongLoopPoints(song: Song, start: Long, end: Long) {
        coroutineScope.launch {
            repository.updateSongLoopPoints(song, start, end)
            loadSongsFromDatabase() // 暴力但有效的刷新喵
        }
    }
}
