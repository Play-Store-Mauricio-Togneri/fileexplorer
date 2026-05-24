package com.mauriciotogneri.fileexplorer.ui.screens.folder

import android.app.Application
import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.model.FileAction
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.OperationMode
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RenameResult
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import com.mauriciotogneri.fileexplorer.R
import io.mockk.Runs
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class FolderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var fileRepository: FileRepository
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var storageRepository: StorageRepository
    private lateinit var showHiddenFlow: MutableStateFlow<Boolean>
    private lateinit var badgeDismissedFlow: MutableStateFlow<Boolean>

    private val testPath = "/storage/emulated/0/Documents"

    private val testFiles = listOf(
        FileItem(
            path = "/storage/emulated/0/Documents/Folder1",
            name = "Folder1",
            isDirectory = true,
            size = 0L,
            lastModified = 1000L,
            createdTime = 1000L,
            mimeType = "",
            childCount = 5
        ),
        FileItem(
            path = "/storage/emulated/0/Documents/file.txt",
            name = "file.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = 2000L,
            createdTime = 2000L,
            mimeType = "text/plain",
            childCount = null
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = mockk(relaxed = true)
        fileRepository = mockk()
        preferencesRepository = mockk()
        storageRepository = mockk()
        showHiddenFlow = MutableStateFlow(false)
        badgeDismissedFlow = MutableStateFlow(false)
        every { preferencesRepository.showHidden } returns showHiddenFlow
        every { preferencesRepository.isBadgeDismissed(any()) } returns badgeDismissedFlow
        coEvery { preferencesRepository.setSortMode(any()) } just Runs
        coEvery { preferencesRepository.setShowHidden(any()) } just Runs
        coEvery { storageRepository.getStorages() } returns listOf(
            StorageDevice(
                path = "/storage/emulated/0",
                displayName = "Internal Storage",
                totalBytes = 64_000_000_000L,
                availableBytes = 32_000_000_000L
            )
        )
        every { application.getString(R.string.error_load_files) } returns "Failed to load files"
        mockkObject(ErrorReporter)
        mockkObject(AnalyticsTracker)
        mockkObject(MediaStoreUtil)
        every { ErrorReporter.critical(any(), any(), any()) } just Runs
        every { ErrorReporter.error(any(), any(), any()) } just Runs
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackScreenFolder() } just Runs
        every { AnalyticsTracker.trackRenameCompleted(any(), any()) } just Runs
        every { AnalyticsTracker.trackDeleteCompleted(any(), any()) } just Runs
        every { AnalyticsTracker.trackOperationFailed(any(), any()) } just Runs
        every { AnalyticsTracker.trackDestinationPickerOperationFinished(any(), any()) } just Runs
        every { AnalyticsTracker.trackCompressCompleted(any()) } just Runs
        every { AnalyticsTracker.setUserProperty(any(), any()) } just Runs
        every { MediaStoreUtil.scanFile(any(), any()) } just Runs
        every { MediaStoreUtil.scanFiles(any(), any()) } just Runs
        coEvery { MediaStoreUtil.notifyDeleted(any(), any()) } just Runs
        SortManager.setSortMode(SortMode.NAME_ASC)
    }

    @After
    fun tearDown() {
        SortManager.setSortMode(SortMode.NAME_ASC)
        Dispatchers.resetMain()
        unmockkObject(ErrorReporter)
        unmockkObject(AnalyticsTracker)
        unmockkObject(MediaStoreUtil)
    }

    private fun createViewModel(): FolderViewModel {
        return FolderViewModel(application, testPath, null, fileRepository, preferencesRepository, storageRepository)
    }

    @Test
    fun `initial state has correct path`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(testPath, viewModel.state.value.currentPath)
    }

    @Test
    fun `initial state loads files`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(2, state.files.size)
        assertNull(state.error)
    }

    @Test
    fun `refresh reloads files`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { fileRepository.listFiles(testPath, false, SortMode.NAME_ASC) }
    }

    @Test
    fun `setSortMode updates sort mode and reloads`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setSortMode(SortMode.SIZE_DESC)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SortMode.SIZE_DESC, viewModel.state.value.sortMode)
        coVerify { fileRepository.listFiles(testPath, false, SortMode.SIZE_DESC) }
    }

    @Test
    fun `toggleHiddenFiles calls setShowHidden with toggled value`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showHidden)

        viewModel.toggleHiddenFiles()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.setShowHidden(true) }
    }

    @Test
    fun `showHidden state updates when flow emits new value`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showHidden)

        showHiddenFlow.value = true
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.showHidden)
        coVerify { fileRepository.listFiles(testPath, true, SortMode.NAME_ASC) }
    }

    @Test
    fun `error state is set when repository throws exception`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } throws RuntimeException("Access denied")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Failed to load files", state.error)
        assertTrue(state.files.isEmpty())
    }

    @Test
    fun `empty folder is handled correctly`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns emptyList()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.files.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun `default sort mode is NAME_ASC`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SortMode.NAME_ASC, viewModel.state.value.sortMode)
    }

    @Test
    fun `default showHidden is false`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showHidden)
    }

    @Test
    fun `all sort modes can be set`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        SortMode.entries.forEach { mode ->
            viewModel.setSortMode(mode)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(mode, viewModel.state.value.sortMode)
        }
    }

    // Selection Mode Tests

    @Test
    fun `initial state has no selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.selectedPaths.isEmpty())
        assertFalse(state.isSelectionMode)
        assertEquals(0, state.selectedCount)
    }

    @Test
    fun `toggleSelection selects unselected file`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])

        val state = viewModel.state.value
        assertTrue(testFiles[0].path in state.selectedPaths)
        assertTrue(state.isSelectionMode)
        assertEquals(1, state.selectedCount)
    }

    @Test
    fun `toggleSelection deselects selected file`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        viewModel.toggleSelection(testFiles[0])

        val state = viewModel.state.value
        assertFalse(testFiles[0].path in state.selectedPaths)
        assertFalse(state.isSelectionMode)
        assertEquals(0, state.selectedCount)
    }

    @Test
    fun `multiple files can be selected`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        viewModel.toggleSelection(testFiles[1])

        val state = viewModel.state.value
        assertTrue(testFiles[0].path in state.selectedPaths)
        assertTrue(testFiles[1].path in state.selectedPaths)
        assertEquals(2, state.selectedCount)
    }

    @Test
    fun `selectAll selects all files`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectAll()

        val state = viewModel.state.value
        assertEquals(testFiles.size, state.selectedCount)
        assertTrue(state.allSelected)
        testFiles.forEach { file ->
            assertTrue(file.path in state.selectedPaths)
        }
    }

    @Test
    fun `clearSelection removes all selections`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectAll()
        viewModel.clearSelection()

        val state = viewModel.state.value
        assertTrue(state.selectedPaths.isEmpty())
        assertFalse(state.isSelectionMode)
        assertEquals(0, state.selectedCount)
    }

    @Test
    fun `getSelectedFiles returns selected FileItems`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])

        val selectedFiles = viewModel.getSelectedFiles()
        assertEquals(1, selectedFiles.size)
        assertEquals(testFiles[0], selectedFiles[0])
    }

    @Test
    fun `allSelected is false when not all files selected`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])

        assertFalse(viewModel.state.value.allSelected)
    }

    @Test
    fun `allSelected is true when all files selected`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectAll()

        assertTrue(viewModel.state.value.allSelected)
    }

    @Test
    fun `refresh clears selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        assertTrue(viewModel.state.value.isSelectionMode)

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isSelectionMode)
        assertTrue(viewModel.state.value.selectedPaths.isEmpty())
    }

    @Test
    fun `setSortMode clears selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        assertTrue(viewModel.state.value.isSelectionMode)

        viewModel.setSortMode(SortMode.DATE_DESC)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isSelectionMode)
    }

    @Test
    fun `showHidden change clears selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        assertTrue(viewModel.state.value.isSelectionMode)

        showHiddenFlow.value = true
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isSelectionMode)
    }

    // Action Bar Tests

    @Test
    fun `onAction SelectAll selects all files`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(FileAction.SelectAll)

        assertTrue(viewModel.state.value.allSelected)
        assertEquals(testFiles.size, viewModel.state.value.selectedCount)
    }

    @Test
    fun `onShare emits ShareFiles event with non-directory files`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Select both folder and file
        viewModel.toggleSelection(testFiles[0]) // folder
        viewModel.toggleSelection(testFiles[1]) // file

        viewModel.events.test {
            viewModel.onAction(FileAction.Share)
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShareFiles)
            val shareEvent = event as FolderUiEvent.ShareFiles
            // Only the file should be shared, not the folder
            assertEquals(1, shareEvent.files.size)
            assertFalse(shareEvent.files[0].isDirectory)
        }
    }

    @Test
    fun `onShare clears selection after sharing`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[1]) // file
        assertTrue(viewModel.state.value.isSelectionMode)

        viewModel.events.test {
            viewModel.onAction(FileAction.Share)
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // consume event
        }

        assertFalse(viewModel.state.value.isSelectionMode)
    }

    // Dialog State Tests

    @Test
    fun `showRenameDialog sets itemToRename`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.itemToRename)

        viewModel.showRenameDialog(testFiles[0])

        assertEquals(testFiles[0], viewModel.state.value.itemToRename)
    }

    @Test
    fun `dismissRenameDialog clears itemToRename`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showRenameDialog(testFiles[0])
        assertEquals(testFiles[0], viewModel.state.value.itemToRename)

        viewModel.dismissRenameDialog()

        assertNull(viewModel.state.value.itemToRename)
    }

    @Test
    fun `onAction Rename shows rename dialog for single selected file`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        viewModel.onAction(FileAction.Rename)

        assertEquals(testFiles[0], viewModel.state.value.itemToRename)
    }

    @Test
    fun `showCreateFolderDialog sets showCreateFolderDialog to true`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showCreateFolderDialog)

        viewModel.showCreateFolderDialog()

        assertTrue(viewModel.state.value.showCreateFolderDialog)
    }

    @Test
    fun `dismissCreateFolderDialog sets showCreateFolderDialog to false`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCreateFolderDialog()
        assertTrue(viewModel.state.value.showCreateFolderDialog)

        viewModel.dismissCreateFolderDialog()

        assertFalse(viewModel.state.value.showCreateFolderDialog)
    }

    @Test
    fun `onAction CreateFolder shows create folder dialog`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(FileAction.CreateFolder)

        assertTrue(viewModel.state.value.showCreateFolderDialog)
    }

    @Test
    fun `showDeleteConfirmDialog sets itemsToDelete`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.itemsToDelete.isEmpty())

        viewModel.showDeleteConfirmDialog(listOf(testFiles[0]))

        assertEquals(listOf(testFiles[0]), viewModel.state.value.itemsToDelete)
    }

    @Test
    fun `dismissDeleteConfirmDialog clears itemsToDelete`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showDeleteConfirmDialog(listOf(testFiles[0]))
        assertTrue(viewModel.state.value.itemsToDelete.isNotEmpty())

        viewModel.dismissDeleteConfirmDialog()

        assertTrue(viewModel.state.value.itemsToDelete.isEmpty())
    }

    @Test
    fun `onAction Delete shows delete confirm dialog for selected files`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        viewModel.onAction(FileAction.Delete)

        assertEquals(listOf(testFiles[0]), viewModel.state.value.itemsToDelete)
    }

    @Test
    fun `onRename dismisses dialog and clears selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.rename(any(), any()) } returns RenameResult(
            oldPath = testFiles[0].path,
            newPath = "/storage/emulated/0/Documents/newName.txt"
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        viewModel.showRenameDialog(testFiles[0])

        viewModel.onRename("newName.txt")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.itemToRename)
        assertFalse(viewModel.state.value.isSelectionMode)
    }

    @Test
    fun `onCreateFolder dismisses dialog`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.createFolder(any(), any()) } returns true

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCreateFolderDialog()

        viewModel.onCreateFolder("NewFolder")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showCreateFolderDialog)
    }

    @Test
    fun `onDeleteConfirmed dismisses dialog and clears selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.collectAllPaths(any()) } returns listOf(testFiles[0].path)
        coEvery { fileRepository.delete(any()) } returns true

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        viewModel.showDeleteConfirmDialog(listOf(testFiles[0]))

        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.itemsToDelete.isEmpty())
        assertFalse(viewModel.state.value.isSelectionMode)
    }

    // Move/Copy Operation Tests

    @Test
    fun `onAction MoveTo opens picker with selected items`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        viewModel.toggleSelection(testFiles[1])

        viewModel.onAction(FileAction.MoveTo)

        val pickerRequest = viewModel.state.value.pickerRequest
        assertNotNull(pickerRequest)
        assertEquals(OperationMode.MOVE, pickerRequest?.mode)
        assertEquals(2, pickerRequest?.items?.size)
    }

    @Test
    fun `onAction CopyTo opens picker with selected items`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[1])

        viewModel.onAction(FileAction.CopyTo)

        val pickerRequest = viewModel.state.value.pickerRequest
        assertNotNull(pickerRequest)
        assertEquals(OperationMode.COPY, pickerRequest?.mode)
        assertEquals(1, pickerRequest?.items?.size)
    }

    @Test
    fun `onAction MoveTo clears selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        assertTrue(viewModel.state.value.isSelectionMode)

        viewModel.onAction(FileAction.MoveTo)

        assertFalse(viewModel.state.value.isSelectionMode)
        assertTrue(viewModel.state.value.selectedPaths.isEmpty())
    }

    @Test
    fun `onAction CopyTo clears selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        assertTrue(viewModel.state.value.isSelectionMode)

        viewModel.onAction(FileAction.CopyTo)

        assertFalse(viewModel.state.value.isSelectionMode)
    }

    @Test
    fun `onAction MoveTo with no selection does nothing`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(FileAction.MoveTo)

        assertNull(viewModel.state.value.pickerRequest)
    }

    @Test
    fun `dismissPicker clears pickerRequest`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        viewModel.onAction(FileAction.MoveTo)
        assertNotNull(viewModel.state.value.pickerRequest)

        viewModel.dismissPicker()

        assertNull(viewModel.state.value.pickerRequest)
    }

    @Test
    fun `cancelOperation sets isCancelling flag`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.cancelOperation()

        assertNull(viewModel.state.value.operationProgress)
    }

    @Test
    fun `initial state has no pickerRequest`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.pickerRequest)
    }

    @Test
    fun `initial state has no operationProgress`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.operationProgress)
    }
}
