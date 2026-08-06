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

    private class NoOpCacheSource : LocationsCacheSource {
        override suspend fun getCachedSize(type: LocationType) = CachedSizeResult(size = null, isValid = false)
        override suspend fun updateCache(type: LocationType, size: Long) = Unit
        override suspend fun clearCache() = Unit
    }
}
