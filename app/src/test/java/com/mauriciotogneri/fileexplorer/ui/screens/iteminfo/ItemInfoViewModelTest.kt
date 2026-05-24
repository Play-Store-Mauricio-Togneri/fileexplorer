package com.mauriciotogneri.fileexplorer.ui.screens.iteminfo

import android.app.Application
import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
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
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
        unmockkObject(ErrorReporter)
        unmockkObject(MediaStoreUtil)
        unmockkObject(IntentUtil)
    }

    private fun createViewModel(filePath: String): ItemInfoViewModel {
        return ItemInfoViewModel(
            filePath = filePath,
            application = application,
            fileRepository = fileRepository,
            storageRepository = storageRepository
        )
    }

    @Test
    fun `loads file info for existing file`() = runTest {
        val testFile = File(tempDir, "test_${System.nanoTime()}.txt")
        testFile.writeText("Hello World")

        val viewModel = createViewModel(testFile.absolutePath)
        testDispatcher.scheduler.advanceUntilIdle()

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
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse(state.error)
        assertNotNull(state.file)
        assertTrue(state.file?.isDirectory ?: false)
    }


    @Test
    fun `sets error state for non-existing file`() = runTest {
        val viewModel = createViewModel("/non/existing/path_${System.nanoTime()}.txt")
        testDispatcher.scheduler.advanceUntilIdle()

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
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.onOpenFile()
            testDispatcher.scheduler.advanceUntilIdle()

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
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.onOpenFile()
            testDispatcher.scheduler.advanceUntilIdle()

            expectNoEvents()
        }
    }

    @Test
    fun `onOpenFile does nothing when file is null`() = runTest {
        val viewModel = createViewModel("/non/existing/path_${System.nanoTime()}.txt")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.onOpenFile()
            testDispatcher.scheduler.advanceUntilIdle()

            expectNoEvents()
        }
    }

    @Test
    fun `file metadata is null for plain text file`() = runTest {
        val testFile = File(tempDir, "plain_${System.nanoTime()}.txt")
        testFile.writeText("Plain text content")

        val viewModel = createViewModel(testFile.absolutePath)
        testDispatcher.scheduler.advanceUntilIdle()

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
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.folderSize)
    }

    @Test
    fun `cancelUncompression clears progress`() = runTest {
        val testFile = File(tempDir, "cancel_test_${System.nanoTime()}.txt")
        testFile.writeText("Content")

        val viewModel = createViewModel(testFile.absolutePath)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.cancelUncompression()

        assertNull(viewModel.state.value.uncompressProgress)
    }

    @Test
    fun `dismissUncompressDialog can be called safely`() = runTest {
        val testFile = File(tempDir, "dismiss_test_${System.nanoTime()}.txt")
        testFile.writeText("Content")

        val viewModel = createViewModel(testFile.absolutePath)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissUncompressDialog()

        assertNull(viewModel.state.value.itemToUncompress)
        assertEquals(0, viewModel.state.value.uncompressEntryCount)
    }
}
