package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.model.Location
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.source.LocationsCacheSource
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.scrubbed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

val Context.locationsCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "locations_cache",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class LocationsRepository(
    private val cacheSource: LocationsCacheSource,
    private val preferencesRepository: PreferencesRepository,
    // Times each pass for the location_sizes_measured event. Monotonic so a wall-clock correction
    // cannot land a negative duration, and a parameter because the unit-test android.jar answers
    // SystemClock with a stub that throws.
    private val elapsedMillis: () -> Long = SystemClock::elapsedRealtime
) {

    // Set from whichever thread observes the change and read by the pass, which runs on
    // Dispatchers.IO. getAndSet is what makes "take the mark and act on it" one step, so a single
    // mark is taken by exactly one pass rather than by every pass that overlaps it. The mark is
    // spent only by a clear that landed: a pass whose clear the store swallowed puts it back, so
    // the pass after it takes the mark and tries again.
    private val sizeCacheStale = AtomicBoolean(false)

    /**
     * Records that shared storage changed outside any operation this app performed — a camera shot,
     * a completed download, a delete in another file manager — so the next [getLocations] measures
     * the tree again instead of serving sizes taken before it.
     *
     * Marks rather than clears, and the mark is consumed at the head of the next pass. Clearing on
     * the spot would be a store write per notification, which another app on the device controls
     * the rate of, and one landing mid-pass would throw away every tree that pass had walked.
     * Nothing reads these sizes except [getLocations], so a mark carries exactly as much freshness.
     */
    fun markSizeCacheStale() {
        sizeCacheStale.set(true)
    }

    // Each surviving type's path is resolved once and carried through, rather than being looked up
    // to test the folder and again to build the Location. getPathForType goes through
    // Environment.getExternalStoragePublicDirectory, which the framework does not cache: every call
    // is a StorageManager.getVolumeList() binder round trip to system_server, so the old shape paid
    // two per location on a path that runs every time the home screen is shown.
    suspend fun getLocations(): List<Location> = withContext(Dispatchers.IO) {
        val startedAt = elapsedMillis()

        // Applied here, at the head of the pass, rather than when the notification arrived: a clear
        // moves the generation, so one landing mid-pass would make updateCache discard every tree
        // this pass had just walked, and the load after it would walk them all again. Clearing
        // first costs the same freshness and lets the pass keep what it measures.
        if (sizeCacheStale.getAndSet(false) && !cacheSource.clearCache()) {
            // The clear was swallowed: it routes through editSafely, which absorbs an IOException
            // and returns normally. Nothing was invalidated, so put the mark back rather than let
            // the pass consume it — otherwise the change that set it never reaches the cards and
            // they report pre-change totals for the rest of the TTL. Re-setting cannot lose a mark
            // that arrived meanwhile: both writes set the same value.
            sizeCacheStale.set(true)
        }

        val enabledLocations = preferencesRepository.enabledLocations.first()
        val computedSizes = mutableMapOf<LocationType, Long>()
        var fileCount = 0
        var cappedCount = 0
        var hadPlaceholder = false

        // Captured before a single tree is walked, so that a clearCache() landing mid-pass — which
        // is what FileRepository does once a mutation finishes — makes updateCache drop everything
        // this pass measured rather than stamp pre-mutation totals fresh for the rest of the TTL.
        //
        // The two halves are what make that sound: FileRepository invalidates only after the tree
        // has stopped changing, so a pass starting after the clear sees the mutated tree, and this
        // generation catches the pass that was already measuring when the clear landed.
        val generation = cacheSource.generation()

        // Reads a type's own size, walking only on a miss. Recording only misses matters:
        // re-stamping a hit would push its TTL out on every load and a size would never expire.
        suspend fun measure(type: LocationType, path: String): Long {
            val cached = cachedSize(type)

            if (cached != null) {
                return cached
            }

            // Read again only on a miss, which is about to walk a tree and dwarf the read.
            if (storedSize(type) == null) {
                hadPlaceholder = true
            }

            val measurement = calculateDirectorySize(File(path), excludedSubtreeFor(type))
            computedSizes[type] = measurement.bytes
            fileCount += measurement.fileCount

            if (measurement.capped) {
                cappedCount++
            }

            return measurement.bytes
        }

        var walkedCount = 0

        val locations = existingLocations(enabledLocations)
            .map { (type, path) ->
                // A card counts as walked once however many trees it took, so Images walking the
                // hidden Screenshots tree as well is still one card.
                val walksBefore = computedSizes.size

                // SCREENSHOTS resolves to a subdirectory of the IMAGES tree and is always left out
                // of the Images walk, so no byte is counted by two walks. When its own card is
                // hidden there is no other card to report those bytes, so Images takes them on.
                //
                // Added here rather than folded into the walk, which is what keeps each stored size
                // to a single meaning: neither has to be re-measured when the setting is toggled,
                // an entry written before a release that changes this cannot be read under the
                // wrong rule, and neither walk can spend the other's MAX_FILES_TO_COUNT budget —
                // one walk over both trees would let screenshots truncate the photo count.
                val screenshots = if (absorbsScreenshots(type, enabledLocations)) {
                    measure(LocationType.SCREENSHOTS, getPathForType(LocationType.SCREENSHOTS))
                } else {
                    0L
                }

                val location =
                    Location(type = type, path = path, totalSizeBytes = measure(type, path) + screenshots)

                if (computedSizes.size > walksBefore) {
                    walkedCount++
                }

                location
            }

        val durationMs = elapsedMillis() - startedAt

        // One write for the whole pass. Every write flushes the store to disk, so updating per
        // location cost up to LocationType.entries.size flushes on a single home load — all of them
        // on the resume path, competing for the disk with whatever else is being written just then.
        //
        // Batching means a cancelled pass persists nothing rather than keeping the locations it had
        // already measured. Accepted: the only caller (HomeViewModel.loadData) defers an overlapping
        // load instead of cancelling one, so this costs a re-walk only when the ViewModel is cleared
        // mid-pass.
        cacheSource.updateCache(computedSizes, generation)

        // A pass served entirely from the cache walked nothing the user waited on.
        if (walkedCount > 0) {
            AnalyticsTracker.trackLocationSizesMeasured(
                durationMs = durationMs,
                cardCount = locations.size,
                walkedCount = walkedCount,
                fileCount = fileCount,
                cappedCount = cappedCount,
                hadPlaceholder = hadPlaceholder
            )
        }

        locations
    }

    /**
     * The same cards [getLocations] returns, carrying the last size stored for each however old it
     * is, or null for a location that has never been measured. Walks nothing and writes nothing,
     * which is what lets the home screen show before [getLocations] has re-measured anything.
     *
     * An expired entry is still shown because expiry only decides when to measure again: the size
     * itself stays in the store until the next measurement replaces it. The stale mark is left for
     * [getLocations] to consume, since this pass does not measure the trees it would invalidate.
     */
    suspend fun getLocationsSnapshot(): List<Location> = withContext(Dispatchers.IO) {
        val enabledLocations = preferencesRepository.enabledLocations.first()

        existingLocations(enabledLocations).map { (type, path) ->
            val size = storedSize(type)
            val totalSize = if (absorbsScreenshots(type, enabledLocations)) {
                // Unknown rather than partial when either half is missing, so a card never shows a
                // total that silently leaves the screenshots out.
                storedSize(LocationType.SCREENSHOTS)?.let { screenshots -> size?.plus(screenshots) }
            } else {
                size
            }

            Location(type = type, path = path, totalSizeBytes = totalSize)
        }
    }

    // The enabled locations whose folder exists, each paired with its resolved path.
    private fun existingLocations(
        enabledLocations: Set<LocationType>
    ): List<Pair<LocationType, String>> =
        LocationType.entries
            .filter { type -> isLocationAvailable(type) && type in enabledLocations }
            .map { type -> type to getPathForType(type) }
            .filter { (_, path) -> isExistingDirectory(path) }

    // Whether the Images card also reports the screenshots, because their own card is hidden. See
    // getLocations for why this is added on top rather than folded into the walk.
    private fun absorbsScreenshots(
        type: LocationType,
        enabledLocations: Set<LocationType>
    ): Boolean =
        type == LocationType.IMAGES && LocationType.SCREENSHOTS !in enabledLocations

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

    // The home screen no longer clears the whole cache immediately before calling getLocations(),
    // which guaranteed a miss and made every load walk each location's whole tree (up to
    // MAX_FILES_TO_COUNT stats apiece) on the resume path. What invalidates a size instead is an
    // explicit clear from each thing that can change one — FileRepository on a mutation this app
    // made, and [markSizeCacheStale] for another app's write — with the TTL left as the backstop
    // for whatever reaches disk without notifying anyone. getLocations guards against a clear
    // landing mid-pass via the generation it captured.
    private suspend fun cachedSize(type: LocationType): Long? {
        val cached = cacheSource.getCachedSize(type)
        return if (cached.isValid) cached.size else null
    }

    // Unlike cachedSize, ignores the TTL: for display only, never to skip a measurement.
    private suspend fun storedSize(type: LocationType): Long? = cacheSource.getCachedSize(type).size

    // SCREENSHOTS resolves to a subdirectory of the IMAGES tree, so walking IMAGES would otherwise
    // also count every screenshot and the two cards together would over-report what is on disk.
    // Unconditional, so that a stored size means the same thing whatever the enabled-locations
    // preference says; getLocations adds the screenshots back on top when their own card is hidden.
    private fun excludedSubtreeFor(type: LocationType): File? = when (type) {
        LocationType.IMAGES -> File(getPathForType(LocationType.SCREENSHOTS))
        else -> null
    }

    // excludedSubtree has no default: it is the only thing keeping the two overlapping locations
    // from counting the same bytes twice, and a default would let the single production call site
    // drop it without a compile error.
    @VisibleForTesting
    fun calculateDirectorySize(directory: File, excludedSubtree: File?): DirectoryMeasurement {
        var bytes = 0L
        var fileCount = 0

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
                .forEach { file ->
                    bytes += file.length()
                    fileCount++
                }

            // A tree of exactly MAX_FILES_TO_COUNT files reads as capped too. Accepted: telling
            // the two apart would cost a stat past the limit on every capped walk.
            DirectoryMeasurement(bytes, fileCount, capped = fileCount == MAX_FILES_TO_COUNT)
        } catch (e: Exception) {
            // Reported rather than swallowed: the 0 this returns is cached for the full TTL, so a
            // transient failure would otherwise show as a silent, sticky "0 B".
            ErrorReporter.error(e.scrubbed(), "calculate_directory_size")
            DirectoryMeasurement(bytes = 0L, fileCount = fileCount, capped = false)
        }
    }

    // One walk's result. fileCount and capped only feed the location_sizes_measured event.
    data class DirectoryMeasurement(val bytes: Long, val fileCount: Int, val capped: Boolean)

    companion object {
        private const val MAX_FILES_TO_COUNT = 10000
    }
}
