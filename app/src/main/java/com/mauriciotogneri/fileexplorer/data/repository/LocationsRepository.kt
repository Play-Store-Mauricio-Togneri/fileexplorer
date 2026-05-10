package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import com.mauriciotogneri.fileexplorer.data.model.Location
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocationsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun getLocations(): List<Location> = withContext(Dispatchers.IO) {
        LocationType.entries
            .filter { isLocationAvailable(it) }
            .map { type ->
                val path = getPathForType(type)
                val directory = File(path)
                Location(
                    type = type,
                    path = path,
                    totalSizeBytes = if (directory.exists() && directory.isDirectory) {
                        getCachedSize(type, directory)
                    } else {
                        0L
                    }
                )
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

    private fun getCachedSize(type: LocationType, directory: File): Long {
        val cacheKey = "${KEY_SIZE_PREFIX}${type.name}"
        val timestampKey = "${KEY_TIMESTAMP_PREFIX}${type.name}"

        val cachedTimestamp = prefs.getLong(timestampKey, 0L)
        val now = System.currentTimeMillis()

        // Use cached value if less than CACHE_DURATION_MS old
        if (now - cachedTimestamp < CACHE_DURATION_MS) {
            return prefs.getLong(cacheKey, 0L)
        }

        // Calculate new size and cache it
        val size = calculateDirectorySize(directory)
        prefs.edit()
            .putLong(cacheKey, size)
            .putLong(timestampKey, now)
            .apply()

        return size
    }

    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        try {
            directory.walkTopDown()
                .filter { it.isFile }
                .take(MAX_FILES_TO_COUNT) // Limit to prevent long calculations
                .forEach { size += it.length() }
        } catch (e: Exception) {
            // Ignore permission errors
        }
        return size
    }

    fun refreshSizeCache() {
        // Clear timestamp cache to force recalculation
        val editor = prefs.edit()
        LocationType.entries.forEach { type ->
            editor.remove("${KEY_TIMESTAMP_PREFIX}${type.name}")
        }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "locations_cache"
        private const val KEY_SIZE_PREFIX = "size_"
        private const val KEY_TIMESTAMP_PREFIX = "timestamp_"
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes
        private const val MAX_FILES_TO_COUNT = 10000 // Limit file count to prevent slow calculations
    }
}
