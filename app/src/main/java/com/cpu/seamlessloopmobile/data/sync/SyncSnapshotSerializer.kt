package com.cpu.seamlessloopmobile.data.sync

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException

/**
 * Gson 驱动的 SyncSnapshot 序列化/反序列化工具。
 *
 * - 忽略未知字段（Gson 默认行为）
 * - 校验 schemaVersion
 * - 拒绝空/格式错误的 JSON
 */
class SyncSnapshotSerializer(
    private val gson: Gson = GsonBuilder().create()
) {

    /**
     * 序列化快照为 JSON 字符串。
     */
    fun serialize(snapshot: SyncSnapshot): String {
        return gson.toJson(snapshot)
    }

    /**
     * 反序列化 JSON 字符串为 SyncSnapshot。
     *
     * @param json 待解析字符串
     * @return 校验通过后的 SyncSnapshot
     * @throws SyncSerializationException 当 JSON 为空、格式错误或 schemaVersion 不匹配时
     */
    fun deserialize(json: String): SyncSnapshot {
        if (json.isBlank()) {
            throw SyncSerializationException(
                "Snapshot JSON is blank or empty"
            )
        }

        val snapshot: SyncSnapshot = try {
            gson.fromJson(json, SyncSnapshot::class.java)
                ?: throw SyncSerializationException(
                    "JSON parsed to null (expected a valid snapshot object)"
                )
        } catch (e: JsonParseException) {
            throw SyncSerializationException(
                "Malformed snapshot JSON: ${e.message}",
                e
            )
        }

        if (snapshot.schemaVersion != CURRENT_SYNC_SCHEMA_VERSION) {
            throw SyncSerializationException(
                "Unsupported schema version ${snapshot.schemaVersion}, " +
                    "expected $CURRENT_SYNC_SCHEMA_VERSION"
            )
        }

        if (snapshot.deviceId.isNullOrBlank()) {
            throw SyncSerializationException("Snapshot deviceId is blank")
        }

        if (snapshot.exportedAt < 0L) {
            throw SyncSerializationException("Snapshot exportedAt is negative")
        }

        @Suppress("USELESS_CAST")
        return snapshot.copy(
            playlists = (snapshot.playlists as List<SyncPlaylist>?) ?: emptyList(),
            loopPoints = (snapshot.loopPoints as List<SyncLoopPointEntry>?) ?: emptyList(),
            ratings = (snapshot.ratings as List<SyncRatingEntry>?) ?: emptyList()
        )
    }
}

/**
 * 快照序列化/反序列化过程中的异常。
 */
class SyncSerializationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
