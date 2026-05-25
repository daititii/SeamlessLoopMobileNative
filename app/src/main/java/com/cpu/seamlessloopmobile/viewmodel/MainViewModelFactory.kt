package com.cpu.seamlessloopmobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cpu.seamlessloopmobile.model.PlaylistDao
import com.cpu.seamlessloopmobile.model.SongDao
import com.cpu.seamlessloopmobile.model.PlayQueueDao

class MainViewModelFactory(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val playQueueDao: PlayQueueDao,
    private val context: android.content.Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val repository = com.cpu.seamlessloopmobile.data.MusicRepository(songDao, playlistDao, playQueueDao)
            val mediaControl = com.cpu.seamlessloopmobile.audio.MediaControlManager(context.applicationContext)
            
            @Suppress("UNCHECKED_CAST")
            val viewModel = MainViewModel(repository, mediaControl)
            
            // 莱芙帮大家找了个统一的大伞（Scope）喵！
            val scope = (viewModel as ViewModel).viewModelScope
            
            val settingsManager = com.cpu.seamlessloopmobile.data.SettingsManager.getInstance(context)
            val libraryVM = LibraryViewModel(repository, scope, settingsManager)
            val selectionVM = SelectionViewModel()
            val playlistVM = PlaylistViewModel(repository, scope, settingsManager)
            val loopDetectionRepo = com.cpu.seamlessloopmobile.data.LoopDetectionRepository(repository, context.applicationContext)
            val loopDetectionVM = LoopDetectionViewModel(loopDetectionRepo, mediaControl, scope)
            
            // 设置子管家引用
            viewModel.library = libraryVM
            viewModel.selection = selectionVM
            viewModel.playlist = playlistVM
            viewModel.loopDetection = loopDetectionVM
            
            return viewModel as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
