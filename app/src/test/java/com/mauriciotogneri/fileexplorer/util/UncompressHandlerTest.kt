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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
        mockkObject(MediaStoreUtil)
        mockkObject(IntentUtil)
        mockkObject(AnalyticsTracker)

        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { ErrorReporter.error(any(), any(), any()) } just Runs
        every { MediaStoreUtil.scanFiles(any(), any()) } just Runs
        coEvery { MediaStoreUtil.notifyTreeDeleted(any(), any()) } just Runs
        every { IntentUtil.trackRecentFile(any(), any()) } just Runs
        every { AnalyticsTracker.trackUncompressCompleted(any()) } just Runs
        every { AnalyticsTracker.trackOperationFailed(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(ErrorReporter)
        unmockkObject(MediaStoreUtil)
        unmockkObject(IntentUtil)
        unmockkObject(AnalyticsTracker)
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
            isComplete = true,
            extractedPaths = listOf("$testTargetDir/file.txt")
        )
        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots, any())
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
            isComplete = true,
            extractedPaths = listOf("$testTargetDir/file.txt")
        )
        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, "secret123", testAllowedRoots, any())
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
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, "wrongpass", testAllowedRoots, any())
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
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots, any())
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
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots, any())
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
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots, any())
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
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots, any())
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
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots, any())
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
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots, any())
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
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots, any())
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
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots, any())
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
     */
    @Test
    fun `cancelUncompression stops a running extraction and clears progress`() = runTest {
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns
            ZipInfo(entryCount = 3, isEncrypted = false)

        var extractionStopped = false
        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots, any())
        } returns flow {
            emit(
                UncompressProgress(
                    currentFile = "file1.txt",
                    extractedFiles = 1,
                    totalFiles = 3,
                    extractedBytes = 100L,
                    totalBytes = 300L,
                    isComplete = false,
                    extractedPaths = emptyList()
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

        handler.confirmUncompress()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("file1.txt", handler.state.value.progress?.currentFile)

        handler.cancelUncompression()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Cancel must stop the running extraction", extractionStopped)
        assertNull(handler.state.value.progress)
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
            isComplete = false,
            extractedPaths = emptyList()
        )
        val progress2 = UncompressProgress(
            currentFile = "file2.txt",
            extractedFiles = 2,
            totalFiles = 3,
            extractedBytes = 200L,
            totalBytes = 300L,
            isComplete = false,
            extractedPaths = emptyList()
        )
        val progress3 = UncompressProgress(
            currentFile = "file3.txt",
            extractedFiles = 3,
            totalFiles = 3,
            extractedBytes = 300L,
            totalBytes = 300L,
            isComplete = true,
            extractedPaths = listOf("$testTargetDir/file1.txt", "$testTargetDir/file2.txt", "$testTargetDir/file3.txt")
        )

        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots, any())
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

    @Test
    fun `every batch of extracted paths is scanned, not just the last emission`() = runTest {
        val zipInfo = ZipInfo(entryCount = 2, isEncrypted = false)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        val batch = UncompressProgress(
            currentFile = "file1.txt",
            extractedFiles = 1,
            totalFiles = 2,
            extractedBytes = 100L,
            totalBytes = 200L,
            isComplete = false,
            extractedPaths = listOf("$testTargetDir/file1.txt")
        )
        val completion = UncompressProgress(
            currentFile = "",
            extractedFiles = 2,
            totalFiles = 2,
            extractedBytes = 200L,
            totalBytes = 200L,
            isComplete = true,
            extractedPaths = listOf("$testTargetDir/file2.txt")
        )
        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots, any())
        } returns flowOf(batch, completion)

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        handler.confirmUncompress()
        testDispatcher.scheduler.advanceUntilIdle()

        // The repository hands the paths over in batches so it does not have to hold one per
        // extracted file. Scanning only the final emission would leave every earlier batch out of
        // MediaStore until the next full media scan.
        verify { MediaStoreUtil.scanFiles(context, listOf("$testTargetDir/file1.txt")) }
        verify { MediaStoreUtil.scanFiles(context, listOf("$testTargetDir/file2.txt")) }
    }

    @Test
    fun `a rolled back extraction drops the media store rows scanned for it`() = runTest {
        val zipInfo = ZipInfo(entryCount = 2, isEncrypted = false)
        coEvery { fileRepository.getZipInfo(testZipFile.path) } returns zipInfo

        val batch = UncompressProgress(
            currentFile = "photos/file1.txt",
            extractedFiles = 1,
            totalFiles = 2,
            extractedBytes = 100L,
            totalBytes = 200L,
            isComplete = false,
            extractedPaths = listOf("$testTargetDir/photos/file1.txt")
        )
        coEvery {
            fileRepository.uncompressFile(testZipFile.path, testTargetDir, null, testAllowedRoots, any())
        } answers {
            val onRolledBack = arg<suspend (List<String>) -> Unit>(4)
            flow {
                emit(batch)
                onRolledBack(listOf("$testTargetDir/photos"))
                throw ZipBombException("Extraction exceeded maximum allowed size")
            }
        }

        val handler = createHandler()
        handler.showUncompressDialog(testZipFile)
        testDispatcher.scheduler.advanceUntilIdle()

        handler.confirmUncompress()
        testDispatcher.scheduler.advanceUntilIdle()

        // The batch was scanned while the extraction ran and the rollback then deleted it. Without
        // the rows going the same way, galleries keep offering files that are no longer there.
        verify { MediaStoreUtil.scanFiles(context, listOf("$testTargetDir/photos/file1.txt")) }
        coVerify { MediaStoreUtil.notifyTreeDeleted(context, listOf("$testTargetDir/photos")) }
    }
}
