package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.model.Location
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.source.LocationsCacheSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

val Context.locationsCacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "locations_cache")

class LocationsRepository(
    private val cacheSource: LocationsCacheSource,
    private val preferencesRepository: PreferencesRepository
) {

    suspend fun getLocations(): List<Location> = withContext(Dispatchers.IO) {
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
                        getCachedOrComputeSize(type, directory)
                    } else {
                        0L
                    }
                )
            }
    }

    suspend fun refreshSizeCache() = withContext(Dispatchers.IO) {
        cacheSource.clearCache()
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

    private suspend fun getCachedOrComputeSize(type: LocationType, directory: File): Long {
        val cached = cacheSource.getCachedSize(type)
        if (cached.isValid && cached.size != null) {
            return cached.size
        }

        val size = calculateDirectorySize(directory)
        cacheSource.updateCache(type, size)
        return size
    }

    private fun calculateDirectorySize(directory: File): Long {
        return try {
            directory.walkTopDown()
                .filter { it.isFile }
                .take(MAX_FILES_TO_COUNT)
                .sumOf { it.length() }
        } catch (e: Exception) {
            0L
        }
    }

    companion object {
        private const val MAX_FILES_TO_COUNT = 10000
    }
}
