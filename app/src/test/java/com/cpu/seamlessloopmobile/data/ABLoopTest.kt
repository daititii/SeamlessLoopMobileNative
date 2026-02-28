package com.cpu.seamlessloopmobile.data

import com.cpu.seamlessloopmobile.model.PlaylistDao
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * 专门针对 AB 循环配对逻辑的纯 Kotlin 单元测试喵！
 */
class ABLoopTest {

    // 莱芙直接提供两个空壳管家，避免引入复杂的第三方 Mock 库喵！
    private val fakeSongDao = org.mockito.Mockito.mock(SongDao::class.java) // 假设存在 Mock 方式，如果编译不过莱芙再教您手写 Fake 喵
    private val fakePlaylistDao = org.mockito.Mockito.mock(PlaylistDao::class.java)
    private val repository = MusicRepository(fakeSongDao, fakePlaylistDao)

    @Test
    fun testFindAbPairSuccess() {
        val folderPath = "/sdcard/music/game/"
        // 模拟一个待测试的 A 段（前奏段）歌曲
        val introSong = Song(id = 1, fileName = "BGM_01_Intro.mp3", filePath = "$folderPath/BGM_01_Intro.mp3", totalSamples = 1000)
        
        // 模拟系统库里扫描出的同级目录的其他歌曲
        val allScannedSongs = listOf(
            introSong,
            Song(id = 2, fileName = "BGM_01_Loop.mp3", filePath = "$folderPath/BGM_01_Loop.mp3", totalSamples = 5000),
            Song(id = 3, fileName = "Other_Song.mp3", filePath = "$folderPath/Other_Song.mp3", totalSamples = 2000)
        )

        // 把前奏交进去，看看能不能把循环段对象找出来喵
        val pair = repository.findAbPair(introSong, allScannedSongs)

        assertNotNull("莱芙竟然没找着循环配对的 B 段喵！", pair)
        assertEquals("找到的主体竟然不是原来的 A 段喵！", "BGM_01_Intro.mp3", pair?.first?.fileName)
        assertEquals("找到的伴侣不是对的 B 段喵！", "BGM_01_Loop.mp3", pair?.second?.fileName)
    }

    @Test
    fun testFindAbPairFailDifferentFolder() {
        // 如果它们不在同一个文件夹，即使名字匹配也不能组队喵！
        val introSong = Song(id = 1, fileName = "BGM_01_Intro.mp3", filePath = "/sdcard/folderA/BGM_01_Intro.mp3", totalSamples = 1000)
        val allScannedSongs = listOf(
            introSong,
            Song(id = 2, fileName = "BGM_01_Loop.mp3", filePath = "/sdcard/folderB/BGM_01_Loop.mp3", totalSamples = 5000)
        )

        val pair = repository.findAbPair(introSong, allScannedSongs)
        assertNull("它们不在一个文件夹，不该组队喵！莱芙判案错误！", pair)
    }
}
