package com.mauriciotogneri.fileexplorer.util

import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.StorageType
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Covers the policy [MainActivity][com.mauriciotogneri.fileexplorer.activities.MainActivity] applies
 * at launch, which the Activity itself cannot be unit-tested through.
 *
 * The distinction these tests exist for is [StartupFolder.TimedOut] against
 * [StartupFolder.Unavailable]. Collapsing the two is what makes a launch tell a user with a slow SD
 * card that their folder is gone and send them to Settings to re-pick a setting that was correct, so
 * every test that produces one asserts it is not the other.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StartupFolderResolverTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Before
    fun setUp() {
        mockkObject(ErrorReporter)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `opens a folder that resolves`() = runTest {
        val root = temporaryFolder.newFolder("emulated")
        val folder = File(root, "Download").apply { mkdirs() }

        val outcome = resolver { listOf(storage(root.absolutePath)) }
            .resolve(folder.absolutePath)

        assertTrue("A folder that exists and is readable must open", outcome is StartupFolder.Open)
        assertEquals(folder.absolutePath, (outcome as StartupFolder.Open).destination.path)
    }

    @Test
    fun `reports a folder that cannot be opened as unavailable`() = runTest {
        val root = temporaryFolder.newFolder("emulated")
        val missing = File(root, "Deleted")

        val outcome = resolver { listOf(storage(root.absolutePath)) }
            .resolve(missing.absolutePath)

        assertEquals(StartupFolder.Unavailable, outcome)
    }

    @Test
    fun `reports a resolution that outlasts the timeout as timed out, not unavailable`() = runTest {
        val root = temporaryFolder.newFolder("emulated")
        val folder = File(root, "Download").apply { mkdirs() }

        // The folder is perfectly fine; the volume it sits on is just slow to answer.
        val outcome = resolver {
            delay(TIMEOUT_MS * 5)
            listOf(storage(root.absolutePath))
        }.resolve(folder.absolutePath)

        assertEquals(
            "A wait that ran out says nothing about the folder, so it must not read as unavailable",
            StartupFolder.TimedOut,
            outcome
        )
    }

    @Test
    fun `stops waiting at the timeout rather than for the resolution`() = runTest {
        val root = temporaryFolder.newFolder("emulated")
        val folder = File(root, "Download").apply { mkdirs() }

        val outcome = resolver {
            delay(TIMEOUT_MS * 5)
            listOf(storage(root.absolutePath))
        }.resolve(folder.absolutePath)

        assertEquals(StartupFolder.TimedOut, outcome)
        // The bound is on the waiting, not on the work: a launch held for the full resolution would
        // sit here for five times as long, which is the blank screen the timeout exists to prevent.
        assertEquals(TIMEOUT_MS, testScheduler.currentTime)
    }

    @Test
    fun `does not report a timeout as a failure`() = runTest {
        val root = temporaryFolder.newFolder("emulated")
        val folder = File(root, "Download").apply { mkdirs() }

        resolver {
            delay(TIMEOUT_MS * 5)
            listOf(storage(root.absolutePath))
        }.resolve(folder.absolutePath)

        // Drain first: cancelling the abandoned coroutine only *queues* its resumption on the test
        // scheduler, so without this the verify runs before the cancellation has reached the catch
        // blocks — and would pass even with the CancellationException rethrow deleted.
        testScheduler.advanceUntilIdle()

        // Giving up on a wait is not a fault to diagnose, and the abandoned coroutine's cancellation
        // is not one either.
        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
    }

    @Test
    fun `reports a failing storage read as unavailable`() = runTest {
        val root = temporaryFolder.newFolder("emulated")
        val folder = File(root, "Download").apply { mkdirs() }

        val outcome = resolver { throw IOException("volume went away") }
            .resolve(folder.absolutePath)

        assertEquals(
            "A failure to read storage leaves the user on the home screen rather than failing to start",
            StartupFolder.Unavailable,
            outcome
        )
        verify(exactly = 1) { ErrorReporter.warning(any(), any(), any()) }
    }

    private fun storage(path: String) = StorageDevice(
        path = path,
        displayName = "Internal storage",
        totalBytes = 0,
        availableBytes = 0,
        type = StorageType.INTERNAL
    )

    /** Runs the resolution on the test scheduler, so the timeout is spent in virtual time. */
    private fun TestScope.resolver(storages: suspend () -> List<StorageDevice>) =
        StartupFolderResolver(
            scope = this,
            storages = storages,
            dispatcher = StandardTestDispatcher(testScheduler),
            timeoutMs = TIMEOUT_MS
        )

    private companion object {
        const val TIMEOUT_MS = 2_000L
    }
}
