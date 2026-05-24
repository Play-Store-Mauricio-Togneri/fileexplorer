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
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.lingala.zip4j.exception.ZipException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { ErrorReporter.error(any(), any(), any()) } just Runs
        every { MediaStoreUtil.scanFiles(any(), any()) } just Runs
        every { IntentUtil.trackRecentFile(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(ErrorReporter)
        unmockkObject(MediaStoreUtil)
        unmockkObject(IntentUtil)
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
    fun `cancelUncompression clears progress`() = runTest {
        val handler = createHandler()

        handler.cancelUncompression()

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
}
