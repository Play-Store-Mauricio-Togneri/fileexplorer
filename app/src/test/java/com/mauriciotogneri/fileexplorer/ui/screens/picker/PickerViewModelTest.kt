package com.mauriciotogneri.fileexplorer.ui.screens.picker

import android.app.Application
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.OperationMode
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PickerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var fileRepository: FileRepository
    private lateinit var storageRepository: StorageRepository
    private lateinit var tempDir: File
    private lateinit var tempDir2: File

    private lateinit var internalStorage: StorageDevice
    private lateinit var sdCard: StorageDevice
    private lateinit var testSourceItems: List<FileItem>
    private lateinit var testFolders: List<FileItem>

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        tempDir = File(System.getProperty("java.io.tmpdir"), "picker_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        tempDir2 = File(System.getProperty("java.io.tmpdir"), "picker_test2_${System.currentTimeMillis()}")
        tempDir2.mkdirs()

        val downloadsDir = File(tempDir, "Downloads")
        downloadsDir.mkdirs()
        val picturesDir = File(tempDir, "Pictures")
        picturesDir.mkdirs()
        val documentsDir = File(tempDir, "Documents")
        documentsDir.mkdirs()
        File(documentsDir, "file.txt").createNewFile()

        internalStorage = StorageDevice(
            path = tempDir.absolutePath,
            displayName = "Internal Storage",
            totalBytes = 64_000_000_000L,
            availableBytes = 32_000_000_000L
        )

        sdCard = StorageDevice(
            path = tempDir2.absolutePath,
            displayName = "SD Card",
            totalBytes = 32_000_000_000L,
            availableBytes = 16_000_000_000L
        )

        testSourceItems = listOf(
            FileItem(
                path = File(documentsDir, "file.txt").absolutePath,
                name = "file.txt",
                isDirectory = false,
                size = 1024L,
                lastModified = 1000L,
                createdTime = 1000L,
                mimeType = "text/plain",
                childCount = null
            )
        )

        testFolders = listOf(
            FileItem(
                path = downloadsDir.absolutePath,
                name = "Downloads",
                isDirectory = true,
                size = 0L,
                lastModified = 1000L,
                createdTime = 1000L,
                mimeType = "",
                childCount = 10
            ),
            FileItem(
                path = picturesDir.absolutePath,
                name = "Pictures",
                isDirectory = true,
                size = 0L,
                lastModified = 2000L,
                createdTime = 2000L,
                mimeType = "",
                childCount = 20
            )
        )

        application = mockk(relaxed = true)
        fileRepository = mockk()
        storageRepository = mockk()

        every { application.getString(R.string.validation_same_folder_move) } returns "Cannot move to the same folder"
        every { application.getString(R.string.validation_same_folder_copy) } returns "Cannot copy to the same folder"
        every { application.getString(R.string.validation_recursive_move) } returns "Cannot move a folder into itself"
        every { application.getString(R.string.validation_recursive_copy) } returns "Cannot copy a folder into itself"

        mockkObject(AnalyticsTracker)
        mockkObject(ErrorReporter)
        every { AnalyticsTracker.trackDestinationPickerStorageSelected() } just Runs
        every { AnalyticsTracker.trackDestinationPickerFolderNavigated() } just Runs
        every { AnalyticsTracker.trackDestinationPickerNavigatedUp() } just Runs
        every { AnalyticsTracker.trackDestinationPickerFolderCreated() } just Runs
        every { ErrorReporter.error(any(), any()) } just Runs
        every { ErrorReporter.error(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        advanceAndWait()
        Dispatchers.resetMain()
        unmockkObject(AnalyticsTracker)
        unmockkObject(ErrorReporter)
        tempDir.deleteRecursively()
        tempDir2.deleteRecursively()
    }

    private fun advanceAndWait() {
        testDispatcher.scheduler.advanceUntilIdle()
        Thread.sleep(100)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun createViewModel(
        sourceItems: List<FileItem> = testSourceItems,
        operationMode: OperationMode = OperationMode.MOVE,
        storages: List<StorageDevice> = listOf(internalStorage)
    ): PickerViewModel {
        coEvery { storageRepository.getStorages() } returns storages
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFolders

        return PickerViewModel(
            application = application,
            fileRepository = fileRepository,
            storageRepository = storageRepository,
            sourceItems = sourceItems,
            operationMode = operationMode,
            sortMode = SortMode.NAME_ASC,
            showHidden = false
        )
    }

    @Test
    fun `single storage navigates directly to storage root`() = runTest {
        val viewModel = createViewModel(storages = listOf(internalStorage))
        advanceAndWait()

        assertEquals(internalStorage.path, viewModel.currentPath.value)
    }

    @Test
    fun `navigateToFolder updates current path`() = runTest {
        val viewModel = createViewModel()
        advanceAndWait()

        viewModel.navigateToFolder(testFolders[0])
        advanceAndWait()

        assertEquals(testFolders[0].path, viewModel.currentPath.value)
    }

    @Test
    fun `navigateUp from subfolder goes to parent`() = runTest {
        val subFolder = File(tempDir, "Documents/SubFolder")
        subFolder.mkdirs()

        val viewModel = createViewModel()
        advanceAndWait()

        viewModel.navigateToPath(subFolder.absolutePath)
        advanceAndWait()

        val result = viewModel.navigateUp()
        advanceAndWait()

        assertTrue(result)
        assertEquals(File(tempDir, "Documents").absolutePath, viewModel.currentPath.value)
    }

    @Test
    fun `navigateUp from storage root with single storage returns false`() = runTest {
        val viewModel = createViewModel(storages = listOf(internalStorage))
        advanceAndWait()

        val result = viewModel.navigateUp()

        assertFalse(result)
    }

    @Test
    fun `showCreateFolderDialog sets state to true`() = runTest {
        val viewModel = createViewModel()
        advanceAndWait()

        assertFalse(viewModel.showCreateFolderDialog.value)

        viewModel.showCreateFolderDialog()

        assertTrue(viewModel.showCreateFolderDialog.value)
    }

    @Test
    fun `dismissCreateFolderDialog sets state to false`() = runTest {
        val viewModel = createViewModel()
        advanceAndWait()

        viewModel.showCreateFolderDialog()
        viewModel.dismissCreateFolderDialog()

        assertFalse(viewModel.showCreateFolderDialog.value)
    }

    @Test
    fun `storages are loaded on init`() = runTest {
        val viewModel = createViewModel(storages = listOf(internalStorage, sdCard))
        advanceAndWait()

        assertEquals(2, viewModel.storages.value.size)
    }

    @Test
    fun `navigateToPath updates current path`() = runTest {
        val viewModel = createViewModel()
        advanceAndWait()

        val newPath = File(tempDir, "Downloads").absolutePath
        viewModel.navigateToPath(newPath)
        advanceAndWait()

        assertEquals(newPath, viewModel.currentPath.value)
    }

    @Test
    fun `navigateToStorage sets current path`() = runTest {
        val viewModel = createViewModel(storages = listOf(internalStorage, sdCard))
        advanceAndWait()

        viewModel.navigateToStorage(sdCard)
        advanceAndWait()

        assertEquals(sdCard.path, viewModel.currentPath.value)
    }
}
