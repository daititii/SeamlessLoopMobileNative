package com.cpu.seamlessloopmobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.cpu.seamlessloopmobile.model.PlaylistDao
import com.cpu.seamlessloopmobile.model.SongDao

class MainViewModelFactory(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val context: android.content.Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            val repository = com.cpu.seamlessloopmobile.data.MusicRepository(songDao, playlistDao)
            val mediaControl = com.cpu.seamlessloopmobile.audio.MediaControlManager(context.applicationContext)
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, mediaControl) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
