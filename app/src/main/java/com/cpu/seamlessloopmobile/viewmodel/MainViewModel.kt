package com.cpu.seamlessloopmobile.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpu.seamlessloopmobile.model.Folder
import com.cpu.seamlessloopmobile.model.Playlist
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.data.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

enum class PlayMode {
    LIST_LOOP,  // 列表循环
    SINGLE_LOOP,// 单曲循环
    SHUFFLE     // 随机播放
}

class MainViewModel(
    private val repository: MusicRepository
) : ViewModel() {

    // --- 乐库数据喵 ---
    private val _allSongs = MutableLiveData<List<Song>>()
    val allSongs: LiveData<List<Song>> = _allSongs

    private val _rawScannedSongs = MutableLiveData<List<Song>>(emptyList())
    val rawScannedSongs: LiveData<List<Song>> = _rawScannedSongs

    private val _folders = MutableLiveData<List<Folder>>(emptyList())
    val folders: LiveData<List<Folder>> = _folders

    private val _albums = MutableLiveData<List<Folder>>(emptyList())
    val albums: LiveData<List<Folder>> = _albums

    private val _artists = MutableLiveData<List<Folder>>(emptyList())
    val artists: LiveData<List<Folder>> = _artists

    private val _playlists = MutableLiveData<List<Playlist>>(emptyList())
    val playlists: LiveData<List<Playlist>> = _playlists

    // --- UI 状态喵 ---
    private val _isExploringLocal = MutableLiveData(false)
    val isExploringLocal: LiveData<Boolean> = _isExploringLocal

    private val _isShowingFolders = MutableLiveData(false)
    val isShowingFolders: LiveData<Boolean> = _isShowingFolders

    private val _isInsidePlaylist = MutableLiveData(false)
    val isInsidePlaylist: LiveData<Boolean> = _isInsidePlaylist

    private val _currentOpenPlaylist = MutableLiveData<Playlist?>(null)
    val currentOpenPlaylist: LiveData<Playlist?> = _currentOpenPlaylist

    // --- 播放控制状态喵 ---
    private val _currentPlaylist = MutableLiveData<List<Song>>(emptyList())
    val currentPlaylist: LiveData<List<Song>> = _currentPlaylist

    private val _currentSongIndex = MutableLiveData(-1)
    val currentSongIndex: LiveData<Int> = _currentSongIndex

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _isAbModePlaying = MutableLiveData(false)
    val isAbModePlaying: LiveData<Boolean> = _isAbModePlaying

    private val _currentAbIntroSong = MutableLiveData<Song?>(null)
    val currentAbIntroSong: LiveData<Song?> = _currentAbIntroSong

    private val _playMode = MutableLiveData(PlayMode.SINGLE_LOOP)
    val playMode: LiveData<PlayMode> = _playMode

    private val _isPlayingPanelVisible = MutableLiveData(false)
    val isPlayingPanelVisible: LiveData<Boolean> = _isPlayingPanelVisible

    fun setPlayingPanelVisible(value: Boolean) { _isPlayingPanelVisible.value = value }

    fun setExploringLocal(value: Boolean) { _isExploringLocal.value = value }
    fun setShowingFolders(value: Boolean) { _isShowingFolders.value = value }
    fun setInsidePlaylist(value: Boolean) { _isInsidePlaylist.value = value }
    fun setCurrentOpenPlaylist(playlist: Playlist?) { _currentOpenPlaylist.value = playlist }
    fun setPlaying(value: Boolean) { _isPlaying.value = value }
    fun setAbModePlaying(value: Boolean) { _isAbModePlaying.value = value }
    fun setCurrentAbIntroSong(song: Song?) { _currentAbIntroSong.value = song }
    fun setPlayMode(mode: PlayMode) { _playMode.value = mode }

    fun togglePlayMode() {
        val next = when (_playMode.value) {
            PlayMode.LIST_LOOP -> PlayMode.SINGLE_LOOP
            PlayMode.SINGLE_LOOP -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.LIST_LOOP
            null -> PlayMode.LIST_LOOP
        }
        _playMode.value = next
    }



    fun updateCurrentPlaylist(songs: List<Song>, index: Int = -1) {
        _currentPlaylist.value = songs
        if (index != -1) _currentSongIndex.value = index
    }

    fun updateSongIndex(index: Int) {
        _currentSongIndex.value = index
    }

    // --- 逻辑操作喵 ---

    fun refreshPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.getAllPlaylists()
            _playlists.postValue(list)
        }
    }

    fun loadSongsFromDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = repository.getAllSongs()
            processSongsAndFolders(songs)
        }
    }

    private suspend fun processSongsAndFolders(songs: List<Song>) {
        withContext(Dispatchers.Default) {
            val sortedSongs = songs.sortedBy { it.displayName ?: it.fileName } // 先排好队喵！
            val folderMap = mutableMapOf<String, MutableList<Song>>()
            for (song in sortedSongs) {
                val parentPath = File(song.filePath).parent ?: "Unknown"
                folderMap.getOrPut(parentPath) { mutableListOf() }.add(song)
            }

            val folderList = folderMap.map { (path, folderSongs) ->
                val name = try { File(path).name } catch (e: Exception) { path }
                Folder(name, path, folderSongs.size, folderSongs)
            }.sortedBy { it.name }
            
            val albumList = sortedSongs.groupBy { it.album ?: "Unknown Album" }
                .map { (name, albumSongs) ->
                    Folder(name, "album_$name", albumSongs.size, albumSongs)
                }.sortedBy { it.name }

            val artistList = sortedSongs.groupBy { it.artist ?: "Unknown Artist" }
                .map { (name, artistSongs) ->
                    Folder(name, "artist_$name", artistSongs.size, artistSongs)
                }.sortedBy { it.name }

            _allSongs.postValue(songs)
            _folders.postValue(folderList)
            _albums.postValue(albumList)
            _artists.postValue(artistList)
        }
    }

    fun updateSongLoopPoints(song: Song, start: Long, end: Long) {
        viewModelScope.launch {
            val updatedSong = repository.updateSongLoopPoints(song, start, end)
            updateSongInMemory(updatedSong)
        }
    }

    fun updateSongInMemory(song: Song) {
        // 同步内存中的列表喵
        val updatedAllSongs = _allSongs.value?.map {
            if (it.filePath == song.filePath) song else it
        } ?: emptyList()
        _allSongs.postValue(updatedAllSongs)

        val updatedFolders = _folders.value?.map { folder ->
            val updatedSongs = folder.songs.map { if (it.filePath == song.filePath) song else it }
            folder.copy(songs = updatedSongs)
        } ?: emptyList()
        _folders.postValue(updatedFolders)

        val updatedCurrentPlaylist = _currentPlaylist.value?.map {
            if (it.filePath == song.filePath) song else it
        } ?: emptyList()
        _currentPlaylist.postValue(updatedCurrentPlaylist)
    }

    /**
     * cpu 大人，这是借鉴电脑端的“定向精准扫描”魔法喵！
     * 只对关联了文件夹的歌单进行扫描，保证 100% 精准的采样数匹配。
     */
    /**
     * cpu 大人，这是借鉴电脑端的“定向精准扫描”魔法喵！
     * 针对现代安卓权限限制，我们改用 ContentResolver 查询，保证 100% 成功喵！
     */
    private val _syncStatus = MutableLiveData<String>("")
    val syncStatus: LiveData<String> = _syncStatus

    // 莱芙的小本本，记得哪些歌单正在干活喵！
    private val syncingPlaylists = Collections.synchronizedSet(mutableSetOf<Int>())

    /**
     * cpu 大人，莱芙现在变聪明了，同一个歌单绝对不干两次活喵！
     */
    fun refreshFolderPlaylist(context: android.content.Context, playlist: Playlist) {
        val folderPath = playlist.folderPath ?: return
        if (playlist.isFolderLinked == 0) return
        
        if (!syncingPlaylists.add(playlist.id)) return

        viewModelScope.launch {
            try {
                _syncStatus.value = "正在搜索文件..."
                
                repository.syncFolderPlaylist(context, playlist) { progress ->
                    _syncStatus.postValue(progress)
                }

                _syncStatus.postValue("同步完成喵！")
                loadPlaylists() 
                
                val updatedSongs = repository.getSongsInPlaylist(playlist.id)
                _currentPlaylist.postValue(updatedSongs)
                
                kotlinx.coroutines.delay(3000)
                _syncStatus.postValue("")
            } catch (e: Exception) {
                e.printStackTrace()
                _syncStatus.postValue("出错了喵：${e.message}")
            } finally {
                syncingPlaylists.remove(playlist.id)
            }
        }
    }


    /**
     * 刷新所有歌单列表喵
     */
    fun loadPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = repository.getAllPlaylists()
            _playlists.postValue(list)
        }
    }

    /**
     * 为了兼容 MainActivity，莱芙把快如闪电的基础扫描带回来啦！
     * 它只管提取 MediaStore 基础信息，不做繁重的 JNI 探测，保证列表秒开喵！
     */
    fun scanLibrary(context: android.content.Context) {
        viewModelScope.launch {
            val updatedSongs = repository.getInitialScannedSongs(context)
            _allSongs.postValue(updatedSongs)
            _rawScannedSongs.postValue(updatedSongs) // 基础扫描结果喵
            
            val folderList = withContext(Dispatchers.Default) {
                updatedSongs.groupBy { java.io.File(it.filePath).parent ?: "Unknown" }
                    .map { (path, songs) ->
                        val name = try { java.io.File(path).name } catch (e: Exception) { path }
                        com.cpu.seamlessloopmobile.model.Folder(name, path, songs.size, songs)
                    }.sortedBy { it.name }
            }
            _folders.postValue(folderList)
            
            val albumList = withContext(Dispatchers.Default) {
                updatedSongs.groupBy { it.album ?: "Unknown Album" }
                    .map { (name, songs) ->
                        com.cpu.seamlessloopmobile.model.Folder(name, "album_$name", songs.size, songs)
                    }.sortedBy { it.name }
            }
            _albums.postValue(albumList)

            val artistList = withContext(Dispatchers.Default) {
                updatedSongs.groupBy { it.artist ?: "Unknown Artist" }
                    .map { (name, songs) ->
                        com.cpu.seamlessloopmobile.model.Folder(name, "artist_$name", songs.size, songs)
                    }.sortedBy { it.name }
            }
            _artists.postValue(artistList)
        }
    }

    fun findAbPair(song: Song): Pair<Song, Song>? {
        val songList = _rawScannedSongs.value ?: return null
        return repository.findAbPair(song, songList)
    }

    // --- 播放顺序控制喵 ---

    fun getNextIndex(): Int {
        val songs = _currentPlaylist.value ?: return -1
        if (songs.isEmpty()) return -1
        val currentIndex = _currentSongIndex.value ?: -1
        val mode = _playMode.value ?: PlayMode.LIST_LOOP

        return when (mode) {
            PlayMode.LIST_LOOP, PlayMode.SINGLE_LOOP -> {
                // 无论是列表循环还是单曲循环，手动点“下一首”都应该去下一位喵！
                (currentIndex + 1) % songs.size
            }
            PlayMode.SHUFFLE -> {
                if (songs.size <= 1) return 0
                var next = (0 until songs.size).random()
                while (next == currentIndex && songs.size > 1) {
                    next = (0 until songs.size).random()
                }
                next
            }
        }
    }

    fun getPrevIndex(): Int {
        val songs = _currentPlaylist.value ?: return -1
        if (songs.isEmpty()) return -1
        val currentIndex = _currentSongIndex.value ?: -1
        val mode = _playMode.value ?: PlayMode.LIST_LOOP

        return when (mode) {
            PlayMode.LIST_LOOP, PlayMode.SINGLE_LOOP -> {
                // 同理，单曲循环时点“上一首”也要能跳走喵
                if (currentIndex <= 0) songs.size - 1 else currentIndex - 1
            }
            PlayMode.SHUFFLE -> {
                getNextIndex() // 随机模式下，上一首也是随机喵！
            }
        }
    }
}
