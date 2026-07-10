package com.cpu.seamlessloopmobile.data.stats

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * Persists [TrackStat] entries as a single JSON array file.
 *
 * All mutations are serialised through a [Mutex] and run on [Dispatchers.IO].
 *
 * @param jsonFile  The file that holds the JSON array of [TrackStat] objects.
 * @param gson      Gson instance for serialisation.
 * @param wallClockMillis Current wall-clock epoch milliseconds.
 * @param zoneId    Optional fixed zone used to assign deltas to local calendar days.
 * @param zoneIdProvider Supplies the current system zone when [zoneId] is not fixed.
 */
class ListenStatsRepository(
    private val jsonFile: File,
    private val gson: Gson = Gson(),
    private val wallClockMillis: () -> Long = { System.currentTimeMillis() },
    private val zoneId: ZoneId? = null,
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) {
    companion object {
        @Volatile
        private var instance: ListenStatsRepository? = null

        fun getInstance(context: Context): ListenStatsRepository {
            return instance ?: synchronized(this) {
                instance ?: ListenStatsRepository(
                    jsonFile = File(context.applicationContext.filesDir, "listen_stats.json")
                ).also { instance = it }
            }
        }
    }

    private val mutex = Mutex()
    private val _allStats = MutableStateFlow(loadFromDisk())
    private val _clearEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Observable snapshot of all tracked songs. UI sorts and filters externally. */
    val allStats: StateFlow<List<TrackStat>> = _allStats.asStateFlow()

    /** Emitted after statistics have been durably cleared from disk. */
    val clearEvents: SharedFlow<Unit> = _clearEvents

    // --- Public API ----------------------------------------------------------

    /**
     * Records (adds or merges) a listened-time delta for the song identified
     * by [stat]. If the song already exists (matched by [TrackStat.identityKey]),
     * the delta is accumulated into [TrackStat.totalListenMs] and the played-at
     * timestamps are updated. Otherwise a new entry is added.
     *
     * @param stat       A [TrackStat] built from the current [Song] + delta.
     * @param listenedMs The wall-clock milliseconds to add (>= 0).
     */
    suspend fun recordListenDeltaNow(stat: TrackStat, listenedMs: Long) {
        if (listenedMs <= 0L) return
        withContext(Dispatchers.IO) {
            val now = wallClockMillis()
            val dailyDeltas = splitDeltaAcrossLocalDates(
                end = Instant.ofEpochMilli(now),
                listenedMs = listenedMs,
                zone = zoneId ?: zoneIdProvider()
            )
            mutex.withLock {
                val current = _allStats.value.toMutableList()
                val idx = current.indexOfFirst { it.identityKey == stat.identityKey }
                if (idx >= 0) {
                    val existing = current[idx]
                    current[idx] = existing.copy(
                        songId = if (existing.songId == 0L && stat.songId > 0L) stat.songId else existing.songId,
                        displayName = stat.displayName.ifEmpty { existing.displayName },
                        artist = stat.artist.ifEmpty { existing.artist },
                        album = stat.album.ifEmpty { existing.album },
                        coverPath = stat.coverPath ?: existing.coverPath,
                        durationMs = if (stat.durationMs > 0L) stat.durationMs else existing.durationMs,
                        totalListenMs = existing.totalListenMs.coerceAtLeast(0L).saturatingAdd(listenedMs),
                        dailyListenMs = existing.dailyListenMs.withAddedDeltas(dailyDeltas),
                        lastPlayedAt = now,
                        firstPlayedAt = if (existing.firstPlayedAt == 0L) now else existing.firstPlayedAt,
                        filePath = stat.filePath.ifEmpty { existing.filePath },
                        fileName = stat.fileName.ifEmpty { existing.fileName }
                    )
                } else {
                    current.add(
                        stat.copy(
                            totalListenMs = listenedMs,
                            dailyListenMs = dailyDeltas,
                            lastPlayedAt = now,
                            firstPlayedAt = now
                        )
                    )
                }
                _allStats.value = current
                saveToDisk(current)
            }
        }
    }

    /** Removes all tracked stats and empties the JSON file. */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                saveToDiskOrThrow(emptyList())
                _allStats.value = emptyList()
                _clearEvents.tryEmit(Unit)
            }
        }
    }

    /**
     * Convenience accessor for querying a single song's stats by identity key.
     * Returns null if the song has never been tracked.
     */
    suspend fun getByIdentityKey(identityKey: String): TrackStat? {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                _allStats.value.find { it.identityKey == identityKey }
            }
        }
    }

    // --- Internal IO ----------------------------------------------------------

    private fun loadFromDisk(): List<TrackStat> {
        if (!jsonFile.exists() || jsonFile.length() == 0L) {
            return emptyList()
        }
        val type = object : TypeToken<List<TrackStat>>() {}.type
        return try {
            (gson.fromJson<List<TrackStat>>(jsonFile.readText(), type) ?: emptyList()).map { stat ->
                // Gson may deserialize an absent or explicit JSON null into this non-null Kotlin field.
                stat.copy(dailyListenMs = stat.dailyListenMs ?: emptyMap())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveToDisk(stats: List<TrackStat>) {
        try {
            saveToDiskOrThrow(stats)
        } catch (e: Exception) {
            // Swallow IO errors — stats are best-effort
        }
    }

    private fun saveToDiskOrThrow(stats: List<TrackStat>) {
        jsonFile.parentFile?.mkdirs()
        jsonFile.writeText(gson.toJson(stats))
    }

    private fun splitDeltaAcrossLocalDates(
        end: Instant,
        listenedMs: Long,
        zone: ZoneId
    ): Map<String, Long> {
        val start = end.minusMillis(listenedMs)
        val deltas = mutableMapOf<String, Long>()
        var cursor = start
        while (cursor < end) {
            val date = cursor.atZone(zone).toLocalDate()
            val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant()
            val sliceEnd = if (nextMidnight < end) nextMidnight else end
            val sliceMs = java.time.Duration.between(cursor, sliceEnd).toMillis()
            deltas[date.toString()] = (deltas[date.toString()] ?: 0L).saturatingAdd(sliceMs)
            cursor = sliceEnd
        }
        return deltas
    }

    private fun Map<String, Long>.withAddedDeltas(deltas: Map<String, Long>): Map<String, Long> {
        return toMutableMap().apply {
            deltas.forEach { (dateKey, delta) ->
                this[dateKey] = (this[dateKey] ?: 0L).coerceAtLeast(0L).saturatingAdd(delta)
            }
        }
    }

    private fun Long.saturatingAdd(other: Long): Long {
        return if (this > Long.MAX_VALUE - other) Long.MAX_VALUE else this + other
    }
}
