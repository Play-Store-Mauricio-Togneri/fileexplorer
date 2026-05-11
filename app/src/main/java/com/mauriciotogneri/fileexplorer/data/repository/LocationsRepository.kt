package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.model.Location
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

val Context.locationsCacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "locations_cache")

class LocationsRepository(
    private val dataStore: DataStore<Preferences>,
    private val preferencesRepository: PreferencesRepository
) {

    suspend fun getLocations(): List<Location> = withContext(Dispatchers.IO) {
        val preferences = dataStore.data.first()
        val enabledLocations = preferencesRepository.enabledLocations.first()
        LocationType.entries
            .filter { isLocationAvailable(it) && it in enabledLocations }
            .map { type ->
                val path = getPathForType(type)
                val directory = File(path)
                Location(
                    type = type,
                    path = path,
                    totalSizeBytes = if (directory.exists() && directory.isDirectory) {
                        getCachedSize(type, directory, preferences)
                    } else {
                        0L
                    }
                )
            }
    }

    suspend fun refreshSizeCache() = withContext(Dispatchers.IO) {
        dataStore.edit { preferences ->
            LocationType.entries.forEach { type ->
                preferences.remove(longPreferencesKey("timestamp_${type.name}"))
            }
        }
    }

    private fun isLocationAvailable(type: LocationType): Boolean {
        return when (type) {
            LocationType.PODCASTS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            else -> true
        }
    }

    private fun getPathForType(type: LocationType): String {
        return when (type) {
            LocationType.DOWNLOADS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
            LocationType.IMAGES -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath
            LocationType.VIDEOS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath
            LocationType.AUDIO -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
            LocationType.DOCUMENTS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
            LocationType.CAMERA -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath
            LocationType.SCREENSHOTS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Screenshots"
            LocationType.PODCASTS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS).absolutePath
            } else {
                ""
            }
        }
    }

    private suspend fun getCachedSize(type: LocationType, directory: File, preferences: Preferences): Long {
        val sizeKey = longPreferencesKey("size_${type.name}")
        val timestampKey = longPreferencesKey("timestamp_${type.name}")

        val cachedTimestamp = preferences[timestampKey] ?: 0L
        val now = System.currentTimeMillis()

        if (now - cachedTimestamp < CACHE_DURATION_MS) {
            return preferences[sizeKey] ?: 0L
        }

        val size = calculateDirectorySize(directory)
        dataStore.edit { prefs ->
            prefs[sizeKey] = size
            prefs[timestampKey] = now
        }

        return size
    }

    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        try {
            directory.walkTopDown()
                .filter { it.isFile }
                .take(MAX_FILES_TO_COUNT)
                .forEach { size += it.length() }
        } catch (e: Exception) {
            // Ignore permission errors
        }
        return size
    }

    companion object {
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes
        private const val MAX_FILES_TO_COUNT = 10000
    }
}
