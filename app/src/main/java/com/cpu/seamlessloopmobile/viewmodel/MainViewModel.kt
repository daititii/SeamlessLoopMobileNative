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
import kotlinx.coroutines.flow.collect

enum class PlayMode {
    LIST_LOOP,    // 列表循环
    SINGLE_LOOP,  // 单曲循环
    SHUFFLE       // 随机播放
}

/**
 * 界面导航的大地图喵！
 */
sealed class MusicUiState {
    object Home : MusicUiState()
    
    // 一级分类展开
    data class CategoryFolders(val title: String, val items: List<Folder>) : MusicUiState()
    
    // 二级歌曲列表
    data class SongList(
        val title: String, 
        val songs: List<Song>, 
        val type: ListType,
        val originalItems: List<Folder>? = null 
    ) : MusicUiState()
    
    enum class ListType { PLAYLIST, FOLDER, ALL_SONGS, ALBUM, ARTIST }
}

/**
 * 主界面调度员喵！
 * 莱芙现在变聪明了，把具体的脏活累活都分给了 Library, Selection 和 Playlist 这三位管家，
 * 现在的 MainViewModel 只负责整体架构的指挥和 UI 状态的同步。
 */
class MainViewModel(
    private val repository: com.cpu.seamlessloopmobile.data.MusicRepository,
    private val mediaControlManager: com.cpu.seamlessloopmobile.audio.MediaControlManager
) : ViewModel() {
    lateinit var library: LibraryViewModel
    lateinit var selection: SelectionViewModel
    lateinit var playlist: PlaylistViewModel
    private var settingsManager: com.cpu.seamlessloopmobile.data.SettingsManager? = null

    init {
        // 莱芙现在变聪明了，不在构造函数里乱连天线了喵！
    }

    fun startObservation() {
        viewModelScope.launch {
            mediaControlManager.metadata.collect { meta ->
                val mediaId = meta?.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
                val mediaUri = meta?.getString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_URI)
                
                val list = _currentPlaylist.value ?: emptyList()
                var index = -1
                
                if (mediaId != null) {
                    index = list.indexOfFirst { it.mediaId.toString() == mediaId }
                }
                
                if (index == -1 && mediaUri != null) {
                    index = list.indexOfFirst { it.filePath == mediaUri }
                }

                if (index != -1 && index != _currentSongIndex.value) {
                    _currentSongIndex.postValue(index)
                }
            }
        }
    }

    // --- 播放引擎状态流代理喵 ---
    val playbackState = mediaControlManager.playbackState
    val metadata = mediaControlManager.metadata
    val currentPosition = mediaControlManager.currentPosition
    val totalDuration = mediaControlManager.totalDuration
    val isConnected = mediaControlManager.isConnected
    val audioPlayState = mediaControlManager.audioPlayState

    fun initSettings(manager: com.cpu.seamlessloopmobile.data.SettingsManager) {
        this.settingsManager = manager
    }

    // --- 数据代理层 (对接 UI 和子模块) ---
    val allSongs: LiveData<List<Song>> get() = library.allSongs
    val folders: LiveData<List<Folder>> get() = library.folders
    val albums: LiveData<List<Folder>> get() = library.albums
    val artists: LiveData<List<Folder>> get() = library.artists
    val syncStatus: LiveData<String> get() = library.syncStatus

    val playlists: LiveData<List<Playlist>> get() = playlist.playlists
    val playlistsWithCounts get() = playlist.playlistsWithCounts

    val isSelectionMode: LiveData<Boolean> get() = selection.isSelectionMode
    val selectedItems: LiveData<Set<String>> get() = selection.selectedItems
    val selectedPlaylists: LiveData<Set<Int>> get() = selection.selectedPlaylists
    val selectedFolders: LiveData<Set<Folder>> get() = selection.selectedFolders

    private val _playMode = MutableLiveData<PlayMode>(PlayMode.LIST_LOOP)
    val playMode: LiveData<PlayMode> = _playMode

    private val _currentPlaylist = MutableLiveData<List<Song>>(emptyList())
    val currentPlaylist: LiveData<List<Song>> = _currentPlaylist

    private val _currentSongIndex = MutableLiveData<Int>(-1)
    val currentSongIndex: LiveData<Int> = _currentSongIndex

    // --- 页面状态喵 ---
    private val _currentOpenPlaylist = MutableLiveData<Playlist?>(null)
    val currentOpenPlaylist: LiveData<Playlist?> = _currentOpenPlaylist

    private val _uiState = MutableLiveData<MusicUiState>(MusicUiState.Home)
    val uiState: LiveData<MusicUiState> = _uiState

    private val navigationStack = mutableListOf<MusicUiState>(MusicUiState.Home)

    // --- 播放器面板显示控制 ---
    private val _isPlayingPanelVisible = MutableLiveData(false)
    val isPlayingPanelVisible: LiveData<Boolean> = _isPlayingPanelVisible

    // --- 核心业务接口转发喵 ---

    fun loadSongsFromDatabase() = library.loadSongsFromDatabase()
    fun scanLibrary(context: android.content.Context) = library.scanLibrary(context)
    fun loadPlaylists() = playlist.loadPlaylists()
    fun refreshPlaylists() = playlist.loadPlaylists()

    fun setSelectionMode(enabled: Boolean) = selection.setSelectionMode(enabled)
    fun toggleSelection(id: String) = selection.toggleSelection(id)
    fun togglePlaylistSelection(id: Int) = selection.togglePlaylistSelection(id)
    fun toggleFolderSelection(f: Folder) = selection.toggleFolderSelection(f)
    fun clearSelection() = selection.clearSelection()
    fun selectAll(songs: List<Song>) = selection.selectAll(songs)

    fun navigateTo(state: MusicUiState) {
        if (navigationStack.lastOrNull() == state) return
        navigationStack.add(state)
        _uiState.value = state
    }

    fun goBack(): Boolean {
        if (navigationStack.size > 1) {
            navigationStack.removeAt(navigationStack.size - 1)
            _uiState.value = navigationStack.last()
            return true
        }
        return false
    }

    fun resetToHome() {
        navigationStack.clear()
        navigationStack.add(MusicUiState.Home)
        _uiState.value = MusicUiState.Home
    }

    fun openHome() = resetToHome()

    // --- 组合业务逻辑喵 (MainViewModel 负责跨模块协调) ---

    fun playSong(song: Song, startPosition: Long = 0, startPaused: Boolean = false) {
        // 智能找索引：如果歌曲已经在现在的播放队列里，就别乱换队列喵！
        val currentList = _currentPlaylist.value ?: emptyList()
        var index = currentList.indexOfFirst { it.filePath == song.filePath }
        var listToUse = currentList

        if (index == -1) {
            // 不在现有的列表里？那才去翻翻全家桶喵
            val allList = library.allSongs.value ?: emptyList()
            index = allList.indexOfFirst { it.filePath == song.filePath }
            if (index != -1) {
                listToUse = allList
                _currentPlaylist.value = allList
            }
        }

        if (index != -1) {
            _currentSongIndex.value = index
        }

        val bundle = android.os.Bundle().apply {
            putLong("start_pos", startPosition)
            putBoolean("start_paused", startPaused)
            putStringArray("playlist_paths", listToUse.map { it.filePath }.toTypedArray())
        }
        mediaControlManager.playFromMediaId(song.mediaId.toString(), bundle)
    }

    fun updateCurrentPlaylist(songs: List<Song>, index: Int = -1) {
        _currentPlaylist.value = songs
        if (index != -1) _currentSongIndex.value = index
    }

    fun togglePlayPause() {
        if (audioPlayState.value == com.cpu.seamlessloopmobile.audio.AudioPlayState.PLAYING) {
            mediaControlManager.pause()
        } else {
            mediaControlManager.play()
        }
    }

    fun play() = mediaControlManager.play()
    fun pause() = mediaControlManager.pause()

    fun skipToNext() = mediaControlManager.skipToNext()
    fun skipToPrevious() = mediaControlManager.skipToPrevious()
    fun seekTo(pos: Long) = mediaControlManager.seekTo(pos)

    fun setPlayMode(mode: PlayMode) {
        _playMode.value = mode
        settingsManager?.playMode = mode
        val bundle = android.os.Bundle().apply {
            putInt("play_mode", mode.ordinal)
        }
        mediaControlManager.sendCustomAction("SET_PLAY_MODE", bundle)
    }

    fun togglePlayMode() {
        val next = when (_playMode.value) {
            PlayMode.LIST_LOOP -> PlayMode.SINGLE_LOOP
            PlayMode.SINGLE_LOOP -> PlayMode.SHUFFLE
            PlayMode.SHUFFLE -> PlayMode.LIST_LOOP
            null -> PlayMode.LIST_LOOP
        }
        setPlayMode(next)
    }

    fun addSelectedToPlaylist(playlistId: Int) {
        val selectedPaths = selectedItems.value ?: return
        viewModelScope.launch {
            val songs = allSongs.value ?: return@launch
            val selectedSongIds = songs.filter { it.filePath in selectedPaths }.map { it.id }
            playlist.addSongsToPlaylist(playlistId, selectedSongIds) {
                selection.clearSelection()
            }
        }
    }

    fun createPlaylistWithSelected(name: String) {
        val selectedPaths = selectedItems.value ?: return
        viewModelScope.launch {
            val songs = allSongs.value ?: return@launch
            val selectedSongIds = songs.filter { it.filePath in selectedPaths }.map { it.id }
            playlist.createPlaylist(name, selectedSongIds) {
                selection.clearSelection()
            }
        }
    }

    fun deletePlaylist(p: Playlist) {
        playlist.deletePlaylist(p)
        val currentState = _uiState.value
        if (currentState is MusicUiState.SongList && currentState.type == MusicUiState.ListType.PLAYLIST && currentState.title == p.name) {
            goBack()
        }
    }

    fun deleteSelectedPlaylists() {
        val ids = selectedPlaylists.value ?: return
        playlist.deleteMultiplePlaylists(ids) {
            selection.clearSelection()
        }
    }

    fun importSelectedFoldersIndividually() {
        val folders = selectedFolders.value ?: return
        viewModelScope.launch {
            playlist.importFoldersIndividually(folders.toList())
            selection.clearSelection()
        }
    }

    fun importSelectedFoldersAsSinglePlaylist(name: String) {
        val folders = selectedFolders.value ?: return
        viewModelScope.launch {
            playlist.importFoldersAsSinglePlaylist(name, folders.toList())
            selection.clearSelection()
        }
    }

    fun openPlaylist(playlistObj: Playlist) {
        viewModelScope.launch {
            _currentOpenPlaylist.value = playlistObj
            val songs = withContext(Dispatchers.IO) { repository.getSongsInPlaylist(playlistObj.id) }
            navigateTo(MusicUiState.SongList(playlistObj.name, songs, MusicUiState.ListType.PLAYLIST))
        }
    }

    fun openCategory(title: String, items: List<Folder>) = navigateTo(MusicUiState.CategoryFolders(title, items))
    fun openSongList(title: String, songs: List<Song>, type: MusicUiState.ListType, original: List<Folder>? = null) 
        = navigateTo(MusicUiState.SongList(title, songs, type, original))

    fun setPlayingPanelVisible(visible: Boolean) { _isPlayingPanelVisible.value = visible }
    fun connectMedia() = mediaControlManager.connect()
    fun disconnectMedia() = mediaControlManager.disconnect()

    fun updateSongLoopPoints(song: Song, start: Long, end: Long) {
        library.updateSongLoopPoints(song, start, end)
        
        // 确保本地 UI 状态 (currentPlaylist) 里的曲目数据也立刻更新，否则在详情页的循环调节数字不会动喵！
        val currentList = _currentPlaylist.value?.toMutableList() ?: return
        val index = currentList.indexOfFirst { it.id == song.id }
        if (index != -1) {
            currentList[index] = currentList[index].copy(loopStart = start, loopEnd = end)
            _currentPlaylist.postValue(currentList)
        }
    }
    
    fun findAbPair(song: Song): Pair<Song, Song>? = repository.findAbPair(song, library.allSongs.value ?: emptyList())
}
