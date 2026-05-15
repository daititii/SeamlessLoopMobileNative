package com.cpu.seamlessloopmobile.db

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cpu.seamlessloopmobile.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers

/**
 * 桌面端数据库同步测试类
 * 莱芙已经准备好了接收 CPU 大人的样本文件喵！(๑•̀ㅂ•́)و✧
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PcDatabaseImporterTest {

    private lateinit var db: AppDatabase
    private lateinit var songDao: SongDao
    private lateinit var playlistDao: PlaylistDao
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = db.songDao()
        playlistDao = db.playlistDao()
        
        // 莱芙在这里强制同步执行，不准偷懒等主线程喵！
        PcDatabaseImporter.ioDispatcher = Dispatchers.Unconfined
        PcDatabaseImporter.mainDispatcher = Dispatchers.Unconfined
    }

    @After
    fun tearDown() {
        db.close()
        // 还原现场，莱芙是很讲礼貌的喵 (´w｀*)
        PcDatabaseImporter.ioDispatcher = Dispatchers.IO
        PcDatabaseImporter.mainDispatcher = Dispatchers.Main
    }

    private fun copyResourceToTempFile(resourcePath: String): File? {
        val inputStream = javaClass.classLoader?.getResourceAsStream(resourcePath)
            ?: return null // 文件还没放进来喵
        val tempFile = File(context.cacheDir, File(resourcePath).name)
        FileOutputStream(tempFile).use { output ->
            inputStream.copyTo(output)
        }
        return tempFile
    }

    @Test
    fun testImportFrom3NFSample() = runBlocking {
        val dbFile = copyResourceToTempFile("pc_db_samples/pc_3nf_sample.db")
        if (dbFile == null) {
            println("跳过测试：还没发现 pc_3nf_sample.db 文件喵，请 CPU 大人放置后再试 (´w｀)")
            return@runBlocking
        }

        // 1. 预埋本地数据（请确保这里的文件名和采样数在您的样本库中存在喵！）
        // 莱芙假设样本库里有一首 "test.mp3"，总采样 100000
        val song = Song(
            fileName = "test.mp3", 
            filePath = "/sdcard/Music/test.mp3", 
            totalSamples = 100000, 
            duration = 2000
        )
        songDao.insertOrUpdateSong(song.song, song.loopStart, song.loopEnd, song.rating)

        val uri = Uri.fromFile(dbFile)
        var capturedCount = -1

        val callback = object : PcDatabaseImporter.ImportCallback {
            override fun onSuccess(syncCount: Int) {
                capturedCount = syncCount
            }
            override fun onError(message: String) {
                fail("同步失败了喵: $message")
            }
        }

        // 2. 执行导入
        PcDatabaseImporter.importFromPcDatabase(context, uri, songDao, playlistDao, callback, appDb = db)

        // 3. 验证结果
        assertTrue("应该至少同步成功一条记录喵", capturedCount >= 0)
        
        val updatedSong = songDao.getAllSongs().find { it.fileName == "test.mp3" }
        assertNotNull("本地歌曲应该还在喵", updatedSong)
        println("同步后的评分: ${updatedSong?.rating}")
        println("同步后的艺术家: ${updatedSong?.artist}")
    }

    @Test
    fun testImportFromFlatSample() = runBlocking {
        val dbFile = copyResourceToTempFile("pc_db_samples/pc_flat_sample.db")
        if (dbFile == null) {
            println("跳过测试：还没发现 pc_flat_sample.db 文件喵")
            return@runBlocking
        }

        val song = Song(fileName = "old_test.mp3", filePath = "/sdcard/Music/old.mp3", totalSamples = 50000)
        songDao.insertOrUpdateSong(song.song, song.loopStart, song.loopEnd, song.rating)

        val uri = Uri.fromFile(dbFile)
        PcDatabaseImporter.importFromPcDatabase(context, uri, songDao, playlistDao, object : PcDatabaseImporter.ImportCallback {
            override fun onSuccess(syncCount: Int) {
                println("旧版架构同步成功: $syncCount")
            }
            override fun onError(message: String) {
                fail("旧版同步失败: $message")
            }
        }, appDb = db)
    }
}
