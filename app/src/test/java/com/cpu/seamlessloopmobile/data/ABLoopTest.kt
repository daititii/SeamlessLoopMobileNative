package com.cpu.seamlessloopmobile.data

import com.cpu.seamlessloopmobile.model.PlaylistDao
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao
import com.cpu.seamlessloopmobile.model.PlayQueueDao
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
    private val fakePlayQueueDao = org.mockito.Mockito.mock(PlayQueueDao::class.java)
    private val repository = MusicRepository(fakeSongDao, fakePlaylistDao, fakePlayQueueDao)

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

    @Test
    fun testFindAbPairRobust_FallbackToMediaStore() = kotlinx.coroutines.runBlocking {
        // 创建一个不存在数据库的 A 段歌曲
        val folderPath = "/sdcard/music/game"
        val introSong = Song(id = 1, fileName = "BGM_02_Intro.mp3", filePath = "$folderPath/BGM_02_Intro.mp3", totalSamples = 1000)

        // 1. 设置伪造的数据库：返回空白，模拟用户还没把歌曲扫进播放列表的情况喵！
        org.mockito.Mockito.`when`(fakeSongDao.getAllSongs()).thenReturn(emptyList())
        org.mockito.Mockito.`when`(fakeSongDao.getAllSongsRaw()).thenReturn(emptyList())

        // 2. 伪造安卓系统环境喵！
        val mockContext = org.mockito.Mockito.mock(android.content.Context::class.java)
        val mockResolver = org.mockito.Mockito.mock(android.content.ContentResolver::class.java)
        val mockCursor = org.mockito.Mockito.mock(android.database.Cursor::class.java)

        org.mockito.Mockito.`when`(mockContext.contentResolver).thenReturn(mockResolver)

        // 当 MusicRepository 试图去 MediaStore 寻求帮助时，把我们伪造的 Cursor 递给它！
        org.mockito.Mockito.`when`(mockResolver.query(
            org.mockito.ArgumentMatchers.eq(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.isNull()
        )).thenReturn(mockCursor)

        // 模拟 Cursor 里的数据：先返回 true (有一条数据)，然后再返回 false (没数据了)
        org.mockito.Mockito.`when`(mockCursor.moveToNext()).thenReturn(true, false)
        
        // 伪装列索引
        org.mockito.Mockito.`when`(mockCursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)).thenReturn(0)
        org.mockito.Mockito.`when`(mockCursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)).thenReturn(1)
        org.mockito.Mockito.`when`(mockCursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)).thenReturn(2)

        // 伪装具体数据：这就是它要找的 B 段伴侣喵！
        org.mockito.Mockito.`when`(mockCursor.getLong(0)).thenReturn(999L)
        org.mockito.Mockito.`when`(mockCursor.getString(1)).thenReturn("BGM_02_Loop.mp3")
        org.mockito.Mockito.`when`(mockCursor.getString(2)).thenReturn("$folderPath/BGM_02_Loop.mp3")

        // 3. 执行终极查找考验！
        val pair = repository.findAbPairRobust(mockContext, introSong)

        // 4. 断言出奇迹！
        assertNotNull("即使数据库装傻，莱芙也应该凭借系统搜索揪出伴侣喵！", pair)
        assertEquals("找到的主体竟然不是原来的 A 段喵！", "BGM_02_Intro.mp3", pair?.first?.fileName)
        assertEquals("找到的伴侣不是对的 B 段喵！", "BGM_02_Loop.mp3", pair?.second?.fileName)
        assertEquals("分配的 MediaId 不对喵！", 999L, pair?.second?.mediaId)
    }
}
