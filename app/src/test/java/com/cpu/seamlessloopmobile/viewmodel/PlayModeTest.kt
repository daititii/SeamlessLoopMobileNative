package com.cpu.seamlessloopmobile.viewmodel

import com.cpu.seamlessloopmobile.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayModeTest {

    private val songs = listOf(
        Song(id = 1, fileName = "Song1.mp3", filePath = "/path1", totalSamples = 1000),
        Song(id = 2, fileName = "Song2.mp3", filePath = "/path2", totalSamples = 2000),
        Song(id = 3, fileName = "Song3.mp3", filePath = "/path3", totalSamples = 3000)
    )

    // 莱芙模拟一个简易的播放逻辑测试喵！
    @Test
    fun testListLoopNextIndex() {
        val currentIndex = 2 // 最后一首歌喵
        val nextIndex = (currentIndex + 1) % songs.size
        
        // 验证：列表循环在最后一首之后应该回到第一首 (0)
        assertEquals("列表循环到头了没回来喵！", 0, nextIndex)
    }

    @Test
    fun testSingleLoopManualNext() {
        val currentIndex = 1
        // 模拟 ViewModel 中的逻辑：手动切歌时，即使是单曲循环也要跳到下一首
        val nextIndex = (currentIndex + 1) % songs.size
        
        assertEquals("手动切歌没跳到下一位喵！", 2, nextIndex)
    }
}
