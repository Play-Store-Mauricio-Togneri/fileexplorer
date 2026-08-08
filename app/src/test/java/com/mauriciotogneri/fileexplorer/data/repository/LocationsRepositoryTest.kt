package com.mauriciotogneri.fileexplorer.data.repository

import android.os.Environment
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.source.CachedSizeResult
import com.mauriciotogneri.fileexplorer.data.source.LocationsCacheSource
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Covers directory-size accounting against real files on disk. The repository resolves its paths
 * through [Environment], which a plain JVM test has to stub.
 */
class LocationsRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var repository: LocationsRepository

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "test_locations_repository_${System.nanoTime()}")
        tempDir.mkdirs()

        mockkObject(ErrorReporter)
        every { ErrorReporter.error(any(), any(), any()) } just Runs

        preferencesRepository = mockk(relaxed = true)
        every { preferencesRepository.enabledLocations } returns MutableStateFlow(LocationType.entries.toSet())

        repository = LocationsRepository(NoOpCacheSource(), preferencesRepository)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        unmockkObject(ErrorReporter)
    }

    private fun writeFile(parent: File, name: String, bytes: Int): File {
        parent.mkdirs()
        return File(parent, name).apply { writeBytes(ByteArray(bytes)) }
    }

    @Test
    fun `calculateDirectorySize sums every file in the tree`() {
        writeFile(tempDir, "a.jpg", 100)
        writeFile(File(tempDir, "nested"), "b.jpg", 250)

        assertEquals(350L, repository.calculateDirectorySize(tempDir, null))
    }

    @Test
    fun `calculateDirectorySize omits the excluded subtree`() {
        // Screenshots live inside the Pictures tree, so the Images total must not count them —
        // otherwise the Images and Screenshots cards each report the same bytes.
        writeFile(tempDir, "photo.jpg", 100)
        val screenshots = File(tempDir, "Screenshots")
        writeFile(screenshots, "shot.png", 400)
        writeFile(File(screenshots, "old"), "older.png", 800)

        assertEquals(100L, repository.calculateDirectorySize(tempDir, screenshots))
    }

    @Test
    fun `calculateDirectorySize omits the excluded subtree when its name differs in case`() {
        // Emulated external storage is case-insensitive, so a directory left as "screenshots" by a
        // restored backup or a third-party capture app is the same one folderExists() resolves for
        // the Screenshots card. An exact match would count it under Images too.
        writeFile(tempDir, "photo.jpg", 100)
        writeFile(File(tempDir, "screenshots"), "shot.png", 400)

        assertEquals(100L, repository.calculateDirectorySize(tempDir, File(tempDir, "Screenshots")))
    }

    @Test
    fun `calculateDirectorySize counts the excluded subtree when it is walked directly`() {
        // The Screenshots card walks the same directory the Images total skips, so those bytes are
        // attributed to exactly one location rather than dropped.
        val screenshots = File(tempDir, "Screenshots")
        writeFile(screenshots, "shot.png", 400)

        assertEquals(400L, repository.calculateDirectorySize(screenshots, null))
    }

    @Test
    fun `calculateDirectorySize keeps sibling directories with a shared name prefix`() {
        // Exclusion is by directory identity, not a path-prefix match: "ScreenshotsOld" is a
        // separate folder and its bytes still belong to the Images total.
        writeFile(File(tempDir, "ScreenshotsOld"), "kept.png", 70)
        val screenshots = File(tempDir, "Screenshots")
        writeFile(screenshots, "shot.png", 400)

        assertEquals(70L, repository.calculateDirectorySize(tempDir, screenshots))
    }

    @Test
    fun `calculateDirectorySize ignores an excluded subtree that does not exist`() {
        writeFile(tempDir, "photo.jpg", 100)

        assertEquals(100L, repository.calculateDirectorySize(tempDir, File(tempDir, "Screenshots")))
    }

    @Test
    fun `calculateDirectorySize excludes everything when handed its own root`() {
        // onEnter is consulted for the root too. Unreachable in production (Pictures is never
        // Pictures/Screenshots), but pinned because calculateDirectorySize is callable directly.
        writeFile(tempDir, "photo.jpg", 100)

        assertEquals(0L, repository.calculateDirectorySize(tempDir, tempDir))
    }

    @Test
    fun `calculateDirectorySize returns zero for a directory that does not exist`() {
        assertEquals(0L, repository.calculateDirectorySize(File(tempDir, "missing"), null))
    }

    @Test
    fun `getLocations does not count screenshots under images`() = runTest {
        // The wiring, not just the mechanism: proves getLocations() actually hands the Screenshots
        // subtree to the walk, so the exclusion cannot be unhooked without a red test.
        val pictures = File(tempDir, "Pictures")
        writeFile(pictures, "photo.jpg", 100)
        writeFile(File(pictures, "Screenshots"), "shot.png", 400)

        mockkStatic(Environment::class)
        try {
            // Every DIRECTORY_* constant reads as null from the Android stub jar, so the answer
            // cannot key off the argument and all public directories collapse onto this one. That
            // is enough here: the assertion is about Images versus its own Screenshots subtree.
            every { Environment.getExternalStoragePublicDirectory(any()) } returns pictures

            val locations = repository.getLocations()

            assertEquals(100L, locations.single { it.type == LocationType.IMAGES }.totalSizeBytes)
            assertEquals(400L, locations.single { it.type == LocationType.SCREENSHOTS }.totalSizeBytes)
        } finally {
            unmockkStatic(Environment::class)
        }
    }

    @Test
    fun `getLocations counts screenshots under images when the screenshots card is hidden`() = runTest {
        // The other half of the exclusion. With no Screenshots card to account for those bytes,
        // excluding them from Images would leave them counted under no location at all, and Images
        // would report less than the folder that card opens.
        val pictures = File(tempDir, "Pictures")
        writeFile(pictures, "photo.jpg", 100)
        writeFile(File(pictures, "Screenshots"), "shot.png", 400)
        every { preferencesRepository.enabledLocations } returns
            MutableStateFlow(LocationType.entries.toSet() - LocationType.SCREENSHOTS)
        val repository = LocationsRepository(NoOpCacheSource(), preferencesRepository)

        mockkStatic(Environment::class)
        try {
            every { Environment.getExternalStoragePublicDirectory(any()) } returns pictures

            val locations = repository.getLocations()

            assertEquals(500L, locations.single { it.type == LocationType.IMAGES }.totalSizeBytes)
            assertTrue(locations.none { it.type == LocationType.SCREENSHOTS })
        } finally {
            unmockkStatic(Environment::class)
        }
    }

    @Test
    fun `a stale mark clears the cache before the pass captures the generation`() = runTest {
        // Ordering is the point. Clearing after the generation was captured would move it under
        // the pass, and updateCache would then discard every tree the pass had just walked — so
        // the mark buys freshness at the cost of the walk it was meant to make worthwhile.
        val pictures = File(tempDir, "Pictures")
        writeFile(pictures, "photo.jpg", 100)
        val cacheSource = RecordingCacheSource()
        val repository = LocationsRepository(cacheSource, preferencesRepository)
        repository.markSizeCacheStale()

        mockkStatic(Environment::class)
        try {
            every { Environment.getExternalStoragePublicDirectory(any()) } returns pictures

            repository.getLocations()

            assertEquals("clear", cacheSource.calls.first())
            assertEquals("generation", cacheSource.calls[1])
            // The batch survives, which is the whole reason the clear happens up front.
            assertTrue(cacheSource.updates.single().sizes.isNotEmpty())
        } finally {
            unmockkStatic(Environment::class)
        }
    }

    @Test
    fun `a pass with no stale mark does not clear the cache`() = runTest {
        // The mark is consumed, not standing: a second pass must serve the sizes the first one
        // just wrote rather than dropping them and walking every tree again.
        val pictures = File(tempDir, "Pictures")
        writeFile(pictures, "photo.jpg", 100)
        val cacheSource = RecordingCacheSource()
        val repository = LocationsRepository(cacheSource, preferencesRepository)
        repository.markSizeCacheStale()

        mockkStatic(Environment::class)
        try {
            every { Environment.getExternalStoragePublicDirectory(any()) } returns pictures

            repository.getLocations()
            cacheSource.calls.clear()
            repository.getLocations()

            assertFalse(cacheSource.calls.contains("clear"))
        } finally {
            unmockkStatic(Environment::class)
        }
    }

    @Test
    fun `a hidden screenshots card does not change the size stored for images`() = runTest {
        // The stored size means one thing whatever the setting says: Pictures without its
        // Screenshots subtree. Folding the screenshots into the walk instead would put two
        // meanings behind one key — nothing to migrate an entry written before this release, and
        // one MAX_FILES_TO_COUNT budget spent across both trees, which truncates the larger one.
        val pictures = File(tempDir, "Pictures")
        writeFile(pictures, "photo.jpg", 100)
        writeFile(File(pictures, "Screenshots"), "shot.png", 400)
        every { preferencesRepository.enabledLocations } returns
            MutableStateFlow(setOf(LocationType.IMAGES))
        val cacheSource = RecordingCacheSource()
        val repository = LocationsRepository(cacheSource, preferencesRepository)

        mockkStatic(Environment::class)
        try {
            every { Environment.getExternalStoragePublicDirectory(any()) } returns pictures

            val locations = repository.getLocations()
            val stored = cacheSource.updates.single().sizes

            assertEquals(500L, locations.single { it.type == LocationType.IMAGES }.totalSizeBytes)
            assertEquals(100L, stored[LocationType.IMAGES])
            // Measured and kept even though its card is hidden, because Images is reporting it.
            assertEquals(400L, stored[LocationType.SCREENSHOTS])
        } finally {
            unmockkStatic(Environment::class)
        }
    }

    @Test
    fun `getLocations writes every size it computed in one cache update`() = runTest {
        // Each write flushes the store to disk, so a pass that measured N locations must still
        // update the cache once — not once per location, on the resume path.
        val cacheSource = RecordingCacheSource()
        val repository = LocationsRepository(cacheSource, preferencesRepository)
        val pictures = File(tempDir, "Pictures")
        writeFile(pictures, "photo.jpg", 100)

        mockkStatic(Environment::class)
        try {
            every { Environment.getExternalStoragePublicDirectory(any()) } returns pictures

            val locations = repository.getLocations()

            assertEquals(1, cacheSource.updates.size)
            // Every location missed the cache, so each one the pass returned is in that single
            // batch — proving the batch is complete, not merely singular.
            assertEquals(locations.map { it.type }.toSet(), cacheSource.updates.single().sizes.keys)
        } finally {
            unmockkStatic(Environment::class)
        }
    }

    @Test
    fun `getLocations keeps a cached location out of the batch and does not re-walk it`() = runTest {
        // The other half of the batching contract: only misses accumulate. Re-stamping a hit would
        // both cost a write on an all-hit load and push the entry's TTL out on every load.
        val pictures = File(tempDir, "Pictures")
        writeFile(pictures, "photo.jpg", 100)
        val cacheSource = RecordingCacheSource(hits = mapOf(LocationType.IMAGES to 999L))
        val repository = LocationsRepository(cacheSource, preferencesRepository)

        mockkStatic(Environment::class)
        try {
            every { Environment.getExternalStoragePublicDirectory(any()) } returns pictures

            val locations = repository.getLocations()

            // The cached size is returned verbatim, so the walk that would have reported 100 bytes
            // never ran.
            assertEquals(999L, locations.single { it.type == LocationType.IMAGES }.totalSizeBytes)
            assertFalse(cacheSource.updates.single().sizes.containsKey(LocationType.IMAGES))
            // The misses around it still batch, so the hit was skipped rather than the write lost.
            assertTrue(cacheSource.updates.single().sizes.isNotEmpty())
        } finally {
            unmockkStatic(Environment::class)
        }
    }

    @Test
    fun `getLocations reads the generation before it measures anything`() = runTest {
        // Ordering is the whole guard: a generation read after the walks would pick up the very
        // clearCache() the pass needs to notice, and stamp pre-mutation totals fresh. Asserting
        // only the value handed to updateCache would still pass if the read had moved to the end.
        val pictures = File(tempDir, "Pictures")
        writeFile(pictures, "photo.jpg", 100)
        val cacheSource = RecordingCacheSource(generation = 7L)
        val repository = LocationsRepository(cacheSource, preferencesRepository)

        mockkStatic(Environment::class)
        try {
            every { Environment.getExternalStoragePublicDirectory(any()) } returns pictures

            repository.getLocations()

            assertEquals("generation", cacheSource.calls.first())
            assertEquals(1, cacheSource.calls.count { it == "generation" })
            assertEquals("update", cacheSource.calls.last())
            assertEquals(7L, cacheSource.updates.single().generation)
        } finally {
            unmockkStatic(Environment::class)
        }
    }

    private class NoOpCacheSource : LocationsCacheSource {
        override suspend fun getCachedSize(type: LocationType) = CachedSizeResult(size = null, isValid = false)
        override suspend fun generation() = 0L
        override suspend fun updateCache(sizes: Map<LocationType, Long>, generation: Long) = Unit
        override suspend fun clearCache() = Unit
    }

    private data class RecordedUpdate(val sizes: Map<LocationType, Long>, val generation: Long)

    /**
     * Reports a hit for [hits] and a miss for everything else, and keeps each batch it was handed
     * so the number of writes and the generation guarding them can be asserted.
     */
    private class RecordingCacheSource(
        private val hits: Map<LocationType, Long> = emptyMap(),
        private val generation: Long = 0L
    ) : LocationsCacheSource {
        val updates = mutableListOf<RecordedUpdate>()

        /** Every call in the order it arrived, so the guard's ordering can be asserted. */
        val calls = mutableListOf<String>()

        override suspend fun getCachedSize(type: LocationType): CachedSizeResult {
            calls += "read"
            val hit = hits[type]
            return CachedSizeResult(size = hit, isValid = hit != null)
        }

        override suspend fun generation(): Long {
            calls += "generation"
            return generation
        }

        // The sizes are copied, not aliased: the repository hands over its own live accumulator, so
        // recording the reference would let a later mutation rewrite a batch already asserted on.
        override suspend fun updateCache(sizes: Map<LocationType, Long>, generation: Long) {
            calls += "update"
            updates += RecordedUpdate(sizes.toMap(), generation)
        }

        override suspend fun clearCache() {
            calls += "clear"
        }
    }
}
