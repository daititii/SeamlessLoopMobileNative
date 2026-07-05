package com.cpu.seamlessloopmobile.data.stats

/**
 * Immutable snapshot of a single song's listening statistics.
 *
 * This is persisted outside Room as JSON and is rebuilt from [Song] data
 * on first encounter.
 *
 * @property songId       Room SongEntity id (0 if unknown).
 * @property displayName  Human-readable song title.
 * @property fileName     Raw file name.
 * @property artist       Artist display name.
 * @property album        Album display name.
 * @property coverPath    Optional cover art path.
 * @property durationMs   Song duration in milliseconds.
 * @property totalListenMs Cumulative wall-clock milliseconds spent listening.
 * @property lastPlayedAt Epoch millis of the most recent listen session end.
 * @property firstPlayedAt Epoch millis of the first-ever listen session end.
 * @property filePath     Absolute file path on disk.
 * @property identityKey  Stable key for matching across rescans.
 *                        `"$fileName|$durationMs"` when [durationMs] > 0,
 *                        otherwise falls back to [filePath].
 */
data class TrackStat(
    val songId: Long = 0L,
    val displayName: String = "",
    val fileName: String = "",
    val artist: String = "",
    val album: String = "",
    val coverPath: String? = null,
    val durationMs: Long = 0L,
    val totalListenMs: Long = 0L,
    val lastPlayedAt: Long = 0L,
    val firstPlayedAt: Long = 0L,
    val filePath: String = "",
    val identityKey: String = ""
) {
    companion object {
        /**
         * Builds a stable identity key for [fileName] and [durationMs].
         * When duration is known (> 0) the key is `"$fileName|$durationMs"`,
         * which survives rescans as long as the file's name and length stay
         * the same. Falls back to [filePath] when duration is unavailable.
         */
        fun identityKey(fileName: String, durationMs: Long, filePath: String): String {
            return if (durationMs > 0L) "$fileName|$durationMs" else filePath
        }
    }
}
