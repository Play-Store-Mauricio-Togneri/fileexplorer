package com.mauriciotogneri.fileexplorer.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.mauriciotogneri.fileexplorer.data.model.LocationType

class DataStoreLocationsCacheSource(
    private val dataStore: DataStore<Preferences>
) : LocationsCacheSource {

    override suspend fun getCachedSize(type: LocationType): CachedSizeResult {
        val sizeKey = longPreferencesKey("size_${type.name}")
        val timestampKey = longPreferencesKey("timestamp_${type.name}")

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

    override suspend fun updateCache(type: LocationType, size: Long) {
        val sizeKey = longPreferencesKey("size_${type.name}")
        val timestampKey = longPreferencesKey("timestamp_${type.name}")

        dataStore.editSafely("write_locations_cache") { preferences ->
            preferences[sizeKey] = size
            preferences[timestampKey] = System.currentTimeMillis()
        }
    }

    override suspend fun clearCache() {
        dataStore.editSafely("clear_locations_cache") { preferences ->
            LocationType.entries.forEach { type ->
                preferences.remove(longPreferencesKey("timestamp_${type.name}"))
            }
        }
    }

    companion object {
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    }
}
