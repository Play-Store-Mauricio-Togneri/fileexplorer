package com.mauriciotogneri.fileexplorer.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.mauriciotogneri.fileexplorer.data.model.LocationType

class DataStoreLocationsCacheSource(
    private val dataStore: DataStore<Preferences>
) : LocationsCacheSource {

    override suspend fun getCachedSize(type: LocationType): CachedSizeResult {
        val sizeKey = sizeKey(type)
        val timestampKey = timestampKey(type)

        return dataStore.readSafely("read_locations_cache", CachedSizeResult(size = null, isValid = false)) { preferences ->
            val cachedTimestamp = preferences[timestampKey] ?: 0L
            val now = System.currentTimeMillis()
            // A negative age means the device clock moved backwards since the write (NTP
            // correction, manual change, a bad RTC across a reboot). Reject it instead of letting
            // it read as fresh: `now - cachedTimestamp < CACHE_DURATION_MS` is also true for every
            // negative value, which would pin the cached size as valid until wall-clock caught up,
            // and this TTL is the only thing that invalidates an entry.
            val age = now - cachedTimestamp
            val isValid = age in 0 until CACHE_DURATION_MS

            CachedSizeResult(
                size = preferences[sizeKey],
                isValid = isValid
            )
        }
    }

    // Falls back to FIRST_GENERATION when the store cannot be read. That errs the safe way: if the
    // store really holds a later generation, the write guarded by this value is discarded rather
    // than allowed through, costing one recomputed pass instead of persisting a stale size.
    override suspend fun generation(): Long =
        dataStore.readSafely("read_locations_generation", FIRST_GENERATION) { preferences ->
            preferences[GENERATION_KEY] ?: FIRST_GENERATION
        }

    // Returns without touching the store when there is nothing to write, so a load that hit the
    // cache for every location still costs no disk write at all.
    override suspend fun updateCache(sizes: Map<LocationType, Long>, generation: Long) {
        if (sizes.isEmpty()) return

        // One timestamp for the batch: these sizes were all measured in the same pass, so stamping
        // them together also expires them together.
        val now = System.currentTimeMillis()

        dataStore.editSafely("write_locations_cache") { preferences ->
            // Compared inside the transform, which DataStore runs holding the store's write lock,
            // so a clearCache() cannot land between this check and the writes below. A mismatch
            // means the caller measured these sizes before a mutation invalidated the cache: they
            // describe the tree as it was, and stamping them would hide that change for a full TTL.
            if ((preferences[GENERATION_KEY] ?: FIRST_GENERATION) != generation) {
                return@editSafely
            }

            sizes.forEach { (type, size) ->
                preferences[sizeKey(type)] = size
                preferences[timestampKey(type)] = now
            }
        }
    }

    override suspend fun clearCache() {
        dataStore.editSafely("clear_locations_cache") { preferences ->
            LocationType.entries.forEach { type ->
                preferences.remove(timestampKey(type))
            }
            // Bumped in the same write that clears, so a pass that is already measuring cannot
            // write back what this just invalidated.
            //
            // Unconditional, which costs a real flush on every clear: DataStore skips writing when
            // a transform leaves the contents unchanged, so clearing an already-clear store used to
            // be free and now never is. Bumping only when a timestamp was actually removed would
            // restore that, but it would also let a pass whose entries had already expired write
            // its pre-mutation sizes straight back — the exact hole this key exists to close. One
            // small flush per file operation is the cheaper side of that trade, and it replaces up
            // to LocationType.entries.size flushes on every home load.
            preferences[GENERATION_KEY] = (preferences[GENERATION_KEY] ?: FIRST_GENERATION) + 1
        }
    }

    private fun sizeKey(type: LocationType) = longPreferencesKey("size_${type.name}")

    private fun timestampKey(type: LocationType) = longPreferencesKey("timestamp_${type.name}")

    companion object {
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes
        private const val FIRST_GENERATION = 0L
        private val GENERATION_KEY = longPreferencesKey("generation")
    }
}
