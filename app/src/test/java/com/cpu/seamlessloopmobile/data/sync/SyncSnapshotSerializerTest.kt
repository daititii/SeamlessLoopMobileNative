package com.cpu.seamlessloopmobile.data.sync

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * SyncSnapshotSerializer 单元测试。
 * 验证 Gson 驱动的序列化/反序列化正确性，包括 schema 校验和异常处理。
 */
class SyncSnapshotSerializerTest {

    private val serializer = SyncSnapshotSerializer()
    private val gson = GsonBuilder().create()

    // -------------------------------------------------------------------
    // Round-trip
    // -------------------------------------------------------------------

    @Test
    fun `round trip produces identical snapshot`() {
        val original = SyncSnapshot(
            deviceId = "device-123",
            exportedAt = 1000000L,
            playlists = listOf(
                SyncPlaylist(
                    id = "pl-1",
                    name = "Favorites",
                    createdAt = 100L,
                    modifiedAt = 200L,
                    items = listOf(
                        SyncPlaylistItem(
                            song = SyncSongIdentity("song1.mp3", 30000L),
                            sortOrder = 0
                        )
                    )
                )
            ),
            loopPoints = listOf(
                SyncLoopPointEntry(
                    song = SyncSongIdentity("song1.mp3", 30000L),
                    loopPoint = SyncLoopPoint(1000L, 5000L, 300L)
                )
            ),
            ratings = listOf(
                SyncRatingEntry(
                    song = SyncSongIdentity("song1.mp3", 30000L),
                    rating = SyncRating(5, 400L)
                )
            )
        )

        val json = serializer.serialize(original)
        val restored = serializer.deserialize(json)

        assertEquals(original, restored)
    }

    @Test
    fun `round trip with minimal snapshot`() {
        val original = SyncSnapshot(
            deviceId = "device-456",
            exportedAt = 2000000L
        )

        val json = serializer.serialize(original)
        val restored = serializer.deserialize(json)

        assertEquals(original, restored)
    }

    @Test
    fun `round trip preserves all fields`() {
        val original = SyncSnapshot(
            schemaVersion = 1,
            deviceId = "test-device",
            exportedAt = 5000L,
            playlists = listOf(
                SyncPlaylist("p1", "Test", 100L, 200L)
            ),
            loopPoints = listOf(
                SyncLoopPointEntry(
                    SyncSongIdentity("a.mp3", 10000L),
                    SyncLoopPoint(0L, 0L, 300L)
                )
            ),
            ratings = emptyList()
        )

        val json = serializer.serialize(original)
        val restored = serializer.deserialize(json)

        assertEquals(original.schemaVersion, restored.schemaVersion)
        assertEquals(original.deviceId, restored.deviceId)
        assertEquals(original.exportedAt, restored.exportedAt)
        assertEquals(original.playlists.size, restored.playlists.size)
        assertEquals(original.loopPoints.size, restored.loopPoints.size)
        assertEquals(original.ratings.size, restored.ratings.size)
        assertEquals(original, restored)
    }

    // -------------------------------------------------------------------
    // Schema version validation
    // -------------------------------------------------------------------

    @Test(expected = SyncSerializationException::class)
    fun `rejects unsupported schema version - too high`() {
        val json = gson.toJson(
            mapOf(
                "schemaVersion" to 999,
                "deviceId" to "test",
                "exportedAt" to 1000L
            )
        )
        serializer.deserialize(json)
    }

    @Test(expected = SyncSerializationException::class)
    fun `rejects unsupported schema version - too low`() {
        val json = gson.toJson(
            mapOf(
                "schemaVersion" to 0,
                "deviceId" to "test",
                "exportedAt" to 1000L
            )
        )
        serializer.deserialize(json)
    }

    // -------------------------------------------------------------------
    // Malformed / empty JSON
    // -------------------------------------------------------------------

    @Test(expected = SyncSerializationException::class)
    fun `rejects empty string`() {
        serializer.deserialize("")
    }

    @Test(expected = SyncSerializationException::class)
    fun `rejects blank string`() {
        serializer.deserialize("   ")
    }

    @Test(expected = SyncSerializationException::class)
    fun `rejects malformed JSON`() {
        serializer.deserialize("{{{{broken json}///")
    }

    @Test(expected = SyncSerializationException::class)
    fun `rejects null literal`() {
        serializer.deserialize("null")
    }

    @Test(expected = SyncSerializationException::class)
    fun `rejects missing device id`() {
        val json = """{
            "schemaVersion": 1,
            "exportedAt": 1000
        }"""
        serializer.deserialize(json)
    }

    @Test(expected = SyncSerializationException::class)
    fun `rejects negative exported at`() {
        val json = """{
            "schemaVersion": 1,
            "deviceId": "device",
            "exportedAt": -1
        }"""
        serializer.deserialize(json)
    }

    @Test
    fun `normalizes null lists to empty lists`() {
        val json = """{
            "schemaVersion": 1,
            "deviceId": "device",
            "exportedAt": 1000,
            "playlists": null,
            "loopPoints": null,
            "ratings": null
        }"""

        val snapshot = serializer.deserialize(json)

        assertEquals(emptyList<SyncPlaylist>(), snapshot.playlists)
        assertEquals(emptyList<SyncLoopPointEntry>(), snapshot.loopPoints)
        assertEquals(emptyList<SyncRatingEntry>(), snapshot.ratings)
    }

    // -------------------------------------------------------------------
    // Unknown fields ignored
    // -------------------------------------------------------------------

    @Test
    fun `ignores unknown fields during deserialization`() {
        val json = """{
            "schemaVersion": 1,
            "deviceId": "test-device",
            "exportedAt": 1000,
            "unknownField": "should be ignored",
            "anotherUnknown": 42
        }"""
        val snapshot = serializer.deserialize(json)
        assertNotNull(snapshot)
        assertEquals("test-device", snapshot.deviceId)
        assertEquals(1000L, snapshot.exportedAt)
        assertEquals(1, snapshot.schemaVersion)
    }

    // -------------------------------------------------------------------
    // Serialize
    // -------------------------------------------------------------------

    @Test
    fun `serialize produces valid JSON`() {
        val snapshot = SyncSnapshot(deviceId = "d1", exportedAt = 100L)
        val json = serializer.serialize(snapshot)
        // Should be parseable back via Gson
        val parsed = gson.fromJson(json, Map::class.java)
        assertNotNull(parsed)
        assertEquals("d1", (parsed as Map<*, *>)["deviceId"])
    }

    // -------------------------------------------------------------------
    // Custom Gson instance
    // -------------------------------------------------------------------

    @Test
    fun `uses custom gson instance`() {
        val customGson = GsonBuilder().setPrettyPrinting().create()
        val customSerializer = SyncSnapshotSerializer(customGson)
        val snapshot = SyncSnapshot(deviceId = "custom", exportedAt = 200L)
        val json = customSerializer.serialize(snapshot)
        // Pretty-printed JSON should still be valid
        val restored = customSerializer.deserialize(json)
        assertEquals(snapshot, restored)
    }
}
