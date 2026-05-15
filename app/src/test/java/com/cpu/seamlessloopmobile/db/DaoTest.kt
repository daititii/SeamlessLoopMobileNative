package com.cpu.seamlessloopmobile.db

import android.content.Context
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DaoTest {

    private lateinit var db: AppDatabase
    private lateinit var songDao: SongDao
    private lateinit var playlistDao: PlaylistDao
    private lateinit var context: Context

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = db.songDao()
        playlistDao = db.playlistDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndGetSong() = runBlocking {
        val song = Song(
            fileName = "test.mp3",
            filePath = "/path/test.mp3",
            totalSamples = 1000,
            rating = 5
        )
        val id = songDao.insertOrUpdateSong(song)
        val loaded = songDao.getSongById(id)
        assertNotNull(loaded)
        assertEquals("test.mp3", loaded?.fileName)
        assertEquals(5, loaded?.rating)
    }

    @Test
    fun insertOrUpdateSongTest() = runBlocking {
        val song1 = Song(
            fileName = "update.mp3",
            filePath = "/old/path",
            totalSamples = 2000,
            duration = 5000
        )
        val id1 = songDao.insertOrUpdateSong(song1)

        val song2 = Song(
            fileName = "update.mp3",
            filePath = "/new/path",
            totalSamples = 2000,
            duration = 5000,
            rating = 4
        )
        val id2 = songDao.insertOrUpdateSong(song2)

        assertEquals(id1, id2)
        val loaded = songDao.getSongById(id2)
        assertEquals("/new/path", loaded?.filePath)
        assertEquals(4, loaded?.rating)
    }

    @Test
    fun playlistOperations() = runBlocking {
        val playlistId = playlistDao.insertPlaylist(Playlist(name = "Favorites")).toInt()
        val song = Song(fileName = "fav.mp3", filePath = "/fav", totalSamples = 100)
        val songId = songDao.insertOrUpdateSong(song)
        
        playlistDao.addSongsToPlaylist(playlistId, listOf(songId))
        
        val playlists = playlistDao.getPlaylistsWithCounts()
        assertEquals(1, playlists.size)
        assertEquals("Favorites", playlists[0].playlist.name)
        assertEquals(1, playlists[0].songCount)
        
        val songsInPl = playlistDao.getSongsInPlaylist(playlistId)
        assertEquals(1, songsInPl.size)
        assertEquals("fav.mp3", songsInPl[0].fileName)
    }

    @Test
    fun playlistDuplicateSong_dedupById() = runBlocking {
        val playlistId = playlistDao.insertPlaylist(Playlist(name = "DedupTest")).toInt()
        val song = Song(fileName = "duplicate.mp3", filePath = "/dup", totalSamples = 200)
        val songId = songDao.insertOrUpdateSong(song)

        playlistDao.insertPlaylistItem(PlaylistItem(playlistId = playlistId, songId = songId, sortOrder = 1))
        playlistDao.insertPlaylistItem(PlaylistItem(playlistId = playlistId, songId = songId, sortOrder = 2))

        val songsInPl = playlistDao.getSongsInPlaylist(playlistId)
        assertEquals(1, songsInPl.size)
        assertEquals("duplicate.mp3", songsInPl[0].fileName)
    }

    @Test
    fun artistAlbumNormalization() = runBlocking {
        // 1. 模拟扫描出一首带有歌手和专辑的歌
        val artistName = "莱芙"
        val albumName = "无缝循环精选集"
        val song = Song(
            fileName = "norm.mp3",
            filePath = "/path/norm.mp3",
            totalSamples = 5000,
            artist = artistName,
            album = albumName
        )

        // 2. 执行插入 (DAO 会自动处理 Artist/Album 实体)
        val id = songDao.insertOrUpdateSong(song)
        
        // 3. 验证 Song POJO 是否正确拼合了数据
        val loaded = songDao.getSongById(id)
        assertNotNull(loaded)
        assertEquals(artistName, loaded?.artist)
        assertEquals(albumName, loaded?.album)
        
        // 4. 验证底层表是否真的创建了对应的实体
        val artist = songDao.getArtistByName(artistName)
        val album = songDao.getAlbumByName(albumName)
        assertNotNull("应该自动创建了 Artist 实体喵！", artist)
        assertNotNull("应该自动创建了 Album 实体喵！", album)
        assertEquals(artist?.id, loaded?.song?.artistId)
        assertEquals(album?.id, loaded?.song?.albumId)

        // 5. 验证重复使用：插入另一首同歌手的歌，不应创建新的 Artist 实体
        val song2 = Song(fileName = "another.mp3", filePath = "/path/another.mp3", artist = artistName)
        songDao.insertOrUpdateSong(song2)
        
        val artists = db.query("SELECT * FROM Artists", null)
        artists.use {
            assertEquals("不应该重复创建同名歌手喵！", 1, it.count)
        }
    }

    @Test
    fun testCascadeDeletion() = runBlocking {
        val song = Song(fileName = "delete_me.mp3", filePath = "/temp", rating = 5, loopStart = 10, loopEnd = 90)
        val id = songDao.insertOrUpdateSong(song)
        
        // 确认数据已存在
        assertNotNull(songDao.getSongById(id))
        
        // 执行删除
        songDao.deleteSongEntity(song.song.copy(id = id))
        
        // 验证主表已空
        assertNull(songDao.getSongById(id))
        
        // 验证关联表已通过外键级联删除喵！
        val ratings = db.query("SELECT * FROM UserRatings WHERE SongId = $id", null)
        ratings.use { assertEquals("评分应该被级联删除了喵！", 0, it.count) }
        
        val loops = db.query("SELECT * FROM LoopPoints WHERE SongId = $id", null)
        loops.use { assertEquals("循环点应该被级联删除了喵！", 0, it.count) }
    }
}
