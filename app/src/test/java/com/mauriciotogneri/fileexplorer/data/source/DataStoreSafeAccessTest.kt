package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.isNoSpaceLeft
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The safe helpers absorb every I/O failure, but only report the ones worth acting on — a full
 * device is not one of them.
 *
 * Whether a failure really is a full device is decided by [isNoSpaceLeft], which needs a genuine
 * `ErrnoException` and is therefore covered on device by `DiskSpaceTest`. Stubbing it here isolates
 * the other half: that the helpers consult it, and stay silent when it says the disk is full.
 */
class DataStoreSafeAccessTest {

    @Before
    fun setUp() {
        mockkObject(ErrorReporter)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        mockkStatic(DISK_SPACE_FILE_CLASS)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `editSafely absorbs a full disk without reporting it`() = runTest {
        givenTheDiskIsFull(true)
        val source = DataStoreRecentFilesSource(FakeThrowingDataStore())

        source.updateRecentFiles { it }

        verifyNothingWasReported()
    }

    @Test
    fun `readSafely falls back on a full disk without reporting it`() = runTest {
        givenTheDiskIsFull(true)
        val source = DataStoreRecentFilesSource(FakeThrowingDataStore())

        assertTrue(source.getRecentFiles().isEmpty())

        verifyNothingWasReported()
    }

    @Test
    fun `catchIO falls back on a full disk without reporting it`() = runTest {
        givenTheDiskIsFull(true)
        val source = DataStoreRecentFilesSource(FakeThrowingDataStore())

        assertTrue(source.recentFilesFlow.first().isEmpty())

        verifyNothingWasReported()
    }

    @Test
    fun `a failure that is not a full disk is still reported`() = runTest {
        // The suppression has to stay scoped to a full disk: a corrupt store, or a data directory
        // that is no longer writable, is actionable and must keep reaching Crashlytics.
        givenTheDiskIsFull(false)
        val source = DataStoreRecentFilesSource(FakeThrowingDataStore())

        source.updateRecentFiles { it }

        verify(exactly = 1) { ErrorReporter.warning(any(), any(), any()) }
    }

    private fun givenTheDiskIsFull(full: Boolean) {
        every { any<Throwable>().isNoSpaceLeft() } returns full
    }

    private fun verifyNothingWasReported() {
        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
    }

    private companion object {
        const val DISK_SPACE_FILE_CLASS = "com.mauriciotogneri.fileexplorer.data.util.DiskSpaceKt"
    }
}
