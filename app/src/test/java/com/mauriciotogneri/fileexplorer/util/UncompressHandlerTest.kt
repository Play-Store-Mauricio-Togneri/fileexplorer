package com.mauriciotogneri.fileexplorer.util

import android.content.Context
import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.InsufficientStorageException
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.ZipBombException
import com.mauriciotogneri.fileexplorer.data.repository.ZipInfo
import com.mauriciotogneri.fileexplorer.data.repository.ZipSlipException
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.lingala.zip4j.exception.ZipException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.FileNotFoundException

@OptIn(ExperimentalCoroutinesApi::class)
class UncompressHandlerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var context: Context
    private lateinit var fileRepository: FileRepository

    private val testZipFile = FileItem(
        path = "/storage/emulated/0/Downloads/archive.zip",
        name = "archive.zip",
        isDirectory = false,
        size = 1024L,
        lastModified = 1000L,
        createdTime = 1000L,
        mimeType = "application/zip",
        childCount = null
    )

    private val testTargetDir = "/storage/emulated/0/Downloads"
    private val testAllowedRoots = listOf("/storage/emulated/0")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)
        context = mockk(relaxed = true)
        fileRepository = mockk()

        mockkObject(ErrorReporter)
        mockkObject(IntentUtil)
        mockkObject(AnalyticsTracker)
        mockkObject(ReviewPrompt)

        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { ErrorReporter.error(any(), any(), any()) } just Runs
        every { IntentUtil.trackRecentFile(any(), any()) } just Runs
        every { AnalyticsTracker.trackUncompressCompleted(any()) } just Runs
        every { AnalyticsTracker.trackOperationFailed(any(), any(), any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(ErrorReporter)
        unmockkObject(IntentUtil)
        unmockkObject(AnalyticsTracker)
        unmockkObject(ReviewPrompt)
    }

    private fun createHandler(): UncompressHandler {
        return UncompressHandler(
            context = context,
            scope = testScope,
            fileRepository = fileRepository,
            getTargetDirectory = { testTargetDir },
            getAllowedRoots = { testAllowedRoots }
        )
    }

    @Test
    fun `initial state has no item to uncompress`() = runTest {
        val handler = createHandler()

        assertNull(handler.state.value.itemToUncompress)
        assertEquals(0, handler.state.value.entryCount)
        assertFalse(handler.state.value.isPasswordProtected)
        assertNull(handler.state.value.progress)
    }

    @Test
    fun `showUncompressDialog sets item and zip info`() = runTest {
        val zipInfo = ZipInfo(entryCount = 10, isEncrypted = false)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(testZipFile, handler.state.value.itemToUncompress)
        assertEquals(10, handler.state.value.entryCount)
        assertFalse(handler.state.value.isPasswordProtected)
    }

    @Test
    fun `showUncompressDialog sets password protected flag`() = runTest {
        val zipInfo = ZipInfo(entryCount = 5, isEncrypted = true)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(handler.state.value.isPasswordProtected)
    }

    @Test
    fun `showUncompressDialog handles zip info error gracefully`() = runTest {
        coEvery { fileRepository.getZipInfo(testZipFile.path) } throws RuntimeException("Invalid zip")

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(testZipFile, handler.state.value.itemToUncompress)
        assertEquals(0, handler.state.value.entryCount)
        assertFalse(handler.state.value.isPasswordProtected)
    }

    @Test
    fun `showUncompressDialog shows invalid archive toast and keeps dialog closed for non-zip`() = runTest {
        coEvery {
            fileRepository.getZipInfo(testZipFile.path)
        } throws ZipException("Zip headers not found. Probably not a zip file")

        val handler = createHandler()

        handler.events.test {
            handler.showUncompressDialog(testZipFile)
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is UncompressEvent.ShowToast)
            assertEquals(
                R.string.uncompress_error_invalid_archive,
                (event as UncompressEvent.ShowToast).messageResId
            )
        }

        assertNull(handler.state.value.itemToUncompress)
        assertEquals(0, handler.state.value.entryCount)
        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
    }

    @Test
    fun `dismissUncompressDialog clears state`() = runTest {
        val zipInfo = ZipInfo(entryCount = 10, isEncrypted = true)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(handler.state.value.itemToUncompress)

        handler.dismissUncompressDialog()

        assertNull(handler.state.value.itemToUncompress)
        assertEquals(0, handler.state.value.entryCount)
        assertFalse(handler.state.value.isPasswordProtected)
    }

    @Test
    fun `confirmUncompress without item does nothing`() = runTest {
        val handler = createHandler()

        handler.confirmUncompress()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(handler.state.value.progress)
    }

    @Test
    fun `confirmUncompress starts extraction and emits completion event`() = runTest {
        val zipInfo = ZipInfo(entryCount = 2, isEncrypted = false)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        val progress = UncompressProgress(
            currentFile = "file.txt",
            extractedFiles = 2,
            totalFiles = 2,
            extractedBytes = 1024L,
            totalBytes = 1024L,
            isComplete = true
        )
        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots)
        } returns flowOf(progress)

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        handler.events.test {
            handler.confirmUncompress()
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is UncompressEvent.ExtractionComplete)
        }

        assertNull(handler.state.value.itemToUncompress)
        assertNull(handler.state.value.progress)
        verify(exactly = 1) { ReviewPrompt.onActionSucceeded() }
    }

    @Test
    fun `confirmUncompress with password passes password to repository`() = runTest {
        val zipInfo = ZipInfo(entryCount = 2, isEncrypted = true)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        val progress = UncompressProgress(
            currentFile = "file.txt",
            extractedFiles = 2,
            totalFiles = 2,
            extractedBytes = 1024L,
            totalBytes = 1024L,
            isComplete = true
        )
        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, "secret123", testAllowedRoots)
        } returns flowOf(progress)

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        handler.events.test {
            handler.confirmUncompress("secret123")
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is UncompressEvent.ExtractionComplete)
        }
    }

    @Test
    fun `wrong password shows toast and reopens dialog`() = runTest {
        val zipInfo = ZipInfo(entryCount = 2, isEncrypted = true)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        val zipException = ZipException("Wrong password", ZipException.Type.WRONG_PASSWORD)
        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, "wrongpass", testAllowedRoots)
        } throws zipException

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        handler.events.test {
            handler.confirmUncompress("wrongpass")
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is UncompressEvent.ShowToast)
            assertEquals(R.string.uncompress_error_wrong_password, (event as UncompressEvent.ShowToast).messageResId)
        }

        assertEquals(testZipFile, handler.state.value.itemToUncompress)
        assertTrue(handler.state.value.isPasswordProtected)
    }

    @Test
    fun `zip exception shows generic error toast`() = runTest {
        val zipInfo = ZipInfo(entryCount = 2, isEncrypted = false)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots)
        } throws ZipException("Corrupted archive")

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        handler.events.test {
            handler.confirmUncompress()
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is UncompressEvent.ShowToast)
            assertEquals(R.string.uncompress_error, (event as UncompressEvent.ShowToast).messageResId)
        }

        verify(exactly = 0) { ErrorReporter.error(any(), any(), any()) }
    }

    @Test
    fun `zip slip attack shows malicious zip error`() = runTest {
        val zipInfo = ZipInfo(entryCount = 2, isEncrypted = false)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots)
        } throws ZipSlipException()

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        handler.events.test {
            handler.confirmUncompress()
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is UncompressEvent.ShowToast)
            assertEquals(R.string.uncompress_error_malicious, (event as UncompressEvent.ShowToast).messageResId)
        }
    }

    @Test
    fun `zip bomb shows too large error`() = runTest {
        val zipInfo = ZipInfo(entryCount = 2, isEncrypted = false)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots)
        } throws ZipBombException("Decompression ratio too high")

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        handler.events.test {
            handler.confirmUncompress()
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is UncompressEvent.ShowToast)
            assertEquals(R.string.uncompress_error_too_large, (event as UncompressEvent.ShowToast).messageResId)
        }
    }

    @Test
    fun `insufficient storage shows error`() = runTest {
        val zipInfo = ZipInfo(entryCount = 2, isEncrypted = false)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots)
        } throws InsufficientStorageException("Not enough space")

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        handler.events.test {
            handler.confirmUncompress()
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is UncompressEvent.ShowToast)
            assertEquals(R.string.uncompress_error_insufficient_storage, (event as UncompressEvent.ShowToast).messageResId)
        }
    }

    @Test
    fun `security exception shows invalid target path error`() = runTest {
        val zipInfo = ZipInfo(entryCount = 2, isEncrypted = false)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots)
        } throws SecurityException("Target path not allowed")

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        handler.events.test {
            handler.confirmUncompress()
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is UncompressEvent.ShowToast)
            assertEquals(R.string.error_invalid_target_path, (event as UncompressEvent.ShowToast).messageResId)
        }
    }

    @Test
    fun `generic exception shows error toast`() = runTest {
        val zipInfo = ZipInfo(entryCount = 2, isEncrypted = false)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots)
        } throws RuntimeException("Unexpected error")

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        handler.events.test {
            handler.confirmUncompress()
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is UncompressEvent.ShowToast)
            assertEquals(R.string.uncompress_error, (event as UncompressEvent.ShowToast).messageResId)
        }
    }

    @Test
    fun `generic exception is reported without the message or cause it arrived with`() = runTest {
        val zipInfo = ZipInfo(entryCount = 2, isEncrypted = false)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        val thrownAt = arrayOf(StackTraceElement("FileRepository", "uncompressFile", "FileRepository.kt", 1001))
        val leaky = IllegalArgumentException(
            "/storage/emulated/0/Documents/tax-return.pdf: open failed",
            FileNotFoundException("/storage/emulated/0/Documents/tax-return.pdf")
        ).apply { stackTrace = thrownAt }
        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots)
        } throws leaky

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()
        handler.confirmUncompress()
        testDispatcher.scheduler.advanceUntilIdle()

        val reported = slot<Throwable>()
        verify { ErrorReporter.error(capture(reported), "uncompress_file", "zip") }

        // Crashlytics records the message and the whole cause chain, so both have to be gone: the
        // path the extraction failed on names a file the user has.
        val chain = generateSequence<Throwable>(reported.captured) { it.cause }.toList()
        assertEquals(1, chain.size)
        assertFalse(chain.single().message.orEmpty().contains("tax-return"))
        assertTrue(chain.single().message.orEmpty().contains(IllegalArgumentException::class.java.name))
        // The frame that threw survives, or every unknown failure groups as one issue.
        assertArrayEquals(thrownAt, reported.captured.stackTrace)
    }

    @Test
    fun `storage io failure shows error toast without reporting it`() = runTest {
        val zipInfo = ZipInfo(entryCount = 2, isEncrypted = false)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots)
        } throws FileNotFoundException("/storage/emulated/0/Documents/tax-return.pdf: open failed: ENOENT")

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        handler.events.test {
            handler.confirmUncompress()
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is UncompressEvent.ShowToast)
            assertEquals(R.string.uncompress_error, (event as UncompressEvent.ShowToast).messageResId)
        }

        verify { AnalyticsTracker.trackOperationFailed("uncompress", "storage_io_error") }
        verify(exactly = 0) { ErrorReporter.error(any(), any(), any()) }
    }

    /**
     * Extraction writes files, so a cancel that clears the dialog without stopping the job leaves
     * entries appearing on disk after the user cancelled. Cancelling a handler with nothing running
     * only reads back the initial state and would stay green with the whole body deleted.
     *
     * A cancel is the user's choice, not a failure: the cancellation arrives in the extraction's
     * catch-all, and without the guard there every cancel files a non-fatal, counts as a failed
     * operation and blocks the rating ask. The error toast it would also emit is not asserted on
     * its own — emitted from the coroutine being cancelled, it does not get through — so the
     * reporter and analytics checks carry the test.
     */
    @Test
    fun `cancelUncompression stops a running extraction and clears progress`() = runTest {
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns
            ZipInfo(entryCount = 3, isEncrypted = false)

        var extractionStopped = false
        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots)
        } returns flow {
            emit(
                UncompressProgress(
                    currentFile = "file1.txt",
                    extractedFiles = 1,
                    totalFiles = 3,
                    extractedBytes = 100L,
                    totalBytes = 300L,
                    isComplete = false
                )
            )
            // Hold the extraction open so the cancel lands mid-flight, and record whether the
            // collection was actually torn down.
            try {
                awaitCancellation()
            } finally {
                extractionStopped = true
            }
        }

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        handler.events.test {
            handler.confirmUncompress()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("file1.txt", handler.state.value.progress?.currentFile)

            handler.cancelUncompression()
            testDispatcher.scheduler.advanceUntilIdle()

            expectNoEvents()
        }

        assertTrue("Cancel must stop the running extraction", extractionStopped)
        assertNull(handler.state.value.progress)
        verify(exactly = 0) { ErrorReporter.error(any(), any(), any()) }
        verify(exactly = 0) { AnalyticsTracker.trackOperationFailed(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { ReviewPrompt.onActionFailed() }
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
    }

    @Test
    fun `progress updates during extraction`() = runTest {
        val zipInfo = ZipInfo(entryCount = 3, isEncrypted = false)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        val progress1 = UncompressProgress(
            currentFile = "file1.txt",
            extractedFiles = 1,
            totalFiles = 3,
            extractedBytes = 100L,
            totalBytes = 300L,
            isComplete = false
        )
        val progress2 = UncompressProgress(
            currentFile = "file2.txt",
            extractedFiles = 2,
            totalFiles = 3,
            extractedBytes = 200L,
            totalBytes = 300L,
            isComplete = false
        )
        val progress3 = UncompressProgress(
            currentFile = "file3.txt",
            extractedFiles = 3,
            totalFiles = 3,
            extractedBytes = 300L,
            totalBytes = 300L,
            isComplete = true
        )

        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots)
        } returns flowOf(progress1, progress2, progress3)

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        handler.events.test {
            handler.confirmUncompress()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is UncompressEvent.ExtractionComplete)
        }

        assertNull(handler.state.value.progress)
    }

    // === The delayed progress dialog ===

    /** An extraction part-way through the archive, on [currentFile]. */
    private fun extractionUnderway(currentFile: String) = UncompressProgress(
        currentFile = currentFile,
        extractedFiles = 1,
        totalFiles = 4,
        extractedBytes = 100L,
        totalBytes = 400L
    )

    private val extractionFinished = UncompressProgress(
        currentFile = "",
        extractedFiles = 4,
        totalFiles = 4,
        extractedBytes = 400L,
        totalBytes = 400L,
        isComplete = true
    )

    /** Opens the dialog for the test archive and confirms it, as the user does. */
    private fun startExtraction(handler: UncompressHandler) {
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.runCurrent()
        handler.confirmUncompress()
    }

    /** Moves virtual time on by [millis] and runs everything due by then. */
    private fun advanceBy(millis: Long) {
        testDispatcher.scheduler.advanceTimeBy(millis)
        testDispatcher.scheduler.runCurrent()
    }

    @Test
    fun `an extraction that ends within the delay never shows its dialog`() = runTest {
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns ZipInfo(entryCount = 4, isEncrypted = false)
        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots)
        } returns flow {
            emit(extractionUnderway("file1.txt"))
            delay(PROGRESS_DIALOG_DELAY_MS - 1)
            emit(extractionFinished)
        }

        val handler = createHandler()
        // Collected eagerly, so a dialog published and replaced at the same instant is caught too.
        val shown = mutableListOf<UncompressProgress>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            handler.state.collect { state -> state.progress?.let(shown::add) }
        }

        handler.events.test {
            startExtraction(handler)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is UncompressEvent.ExtractionComplete)
        }

        assertEquals(emptyList<UncompressProgress>(), shown)
    }

    @Test
    fun `an extraction still running after the delay shows its latest progress`() = runTest {
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns ZipInfo(entryCount = 4, isEncrypted = false)
        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots)
        } returns flow {
            emit(extractionUnderway("file1.txt"))
            emit(extractionUnderway("file2.txt"))
            awaitCancellation()
        }

        val handler = createHandler()
        startExtraction(handler)
        advanceBy(PROGRESS_DIALOG_DELAY_MS - 1)

        assertNull("The dialog waits out the delay", handler.state.value.progress)

        advanceBy(1)

        assertEquals("file2.txt", handler.state.value.progress?.currentFile)
        handler.cancelUncompression()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    // The archive's headers are read, its password checked and its size weighed against the free
    // space before anything is extracted, and on a large or slow archive that can outlast the delay.
    @Test
    fun `an extraction still reading its headers when the delay ends shows a dialog with no total yet`() = runTest {
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns ZipInfo(entryCount = 4, isEncrypted = false)
        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots)
        } returns flow<UncompressProgress> { awaitCancellation() }

        val handler = createHandler()
        startExtraction(handler)
        advanceBy(PROGRESS_DIALOG_DELAY_MS)

        assertEquals(
            UncompressProgress(
                currentFile = "",
                extractedFiles = 0,
                totalFiles = 0,
                extractedBytes = 0L,
                totalBytes = 0L
            ),
            handler.state.value.progress
        )
        handler.cancelUncompression()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    /**
     * Until an extraction's dialog appears nothing covers the screen, so a second one can start.
     * The first must then stay out of the second one's dialog — its only Cancel button: neither its
     * progress nor its end may reach it, and its end must not take the second one's handle.
     */
    @Test
    fun `an extraction that finishes while a newer one runs leaves that one its dialog and its cancel`() = runTest {
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns ZipInfo(entryCount = 4, isEncrypted = false)
        val firstExtraction = CompletableDeferred<Unit>()
        var secondExtractionStopped = false
        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots)
        } returnsMany listOf(
            flow {
                emit(extractionUnderway("first.txt"))
                firstExtraction.await()
                emit(extractionUnderway("first-later.txt"))
                emit(extractionFinished)
            },
            flow {
                emit(extractionUnderway("second.txt"))
                try {
                    awaitCancellation()
                } finally {
                    secondExtractionStopped = true
                }
            }
        )

        val handler = createHandler()
        startExtraction(handler)
        testDispatcher.scheduler.runCurrent()
        startExtraction(handler)
        advanceBy(PROGRESS_DIALOG_DELAY_MS)

        assertEquals("second.txt", handler.state.value.progress?.currentFile)

        firstExtraction.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "The first extraction must neither overwrite nor close the second one's dialog",
            "second.txt",
            handler.state.value.progress?.currentFile
        )
        verify(exactly = 1) { AnalyticsTracker.trackUncompressCompleted(any()) }

        handler.cancelUncompression()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("The second extraction's Cancel must still stop it", secondExtractionStopped)
        assertNull(handler.state.value.progress)
    }
}
