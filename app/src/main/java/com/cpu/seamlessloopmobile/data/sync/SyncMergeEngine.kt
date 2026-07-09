package com.cpu.seamlessloopmobile.data.sync

/**
 * 合并操作的结果。
 * @property snapshot 合并后的快照（保留本地 deviceId 和 exportedAt）
 * @property report 合并操作报告
 */
data class MergeResult(
    val snapshot: SyncSnapshot,
    val report: SyncReport
)

/**
 * 双向同步合并引擎。
 *
 * 接收远端（可能为 null）和本地快照，按稳定歌曲身份标识和歌单 ID 进行合并，
 * 使用可注入的策略对象处理各数据类型的冲突。
 */
class SyncMergeEngine(
    private val playlistPolicy: PlaylistMergePolicy = DefaultPlaylistMergePolicy,
    private val loopPointPolicy: LoopPointMergePolicy = DefaultLoopPointMergePolicy,
    private val ratingPolicy: RatingMergePolicy = DefaultRatingMergePolicy
) {

    /**
     * 合并远端和本地快照。
     * - 如果远端为 null，直接返回本地快照（首次同步）
     * - 按歌单 ID 合并播放列表
     * - 按 fileName(忽略大小写)+durationMs 合并循环点和评分；totalSamples 仅作辅助匹配字段
     * - 合并结果保留本地的 schemaVersion、deviceId、exportedAt
     *
     * @param remote 来自远端的快照（可能为 null）
     * @param local 本地导出的快照
     * @return 合并后的快照及报告
     */
    suspend fun merge(remote: SyncSnapshot?, local: SyncSnapshot): MergeResult {
        if (remote == null) {
            return MergeResult(
                snapshot = local,
                report = SyncReport(
                    playlistsUploaded = local.playlists.size,
                    loopPointsUploaded = local.loopPoints.size,
                    ratingsUploaded = local.ratings.size
                )
            )
        }

        val conflicts = mutableListOf<SyncConflict>()

        val mergedPlaylists = mergePlaylists(remote.playlists, local.playlists, conflicts)
        val mergedLoopPoints = mergeEntries(
            remote.loopPoints,
            local.loopPoints,
            { it.song.stableKey() },
            { r, l ->
                val merged = loopPointPolicy.resolve(r?.loopPoint, l?.loopPoint)
                val song = preferStableSongIdentity(remote = r?.song, local = l?.song)
                if (song != null && merged != null) SyncLoopPointEntry(song, merged) else null
            },
            conflicts
        )
        val mergedRatings = mergeEntries(
            remote.ratings,
            local.ratings,
            { it.song.stableKey() },
            { r, l ->
                val merged = ratingPolicy.resolve(r?.rating, l?.rating)
                val song = preferStableSongIdentity(remote = r?.song, local = l?.song)
                if (song != null && merged != null) SyncRatingEntry(song, merged) else null
            },
            conflicts
        )

        val mergedSnapshot = SyncSnapshot(
            schemaVersion = local.schemaVersion,
            deviceId = local.deviceId,
            exportedAt = local.exportedAt,
            playlists = mergedPlaylists,
            loopPoints = mergedLoopPoints,
            ratings = mergedRatings
        )

        val report = SyncReport(
            playlistsUploaded = local.playlists.size,
            playlistsDownloaded = remote.playlists.size,
            loopPointsUploaded = local.loopPoints.size,
            loopPointsDownloaded = remote.loopPoints.size,
            ratingsUploaded = local.ratings.size,
            ratingsDownloaded = remote.ratings.size,
            conflicts = conflicts
        )

        return MergeResult(mergedSnapshot, report)
    }

    // -------------------------------------------------------------------
    // 内部辅助方法
    // -------------------------------------------------------------------

    /**
     * 按歌单 ID 合并播放列表。
     */
    private fun mergePlaylists(
        remote: List<SyncPlaylist>,
        local: List<SyncPlaylist>,
        conflicts: MutableList<SyncConflict>
    ): List<SyncPlaylist> {
        val localById = local.associateBy { it.id }
        val remoteById = remote.associateBy { it.id }
        val allIds = (localById.keys + remoteById.keys).toSet()

        return allIds.mapNotNull { id ->
            val localPlaylist = localById[id]
            val remotePlaylist = remoteById[id]

            if (localPlaylist == null) return@mapNotNull remotePlaylist?.withoutDuplicateStableItems()
            if (remotePlaylist == null) return@mapNotNull localPlaylist.withoutDuplicateStableItems()

            // Record conflict if names differ
            if (localPlaylist.name != remotePlaylist.name) {
                conflicts.add(
                    SyncConflict(
                        playlistName = localPlaylist.name,
                        remoteValue = remotePlaylist.name,
                        localValue = localPlaylist.name,
                        resolution = if (remotePlaylist.modifiedAt >= localPlaylist.modifiedAt)
                            "remote" else "local"
                    )
                )
            }
            playlistPolicy.resolve(remotePlaylist, localPlaylist).withoutDuplicateStableItems()
        }.sortedBy { it.name }
    }

    private fun SyncPlaylist.withoutDuplicateStableItems(): SyncPlaylist =
        copy(items = items.distinctBy { it.song.stableKey() })

    /**
     * 泛型条目合并辅助方法。
     * @param remote 远端条目列表
     * @param local 本地条目列表
     * @param keySelector 从条目中提取匹配键的函数
     * @param resolver 合并两个可选条目为一个可选结果
     * @param conflicts 冲突列表（用于记录）
     */
    private fun <T, K> mergeEntries(
        remote: List<T>,
        local: List<T>,
        keySelector: (T) -> K,
        resolver: (T?, T?) -> T?,
        conflicts: MutableList<SyncConflict>
    ): List<T> {
        val localByKey = local.associateBy(keySelector)
        val remoteByKey = remote.associateBy(keySelector)
        val allKeys = (localByKey.keys + remoteByKey.keys).toSet()

        return allKeys.mapNotNull { key ->
            val localEntry = localByKey[key]
            val remoteEntry = remoteByKey[key]
            resolver(remoteEntry, localEntry)
        }
    }
}
