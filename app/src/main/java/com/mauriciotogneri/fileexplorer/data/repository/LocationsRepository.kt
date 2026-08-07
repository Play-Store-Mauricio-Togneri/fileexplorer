package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.model.Location
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.source.LocationsCacheSource
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

val Context.locationsCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "locations_cache",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class LocationsRepository(
    private val cacheSource: LocationsCacheSource,
    private val preferencesRepository: PreferencesRepository
) {

    // Each surviving type's path is resolved once and carried through, rather than being looked up
    // to test the folder and again to build the Location. getPathForType goes through
    // Environment.getExternalStoragePublicDirectory, which the framework does not cache: every call
    // is a StorageManager.getVolumeList() binder round trip to system_server, so the old shape paid
    // two per location on a path that runs every time the home screen is shown.
    suspend fun getLocations(): List<Location> = withContext(Dispatchers.IO) {
        val enabledLocations = preferencesRepository.enabledLocations.first()
        val computedSizes = mutableMapOf<LocationType, Long>()

        // Captured before a single tree is walked, so that a clearCache() landing mid-pass — which
        // is what FileRepository does once a mutation finishes — makes updateCache drop everything
        // this pass measured rather than stamp pre-mutation totals fresh for the rest of the TTL.
        //
        // The two halves are what make that sound: FileRepository invalidates only after the tree
        // has stopped changing, so a pass starting after the clear sees the mutated tree, and this
        // generation catches the pass that was already measuring when the clear landed.
        val generation = cacheSource.generation()

        val locations = LocationType.entries
            .filter { type -> isLocationAvailable(type) && type in enabledLocations }
            .map { type -> type to getPathForType(type) }
            .filter { (_, path) -> isExistingDirectory(path) }
            .map { (type, path) ->
                val cached = cachedSize(type)
                val size = cached ?: calculateDirectorySize(File(path), excludedSubtreeFor(type))

                // Only misses are recorded: re-stamping a hit would push its TTL out on every load
                // and a size would never expire.
                if (cached == null) {
                    computedSizes[type] = size
                }

                Location(type = type, path = path, totalSizeBytes = size)
            }

        // One write for the whole pass. Every write flushes the store to disk, so updating per
        // location cost up to LocationType.entries.size flushes on a single home load — all of them
        // on the resume path, competing for the disk with whatever else is being written just then.
        //
        // Batching means a cancelled pass persists nothing rather than keeping the locations it had
        // already measured. Accepted: the only caller (HomeViewModel.loadData) defers an overlapping
        // load instead of cancelling one, so this costs a re-walk only when the ViewModel is cleared
        // mid-pass.
        cacheSource.updateCache(computedSizes, generation)

        locations
    }

    suspend fun getAvailableLocationTypes(): List<LocationType> = withContext(Dispatchers.IO) {
        LocationType.entries.filter { type ->
            isLocationAvailable(type) && folderExists(type)
        }
    }

    private fun folderExists(type: LocationType): Boolean = isExistingDirectory(getPathForType(type))

    private fun isExistingDirectory(path: String): Boolean {
        val directory = File(path)
        return directory.exists() && directory.isDirectory
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

    // Within a pass, the cache's TTL is what invalidates a size. The home screen no longer clears
    // the whole cache immediately before calling getLocations(), which guaranteed a miss and made
    // every load walk each location's whole tree (up to MAX_FILES_TO_COUNT stats apiece) on the
    // resume path; FileRepository still clears it on mutation, which getLocations guards against.
    // The trade is that a size can read up to CACHE_DURATION_MS stale after the user adds or
    // deletes something large.
    private suspend fun cachedSize(type: LocationType): Long? {
        val cached = cacheSource.getCachedSize(type)
        return if (cached.isValid) cached.size else null
    }

    // SCREENSHOTS resolves to a subdirectory of the IMAGES tree, so walking IMAGES also counts
    // every screenshot: the two cards each report those bytes and together over-report what is on
    // disk. Excluding the subtree attributes each byte to exactly one location.
    //
    // Unconditional, rather than only when the Screenshots card is actually shown: the size cache
    // is keyed by LocationType alone, so making IMAGES depend on the enabled-locations preference
    // would serve a stale size for up to the cache TTL after that setting was toggled. Two costs
    // follow: with Screenshots hidden its bytes are counted under no location at all, and the
    // Images total no longer matches the folder that card opens (nor the figure the item-info
    // screen reports for Pictures, which walks the tree whole).
    private fun excludedSubtreeFor(type: LocationType): File? = when (type) {
        LocationType.IMAGES -> File(getPathForType(LocationType.SCREENSHOTS))
        else -> null
    }

    // excludedSubtree has no default: it is the only thing keeping the two overlapping locations
    // from counting the same bytes twice, and a default would let the single production call site
    // drop it without a compile error.
    @VisibleForTesting
    fun calculateDirectorySize(directory: File, excludedSubtree: File?): Long {
        return try {
            directory.walkTopDown()
                // Matched case-insensitively because emulated external storage is. The platform
                // creates "Screenshots", but a restored backup or a third-party capture app can
                // leave "screenshots" on disk — which folderExists() still resolves, so an exact
                // match would show that card and silently count its bytes under Images as well,
                // reinstating the double count in precisely the case this exists to prevent.
                .onEnter { !it.path.equals(excludedSubtree?.path, ignoreCase = true) }
                .filter { it.isFile }
                .take(MAX_FILES_TO_COUNT)
                .sumOf { it.length() }
        } catch (e: Exception) {
            // Reported rather than swallowed: the 0 this returns is cached for the full TTL, so a
            // transient failure would otherwise show as a silent, sticky "0 B".
            ErrorReporter.error(e, "calculate_directory_size")
            0L
        }
    }

    companion object {
        private const val MAX_FILES_TO_COUNT = 10000
    }
}
