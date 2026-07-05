package com.cpu.seamlessloopmobile.data.stats

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Persists [TrackStat] entries as a single JSON array file.
 *
 * All mutations are serialised through a [Mutex] and run on [Dispatchers.IO].
 *
 * @param jsonFile  The file that holds the JSON array of [TrackStat] objects.
 * @param gson      Gson instance for serialisation.
 */
class ListenStatsRepository(
    private val jsonFile: File,
    private val gson: Gson = Gson()
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

    /** Observable snapshot of all tracked songs. UI sorts and filters externally. */
    val allStats: StateFlow<List<TrackStat>> = _allStats.asStateFlow()

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
            val now = System.currentTimeMillis()
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
                        totalListenMs = existing.totalListenMs + listenedMs,
                        lastPlayedAt = now,
                        firstPlayedAt = if (existing.firstPlayedAt == 0L) now else existing.firstPlayedAt,
                        filePath = stat.filePath.ifEmpty { existing.filePath },
                        fileName = stat.fileName.ifEmpty { existing.fileName }
                    )
                } else {
                    current.add(
                        stat.copy(
                            totalListenMs = listenedMs,
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
                _allStats.value = emptyList()
                saveToDisk(emptyList())
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
            gson.fromJson(jsonFile.readText(), type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveToDisk(stats: List<TrackStat>) {
        try {
            jsonFile.parentFile?.mkdirs()
            jsonFile.writeText(gson.toJson(stats))
        } catch (e: Exception) {
            // Swallow IO errors — stats are best-effort
        }
    }
}
