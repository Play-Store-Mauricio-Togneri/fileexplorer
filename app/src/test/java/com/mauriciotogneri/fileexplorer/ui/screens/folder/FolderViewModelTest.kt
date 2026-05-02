package com.mauriciotogneri.fileexplorer.ui.screens.folder

import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
class FolderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fileRepository: FileRepository

    private val testPath = "/storage/emulated/0/Documents"

    private val testFiles = listOf(
        FileItem(
            path = "/storage/emulated/0/Documents/Folder1",
            name = "Folder1",
            isDirectory = true,
            size = 0L,
            lastModified = 1000L,
            mimeType = "",
            childCount = 5
        ),
        FileItem(
            path = "/storage/emulated/0/Documents/file.txt",
            name = "file.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = 2000L,
            mimeType = "text/plain",
            childCount = null
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fileRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has correct path`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(testPath, viewModel.state.value.currentPath)
    }

    @Test
    fun `initial state loads files`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals(2, state.files.size)
        assertNull(state.error)
    }

    @Test
    fun `refresh reloads files`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { fileRepository.listFiles(testPath, false, SortMode.NAME_ASC) }
    }

    @Test
    fun `setSortMode updates sort mode and reloads`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setSortMode(SortMode.SIZE_DESC)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SortMode.SIZE_DESC, viewModel.state.value.sortMode)
        coVerify { fileRepository.listFiles(testPath, false, SortMode.SIZE_DESC) }
    }

    @Test
    fun `toggleHiddenFiles toggles and reloads`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showHidden)

        viewModel.toggleHiddenFiles()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.showHidden)
        coVerify { fileRepository.listFiles(testPath, true, SortMode.NAME_ASC) }
    }

    @Test
    fun `toggleHiddenFiles twice returns to original state`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleHiddenFiles()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.showHidden)

        viewModel.toggleHiddenFiles()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.state.value.showHidden)
    }

    @Test
    fun `error state is set when repository throws exception`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } throws RuntimeException("Access denied")

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Access denied", state.error)
        assertTrue(state.files.isEmpty())
    }

    @Test
    fun `empty folder is handled correctly`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns emptyList()

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.files.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun `default sort mode is NAME_ASC`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SortMode.NAME_ASC, viewModel.state.value.sortMode)
    }

    @Test
    fun `default showHidden is false`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.showHidden)
    }

    @Test
    fun `all sort modes can be set`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = FolderViewModel(testPath, fileRepository)
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

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.selectedPaths.isEmpty())
        assertFalse(state.isSelectionMode)
        assertEquals(0, state.selectedCount)
    }

    @Test
    fun `toggleSelection selects unselected file`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = FolderViewModel(testPath, fileRepository)
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

        val viewModel = FolderViewModel(testPath, fileRepository)
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

        val viewModel = FolderViewModel(testPath, fileRepository)
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

        val viewModel = FolderViewModel(testPath, fileRepository)
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

        val viewModel = FolderViewModel(testPath, fileRepository)
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

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])

        val selectedFiles = viewModel.getSelectedFiles()
        assertEquals(1, selectedFiles.size)
        assertEquals(testFiles[0], selectedFiles[0])
    }

    @Test
    fun `allSelected is false when not all files selected`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])

        assertFalse(viewModel.state.value.allSelected)
    }

    @Test
    fun `allSelected is true when all files selected`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectAll()

        assertTrue(viewModel.state.value.allSelected)
    }

    @Test
    fun `refresh clears selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = FolderViewModel(testPath, fileRepository)
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

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        assertTrue(viewModel.state.value.isSelectionMode)

        viewModel.setSortMode(SortMode.DATE_DESC)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isSelectionMode)
    }

    @Test
    fun `toggleHiddenFiles clears selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = FolderViewModel(testPath, fileRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        assertTrue(viewModel.state.value.isSelectionMode)

        viewModel.toggleHiddenFiles()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isSelectionMode)
    }
}
