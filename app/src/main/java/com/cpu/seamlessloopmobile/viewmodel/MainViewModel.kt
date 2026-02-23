package com.cpu.seamlessloopmobile.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpu.seamlessloopmobile.model.Folder
import com.cpu.seamlessloopmobile.model.Playlist
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao
import com.cpu.seamlessloopmobile.model.PlaylistDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao
) : ViewModel() {

    // --- 乐库数据喵 ---
    private val _allSongs = MutableLiveData<List<Song>>()
    val allSongs: LiveData<List<Song>> = _allSongs

    private val _rawScannedSongs = MutableLiveData<List<Song>>(emptyList())
    val rawScannedSongs: LiveData<List<Song>> = _rawScannedSongs

    private val _folders = MutableLiveData<List<Folder>>(emptyList())
    val folders: LiveData<List<Folder>> = _folders

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

    fun setExploringLocal(value: Boolean) { _isExploringLocal.value = value }
    fun setShowingFolders(value: Boolean) { _isShowingFolders.value = value }
    fun setInsidePlaylist(value: Boolean) { _isInsidePlaylist.value = value }
    fun setCurrentOpenPlaylist(playlist: Playlist?) { _currentOpenPlaylist.value = playlist }
    fun setPlaying(value: Boolean) { _isPlaying.value = value }
    fun setAbModePlaying(value: Boolean) { _isAbModePlaying.value = value }
    fun setCurrentAbIntroSong(song: Song?) { _currentAbIntroSong.value = song }

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
            val list = playlistDao.getAllPlaylists()
            _playlists.postValue(list)
        }
    }

    fun loadSongsFromDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = songDao.getAllSongs()
            processSongsAndFolders(songs)
        }
    }

    private suspend fun processSongsAndFolders(songs: List<Song>) {
        withContext(Dispatchers.Default) {
            val folderMap = mutableMapOf<String, MutableList<Song>>()
            for (song in songs) {
                val parentPath = File(song.filePath).parent ?: "Unknown"
                folderMap.getOrPut(parentPath) { mutableListOf() }.add(song)
            }

            val folderList = folderMap.map { (path, folderSongs) ->
                val name = try { File(path).name } catch (e: Exception) { path }
                Folder(name, path, folderSongs.size, folderSongs)
            }.sortedBy { it.name }

            _allSongs.postValue(songs)
            _folders.postValue(folderList)
        }
    }

    fun updateSongLoopPoints(song: Song, start: Long, end: Long) {
        val newSong = song.copy(loopStart = start, loopEnd = end)
        
        viewModelScope.launch(Dispatchers.IO) {
            songDao.insertOrUpdateSong(newSong)
            updateSongInMemory(newSong)
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

    fun scanLibrary(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. 获取系统媒体库的原始列表喵
            val scannedSongs = com.cpu.seamlessloopmobile.scanner.AudioScanner.scan(context)
            _rawScannedSongs.postValue(scannedSongs)
            
            val scannedMap = scannedSongs.associateBy { it.filePath }
            val pathsToIgnore = mutableSetOf<String>()
            val bSuffixes = arrayOf("_B", "_b", "_loop", "_Loop")
            val aSuffixes = arrayOf("_A", "_a", "_intro", "_Intro")
            
            for (song in scannedSongs) {
                val fileNameNormal = song.fileName.substringBeforeLast(".")
                val fileObj = java.io.File(song.filePath)
                val parent = fileObj.parent
                
                for (i in bSuffixes.indices) {
                    if (fileNameNormal.endsWith(bSuffixes[i])) {
                        val baseName = fileNameNormal.substring(0, fileNameNormal.length - bSuffixes[i].length)
                        val targetAName = baseName + aSuffixes[i]
                        val hasA = scannedSongs.any { 
                            it.fileName.substringBeforeLast(".") == targetAName && 
                            java.io.File(it.filePath).parent == parent
                        }
                        if (hasA) {
                            pathsToIgnore.add(song.filePath)
                            break
                        }
                    }
                }
            }

            // 过滤掉作为 B 段存在的歌曲喵
            val filteredSongs = scannedSongs.filter { it.filePath !in pathsToIgnore }
            
            // 2. 与数据库对碰喵
            val dbSongs = songDao.getAllSongs().associateBy { it.fileName } 
            
            val updatedSongs = filteredSongs.map { song ->
                val dbSong = dbSongs[song.fileName]
                val fileObj = java.io.File(song.filePath)
                val totalDuration = if (fileObj.exists()) fileObj.length() else 0L

                if (dbSong != null) {
                    song.copy(
                        id = dbSong.id,
                        loopStart = dbSong.loopStart,
                        loopEnd = dbSong.loopEnd,
                        totalSamples = dbSong.totalSamples,
                        displayName = dbSong.displayName ?: song.displayName,
                        duration = totalDuration
                    )
                } else {
                    song.copy(duration = totalDuration)
                }
            }

            _allSongs.postValue(updatedSongs)
            
            // 构建文件夹列表喵
            val folderMap = mutableMapOf<String, MutableList<Song>>()
            for (song in updatedSongs) {
                val parentPath = java.io.File(song.filePath).parent ?: "Unknown"
                folderMap.getOrPut(parentPath) { mutableListOf() }.add(song)
            }
            
            val folderList = folderMap.map { (path, songs) ->
                val name = try { java.io.File(path).name } catch (e: Exception) { path }
                com.cpu.seamlessloopmobile.model.Folder(name, path, songs.size, songs)
            }.sortedBy { it.name }
            
            _folders.postValue(folderList)
        }
    }

    fun findAbPair(song: Song): Pair<Song, Song>? {
        val songList = _rawScannedSongs.value ?: return null
        val fileName = song.fileName.substringBeforeLast(".")
        val aSuffixes = arrayOf("_A", "_a", "_intro", "_Intro")
        val bSuffixes = arrayOf("_B", "_b", "_loop", "_Loop")

        for (i in aSuffixes.indices) {
            if (fileName.endsWith(aSuffixes[i])) {
                val baseName = fileName.substring(0, fileName.length - aSuffixes[i].length)
                val targetBName = baseName + bSuffixes[i]
                
                val partB = songList.find { 
                    it.fileName.substringBeforeLast(".") == targetBName &&
                    java.io.File(it.filePath).parent == java.io.File(song.filePath).parent
                }
                if (partB != null) return Pair(song, partB)
            }
        }
        return null
    }
}
