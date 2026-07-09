package com.cpu.seamlessloopmobile.data.sync.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cpu.seamlessloopmobile.data.sync.SyncLoopPoint
import com.cpu.seamlessloopmobile.data.sync.SyncLoopPointEntry
import com.cpu.seamlessloopmobile.data.sync.SyncPlaylist
import com.cpu.seamlessloopmobile.data.sync.SyncPlaylistItem
import com.cpu.seamlessloopmobile.data.sync.SyncRating
import com.cpu.seamlessloopmobile.data.sync.SyncRatingEntry
import com.cpu.seamlessloopmobile.data.sync.SyncSnapshot
import com.cpu.seamlessloopmobile.data.sync.SyncSongIdentity
import com.cpu.seamlessloopmobile.db.AppDatabase
import com.cpu.seamlessloopmobile.model.Playlist
import com.cpu.seamlessloopmobile.model.PlaylistDao
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao
import com.cpu.seamlessloopmobile.model.UserRating
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSyncSnapshotStoreTest {

    private lateinit var db: AppDatabase
    private lateinit var songDao: SongDao
    private lateinit var playlistDao: PlaylistDao
    private lateinit var mapper: FakePlaylistIdMapper
    private lateinit var store: RoomSyncSnapshotStore

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = db.songDao()
        playlistDao = db.playlistDao()
        mapper = FakePlaylistIdMapper()
        store = RoomSyncSnapshotStore(db, songDao, playlistDao, mapper)
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun exportSnapshotUsesPortableSongIdentityAndPlaylistSyncId() = runBlocking {
        val loopSongId = insertSong(
            fileName = "loop.mp3",
            filePath = "/storage/device-a/loop.mp3",
            duration = 123_000L,
            totalSamples = 5_424_300L,
            loopStart = 1_000L,
            loopEnd = 120_000L,
            rating = 4
        )
        val plainSongId = insertSong(
            fileName = "plain.mp3",
            filePath = "/storage/device-a/plain.mp3",
            duration = 90_000L,
            totalSamples = 3_969_000L
        )
        val playlistId = playlistDao.insertPlaylist(
            Playlist(name = "Favorites", createdAt = 1_000L)
        ).toInt()
        playlistDao.clearAndSyncPlaylist(playlistId, listOf(loopSongId, plainSongId))

        val snapshot = store.exportSnapshot(deviceId = "device-a", now = 10_000L)

        assertEquals("device-a", snapshot.deviceId)
        assertEquals(10_000L, snapshot.exportedAt)
        assertEquals(1, snapshot.loopPoints.size)
        assertEquals(
            SyncSongIdentity("loop.mp3", 123_000L, 5_424_300L),
            snapshot.loopPoints.single().song
        )
        assertEquals(10_000L, snapshot.loopPoints.single().loopPoint.lastModified)
        assertEquals(1, snapshot.ratings.size)
        assertEquals(SyncSongIdentity("loop.mp3", 123_000L, 5_424_300L), snapshot.ratings.single().song)
        assertEquals(1, snapshot.playlists.size)
        assertEquals("playlist-sync-1", snapshot.playlists.single().id)
        assertEquals("Favorites", snapshot.playlists.single().name)
        assertEquals(
            listOf(
                SyncSongIdentity("loop.mp3", 123_000L, 5_424_300L),
                SyncSongIdentity("plain.mp3", 90_000L, 3_969_000L)
            ),
            snapshot.playlists.single().items.map { it.song }
        )
    }

    @Test
    fun applySnapshotWritesMatchedDataAndSkipsUnmatchedSongs() = runBlocking {
        val localSongId = insertSong(
            fileName = "remote.mp3",
            filePath = "/local/remote.mp3",
            duration = 100_000L,
            totalSamples = 4_410_000L
        )
        val matched = SyncSongIdentity("remote.mp3", 100_000L, 4_410_000L)
        val missing = SyncSongIdentity("missing.mp3", 80_000L, 3_528_000L)
        val snapshot = SyncSnapshot(
            deviceId = "device-b",
            exportedAt = 20_000L,
            loopPoints = listOf(
                SyncLoopPointEntry(matched, SyncLoopPoint(2_000L, 98_000L, 20_000L)),
                SyncLoopPointEntry(missing, SyncLoopPoint(1_000L, 70_000L, 20_000L))
            ),
            ratings = listOf(
                SyncRatingEntry(matched, SyncRating(5, 20_000L))
            ),
            playlists = listOf(
                SyncPlaylist(
                    id = "remote-playlist-1",
                    name = "Remote List",
                    createdAt = 12_000L,
                    modifiedAt = 20_000L,
                    items = listOf(
                        SyncPlaylistItem(matched, sortOrder = 0),
                        SyncPlaylistItem(missing, sortOrder = 1)
                    )
                )
            )
        )

        val report = store.applySnapshot(snapshot)

        assertEquals(1, report.loopPointsDownloaded)
        assertEquals(1, report.ratingsDownloaded)
        assertEquals(1, report.playlistsDownloaded)
        assertEquals(2, report.conflicts.size)
        assertEquals(1, songDao.getAllSongs().size)

        val updatedSong = songDao.getSongById(localSongId)
        assertNotNull(updatedSong)
        assertEquals(2_000L, updatedSong?.loopStart)
        assertEquals(98_000L, updatedSong?.loopEnd)
        assertEquals(5, updatedSong?.rating)

        val playlists = playlistDao.getPlaylistsWithCounts()
        assertEquals(1, playlists.size)
        assertEquals("Remote List", playlists.single().playlist.name)
        val playlistSongs = playlistDao.getSongsInPlaylist(playlists.single().playlist.id)
        assertEquals(listOf(localSongId), playlistSongs.map { it.id })
    }

    @Test
    fun applySnapshotMatchesByExactDurationBeforeTotalSamplesAndToleratesSampleDifferences() = runBlocking {
        val localSongId = insertSong(
            fileName = "1-02. Summer Pockets.flac",
            filePath = "/local/1-02. Summer Pockets.flac",
            duration = 239_987L,
            totalSamples = 10_583_412L
        )
        val remoteIdentity = SyncSongIdentity(
            fileName = "1-02. Summer Pockets.flac",
            durationMs = 239_987L,
            totalSamples = 10_583_426L
        )

        val report = store.applySnapshot(
            SyncSnapshot(
                deviceId = "desktop",
                exportedAt = 20_000L,
                ratings = listOf(
                    SyncRatingEntry(remoteIdentity, SyncRating(4, 20_000L))
                ),
                loopPoints = listOf(
                    SyncLoopPointEntry(remoteIdentity, SyncLoopPoint(1_000L, 10_000L, 20_000L))
                ),
                playlists = listOf(
                    SyncPlaylist(
                        id = "desktop-playlist",
                        name = "Desktop Playlist",
                        createdAt = 10_000L,
                        modifiedAt = 20_000L,
                        items = listOf(SyncPlaylistItem(remoteIdentity, sortOrder = 0))
                    )
                )
            )
        )

        assertEquals(1, report.ratingsDownloaded)
        assertEquals(1, report.loopPointsDownloaded)
        assertEquals(1, report.playlistsDownloaded)
        assertTrue(report.conflicts.isEmpty())
        val updatedSong = songDao.getSongById(localSongId)
        assertNotNull(updatedSong)
        assertEquals(4, updatedSong?.rating)
        assertEquals(1_000L, updatedSong?.loopStart)
        assertEquals(10_000L, updatedSong?.loopEnd)
        val playlistSongs = playlistDao.getSongsInPlaylist(playlistDao.getPlaylistsWithCounts().single().playlist.id)
        assertEquals(listOf(localSongId), playlistSongs.map { it.id })
    }

    @Test
    fun applySnapshotKeepsSubstantiveLocalDataWhenRemoteIsUnsetOrOlder() = runBlocking {
        val localSongId = insertSong(
            fileName = "protected.mp3",
            filePath = "/local/protected.mp3",
            duration = 60_000L,
            totalSamples = 2_646_000L,
            loopStart = 500L,
            loopEnd = 58_000L,
            rating = 5
        )
        songDao.insertUserRating(UserRating(localSongId, rating = 5, lastModified = 30_000L))

        val identity = SyncSongIdentity("protected.mp3", 60_000L, 2_646_000L)
        val snapshot = SyncSnapshot(
            deviceId = "device-b",
            exportedAt = 20_000L,
            loopPoints = listOf(
                SyncLoopPointEntry(identity, SyncLoopPoint(0L, 0L, 20_000L))
            ),
            ratings = listOf(
                SyncRatingEntry(identity, SyncRating(1, 20_000L))
            )
        )

        val report = store.applySnapshot(snapshot)

        assertEquals(0, report.loopPointsDownloaded)
        assertEquals(0, report.ratingsDownloaded)
        val updatedSong = songDao.getSongById(localSongId)
        assertNotNull(updatedSong)
        assertEquals(500L, updatedSong?.loopStart)
        assertEquals(58_000L, updatedSong?.loopEnd)
        assertEquals(5, updatedSong?.rating)
    }

    @Test
    fun applySnapshotSkipsAmbiguousSongMatches() = runBlocking {
        val firstId = insertSong(
            fileName = "duplicate.mp3",
            filePath = "/local/a/duplicate.mp3",
            duration = 120_000L,
            totalSamples = 5_292_000L
        )
        val secondId = songDao.insertSongEntity(
            com.cpu.seamlessloopmobile.model.SongEntity(
                fileName = "duplicate.mp3",
                filePath = "/local/b/duplicate.mp3",
                duration = 120_000L,
                totalSamples = 5_292_000L
            )
        )
        val identity = SyncSongIdentity("duplicate.mp3", 120_000L, 5_292_000L)

        val report = store.applySnapshot(
            SyncSnapshot(
                deviceId = "device-b",
                exportedAt = 20_000L,
                loopPoints = listOf(
                    SyncLoopPointEntry(identity, SyncLoopPoint(1_000L, 119_000L, 20_000L))
                )
            )
        )

        assertEquals(0, report.loopPointsDownloaded)
        assertEquals(1, report.conflicts.size)
        assertEquals(0L, songDao.getSongById(firstId)?.loopStart)
        assertEquals(0L, songDao.getSongById(secondId)?.loopStart)
    }

    private suspend fun insertSong(
        fileName: String,
        filePath: String,
        duration: Long,
        totalSamples: Long,
        loopStart: Long = 0L,
        loopEnd: Long = 0L,
        rating: Int = 0
    ): Long {
        return songDao.insertOrUpdateSong(
            Song(
                fileName = fileName,
                filePath = filePath,
                duration = duration,
                totalSamples = totalSamples,
                loopStart = loopStart,
                loopEnd = loopEnd,
                rating = rating
            )
        )
    }

    private class FakePlaylistIdMapper : PlaylistIdMapper {
        private val records = mutableMapOf<String, PlaylistSyncRecord>()
        private var nextId = 1

        override suspend fun getOrCreateSyncIdForExport(
            localId: Int,
            fingerprint: String,
            now: Long
        ): PlaylistSyncRecord {
            val existing = records.values.firstOrNull { it.localId == localId }
            if (existing != null) {
                val updated = if (existing.fingerprint == fingerprint) {
                    existing
                } else {
                    existing.copy(modifiedAt = now, fingerprint = fingerprint)
                }
                records[updated.syncId] = updated
                return updated
            }

            val record = PlaylistSyncRecord(
                syncId = "playlist-sync-${nextId++}",
                localId = localId,
                modifiedAt = now,
                fingerprint = fingerprint
            )
            records[record.syncId] = record
            return record
        }

        override suspend fun findLocalId(syncId: String): Int? = records[syncId]?.localId

        override suspend fun findSyncId(localId: Int): String? =
            records.values.firstOrNull { it.localId == localId }?.syncId

        override suspend fun saveMapping(
            syncId: String,
            localId: Int,
            modifiedAt: Long,
            fingerprint: String
        ) {
            records.entries.removeAll { it.key == syncId || it.value.localId == localId }
            records[syncId] = PlaylistSyncRecord(syncId, localId, modifiedAt, fingerprint)
        }

        override suspend fun removeStaleMappings(validLocalIds: Set<Int>) {
            records.entries.removeAll { it.value.localId !in validLocalIds }
        }

        override suspend fun clearAllMappings() {
            records.clear()
        }
    }
}
