package com.cpu.seamlessloopmobile.data.sync

/**
 * 同步用的歌曲身份标识。
 * 不包含 filePath，使用 fileName + durationMs 作为跨设备匹配主键。
 * totalSamples 作为辅助匹配字段（可选）。
 */
data class SyncSongIdentity(
    val fileName: String,
    val durationMs: Long,
    val totalSamples: Long? = null
)

/**
 * 循环点快照数据（已与 songId 解耦）。
 */
data class SyncLoopPoint(
    val loopStart: Long,
    val loopEnd: Long,
    val lastModified: Long
) {
    /** True when this entry carries a real loop range rather than an unset sentinel. */
    val isSubstantive: Boolean get() = !isUnset(loopStart, loopEnd)

    companion object {
        /** Sentinel value indicating an unset/zero loop point. */
        fun isUnset(loopStart: Long, loopEnd: Long): Boolean =
            loopStart == 0L && loopEnd == 0L
    }
}

/**
 * 用户评分快照数据（已与 songId 解耦）。
 */
data class SyncRating(
    val rating: Int,
    val lastModified: Long
) {
    companion object {
        /** Sentinel value: rating 0 means unset. */
        const val UNSET_RATING: Int = 0
    }
}

/**
 * 歌单中的单曲条目，用 SyncSongIdentity 标识歌曲。
 */
data class SyncPlaylistItem(
    val song: SyncSongIdentity,
    val sortOrder: Int
)

/**
 * 歌单快照。
 * id 使用字符串以保证跨设备可移植性（如 UUID 或 name+createdAt 哈希）。
 */
data class SyncPlaylist(
    val id: String,
    val name: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val items: List<SyncPlaylistItem> = emptyList()
)

/**
 * SyncSnapshot 中使用的（歌曲身份 -> 循环点）条目对。
 * 使用列表而非 Map 以便 Gson 序列化。
 */
data class SyncLoopPointEntry(
    val song: SyncSongIdentity,
    val loopPoint: SyncLoopPoint
)

/**
 * SyncSnapshot 中使用的（歌曲身份 -> 评分）条目对。
 * 使用列表而非 Map 以便 Gson 序列化。
 */
data class SyncRatingEntry(
    val song: SyncSongIdentity,
    val rating: SyncRating
)

/**
 * 当前支持的快照 schema 版本。
 */
const val CURRENT_SYNC_SCHEMA_VERSION = 1

/**
 * 完整的同步快照，包含播放列表、循环点和评分数据。
 * 不包含设备特定设置或原始 Room 数据库路径。
 */
data class SyncSnapshot(
    val schemaVersion: Int = CURRENT_SYNC_SCHEMA_VERSION,
    val deviceId: String,
    val exportedAt: Long,
    val playlists: List<SyncPlaylist> = emptyList(),
    val loopPoints: List<SyncLoopPointEntry> = emptyList(),
    val ratings: List<SyncRatingEntry> = emptyList()
)

/**
 * Lightweight remote snapshot listing entry for sync backends.
 */
data class SyncSnapshotSummary(
    val id: String,
    val schemaVersion: Int,
    val deviceId: String,
    val exportedAt: Long
)
