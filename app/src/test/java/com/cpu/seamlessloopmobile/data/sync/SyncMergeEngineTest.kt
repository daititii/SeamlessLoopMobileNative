package com.cpu.seamlessloopmobile.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SyncMergeEngine 单元测试。
 * 验证双向合并的正确性：按歌单 ID 和歌曲身份标识合并，
 * 保护本地元数据，以及报告冲突。
 */
class SyncMergeEngineTest {

    private val engine = SyncMergeEngine()

    // -------------------------------------------------------------------
    // Null remote (initial sync)
    // -------------------------------------------------------------------

    @Test
    fun `merge with null remote returns local snapshot`() {
        val local = SyncSnapshot(
            deviceId = "local-device",
            exportedAt = 1000L,
            playlists = listOf(
                SyncPlaylist("p1", "Local P1", 100L, 200L)
            )
        )

        val result = runBlockingTest { engine.merge(null, local) }

        assertEquals(local, result.snapshot)
        assertEquals(1, result.report.playlistsUploaded)
        assertEquals(0, result.report.playlistsDownloaded)
    }

    @Test
    fun `merge with null remote and empty local returns empty`() {
        val local = SyncSnapshot(deviceId = "d", exportedAt = 100L)

        val result = runBlockingTest { engine.merge(null, local) }

        assertEquals(local, result.snapshot)
        assertEquals(0, result.report.playlistsUploaded)
        assertTrue(result.report.conflicts.isEmpty())
    }

    // -------------------------------------------------------------------
    // Playlist merge by id
    // -------------------------------------------------------------------

    @Test
    fun `merge combines playlists from both sides`() {
        val local = SyncSnapshot(
            deviceId = "local",
            exportedAt = 100L,
            playlists = listOf(
                SyncPlaylist("p1", "Local Only", 100L, 200L)
            )
        )
        val remote = SyncSnapshot(
            deviceId = "remote",
            exportedAt = 200L,
            playlists = listOf(
                SyncPlaylist("p2", "Remote Only", 300L, 400L)
            )
        )

        val result = runBlockingTest { engine.merge(remote, local) }

        assertEquals(2, result.snapshot.playlists.size)
        val names = result.snapshot.playlists.map { it.name }.sorted()
        assertEquals(listOf("Local Only", "Remote Only"), names)
    }

    @Test
    fun `merge same playlist id picks newer`() {
        val local = SyncSnapshot(
            deviceId = "local",
            exportedAt = 100L,
            playlists = listOf(
                SyncPlaylist("p1", "Old Name", 100L, 200L)
            )
        )
        val remote = SyncSnapshot(
            deviceId = "remote",
            exportedAt = 200L,
            playlists = listOf(
                SyncPlaylist("p1", "New Name", 100L, 400L)
            )
        )

        val result = runBlockingTest { engine.merge(remote, local) }

        assertEquals(1, result.snapshot.playlists.size)
        assertEquals("New Name", result.snapshot.playlists[0].name)
    }

    @Test
    fun `merge playlist with same id but newer local keeps local name`() {
        val local = SyncSnapshot(
            deviceId = "local",
            exportedAt = 100L,
            playlists = listOf(
                SyncPlaylist("p1", "Local Name", 100L, 500L)
            )
        )
        val remote = SyncSnapshot(
            deviceId = "remote",
            exportedAt = 200L,
            playlists = listOf(
                SyncPlaylist("p1", "Remote Name", 100L, 300L)
            )
        )

        val result = runBlockingTest { engine.merge(remote, local) }

        assertEquals("Local Name", result.snapshot.playlists[0].name)
    }

    // -------------------------------------------------------------------
    // Merge by song identity
    // -------------------------------------------------------------------

    @Test
    fun `merge loop points by song identity`() {
        val song = SyncSongIdentity("track.mp3", 30000L)
        val local = SyncSnapshot(
            deviceId = "local",
            exportedAt = 100L,
            loopPoints = listOf(
                SyncLoopPointEntry(song, SyncLoopPoint(100L, 200L, 1000L))
            )
        )
        val remote = SyncSnapshot(
            deviceId = "remote",
            exportedAt = 200L,
            loopPoints = listOf(
                SyncLoopPointEntry(song, SyncLoopPoint(150L, 250L, 2000L))
            )
        )

        // remote loop point is newer → merged should use remote
        val result = runBlockingTest { engine.merge(remote, local) }

        assertEquals(1, result.snapshot.loopPoints.size)
        val mergedEntry = result.snapshot.loopPoints[0]
        assertEquals(song, mergedEntry.song)
        assertEquals(150L, mergedEntry.loopPoint.loopStart)
        assertEquals(250L, mergedEntry.loopPoint.loopEnd)
    }

    @Test
    fun `merge ratings by song identity`() {
        val song = SyncSongIdentity("track.mp3", 30000L)
        val local = SyncSnapshot(
            deviceId = "local",
            exportedAt = 100L,
            ratings = listOf(
                SyncRatingEntry(song, SyncRating(5, 1000L))
            )
        )
        val remote = SyncSnapshot(
            deviceId = "remote",
            exportedAt = 200L,
            ratings = listOf(
                SyncRatingEntry(song, SyncRating(3, 2000L))
            )
        )

        // remote rating is newer → merged should use remote
        val result = runBlockingTest { engine.merge(remote, local) }

        assertEquals(1, result.snapshot.ratings.size)
        assertEquals(3, result.snapshot.ratings[0].rating.rating)
    }

    @Test
    fun `merge ratings with same file and duration ignores totalSamples mismatch and keeps remote identity`() {
        val remoteSong = SyncSongIdentity("1-02. Summer Pockets.flac", 239_987L, 10_583_412L)
        val localSong = SyncSongIdentity("1-02. Summer Pockets.flac", 239_987L, 10_583_426L)
        val local = SyncSnapshot(
            deviceId = "local",
            exportedAt = 100L,
            ratings = listOf(
                SyncRatingEntry(localSong, SyncRating(4, 1_000L))
            )
        )
        val remote = SyncSnapshot(
            deviceId = "remote",
            exportedAt = 200L,
            ratings = listOf(
                SyncRatingEntry(remoteSong, SyncRating(3, 2_000L))
            )
        )

        val result = runBlockingTest { engine.merge(remote, local) }

        assertEquals(1, result.snapshot.ratings.size)
        assertEquals(remoteSong, result.snapshot.ratings.single().song)
        assertEquals(3, result.snapshot.ratings.single().rating.rating)
    }

    @Test
    fun `merge loop points with same file and duration ignores totalSamples mismatch and keeps remote identity`() {
        val remoteSong = SyncSongIdentity("loop.flac", 120_000L, 5_292_000L)
        val localSong = SyncSongIdentity("loop.flac", 120_000L, 5_292_014L)
        val local = SyncSnapshot(
            deviceId = "local",
            exportedAt = 100L,
            loopPoints = listOf(
                SyncLoopPointEntry(localSong, SyncLoopPoint(100L, 1_000L, 1_000L))
            )
        )
        val remote = SyncSnapshot(
            deviceId = "remote",
            exportedAt = 200L,
            loopPoints = listOf(
                SyncLoopPointEntry(remoteSong, SyncLoopPoint(200L, 2_000L, 2_000L))
            )
        )

        val result = runBlockingTest { engine.merge(remote, local) }

        assertEquals(1, result.snapshot.loopPoints.size)
        assertEquals(remoteSong, result.snapshot.loopPoints.single().song)
        assertEquals(200L, result.snapshot.loopPoints.single().loopPoint.loopStart)
    }

    @Test
    fun `merge playlist items with same file and duration ignores totalSamples mismatch and keeps remote identity`() {
        val remoteSong = SyncSongIdentity("track.flac", 180_000L, 7_938_000L)
        val localSong = SyncSongIdentity("track.flac", 180_000L, 7_938_014L)
        val local = SyncSnapshot(
            deviceId = "local",
            exportedAt = 100L,
            playlists = listOf(
                SyncPlaylist(
                    id = "p1",
                    name = "Playlist",
                    createdAt = 100L,
                    modifiedAt = 3_000L,
                    items = listOf(SyncPlaylistItem(localSong, sortOrder = 0))
                )
            )
        )
        val remote = SyncSnapshot(
            deviceId = "remote",
            exportedAt = 200L,
            playlists = listOf(
                SyncPlaylist(
                    id = "p1",
                    name = "Playlist",
                    createdAt = 100L,
                    modifiedAt = 2_000L,
                    items = listOf(SyncPlaylistItem(remoteSong, sortOrder = 0))
                )
            )
        )

        val result = runBlockingTest { engine.merge(remote, local) }

        assertEquals(1, result.snapshot.playlists.single().items.size)
        assertEquals(remoteSong, result.snapshot.playlists.single().items.single().song)
    }

    @Test
    fun `merge loop points from different songs`() {
        val songA = SyncSongIdentity("a.mp3", 10000L)
        val songB = SyncSongIdentity("b.mp3", 20000L)
        val local = SyncSnapshot(
            deviceId = "local",
            exportedAt = 100L,
            loopPoints = listOf(
                SyncLoopPointEntry(songA, SyncLoopPoint(100L, 200L, 1000L))
            )
        )
        val remote = SyncSnapshot(
            deviceId = "remote",
            exportedAt = 200L,
            loopPoints = listOf(
                SyncLoopPointEntry(songB, SyncLoopPoint(300L, 400L, 2000L))
            )
        )

        val result = runBlockingTest { engine.merge(remote, local) }

        assertEquals(2, result.snapshot.loopPoints.size)
    }

    // -------------------------------------------------------------------
    // Preserve local metadata
    // -------------------------------------------------------------------

    @Test
    fun `merge preserves local deviceId and exportedAt`() {
        val local = SyncSnapshot(
            deviceId = "my-device",
            exportedAt = 5000L,
            playlists = listOf(
                SyncPlaylist("p1", "My List", 100L, 200L)
            )
        )
        val remote = SyncSnapshot(
            deviceId = "other-device",
            exportedAt = 3000L,
            playlists = listOf(
                SyncPlaylist("p1", "Remote List", 100L, 400L)
            )
        )

        val result = runBlockingTest { engine.merge(remote, local) }

        // Local metadata should be preserved
        assertEquals("my-device", result.snapshot.deviceId)
        assertEquals(5000L, result.snapshot.exportedAt)
    }

    @Test
    fun `merge preserves local schemaVersion`() {
        val local = SyncSnapshot(
            deviceId = "local",
            exportedAt = 100L,
            playlists = listOf(SyncPlaylist("p1", "Test", 100L, 200L))
        )
        val remote = SyncSnapshot(
            deviceId = "remote",
            exportedAt = 200L,
            playlists = listOf(SyncPlaylist("p1", "Updated", 100L, 400L))
        )

        val result = runBlockingTest { engine.merge(remote, local) }

        assertEquals(1, result.snapshot.schemaVersion)
    }

    // -------------------------------------------------------------------
    // Report counts
    // -------------------------------------------------------------------

    @Test
    fun `merge report has correct counts`() {
        val local = SyncSnapshot(
            deviceId = "local",
            exportedAt = 100L,
            playlists = listOf(
                SyncPlaylist("p1", "Local", 100L, 200L)
            ),
            loopPoints = listOf(
                SyncLoopPointEntry(
                    SyncSongIdentity("a.mp3", 10000L),
                    SyncLoopPoint(100L, 200L, 100L)
                )
            ),
            ratings = listOf(
                SyncRatingEntry(
                    SyncSongIdentity("b.mp3", 20000L),
                    SyncRating(4, 200L)
                )
            )
        )
        val remote = SyncSnapshot(
            deviceId = "remote",
            exportedAt = 200L,
            playlists = listOf(
                SyncPlaylist("p2", "Remote", 300L, 400L)
            ),
            loopPoints = listOf(
                SyncLoopPointEntry(
                    SyncSongIdentity("c.mp3", 30000L),
                    SyncLoopPoint(300L, 400L, 300L)
                )
            ),
            ratings = listOf(
                SyncRatingEntry(
                    SyncSongIdentity("d.mp3", 40000L),
                    SyncRating(3, 400L)
                )
            )
        )

        val result = runBlockingTest { engine.merge(remote, local) }

        assertEquals(1, result.report.playlistsUploaded)   // local has 1
        assertEquals(1, result.report.playlistsDownloaded) // remote has 1
        assertEquals(1, result.report.loopPointsUploaded)
        assertEquals(1, result.report.loopPointsDownloaded)
        assertEquals(1, result.report.ratingsUploaded)
        assertEquals(1, result.report.ratingsDownloaded)
    }

    // -------------------------------------------------------------------
    // Conflict detection
    // -------------------------------------------------------------------

    @Test
    fun `merge detects playlist name conflict`() {
        val local = SyncSnapshot(
            deviceId = "local",
            exportedAt = 100L,
            playlists = listOf(
                SyncPlaylist("p1", "Local Name", 100L, 200L)
            )
        )
        val remote = SyncSnapshot(
            deviceId = "remote",
            exportedAt = 200L,
            playlists = listOf(
                SyncPlaylist("p1", "Remote Name", 100L, 300L)
            )
        )

        val result = runBlockingTest { engine.merge(remote, local) }

        assertTrue(result.report.conflicts.isNotEmpty())
        assertEquals("Local Name", result.report.conflicts[0].playlistName)
        assertEquals("Remote Name", result.report.conflicts[0].remoteValue)
        assertEquals("Local Name", result.report.conflicts[0].localValue)
    }

    @Test
    fun `merge no conflict when playlist names match`() {
        val local = SyncSnapshot(
            deviceId = "local",
            exportedAt = 100L,
            playlists = listOf(
                SyncPlaylist("p1", "Same Name", 100L, 200L)
            )
        )
        val remote = SyncSnapshot(
            deviceId = "remote",
            exportedAt = 200L,
            playlists = listOf(
                SyncPlaylist("p1", "Same Name", 100L, 300L)
            )
        )

        val result = runBlockingTest { engine.merge(remote, local) }

        assertTrue(result.report.conflicts.isEmpty())
    }

    // -------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------

    /**
     * Small inline helper to run suspend code in tests without
     * requiring kotlinx-coroutines-test runTest integration.
     */
    private fun <T> runBlockingTest(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking { block() }
    }
}
