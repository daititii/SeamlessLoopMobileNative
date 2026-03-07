package com.cpu.seamlessloopmobile.audio

import com.cpu.seamlessloopmobile.model.Song

/**
 * 播放队列大管家喵！
 * 专门负责管理现在播放的是哪一个列表、下一首放什么、是不是随机播放。
 * 彻底把 PlaybackManager 从“选歌”这种琐事中解放出来！
 */
class QueueManager {
    // 0: LIST_LOOP, 1: SINGLE_LOOP, 2: SHUFFLE
    var playMode: Int = 0 
    
    private var currentPlaylist: List<Song> = emptyList()
    val currentPlaylistSnapshot: List<Song>
        get() = currentPlaylist.toList()
        
    var currentIndex: Int = -1
        private set

    /**
     * 当前正在伺候的曲子喵
     */
    val currentSong: Song?
        get() = currentPlaylist.getOrNull(currentIndex)

    /**
     * 更新播放歌单并指定要放的歌喵
     */
    fun updatePlaylist(songs: List<Song>, initialIndex: Int) {
        currentPlaylist = songs
        currentIndex = initialIndex
    }

    /**
     * 计算并切换到下一首喵（不实际播放，只给结果）
     */
    fun getNextSong(): Song? {
        if (currentPlaylist.isEmpty()) return null
        
        currentIndex = when (playMode) {
            2 -> { // SHUFFLE: 随机跳一首不一样的喵
                if (currentPlaylist.size <= 1) 0 
                else {
                    var rand = (currentPlaylist.indices).random()
                    // 尽量不连续放同一首
                    if (rand == currentIndex && currentPlaylist.size > 1) {
                        rand = (rand + 1) % currentPlaylist.size
                    }
                    rand
                }
            }
            else -> { // 普通下一波 (单曲循环在这个阶段相当于列表循环，单曲循环的重复是在播放结束时处理的喵)
                (currentIndex + 1) % currentPlaylist.size
            }
        }
        return currentSong
    }

    /**
     * 计算并切换到上一首喵
     */
    fun getPreviousSong(): Song? {
        if (currentPlaylist.isEmpty()) return null
        
        // 即使是单曲/随机模式，手动点“上一首”通常也直接往前回退一个索引喵
        currentIndex = if (currentIndex <= 0) currentPlaylist.size - 1 else currentIndex - 1
        return currentSong
    }
}
