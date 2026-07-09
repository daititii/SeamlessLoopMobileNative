package com.cpu.seamlessloopmobile.data.sync

/**
 * Stable sync identity for cross-device merge de-duplication.
 *
 * `totalSamples` can differ slightly between Android/native decoders and the
 * desktop importer for the same file, so it must remain an auxiliary matching
 * field rather than part of the primary sync key.
 */
internal data class SyncSongStableKey(
    val fileNameKey: String,
    val durationMs: Long
)

internal fun SyncSongIdentity.stableKey(): SyncSongStableKey =
    SyncSongStableKey(
        fileNameKey = fileName.lowercase(),
        durationMs = durationMs
    )

internal fun areSameSongIdentity(
    first: SyncSongIdentity,
    second: SyncSongIdentity
): Boolean = first.stableKey() == second.stableKey()

internal fun preferStableSongIdentity(
    remote: SyncSongIdentity?,
    local: SyncSongIdentity?
): SyncSongIdentity? = remote ?: local
