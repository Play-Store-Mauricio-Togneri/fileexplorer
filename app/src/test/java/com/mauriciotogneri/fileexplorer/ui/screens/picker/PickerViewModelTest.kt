package com.mauriciotogneri.fileexplorer.ui.screens.picker

import android.content.Context
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.OperationMode
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
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

@OptIn(ExperimentalCoroutinesApi::class)
class PickerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var fileRepository: FileRepository
    private lateinit var storageRepository: StorageRepository

    private val internalStorage = StorageDevice(
        path = "/storage/emulated/0",
        displayName = "Internal Storage",
        totalBytes = 64_000_000_000L,
        availableBytes = 32_000_000_000L
    )

    private val sdCard = StorageDevice(
        path = "/storage/sdcard1",
        displayName = "SD Card",
        totalBytes = 32_000_000_000L,
        availableBytes = 16_000_000_000L
    )

    private val testSourceItems = listOf(
        FileItem(
            path = "/storage/emulated/0/Documents/file.txt",
            name = "file.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = 1000L,
            createdTime = 1000L,
            mimeType = "text/plain",
            childCount = null
        )
    )

    private val testSourceFolder = listOf(
        FileItem(
            path = "/storage/emulated/0/Documents/MyFolder",
            name = "MyFolder",
            isDirectory = true,
            size = 0L,
            lastModified = 1000L,
            createdTime = 1000L,
            mimeType = "",
            childCount = 5
        )
    )

    private val testFolders = listOf(
        FileItem(
            path = "/storage/emulated/0/Downloads",
            name = "Downloads",
            isDirectory = true,
            size = 0L,
            lastModified = 1000L,
            createdTime = 1000L,
            mimeType = "",
            childCount = 10
        ),
        FileItem(
            path = "/storage/emulated/0/Pictures",
            name = "Pictures",
            isDirectory = true,
            size = 0L,
            lastModified = 2000L,
            createdTime = 2000L,
            mimeType = "",
            childCount = 20
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk()
        fileRepository = mockk()
        storageRepository = mockk()

        every { context.getString(R.string.validation_same_folder_move) } returns "Cannot move to the same folder"
        every { context.getString(R.string.validation_same_folder_copy) } returns "Cannot copy to the same folder"
        every { context.getString(R.string.validation_recursive_move) } returns "Cannot move a folder into itself"
        every { context.getString(R.string.validation_recursive_copy) } returns "Cannot copy a folder into itself"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        sourceItems: List<FileItem> = testSourceItems,
        operationMode: OperationMode = OperationMode.MOVE,
        storages: List<StorageDevice> = listOf(internalStorage)
    ): PickerViewModel {
        coEvery { storageRepository.getStorages() } returns storages
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFolders

        return PickerViewModel(
            context = context,
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
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(internalStorage.path, viewModel.currentPath.value)
        assertFalse(viewModel.showStorageSelector.value)
    }

    @Test
    fun `multiple storages shows storage selector`() = runTest {
        val viewModel = createViewModel(storages = listOf(internalStorage, sdCard))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.currentPath.value)
        assertTrue(viewModel.showStorageSelector.value)
    }

    @Test
    fun `navigateToStorage sets current path`() = runTest {
        val viewModel = createViewModel(storages = listOf(internalStorage, sdCard))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigateToStorage(sdCard)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(sdCard.path, viewModel.currentPath.value)
        assertFalse(viewModel.showStorageSelector.value)
    }

    @Test
    fun `navigateToFolder updates current path`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigateToFolder(testFolders[0])
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(testFolders[0].path, viewModel.currentPath.value)
    }

    @Test
    fun `navigateUp from subfolder goes to parent`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigateToPath("/storage/emulated/0/Documents/SubFolder")
        testDispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.navigateUp()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(result)
        assertEquals("/storage/emulated/0/Documents", viewModel.currentPath.value)
    }

    @Test
    fun `navigateUp from storage root with multiple storages goes to selector`() = runTest {
        val viewModel = createViewModel(storages = listOf(internalStorage, sdCard))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigateToStorage(internalStorage)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.navigateUp()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(result)
        assertNull(viewModel.currentPath.value)
        assertTrue(viewModel.showStorageSelector.value)
    }

    @Test
    fun `navigateUp from storage root with single storage returns false`() = runTest {
        val viewModel = createViewModel(storages = listOf(internalStorage))
        testDispatcher.scheduler.advanceUntilIdle()

        val result = viewModel.navigateUp()

        assertFalse(result)
    }

    @Test
    fun `showCreateFolderDialog sets state to true`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.showCreateFolderDialog.value)

        viewModel.showCreateFolderDialog()

        assertTrue(viewModel.showCreateFolderDialog.value)
    }

    @Test
    fun `dismissCreateFolderDialog sets state to false`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCreateFolderDialog()
        viewModel.dismissCreateFolderDialog()

        assertFalse(viewModel.showCreateFolderDialog.value)
    }

    @Test
    fun `createFolder navigates to new folder on success`() = runTest {
        coEvery { fileRepository.createFolder(any(), any()) } returns true

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.createFolder("NewFolder")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("/storage/emulated/0/NewFolder", viewModel.currentPath.value)
        assertFalse(viewModel.showCreateFolderDialog.value)
    }

    @Test
    fun `createFolder dismisses dialog on failure`() = runTest {
        coEvery { fileRepository.createFolder(any(), any()) } returns false

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCreateFolderDialog()
        viewModel.createFolder("NewFolder")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.showCreateFolderDialog.value)
    }

    @Test
    fun `getExistingNames returns folder names`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val names = viewModel.getExistingNames()

        assertTrue(names.contains("Downloads"))
        assertTrue(names.contains("Pictures"))
    }

    @Test
    fun `getCurrentStorageRoot returns correct storage`() = runTest {
        val viewModel = createViewModel(storages = listOf(internalStorage, sdCard))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigateToStorage(internalStorage)
        testDispatcher.scheduler.advanceUntilIdle()

        val root = viewModel.getCurrentStorageRoot()

        assertEquals(internalStorage, root)
    }

    @Test
    fun `isLoading is false after loading completes`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
    }

    // Validation Tests

    @Test
    fun `validation error when moving to same folder`() = runTest {
        val viewModel = createViewModel(
            sourceItems = testSourceItems,
            operationMode = OperationMode.MOVE
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigateToPath("/storage/emulated/0/Documents")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Cannot move to the same folder", viewModel.validationError.value)
        assertFalse(viewModel.isValidDestination.value)
    }

    @Test
    fun `validation error when copying to same folder`() = runTest {
        val viewModel = createViewModel(
            sourceItems = testSourceItems,
            operationMode = OperationMode.COPY
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigateToPath("/storage/emulated/0/Documents")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Cannot copy to the same folder", viewModel.validationError.value)
        assertFalse(viewModel.isValidDestination.value)
    }

    @Test
    fun `validation error when moving folder into itself`() = runTest {
        val viewModel = createViewModel(
            sourceItems = testSourceFolder,
            operationMode = OperationMode.MOVE
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigateToPath("/storage/emulated/0/Documents/MyFolder/SubFolder")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Cannot move a folder into itself", viewModel.validationError.value)
        assertFalse(viewModel.isValidDestination.value)
    }

    @Test
    fun `validation error when copying folder into itself`() = runTest {
        val viewModel = createViewModel(
            sourceItems = testSourceFolder,
            operationMode = OperationMode.COPY
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigateToPath("/storage/emulated/0/Documents/MyFolder/SubFolder")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Cannot copy a folder into itself", viewModel.validationError.value)
        assertFalse(viewModel.isValidDestination.value)
    }

    @Test
    fun `no validation error for valid destination`() = runTest {
        val viewModel = createViewModel(
            sourceItems = testSourceItems,
            operationMode = OperationMode.MOVE
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigateToPath("/storage/emulated/0/Downloads")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.validationError.value)
        assertTrue(viewModel.isValidDestination.value)
    }

    @Test
    fun `isValidDestination false when currentPath is null`() = runTest {
        val viewModel = createViewModel(storages = listOf(internalStorage, sdCard))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.currentPath.value)
        assertFalse(viewModel.isValidDestination.value)
    }

    @Test
    fun `validation checks all source items for same folder`() = runTest {
        val multipleSourceItems = listOf(
            FileItem(
                path = "/storage/emulated/0/Documents/file1.txt",
                name = "file1.txt",
                isDirectory = false,
                size = 1024L,
                lastModified = 1000L,
                createdTime = 1000L,
                mimeType = "text/plain",
                childCount = null
            ),
            FileItem(
                path = "/storage/emulated/0/Pictures/file2.txt",
                name = "file2.txt",
                isDirectory = false,
                size = 1024L,
                lastModified = 1000L,
                createdTime = 1000L,
                mimeType = "text/plain",
                childCount = null
            )
        )

        val viewModel = createViewModel(
            sourceItems = multipleSourceItems,
            operationMode = OperationMode.MOVE
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.navigateToPath("/storage/emulated/0/Pictures")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Cannot move to the same folder", viewModel.validationError.value)
    }
}
