package com.cpu.seamlessloopmobile.data.sync

/**
 * 播放列表合并策略。
 * resolve 方法根据具体规则合并远程与本地歌单，返回合并结果。
 */
interface PlaylistMergePolicy {
    /**
     * 合并远程和本地歌单。
     * @param remote 从后端下载的歌单快照
     * @param local 本地的歌单数据
     * @return 合并后的歌单
     */
    fun resolve(remote: SyncPlaylist, local: SyncPlaylist): SyncPlaylist
}

/**
 * 循环点合并策略。
 */
interface LoopPointMergePolicy {
    /**
     * 合并远程和本地的循环点数据。
     * @param remote 后端的循环点（可能为 null）
     * @param local 本地的循环点（可能为 null）
     * @return 合并后的循环点，若双方均为 null 则返回 null
     */
    fun resolve(remote: SyncLoopPoint?, local: SyncLoopPoint?): SyncLoopPoint?
}

/**
 * 评分合并策略。
 */
interface RatingMergePolicy {
    /**
     * 合并远程和本地的评分数据。
     * @param remote 后端的评分（可能为 null）
     * @param local 本地的评分（可能为 null）
     * @return 合并后的评分，若双方均为 null 则返回 null
     */
    fun resolve(remote: SyncRating?, local: SyncRating?): SyncRating?
}

// ---------------------------------------------------------------------------
// 默认实现
// ---------------------------------------------------------------------------

/**
 * 默认播放列表合并策略：
 * - 歌单元数据采用 Last-Writer-Wins（根据 modifiedAt）
 * - 歌单内的歌曲条目按 song identity 去重合并，保持最新的 sortOrder
 */
object DefaultPlaylistMergePolicy : PlaylistMergePolicy {

    override fun resolve(remote: SyncPlaylist, local: SyncPlaylist): SyncPlaylist {
        val (winner, loser) = if (remote.modifiedAt >= local.modifiedAt) remote to local else local to remote
        val mergedItems = mergeItems(winner.items, loser.items)
        return winner.copy(items = mergedItems)
    }

    private fun mergeItems(
        base: List<SyncPlaylistItem>,
        other: List<SyncPlaylistItem>
    ): List<SyncPlaylistItem> {
        val result = base.toMutableList()
        val seenIdentities = base.map { it.song }.toMutableSet()
        for (item in other) {
            if (seenIdentities.add(item.song)) {
                result.add(item)
            }
        }
        return result.sortedBy { it.sortOrder }
    }
}

/**
 * 默认循环点合并策略：Last-Writer-Wins（根据 lastModified）。
 * - 如果只有一方有数据则采用该方数据
 * - 如果双方都有数据则采用 lastModified 更新的一方
 * - 如果双方都为 null 则返回 null
 */
object DefaultLoopPointMergePolicy : LoopPointMergePolicy {

    override fun resolve(remote: SyncLoopPoint?, local: SyncLoopPoint?): SyncLoopPoint? {
        if (remote == null) return local
        if (local == null) return remote
        return if (remote.lastModified >= local.lastModified) remote else local
    }
}

/**
 * 默认评分合并策略：Last-Writer-Wins（根据 lastModified）。
 * 规则与 DefaultLoopPointMergePolicy 一致。
 */
object DefaultRatingMergePolicy : RatingMergePolicy {

    override fun resolve(remote: SyncRating?, local: SyncRating?): SyncRating? {
        if (remote == null) return local
        if (local == null) return remote
        return if (remote.lastModified >= local.lastModified) remote else local
    }
}
