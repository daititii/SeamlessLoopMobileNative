package com.cpu.seamlessloopmobile.data.sync.room

import androidx.room.withTransaction
import com.cpu.seamlessloopmobile.db.AppDatabase
import com.cpu.seamlessloopmobile.model.Playlist
import com.cpu.seamlessloopmobile.model.PlaylistDao
import com.cpu.seamlessloopmobile.model.Song
import com.cpu.seamlessloopmobile.model.SongDao
import com.cpu.seamlessloopmobile.model.LoopPoint
import com.cpu.seamlessloopmobile.model.UserRating
import com.cpu.seamlessloopmobile.data.sync.SyncConflict
import com.cpu.seamlessloopmobile.data.sync.SyncLoopPoint
import com.cpu.seamlessloopmobile.data.sync.SyncLoopPointEntry
import com.cpu.seamlessloopmobile.data.sync.SyncPlaylist
import com.cpu.seamlessloopmobile.data.sync.SyncPlaylistItem
import com.cpu.seamlessloopmobile.data.sync.SyncRating
import com.cpu.seamlessloopmobile.data.sync.SyncRatingEntry
import com.cpu.seamlessloopmobile.data.sync.SyncReport
import com.cpu.seamlessloopmobile.data.sync.SyncSnapshot
import com.cpu.seamlessloopmobile.data.sync.SyncSongIdentity
import com.cpu.seamlessloopmobile.data.sync.SyncSnapshotStore
import com.cpu.seamlessloopmobile.data.sync.stableKey
import kotlin.math.abs

/**
 * Room 数据库驱动的 [SyncSnapshotStore] 实现。
 *
 * 导出时从 Room 读取播放列表、循环点、评分数据；
 * 应用时将合并后的快照写回 Room。
 */
class RoomSyncSnapshotStore(
    private val database: AppDatabase,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val playlistIdMapper: PlaylistIdMapper
) : SyncSnapshotStore {

    // ===================================================================
    // exportSnapshot
    // ===================================================================

    override suspend fun exportSnapshot(
        deviceId: String,
        now: Long
    ): SyncSnapshot {
        val allSongs = songDao.getAllSongs() // IsAbPartB = 0

        // 循环点：仅导出有实质内容的（非 0,0）
        val loopPoints = allSongs
            .filter { it.loopStart != 0L || it.loopEnd != 0L }
            .map { song ->
                SyncLoopPointEntry(
                    song = identityFromSong(song),
                    loopPoint = SyncLoopPoint(
                        loopStart = song.loopStart,
                        loopEnd = song.loopEnd,
                        lastModified = now // 替代值：目前 LoopPoint 表无 lastModified
                    )
                )
            }

        // 评分：仅导出非零评分
        val ratings = allSongs
            .filter { it.rating != 0 }
            .map { song ->
                SyncRatingEntry(
                    song = identityFromSong(song),
                    rating = SyncRating(
                        rating = song.rating,
                        lastModified = song.userRating?.lastModified ?: now
                    )
                )
            }

        // 播放列表
        val playlistWithCounts = playlistDao.getPlaylistsWithCounts()
        val playlists = playlistWithCounts.map { pc ->
            val playlist = pc.playlist
            val songs = playlistDao.getSongsInPlaylist(playlist.id)
            val items = songs.mapIndexed { index, song ->
                SyncPlaylistItem(
                    song = identityFromSong(song),
                    sortOrder = index
                )
            }
            val fingerprint = computePlaylistFingerprint(playlist.name, items)
            val record = playlistIdMapper.getOrCreateSyncIdForExport(
                localId = playlist.id,
                fingerprint = fingerprint,
                now = now
            )
            SyncPlaylist(
                id = record.syncId,
                name = playlist.name,
                createdAt = playlist.createdAt,
                modifiedAt = record.modifiedAt,
                items = items
            )
        }

        return SyncSnapshot(
            deviceId = deviceId,
            exportedAt = now,
            playlists = playlists,
            loopPoints = loopPoints,
            ratings = ratings
        )
    }

    // ===================================================================
    // applySnapshot
    // ===================================================================

    override suspend fun applySnapshot(snapshot: SyncSnapshot): SyncReport {
        return database.withTransaction {
            applySnapshotInternal(snapshot)
        }
    }

    private suspend fun applySnapshotInternal(snapshot: SyncSnapshot): SyncReport {
        val conflicts = mutableListOf<SyncConflict>()
        val allLocalSongs = songDao.getAllSongs() // IsAbPartB = 0
        val matcher = SongMatcher(allLocalSongs)

        // ---- 循环点 ----
        var loopDownloaded = 0
        for (entry in snapshot.loopPoints) {
            val localSong = matcher.match(entry.song)
            if (localSong == null) {
                conflicts.add(SyncConflict(
                    songIdentity = entry.song,
                    field = "loopPoint",
                    remoteValue = "(${entry.loopPoint.loopStart}, ${entry.loopPoint.loopEnd})",
                    localValue = null,
                    resolution = "skipped-unmatched"
                ))
                continue
            }

            // 仅当远程有实质内容时才应用
            if (!entry.loopPoint.isSubstantive) continue

            val existingLoop = songDao.getLoopPointBySongId(localSong.id)
            if (existingLoop?.loopStart == entry.loopPoint.loopStart &&
                existingLoop.loopEnd == entry.loopPoint.loopEnd
            ) {
                continue
            }

            // 远程是实质内容时才覆盖本地；未设置值在上方直接跳过。
            songDao.insertLoopPoint(LoopPoint(
                songId = localSong.id,
                loopStart = entry.loopPoint.loopStart,
                loopEnd = entry.loopPoint.loopEnd
            ))
            loopDownloaded++
        }

        // ---- 评分 ----
        var ratingDownloaded = 0
        for (entry in snapshot.ratings) {
            val localSong = matcher.match(entry.song)
            if (localSong == null) {
                conflicts.add(SyncConflict(
                    songIdentity = entry.song,
                    field = "rating",
                    remoteValue = "${entry.rating.rating}",
                    localValue = null,
                    resolution = "skipped-unmatched"
                ))
                continue
            }

            // 评分 0 是未设置 → 跳过
            if (entry.rating.rating == 0) continue

            val existingRating = songDao.getUserRatingBySongId(localSong.id)

            // 仅当远程评分更新或等新时才覆盖
            if (existingRating != null &&
                existingRating.rating != 0 &&
                existingRating.lastModified > entry.rating.lastModified
            ) {
                // 本地评分更新且非零 → 保留本地
                continue
            }

            songDao.insertUserRating(UserRating(
                songId = localSong.id,
                rating = entry.rating.rating,
                lastModified = entry.rating.lastModified
            ))
            ratingDownloaded++
        }

        // ---- 播放列表 ----
        val allLocalPlaylists = playlistDao.getPlaylistsWithCounts()
        val validLocalIds = allLocalPlaylists.map { it.playlist.id }.toSet()
        playlistIdMapper.removeStaleMappings(validLocalIds)

        var playlistDownloaded = 0

        for (playlist in snapshot.playlists) {
            // 解析本地歌单 ID
            val resolvedId = resolvePlaylistId(playlist)

            if (resolvedId == null) {
                conflicts.add(SyncConflict(
                    playlistName = playlist.name,
                    resolution = "skipped-unmatched-local-playlist"
                ))
                continue
            }

            // 更新歌单名称
            val existingPlaylist = playlistDao.getPlaylistById(resolvedId)
            if (existingPlaylist != null && existingPlaylist.name != playlist.name) {
                playlistDao.updatePlaylist(existingPlaylist.copy(name = playlist.name))
            }

            // 匹配歌曲
            val orderedItems = playlist.items.sortedWith(
                compareBy<SyncPlaylistItem> { it.sortOrder }
                    .thenBy { it.song.fileName.lowercase() }
                    .thenBy { it.song.durationMs }
            )
            val matchedSongIds = orderedItems.distinctBy { it.song.stableKey() }.mapNotNull { item ->
                val song = matcher.match(item.song)
                if (song == null) {
                    conflicts.add(SyncConflict(
                        songIdentity = item.song,
                        field = "playlistItem",
                        playlistName = playlist.name,
                        resolution = "skipped-unmatched"
                    ))
                    null
                } else {
                    song.id
                }
            }

            // 清空并同步
            playlistDao.clearAndSyncPlaylist(resolvedId, matchedSongIds)
            playlistDownloaded++

            // 保存或更新映射
            val fingerprint = computePlaylistFingerprint(playlist.name, playlist.items)
            playlistIdMapper.saveMapping(
                syncId = playlist.id,
                localId = resolvedId,
                modifiedAt = playlist.modifiedAt,
                fingerprint = fingerprint
            )
        }
        return SyncReport(
            playlistsDownloaded = playlistDownloaded,
            loopPointsDownloaded = loopDownloaded,
            ratingsDownloaded = ratingDownloaded,
            conflicts = conflicts
        )
    }

    // ===================================================================
    // 解析播放列表
    // ===================================================================

    /**
     * 解析远程歌单对应的本地歌单 ID。
     * 1. 通过映射查找
     * 2. 如果映射失效（已删除），按名称匹配
     * 3. 如果仍找不到，创建新歌单
     */
    private suspend fun resolvePlaylistId(playlist: SyncPlaylist): Int? {
        // 步骤 1：通过 syncId 查找映射
        val mappedLocalId = playlistIdMapper.findLocalId(playlist.id)
        if (mappedLocalId != null) {
            val existing = playlistDao.getPlaylistById(mappedLocalId)
            if (existing != null) return existing.id
        }

        // 步骤 2：按名称匹配
        val byName = playlistDao.getPlaylistByName(playlist.name)
        if (byName != null) return byName.id

        // 步骤 3：创建新歌单
        val newId = playlistDao.insertPlaylist(Playlist(
            name = playlist.name,
            createdAt = playlist.createdAt
        ))
        return newId.toInt()
    }

    // ===================================================================
    // 歌曲身份匹配
    // ===================================================================

    /**
     * 本地歌曲匹配器。
     * 对传入的 [SyncSongIdentity] 执行多级匹配，返回匹配的本地 [Song] 或 null。
     */
    private class SongMatcher(allLocalSongs: List<Song>) {
        /** fileName (小写) → 候选列表 */
        private val byName: Map<String, List<Song>> =
            allLocalSongs.groupBy { it.fileName.lowercase() }

        /** fileName+duration → candidates. Only unique matches are accepted. */
        private val byFingerprint: Map<Pair<String, Long>, List<Song>> =
            allLocalSongs.groupBy { it.fileName.lowercase() to it.duration }

        /** fileName+totalSamples → candidates (仅 non-zero samples). Only unique matches are accepted. */
        private val bySamples: Map<Pair<String, Long>, List<Song>> =
            allLocalSongs
                .filter { it.totalSamples != 0L }
                .groupBy { it.fileName.lowercase() to it.totalSamples }

        /**
         * 执行多级匹配。
         * 顺序：精确 fileName+duration → 精确 fileName+totalSamples →
         *       fileName+totalSamples ±10000 → fileName+duration ±200ms → 唯一同名。
         */
        fun match(identity: SyncSongIdentity): Song? {
            val key = identity.fileName.lowercase()

            // 1. 精确 fileName + duration
            byFingerprint[key to identity.durationMs]
                ?.takeIf { it.size == 1 }
                ?.first()
                ?.let { return it }

            // 2. 精确 fileName + totalSamples
            identity.totalSamples?.let { samples ->
                bySamples[key to samples]
                    ?.takeIf { it.size == 1 }
                    ?.first()
                    ?.let { return it }
            }

            // 3. fileName + totalSamples ±10000
            val candidates = byName[key].orEmpty()
            identity.totalSamples?.let { samples ->
                val sampleToleranceMatches = candidates.filter {
                    it.totalSamples > 0L && abs(it.totalSamples - samples) <= TOTAL_SAMPLES_TOLERANCE
                }
                if (sampleToleranceMatches.size == 1) return sampleToleranceMatches.first()
            }

            // 4. fileName + duration ±200ms
            if (candidates.isNotEmpty()) {
                val toleranceMatches = candidates.filter {
                    abs(it.duration - identity.durationMs) <= 200L
                }
                if (toleranceMatches.size == 1) return toleranceMatches.first()
            }

            // 5. 唯一同名（只有一个候选）
            if (candidates.size == 1) return candidates.first()

            return null
        }

        companion object {
            private const val TOTAL_SAMPLES_TOLERANCE = 10_000L
        }
    }

    // ===================================================================
    // 工具方法
    // ===================================================================

    private fun identityFromSong(song: Song): SyncSongIdentity =
        SyncSongIdentity(
            fileName = song.fileName,
            durationMs = song.duration,
            totalSamples = song.totalSamples.takeIf { it != 0L }
        )

    /**
     * 计算歌单内容指纹。
     * 格式：name|file1,dur1;file2,dur2;...
     */
    private fun computePlaylistFingerprint(
        name: String,
        items: List<SyncPlaylistItem>
    ): String {
        val itemsPart = items
            .distinctBy { it.song.stableKey() }
            .sortedWith(
                compareBy<SyncPlaylistItem> { it.sortOrder }
                    .thenBy { it.song.fileName.lowercase() }
                    .thenBy { it.song.durationMs }
            )
            .joinToString(";") { item ->
                "${item.sortOrder},${item.song.fileName},${item.song.durationMs}"
        }
        return "$name|$itemsPart"
    }
}
