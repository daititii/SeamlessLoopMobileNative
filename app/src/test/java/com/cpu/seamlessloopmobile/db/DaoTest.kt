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
        val id = songDao.insertSong(song)
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
        val songId = songDao.insertSong(Song(fileName = "fav.mp3", filePath = "/fav", totalSamples = 100))
        
        playlistDao.addSongsToPlaylist(playlistId, listOf(songId))
        
        val playlists = playlistDao.getPlaylistsWithCounts()
        assertEquals(1, playlists.size)
        assertEquals("Favorites", playlists[0].playlist.name)
        assertEquals(1, playlists[0].songCount)
        
        val songsInPl = playlistDao.getSongsInPlaylist(playlistId)
        assertEquals(1, songsInPl.size)
        assertEquals("fav.mp3", songsInPl[0].fileName)
    }
}
