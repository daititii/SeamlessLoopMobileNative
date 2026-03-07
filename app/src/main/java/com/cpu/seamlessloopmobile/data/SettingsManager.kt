package com.cpu.seamlessloopmobile.data

import android.content.Context
import android.content.SharedPreferences
import com.cpu.seamlessloopmobile.viewmodel.PlayMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 莱芙的私人小本本 📒
 * 负责记录 cpu 大人的所有偏好和上次的播放状态，哪怕 app 睡着了莱芙也能记得喵！
 */
class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREF_NAME = "seamless_loop_settings"
        private const val KEY_LAST_SONG_PATH = "last_song_path"
        private const val KEY_LAST_POSITION = "last_position"
        private const val KEY_PLAY_MODE = "play_mode"
        private const val KEY_IS_AB_MODE = "is_ab_mode"
        private const val KEY_CURRENT_SONG_INDEX = "current_song_index"
        
        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // --- 基础状态读写喵 ---

    var lastSongPath: String?
        get() = prefs.getString(KEY_LAST_SONG_PATH, null)
        set(value) = prefs.edit().putString(KEY_LAST_SONG_PATH, value).apply()

    var lastPosition: Long
        get() = prefs.getLong(KEY_LAST_POSITION, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_POSITION, value).apply()

    var playMode: PlayMode
        get() {
            val modeName = prefs.getString(KEY_PLAY_MODE, PlayMode.LIST_LOOP.name)
            return try { PlayMode.valueOf(modeName!!) } catch (e: Exception) { PlayMode.LIST_LOOP }
        }
        set(value) = prefs.edit().putString(KEY_PLAY_MODE, value.name).apply()

    var isAbMode: Boolean
        get() = prefs.getBoolean(KEY_IS_AB_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_AB_MODE, value).apply()

    var currentSongIndex: Int
        get() = prefs.getInt(KEY_CURRENT_SONG_INDEX, -1)
        set(value) = prefs.edit().putInt(KEY_CURRENT_SONG_INDEX, value).apply()

    /**
     * 一键记录所有关键状态喵！
     */
    fun saveFullState(
        songPath: String?,
        position: Long,
        mode: PlayMode,
        isAb: Boolean,
        playlist: List<String>,
        index: Int
    ) {
        prefs.edit().apply {
            putString(KEY_LAST_SONG_PATH, songPath)
            putLong(KEY_LAST_POSITION, position)
            putString(KEY_PLAY_MODE, mode.name)
            putBoolean(KEY_IS_AB_MODE, isAb)
            putInt(KEY_CURRENT_SONG_INDEX, index)
            apply()
        }
    }
}
