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
            val isValid = now - cachedTimestamp < CACHE_DURATION_MS

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
