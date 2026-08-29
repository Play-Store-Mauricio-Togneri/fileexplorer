package com.mauriciotogneri.fileexplorer.ui.screens.iteminfo

import android.app.Application
import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.ZipInfo
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ItemInfoViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var fileRepository: FileRepository
    private lateinit var storageRepository: StorageRepository
    private lateinit var tempDir: File

    private val testStorage = StorageDevice(
        path = "/storage/emulated/0",
        displayName = "Internal Storage",
        totalBytes = 64_000_000_000L,
        availableBytes = 32_000_000_000L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        tempDir = File(System.getProperty("java.io.tmpdir"), "iteminfo_test_${System.nanoTime()}")
        tempDir.mkdirs()

        application = mockk(relaxed = true)
        fileRepository = mockk(relaxed = true)
        storageRepository = mockk()

        coEvery { storageRepository.getStorages() } returns listOf(testStorage)

        mockkObject(ErrorReporter)
        mockkObject(MediaStoreUtil)
        mockkObject(IntentUtil)
        every { ErrorReporter.error(any(), any()) } just Runs
        every { ErrorReporter.error(any(), any(), any()) } just Runs
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { MediaStoreUtil.scanFiles(any(), any()) } just Runs
        every { IntentUtil.trackRecentFile(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        advanceAndWait()
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
        unmockkObject(ErrorReporter)
        unmockkObject(MediaStoreUtil)
        unmockkObject(IntentUtil)
    }

    /**
     * Runs every coroutine to completion, background work included.
     *
     * This used to be `advanceUntilIdle()` around a `Thread.sleep(100)`, because the metadata load
     * ran on a hardcoded `Dispatchers.IO` that `setMain` cannot intercept — so the scheduler had no
     * idea it existed. That made every test in this file a race: slower than 100 ms under CI load
     * and it failed, faster and passing was never evidence the coroutine had actually run. The
     * ViewModel now takes [ItemInfoViewModel.ioDispatcher], so [testDispatcher] owns that work too
     * and idle means idle.
     */
    private fun advanceAndWait() {
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun createViewModel(filePath: String): ItemInfoViewModel {
        return ItemInfoViewModel(
            filePath = filePath,
            application = application,
            fileRepository = fileRepository,
            storageRepository = storageRepository,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `loads file info for existing file`() = runTest {
        val testFile = File(tempDir, "test_${System.nanoTime()}.txt")
        testFile.writeText("Hello World")

        val viewModel = createViewModel(testFile.absolutePath)
        advanceAndWait()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse(state.error)
        assertNotNull(state.file)
        assertEquals(testFile.name, state.file?.name)
        assertFalse(state.file?.isDirectory ?: true)
    }

    @Test
    fun `loads folder info for existing folder`() = runTest {
        val testFolder = File(tempDir, "TestFolder_${System.nanoTime()}")
        testFolder.mkdirs()
        File(testFolder, "file.txt").writeText("Content")

        val viewModel = createViewModel(testFolder.absolutePath)
        advanceAndWait()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse(state.error)
        assertNotNull(state.file)
        assertTrue(state.file?.isDirectory ?: false)
    }


    @Test
    fun `sets error state for non-existing file`() = runTest {
        val viewModel = createViewModel("/non/existing/path_${System.nanoTime()}.txt")
        advanceAndWait()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.error)
        assertNull(state.file)
    }

    @Test
    fun `onOpenFile emits event for file`() = runTest {
        val testFile = File(tempDir, "open_test_${System.nanoTime()}.txt")
        testFile.writeText("Hello World")

        val viewModel = createViewModel(testFile.absolutePath)
        advanceAndWait()

        viewModel.events.test {
            viewModel.onOpenFile()
            advanceAndWait()

            val event = awaitItem()
            assertTrue(event is ItemInfoUiEvent.OpenFile)
            assertEquals(testFile.name, (event as ItemInfoUiEvent.OpenFile).file.name)
        }
    }

    @Test
    fun `onOpenFile does nothing for folder`() = runTest {
        val testFolder = File(tempDir, "folder_open_test_${System.nanoTime()}")
        testFolder.mkdirs()

        val viewModel = createViewModel(testFolder.absolutePath)
        advanceAndWait()

        viewModel.events.test {
            viewModel.onOpenFile()
            advanceAndWait()

            expectNoEvents()
        }
    }

    @Test
    fun `onOpenFile does nothing when file is null`() = runTest {
        val viewModel = createViewModel("/non/existing/path_${System.nanoTime()}.txt")
        advanceAndWait()

        viewModel.events.test {
            viewModel.onOpenFile()
            advanceAndWait()

            expectNoEvents()
        }
    }

    @Test
    fun `file metadata is null for plain text file`() = runTest {
        val testFile = File(tempDir, "plain_${System.nanoTime()}.txt")
        testFile.writeText("Plain text content")

        val viewModel = createViewModel(testFile.absolutePath)
        advanceAndWait()

        val state = viewModel.state.value
        assertNull(state.imageMetadata)
        assertNull(state.audioMetadata)
        assertNull(state.videoMetadata)
        assertNull(state.pdfMetadata)
        assertNull(state.apkMetadata)
        assertNull(state.zipMetadata)
    }

    @Test
    fun `folder size is null for files`() = runTest {
        val testFile = File(tempDir, "size_null_test_${System.nanoTime()}.txt")
        testFile.writeText("Content")

        val viewModel = createViewModel(testFile.absolutePath)
        advanceAndWait()

        assertNull(viewModel.state.value.folderSize)
    }

    // Both uncompress entry points below are thin delegations to UncompressHandler. Driving them
    // from a fresh view model only reads back ItemInfoUiState defaults, which stays green even if
    // the delegation is severed — leaving this screen unable to cancel or dismiss an extraction.

    @Test
    fun `cancelUncompression stops a running extraction and clears progress`() = runTest {
        val zipFile = File(tempDir, "archive_${System.nanoTime()}.zip")
        zipFile.writeText("Content")
        val zip = FileItem.from(zipFile)

        coEvery { fileRepository.getZipInfo(zip.path) } returns ZipInfo(entryCount = 3, isEncrypted = false)

        var extractionStopped = false
        coEvery { fileRepository.uncompressFile(any(), any(), any(), any(), any()) } returns flow {
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
            // Hold the extraction open so the cancel lands mid-flight.
            try {
                awaitCancellation()
            } finally {
                extractionStopped = true
            }
        }

        val viewModel = createViewModel(zipFile.absolutePath)
        advanceAndWait()

        viewModel.showUncompressDialog(zip)
        advanceAndWait()
        viewModel.confirmUncompress()
        advanceAndWait()

        assertEquals("file1.txt", viewModel.state.value.uncompressProgress?.currentFile)

        viewModel.cancelUncompression()
        advanceAndWait()

        assertTrue("Cancel must stop the running extraction", extractionStopped)
        assertNull(viewModel.state.value.uncompressProgress)
    }

    @Test
    fun `dismissUncompressDialog clears the open dialog`() = runTest {
        val zipFile = File(tempDir, "archive_${System.nanoTime()}.zip")
        zipFile.writeText("Content")
        val zip = FileItem.from(zipFile)

        coEvery { fileRepository.getZipInfo(zip.path) } returns ZipInfo(entryCount = 7, isEncrypted = false)

        val viewModel = createViewModel(zipFile.absolutePath)
        advanceAndWait()

        viewModel.showUncompressDialog(zip)
        advanceAndWait()
        assertNotNull("The dialog must be open first", viewModel.state.value.itemToUncompress)
        assertEquals(7, viewModel.state.value.uncompressEntryCount)

        viewModel.dismissUncompressDialog()
        // The dialog state reaches the screen state through the handler's state collector.
        advanceAndWait()

        assertNull(viewModel.state.value.itemToUncompress)
        assertEquals(0, viewModel.state.value.uncompressEntryCount)
    }
}
