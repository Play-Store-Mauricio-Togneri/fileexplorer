package com.mauriciotogneri.fileexplorer.ui.screens.folder

import android.app.Application
import android.os.StatFs
import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.model.FileAction
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.model.OperationMode
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.CompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.CopyProgress
import com.mauriciotogneri.fileexplorer.data.repository.DeleteProgress
import com.mauriciotogneri.fileexplorer.data.repository.DestinationNotWritableException
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.util.ERRNO_UNKNOWN
import com.mauriciotogneri.fileexplorer.data.repository.DeleteResult
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileTransferIOException
import com.mauriciotogneri.fileexplorer.data.repository.InsufficientStorageException
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RenameResult
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import com.mauriciotogneri.fileexplorer.R
import io.mockk.Runs
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FolderViewModelTest {
    // Stand-ins for OsConstants, whose every field reads 0 off device. Only distinctness matters
    // here; FileAccessTest is what asserts the real constants map to the causes below.
    private val EACCES = 13
    private val EROFS = 30


    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var fileRepository: FileRepository
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var storageRepository: StorageRepository
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var recentFilesRepository: RecentFilesRepository
    private lateinit var showHiddenFlow: MutableStateFlow<Boolean>
    private lateinit var folderSecondLineFlow: MutableStateFlow<FolderSecondLine>
    private lateinit var fileSecondLineFlow: MutableStateFlow<FileSecondLine>
    private lateinit var swipeLeftActionFlow: MutableStateFlow<SwipeAction>
    private lateinit var swipeRightActionFlow: MutableStateFlow<SwipeAction>
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
        favoritesRepository = mockk(relaxed = true)
        recentFilesRepository = mockk(relaxed = true)
        showHiddenFlow = MutableStateFlow(false)
        folderSecondLineFlow = MutableStateFlow(FolderSecondLine.ITEM_COUNT)
        fileSecondLineFlow = MutableStateFlow(FileSecondLine.SIZE)
        swipeLeftActionFlow = MutableStateFlow(SwipeAction.RENAME)
        swipeRightActionFlow = MutableStateFlow(SwipeAction.DELETE)
        badgeDismissedFlow = MutableStateFlow(false)
        every { preferencesRepository.showHidden } returns showHiddenFlow
        every { preferencesRepository.folderSecondLine } returns folderSecondLineFlow
        every { preferencesRepository.fileSecondLine } returns fileSecondLineFlow
        every { preferencesRepository.swipeLeftAction } returns swipeLeftActionFlow
        every { preferencesRepository.swipeRightAction } returns swipeRightActionFlow
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
        coEvery { fileRepository.countChildren(any(), any()) } returns 0
        mockkObject(ErrorReporter)
        mockkObject(AnalyticsTracker)
        mockkObject(MediaStoreUtil)
        every { ErrorReporter.critical(any(), any(), any()) } just Runs
        every { ErrorReporter.error(any(), any(), any()) } just Runs
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { ErrorReporter.setCount(any(), any()) } just Runs
        every { ErrorReporter.recordHeap() } just Runs
        every { AnalyticsTracker.trackScreenFolder() } just Runs
        every { AnalyticsTracker.trackRenameCompleted(any(), any()) } just Runs
        every { AnalyticsTracker.trackDeleteCompleted(any(), any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackOperationFailed(any(), any(), any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackDestinationPickerOperationFinished(any(), any()) } just Runs
        every { AnalyticsTracker.trackCompressCompleted(any()) } just Runs
        every { AnalyticsTracker.setUserProperty(any(), any()) } just Runs
        every { MediaStoreUtil.scanFile(any(), any()) } just Runs
        every { MediaStoreUtil.scanFiles(any(), any()) } just Runs
        coEvery { MediaStoreUtil.notifyDeleted(any(), any()) } just Runs
        coEvery { MediaStoreUtil.notifyTreeDeleted(any(), any()) } just Runs
        // StatFs is an Android class with no JVM stub; mock its constructor so executeOperation's
        // space pre-check returns ample free space instead of throwing.
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns Long.MAX_VALUE
        SortManager.setSortMode(SortMode.NAME_ASC)
    }

    @After
    fun tearDown() {
        SortManager.setSortMode(SortMode.NAME_ASC)
        Dispatchers.resetMain()
        unmockkObject(ErrorReporter)
        unmockkObject(AnalyticsTracker)
        unmockkObject(MediaStoreUtil)
        unmockkConstructor(StatFs::class)
    }

    private fun createViewModel(path: String = testPath): FolderViewModel {
        return FolderViewModel(
            application,
            path,
            null,
            fileRepository,
            preferencesRepository,
            storageRepository,
            favoritesRepository,
            recentFilesRepository,
            ioDispatcher = testDispatcher,
            countDispatcher = testDispatcher
        )
    }

    /**
     * The single emission a finished compression ends on, with the counts the tests below vary and
     * everything else fixed: the progress emissions before it carry no decision the ViewModel makes.
     */
    private fun compressCompletion(compressedFiles: Int, skippedFiles: Int) = CompressProgress(
        currentFile = "",
        compressedFiles = compressedFiles,
        totalFiles = compressedFiles + skippedFiles,
        compressedBytes = 0,
        totalBytes = 0,
        isComplete = true,
        outputPath = "$testPath/archive.zip",
        skippedFiles = skippedFiles
    )

    /**
     * The single emission a finished copy or move ends on, with the counts the tests below vary.
     */
    private fun transferCompletion(
        copiedFiles: Int,
        skippedFiles: Int,
        sourceDeleteFailed: Boolean = false,
        skippedErrno: Int? = null
    ) = CopyProgress(
        currentFile = "",
        copiedFiles = copiedFiles,
        totalFiles = copiedFiles + skippedFiles,
        copiedBytes = 0,
        totalBytes = 0,
        isComplete = true,
        sourceDeleteFailed = sourceDeleteFailed,
        skippedFiles = skippedFiles,
        skippedErrno = skippedErrno
    )

    /**
     * Reloads the listing the only way a caller outside the ViewModel can ask for one: the screen
     * resumes again. The first resume coincides with the load kicked off on creation and is skipped
     * (see [FolderViewModel.onScreenResumed]), so this takes two calls — and is correct only once
     * per ViewModel, on one whose initial resume has not been consumed yet.
     */
    private fun reload(viewModel: FolderViewModel) {
        viewModel.onScreenResumed()
        viewModel.onScreenResumed()
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
    fun `cancelled load does not surface a load error`() = runTest {
        // When a reload supersedes an in-flight load, loadJob is cancelled and the load throws
        // CancellationException. That must not flash the red "unable to load" error.
        coEvery { fileRepository.listFiles(any(), any(), any()) } throws CancellationException("superseded by a newer load")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `childCounts populated for directories after load`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.countChildren("/storage/emulated/0/Documents/Folder1", any()) } returns 7

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(7, viewModel.childCounts.value["/storage/emulated/0/Documents/Folder1"])
    }

    /**
     * More directories than the fixed pool of workers that counts them, so a worker has to come back
     * for another index and the tail of the list is not left uncounted.
     */
    @Test
    fun `childCounts populated for every directory beyond the worker pool size`() = runTest {
        val directories = (1..50).map { index ->
            testFiles[0].copy(
                path = "$testPath/Folder$index",
                name = "Folder$index"
            )
        }
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns directories
        coEvery { fileRepository.countChildren(any(), any()) } answers {
            firstArg<String>().removePrefix("$testPath/Folder").toInt()
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val counts = viewModel.childCounts.value
        assertEquals(directories.size, counts.size)
        directories.forEachIndexed { index, directory ->
            assertEquals(index + 1, counts[directory.path])
        }
    }

    @Test
    fun `childCounts excludes non-directory entries`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.childCounts.value.containsKey("/storage/emulated/0/Documents/file.txt"))
    }

    @Test
    fun `second line settings reach the state`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        folderSecondLineFlow.value = FolderSecondLine.LAST_MODIFIED
        fileSecondLineFlow.value = FileSecondLine.NONE

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(FolderSecondLine.LAST_MODIFIED, viewModel.state.value.folderSecondLine)
        assertEquals(FileSecondLine.NONE, viewModel.state.value.fileSecondLine)
    }

    @Test
    fun `swipe action settings reach the state`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        swipeLeftActionFlow.value = SwipeAction.NONE
        swipeRightActionFlow.value = SwipeAction.MOVE_TO

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SwipeAction.NONE, viewModel.state.value.swipeLeftAction)
        assertEquals(SwipeAction.MOVE_TO, viewModel.state.value.swipeRightAction)
    }

    /**
     * The setting picks what a gesture reveals, nothing more: a folder already loaded must not be
     * listed again just because a swipe direction was pointed somewhere else.
     */
    @Test
    fun `changing a swipe action setting updates the state without reloading the folder`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SwipeAction.DELETE, viewModel.state.value.swipeRightAction)

        swipeLeftActionFlow.value = SwipeAction.INFO
        swipeRightActionFlow.value = SwipeAction.COPY_TO
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { fileRepository.listFiles(any(), any(), any()) }
        assertEquals(SwipeAction.INFO, viewModel.state.value.swipeLeftAction)
        assertEquals(SwipeAction.COPY_TO, viewModel.state.value.swipeRightAction)
    }

    @Test
    fun `changing a second line setting updates the state`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(FolderSecondLine.ITEM_COUNT, viewModel.state.value.folderSecondLine)

        folderSecondLineFlow.value = FolderSecondLine.NONE
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(FolderSecondLine.NONE, viewModel.state.value.folderSecondLine)
    }

    /**
     * The setting picks what a row displays, nothing more: a folder already loaded must not be
     * listed again just because its second line changed.
     */
    @Test
    fun `changing a second line setting does not reload the folder`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        folderSecondLineFlow.value = FolderSecondLine.LAST_MODIFIED
        fileSecondLineFlow.value = FileSecondLine.LAST_MODIFIED
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { fileRepository.listFiles(any(), any(), any()) }
        assertEquals(FolderSecondLine.LAST_MODIFIED, viewModel.state.value.folderSecondLine)
    }

    /**
     * Counts keep being taken whichever second line is chosen: the same pass is what marks a folder
     * the app cannot read, and that has to show under all three options.
     */
    @Test
    fun `child counts are still taken when folders show no count`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.countChildren("/storage/emulated/0/Documents/Folder1", any()) } returns null
        folderSecondLineFlow.value = FolderSecondLine.NONE

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.childCounts.value.containsKey("/storage/emulated/0/Documents/Folder1"))
        assertNull(viewModel.childCounts.value["/storage/emulated/0/Documents/Folder1"])
    }

    /** The count describes the rows below it, so it is taken under the listing's own hidden filter. */
    @Test
    fun `child counts are taken with the same hidden filter as the listing`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        showHiddenFlow.value = true

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.showHidden)
        coVerify { fileRepository.countChildren("/storage/emulated/0/Documents/Folder1", true) }
    }

    @Test
    fun `childCounts marks unreadable directories as restricted`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.countChildren(any(), any()) } returns null

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // present-null entry distinguishes "restricted" from "still loading" (absent)
        assertTrue(viewModel.childCounts.value.containsKey("/storage/emulated/0/Documents/Folder1"))
        assertNull(viewModel.childCounts.value["/storage/emulated/0/Documents/Folder1"])
    }

    @Test
    fun `childCounts overwrites existing count on reload`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.countChildren("/storage/emulated/0/Documents/Folder1", any()) } returns 2

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.childCounts.value["/storage/emulated/0/Documents/Folder1"])

        coEvery { fileRepository.countChildren("/storage/emulated/0/Documents/Folder1", any()) } returns 9
        reload(viewModel)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(9, viewModel.childCounts.value["/storage/emulated/0/Documents/Folder1"])
    }

    @Test
    fun `childCounts prunes paths absent after reload`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.countChildren("/storage/emulated/0/Documents/Folder1", any()) } returns 3

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, viewModel.childCounts.value["/storage/emulated/0/Documents/Folder1"])

        val otherFolder = FileItem(
            path = "/storage/emulated/0/Documents/Folder2",
            name = "Folder2",
            isDirectory = true,
            size = 0L,
            lastModified = 1000L,
            createdTime = 1000L,
            mimeType = "",
            childCount = null
        )
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns listOf(otherFolder)
        coEvery { fileRepository.countChildren("/storage/emulated/0/Documents/Folder2", any()) } returns 1

        reload(viewModel)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.childCounts.value.containsKey("/storage/emulated/0/Documents/Folder1"))
        assertEquals(1, viewModel.childCounts.value["/storage/emulated/0/Documents/Folder2"])
    }

    @Test
    fun `isCurrentFolderRestricted true when folder empty and unreadable`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns emptyList()
        coEvery { fileRepository.countChildren(testPath, any()) } returns null

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.isCurrentFolderRestricted)
    }

    @Test
    fun `isCurrentFolderRestricted false for genuinely empty folder`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns emptyList()
        coEvery { fileRepository.countChildren(testPath, any()) } returns 0

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isCurrentFolderRestricted)
    }

    @Test
    fun `isCurrentFolderRestricted false when folder has items`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.state.value.isCurrentFolderRestricted)
    }

    @Test
    fun `onScreenResumed does not reload on first resume`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // The initial load already ran once on creation; the first resume coincides with it and
        // must not trigger a redundant reload.
        viewModel.onScreenResumed()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { fileRepository.listFiles(testPath, false, SortMode.NAME_ASC) }
    }

    @Test
    fun `onScreenResumed reloads when returning to the screen`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // The first resume is the initial appearance (skipped); a later resume is a genuine return
        // (e.g. popping back from a child folder) and must reload to reflect external changes.
        viewModel.onScreenResumed()
        viewModel.onScreenResumed()
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
    fun `onScreenResumed clears selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        assertTrue(viewModel.state.value.isSelectionMode)

        reload(viewModel)
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

    /**
     * The selection outlives the event: the screen clears it only once the chooser launches, so a
     * share the system refuses (a selection too large for a Binder transaction) leaves the user
     * with something to narrow.
     */
    @Test
    fun `onShare keeps the selection until the screen clears it`() = runTest {
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

        assertTrue(viewModel.state.value.isSelectionMode)

        viewModel.clearSelection()

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
    fun `onRename remaps favorites and recents to the new path`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        val newPath = "/storage/emulated/0/Documents/newName.txt"
        coEvery { fileRepository.rename(any(), any()) } returns RenameResult(
            oldPath = testFiles[0].path,
            newPath = newPath
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showRenameDialog(testFiles[0])
        viewModel.onRename("newName.txt")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { favoritesRepository.updatePath(testFiles[0].path, newPath) }
        coVerify { recentFilesRepository.updatePath(testFiles[0].path, newPath) }
    }

    @Test
    fun `onRename failure leaves favorites and recents untouched`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.rename(any(), any()) } returns null

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showRenameDialog(testFiles[0])
        viewModel.onRename("newName.txt")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { favoritesRepository.updatePath(any(), any()) }
        coVerify(exactly = 0) { recentFilesRepository.updatePath(any(), any()) }
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
        coEvery { fileRepository.totalNodeCount(any()) } returns 1
        coEvery { fileRepository.delete(any()) } answers { DeleteResult(removedPaths = firstArg<List<FileItem>>().map { it.path }) }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        viewModel.showDeleteConfirmDialog(listOf(testFiles[0]))

        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.itemsToDelete.isEmpty())
        assertFalse(viewModel.state.value.isSelectionMode)
    }

    // The rule the small path was reworked for holds on this path too, or the invariant is only
    // half applied: a root that was already empty is scanned, never handed to the prefix-matching
    // row delete. Reachable with a big selection whose small member something else removed first.
    @Test
    fun `large delete scans an already absent root instead of reporting it deleted`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalNodeCount(any()) } returns 12
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 12,
                totalFiles = 12,
                failedFiles = 0,
                removedRootPaths = listOf(testFiles[0].path),
                absentRootPaths = listOf(testFiles[1].path),
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showDeleteConfirmDialog(testFiles)
        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            MediaStoreUtil.notifyTreeDeleted(any(), listOf(testFiles[0].path))
        }
        verify(exactly = 1) { MediaStoreUtil.scanFiles(any(), listOf(testFiles[1].path)) }
        verify(exactly = 1) {
            AnalyticsTracker.trackDeleteCompleted(
                testFiles.size,
                "folder",
                removedCount = 1,
                alreadyAbsentCount = 1
            )
        }
    }

    // `removed_count` is selected roots on every producer of this event, so the walk's leaf
    // tallies must not leak into it. Empty directories are the case that shows the whole
    // difference at once: a directory contributes no leaf, so the leaf tallies the old arithmetic
    // read stayed 0 for a selection every root of which was accounted for, and it filed a delete of
    // twelve folders as having removed nothing and found nothing already gone. Twelve roots is
    // also what clears DELETE_PROGRESS_THRESHOLD without the fixture having to claim nodes the
    // selection does not contain.
    @Test
    fun `large delete of empty directories counts roots, not leaf files`() = runTest {
        val directories = (1..12).map { index ->
            FileItem(
                path = "/storage/emulated/0/Documents/Empty$index",
                name = "Empty$index",
                isDirectory = true,
                size = 0L,
                lastModified = 1000L,
                createdTime = 1000L,
                mimeType = "",
                childCount = 0
            )
        }
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns directories
        coEvery { fileRepository.totalNodeCount(any()) } returns directories.size
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 0,
                totalFiles = 0,
                failedFiles = 0,
                // Split so that both halves of the event discriminate: nine roots this walk
                // emptied and three something else had already taken, with no leaf anywhere for
                // the leaf tally to have counted.
                removedRootPaths = directories.take(9).map { it.path },
                absentRootPaths = directories.drop(9).map { it.path },
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showDeleteConfirmDialog(directories)
        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) {
            AnalyticsTracker.trackDeleteCompleted(
                directories.size,
                "folder",
                removedCount = 9,
                alreadyAbsentCount = 3
            )
        }
    }

    // The old gate said nothing to MediaStore whenever any node failed, so the roots that did come
    // away kept rows for files that were gone until the next full media scan. Per-root reporting is
    // what closes that, and it is safe because a root in removedRootPaths holds nothing.
    @Test
    fun `large partial delete still reconciles the roots that came away`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalNodeCount(any()) } returns 12
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 11,
                totalFiles = 12,
                failedFiles = 1,
                removedRootPaths = listOf(testFiles[0].path),
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showDeleteConfirmDialog(testFiles)
        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        // The failed root is in neither list, so it is never named to MediaStore — the prefix
        // match would drop the rows of everything still standing under it.
        coVerify(exactly = 1) {
            MediaStoreUtil.notifyTreeDeleted(any(), listOf(testFiles[0].path))
        }
    }

    // The walk no longer stops at the first failure, so a mixed selection really does leave some
    // roots deleted and some standing. Calling that an error reads as "nothing happened" about a
    // folder that just lost most of its contents, and the progress path has always said otherwise
    // for the same situation.
    @Test
    fun `small delete that partly succeeded reports a partial success`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalNodeCount(any()) } returns 3
        coEvery { fileRepository.delete(any()) } returns DeleteResult(
            removedPaths = listOf(testFiles[0].path, testFiles[1].path),
            failedCount = 1,
            failureErrno = EROFS
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(testFiles)
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowDeletePartialSuccess)
            event as FolderUiEvent.ShowDeletePartialSuccess
            assertEquals(2, event.deleted)
            assertEquals(1, event.failed)
        }

        verify {
            AnalyticsTracker.trackOperationFailed("delete", any(), EROFS, "folder", "partial")
        }
    }

    // The roots that came away are gone whatever happened to the rest, so their MediaStore rows
    // have to go with them — the pre-change all-or-nothing gate left a gallery offering files that
    // no longer existed. The failed root must not be in that set: the notification matches as a
    // prefix, so it would drop the rows of everything still standing underneath it.
    @Test
    fun `small partial delete reconciles only the roots that came away`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalNodeCount(any()) } returns 3
        coEvery { fileRepository.delete(any()) } returns DeleteResult(
            removedPaths = listOf(testFiles[1].path),
            failedCount = 1,
            failureErrno = EROFS
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showDeleteConfirmDialog(testFiles)
        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            MediaStoreUtil.notifyTreeDeleted(any(), listOf(testFiles[1].path))
        }
    }

    // A root that was already empty is the one case that must never reach notifyTreeDeleted: this
    // app did not remove it and cannot say what occupies the path now, and the notification's
    // prefix match would take whatever does. Scanning drops a stale row just as well and
    // re-indexes a path that has been taken over.
    @Test
    fun `small delete scans an already absent root instead of reporting it deleted`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalNodeCount(any()) } returns 1
        coEvery { fileRepository.delete(any()) } returns DeleteResult(
            alreadyAbsentPaths = listOf(testFiles[0].path)
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showDeleteConfirmDialog(listOf(testFiles[0]))
        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { MediaStoreUtil.notifyTreeDeleted(any(), any()) }
        verify(exactly = 1) { MediaStoreUtil.scanFiles(any(), listOf(testFiles[0].path)) }
        verify(exactly = 1) {
            AnalyticsTracker.trackDeleteCompleted(
                1,
                "folder",
                removedCount = 0,
                alreadyAbsentCount = 1
            )
        }
    }

    // The delete that used to report `unknown` — every selection under DELETE_PROGRESS_THRESHOLD,
    // which is nearly all of them — now carries the errno the repository kept all the way to the
    // event. Which cause that errno names, and which message goes with it, is `FileAccessTest`'s
    // to assert: every OsConstants field reads 0 off device, so a mapping asserted here would be
    // asserting the collapse rather than the real thing. What is worth pinning here is the
    // plumbing — that the repository's errno reaches analytics unchanged instead of being dropped
    // the way it was for the whole life of this event.
    @Test
    fun `small delete that failed forwards the repository's errno`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalNodeCount(any()) } returns 1
        coEvery { fileRepository.delete(any()) } returns DeleteResult(failedCount = 1, failureErrno = EROFS)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(listOf(testFiles[0]))
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is FolderUiEvent.ShowToastRes)
        }

        verify {
            AnalyticsTracker.trackOperationFailed("delete", any(), EROFS, "folder", "all_failed")
        }
        verify(exactly = 0) {
            AnalyticsTracker.trackOperationFailed("delete", "unknown", any(), any(), any())
        }
    }

    // A failure the platform gave no errno for keeps the generic message and the label the
    // dashboard already knows. It is the honest answer, not a placeholder to be removed.
    @Test
    fun `small delete that failed without an errno stays unknown`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalNodeCount(any()) } returns 1
        coEvery { fileRepository.delete(any()) } returns DeleteResult(failedCount = 1, failureErrno = ERRNO_UNKNOWN)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(listOf(testFiles[0]))
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem() as FolderUiEvent.ShowToastRes
            assertEquals(R.string.delete_error, event.messageResId)
        }

        // Null rather than 0: reporting the marker as an errno would read on the dashboard as a
        // cause rather than as the absence of one.
        verify {
            AnalyticsTracker.trackOperationFailed("delete", "unknown", null, "folder", "all_failed")
        }
    }

    // The progress path keeps reporting the shape of the failure — only it can tell all_failed
    // from partial — and gains the cause as the errno the two paths now share.
    @Test
    fun `large delete that failed keeps its shape label and adds the errno`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalNodeCount(any()) } returns 12
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 0,
                totalFiles = 12,
                failedFiles = 12,
                failureErrno = EACCES,
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(testFiles)
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is FolderUiEvent.ShowToastRes)
        }

        verify {
            AnalyticsTracker.trackOperationFailed("delete", "all_failed", EACCES, "folder", "all_failed")
        }
    }

    @Test
    fun `large delete that fully succeeds notifies MediaStore`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        // >= DELETE_PROGRESS_THRESHOLD (10) files routes through the deleteWithProgress branch.
        coEvery { fileRepository.totalNodeCount(any()) } returns 12
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 12,
                totalFiles = 12,
                failedFiles = 0,
                removedRootPaths = testFiles.map { it.path },
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showDeleteConfirmDialog(testFiles)
        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        // The selected paths, not the tree below them: notifyTreeDeleted matches each one as a
        // prefix, so the descendants never have to be enumerated and held in memory.
        coVerify(exactly = 1) { MediaStoreUtil.notifyTreeDeleted(any(), testFiles.map { it.path }) }
    }

    @Test
    fun `a large delete that reports every file still finishes on the last one`() = runTest {
        // The progress flow is conflated before it reaches the state, so intermediate values are
        // dropped when the UI cannot keep up. Everything that ends the operation — closing the
        // dialog, notifying MediaStore, reloading the folder — hangs off the terminal value, so
        // this pins that conflation cannot drop it.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalNodeCount(any()) } returns 500
        every { fileRepository.deleteWithProgress(any()) } returns flow {
            repeat(500) { index ->
                emit(
                    DeleteProgress(
                        currentFile = "f$index",
                        deletedFiles = index,
                        totalFiles = 500,
                        failedFiles = 0
                    )
                )
            }
            emit(
                DeleteProgress(
                    currentFile = "",
                    deletedFiles = 500,
                    totalFiles = 500,
                    failedFiles = 0,
                    removedRootPaths = testFiles.map { it.path },
                    isComplete = true
                )
            )
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showDeleteConfirmDialog(testFiles)
        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull("Progress dialog must close", viewModel.state.value.deleteProgress)
        coVerify(exactly = 1) { MediaStoreUtil.notifyTreeDeleted(any(), testFiles.map { it.path }) }
        coVerify {
            AnalyticsTracker.trackDeleteCompleted(
                testFiles.size,
                "folder",
                removedCount = testFiles.size,
                alreadyAbsentCount = 0
            )
        }
        // Once for the initial load, once after the delete.
        coVerify(exactly = 2) { fileRepository.listFiles(any(), any(), any()) }
    }

    @Test
    fun `large delete with a partial failure does not notify MediaStore`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalNodeCount(any()) } returns 12
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 11,
                totalFiles = 12,
                failedFiles = 1,
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Collect events so the partial-success emission has a subscriber and the flow completes.
        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(testFiles)
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is FolderUiEvent.ShowDeletePartialSuccess)
        }

        // Notifying here would purge the still-present (failed) files from MediaStore views —
        // and the provider unlinks the file backing every row it drops.
        coVerify(exactly = 0) { MediaStoreUtil.notifyTreeDeleted(any(), any()) }
    }

    @Test
    fun `large delete reports an error and skips MediaStore when only a directory could not be removed`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalNodeCount(any()) } returns 12
        // Every leaf file deleted (failedFiles == 0), but a directory could not be unlinked.
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 12,
                totalFiles = 12,
                failedFiles = 0,
                structuralDeleteFailed = true,
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(testFiles)
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowToastRes)
            assertEquals(R.string.delete_error, (event as FolderUiEvent.ShowToastRes).messageResId)
        }

        // The tree was not fully removed, so MediaStore must not be told the files are gone.
        coVerify(exactly = 0) { MediaStoreUtil.notifyTreeDeleted(any(), any()) }
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

    /**
     * A row's own menu and its swipe buttons act on that row alone. They used to say so by adding
     * the row to the selection and then running the selection-based move, which moved everything
     * already selected along with it.
     */
    @Test
    fun `onMoveTo for a single file ignores the current selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])

        viewModel.onMoveTo(testFiles[1])

        val pickerRequest = viewModel.state.value.pickerRequest
        assertEquals(OperationMode.MOVE, pickerRequest?.mode)
        assertEquals(listOf(testFiles[1]), pickerRequest?.items)
        assertEquals(setOf(testFiles[0].path), viewModel.state.value.selectedPaths)
    }

    @Test
    fun `onCopyTo for a single file ignores the current selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])

        viewModel.onCopyTo(testFiles[1])

        val pickerRequest = viewModel.state.value.pickerRequest
        assertEquals(OperationMode.COPY, pickerRequest?.mode)
        assertEquals(listOf(testFiles[1]), pickerRequest?.items)
        assertEquals(setOf(testFiles[0].path), viewModel.state.value.selectedPaths)
    }

    /**
     * A selected path that no longer resolves to a listed file — deleted by another app, or on a
     * volume that was unmounted — left the screen counting a row it could not show, because the
     * empty-selection exit ran before the write that clears the selection.
     */
    @Test
    fun `onAction MoveTo with a selection that resolves to nothing leaves selection mode`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0].copy(path = "/storage/emulated/0/Documents/gone.txt"))
        assertTrue(viewModel.state.value.isSelectionMode)

        viewModel.onAction(FileAction.MoveTo)

        assertNull(viewModel.state.value.pickerRequest)
        assertFalse(viewModel.state.value.isSelectionMode)
        assertTrue(viewModel.state.value.selectedPaths.isEmpty())
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

    /**
     * Cancelling has to do two things: flag the dialog so the button reads as pressed, and actually
     * stop the transfer. Both need an operation genuinely in flight — calling `cancelOperation()`
     * on a fresh view model only reads back `FolderUiState`'s defaults, which stays green with the
     * whole body deleted, leaving a copy running behind a dead Cancel button and no way to undo it.
     */
    @Test
    fun `cancelOperation flags the dialog and stops the running transfer`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L

        var transferStopped = false
        coEvery { fileRepository.copyFiles(any(), any(), any(), any(), any()) } returns flow {
            emit(
                CopyProgress(
                    currentFile = "big.bin",
                    copiedFiles = 0,
                    totalFiles = 1,
                    copiedBytes = 1L,
                    totalBytes = 10L
                )
            )
            // Hold the transfer open so the cancel lands mid-operation, and record that the
            // collection was torn down rather than left running.
            try {
                awaitCancellation()
            } finally {
                transferStopped = true
            }
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.CopyTo)
        viewModel.executeOperation("/storage/emulated/0/Target")
        testDispatcher.scheduler.advanceUntilIdle()

        val inFlight = viewModel.state.value.operationProgress
        assertEquals("big.bin", inFlight?.currentFile)
        assertFalse("Nothing has been cancelled yet", inFlight!!.isCancelling)

        viewModel.cancelOperation()

        // The flag is raised synchronously: the dialog must show the cancel as accepted before the
        // job finishes unwinding, which is when the progress state is cleared.
        assertTrue(
            "Cancel must flag the progress dialog",
            viewModel.state.value.operationProgress?.isCancelling == true
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Cancel must stop the running transfer", transferStopped)
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

    @Test
    fun `move that fails to delete source skips MediaStore notify and reports failure`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any(), any()) } returns flowOf(
            CopyProgress(
                currentFile = "",
                copiedFiles = 1,
                totalFiles = 1,
                copiedBytes = 10L,
                totalBytes = 10L,
                isComplete = true,
                sourceDeleteFailed = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.MoveTo)

        viewModel.events.test {
            viewModel.executeOperation("/storage/emulated/0/Target")
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowToastRes)
            assertEquals(
                R.string.error_move_source_not_deleted,
                (event as FolderUiEvent.ShowToastRes).messageResId
            )
        }

        coVerify(exactly = 0) { MediaStoreUtil.notifyDeleted(any(), any()) }
        coVerify { AnalyticsTracker.trackDestinationPickerOperationFinished("move", false) }
    }

    @Test
    fun `copy that could not read every file reports a partial success`() = runTest {
        // Scoped storage lets `list()` name the entries under `Android/data` on a removable volume
        // and then denies the open, so a selection can lose files to it silently. Everything
        // readable reached the destination — a success, not a failure — but the user has to be
        // told it is not the whole selection.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any(), any()) } returns flowOf(
            transferCompletion(copiedFiles = 2, skippedFiles = 1)
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.CopyTo)

        viewModel.events.test {
            viewModel.executeOperation("/storage/emulated/0/Target")
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowTransferPartialSuccess)
            event as FolderUiEvent.ShowTransferPartialSuccess
            assertEquals(R.plurals.copy_partial_success, event.pluralResId)
            assertEquals(2, event.transferred)
            assertEquals(1, event.skipped)
        }

        coVerify { AnalyticsTracker.trackDestinationPickerOperationFinished("copy", false) }
        verify { AnalyticsTracker.trackOperationFailed("copy", "partial") }
    }

    @Test
    fun `move that could not read every file reports a partial success in its own words`() = runTest {
        // The same branch on the other mode: a move that left files behind must not borrow the
        // copy wording, exactly as the failure toasts on this path already choose between
        // error_move_failed and error_copy_failed.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any(), any()) } returns flowOf(
            transferCompletion(copiedFiles = 2, skippedFiles = 1)
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.MoveTo)

        viewModel.events.test {
            viewModel.executeOperation("/storage/emulated/0/Target")
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowTransferPartialSuccess)
            assertEquals(
                R.plurals.move_partial_success,
                (event as FolderUiEvent.ShowTransferPartialSuccess).pluralResId
            )
        }

        verify { AnalyticsTracker.trackOperationFailed("move", "partial") }
    }

    @Test
    fun `a move that skipped files and could not delete a source reports both`() = runTest {
        // Both conditions at once, which the repository allows: the guard that keeps a directory
        // left standing by a skipped file from raising sourceDeleteFailed does not cover a copied
        // leaf whose source will not unlink. Neither fact may shadow the other — a user told only
        // that some originals remain has no reason not to delete the source folder by hand, and
        // the skipped files would go with it.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any(), any()) } returns flowOf(
            transferCompletion(copiedFiles = 2, skippedFiles = 1, sourceDeleteFailed = true)
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.MoveTo)

        viewModel.events.test {
            viewModel.executeOperation("/storage/emulated/0/Target")
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowTransferPartialSuccess)
            event as FolderUiEvent.ShowTransferPartialSuccess
            assertEquals(R.plurals.move_partial_success_source_not_deleted, event.pluralResId)
            assertEquals(2, event.transferred)
            assertEquals(1, event.skipped)
            expectNoEvents()
        }

        coVerify { AnalyticsTracker.trackDestinationPickerOperationFinished("move", false) }
        coVerify(exactly = 0) { MediaStoreUtil.notifyDeleted(any(), any()) }
        verify {
            AnalyticsTracker.trackOperationFailed(
                "move",
                "source_delete_failed",
                null,
                outcome = "partial"
            )
        }
    }

    @Test
    fun `a partial transfer reports the errno behind its first skip`() = runTest {
        // The errno is what separates a source volume that went away from the ordinary
        // Android/data denial, and it only reaches the dashboard through this branch.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any(), any()) } returns flowOf(
            transferCompletion(copiedFiles = 2, skippedFiles = 1, skippedErrno = EACCES)
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.CopyTo)

        viewModel.executeOperation("/storage/emulated/0/Target")
        testDispatcher.scheduler.advanceUntilIdle()

        verify {
            AnalyticsTracker.trackOperationFailed("copy", "partial", EACCES, null, null)
        }
    }

    @Test
    fun `a transfer that read every file reports nothing beyond the result`() = runTest {
        // The other side of the branch: a complete transfer must stay silent, or the toast that
        // means "part of your selection is missing" appears every time and stops meaning anything.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any(), any()) } returns flowOf(
            transferCompletion(copiedFiles = 3, skippedFiles = 0)
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.CopyTo)

        viewModel.events.test {
            viewModel.executeOperation("/storage/emulated/0/Target")
            testDispatcher.scheduler.advanceUntilIdle()

            expectNoEvents()
        }

        coVerify { AnalyticsTracker.trackDestinationPickerOperationFinished("copy", true) }
        verify(exactly = 0) { AnalyticsTracker.trackOperationFailed("copy", any()) }
    }

    @Test
    fun `copy that runs out of space shows an actionable toast and is not reported`() = runTest {
        // The pre-flight space check can be overtaken by another app filling the volume, so the
        // repository can still report a full device once the transfer is under way. That's the
        // state of the device, not an app bug: actionable toast, and Crashlytics stays quiet.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        every { fileRepository.copyFiles(any(), any(), any(), any(), any()) } returns flow {
            throw InsufficientStorageException("Not enough disk space")
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.CopyTo)

        viewModel.events.test {
            viewModel.executeOperation("/storage/emulated/0/Target")
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowToastRes)
            assertEquals(
                R.string.error_not_enough_space,
                (event as FolderUiEvent.ShowToastRes).messageResId
            )
        }

        assertNull(viewModel.state.value.operationProgress)
        verify { AnalyticsTracker.trackOperationFailed("copy", "insufficient_storage") }
        verify { AnalyticsTracker.trackDestinationPickerOperationFinished("copy", false) }
        verify(exactly = 0) { ErrorReporter.error(any(), any(), any()) }
    }

    @Test
    fun `copy to a destination that no longer exists shows an invalid target toast`() = runTest {
        // The destination can be unmounted or removed between the picker listing it and the
        // operation starting, and the space pre-flight then fails on a path it cannot stat.
        // MockK cannot make a constructor throw, so the failure is raised from availableBytes,
        // which is inside the same guard.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        every { anyConstructed<StatFs>().availableBytes } throws
            IllegalArgumentException("Invalid path: /storage/1234-5678/Target")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.CopyTo)

        viewModel.events.test {
            viewModel.executeOperation("/storage/1234-5678/Target")
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowToastRes)
            assertEquals(
                R.string.error_invalid_target_path,
                (event as FolderUiEvent.ShowToastRes).messageResId
            )
        }

        assertNull(viewModel.state.value.operationProgress)
        verify(exactly = 0) { fileRepository.copyFiles(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { ErrorReporter.error(any(), any(), any()) }
    }

    @Test
    fun `move that deletes source notifies MediaStore and reports success`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any(), any()) } returns flowOf(
            CopyProgress(
                currentFile = "",
                copiedFiles = 1,
                totalFiles = 1,
                copiedBytes = 10L,
                totalBytes = 10L,
                isComplete = true,
                sourceDeleteFailed = false,
                deletedSourcePaths = listOf("/storage/emulated/0/Source/file.txt")
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.MoveTo)

        viewModel.executeOperation("/storage/emulated/0/Target")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            MediaStoreUtil.notifyDeleted(any(), listOf("/storage/emulated/0/Source/file.txt"))
        }
        coVerify { AnalyticsTracker.trackDestinationPickerOperationFinished("move", true) }
    }

    @Test
    fun `copy scans every batch of created paths, not only the final one`() = runTest {
        // The repository reports created paths in batches while the transfer runs, so waiting for
        // the final emission would leave everything but the last batch out of MediaStore.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any(), any()) } returns flowOf(
            CopyProgress(
                currentFile = "first.txt",
                copiedFiles = 1,
                totalFiles = 2,
                copiedBytes = 5L,
                totalBytes = 10L,
                createdPaths = listOf("/storage/emulated/0/Target/first.txt")
            ),
            CopyProgress(
                currentFile = "",
                copiedFiles = 2,
                totalFiles = 2,
                copiedBytes = 10L,
                totalBytes = 10L,
                isComplete = true,
                createdPaths = listOf("/storage/emulated/0/Target/second.txt")
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.CopyTo)

        viewModel.executeOperation("/storage/emulated/0/Target")
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) {
            MediaStoreUtil.scanFiles(any(), listOf("/storage/emulated/0/Target/first.txt"))
        }
        verify(exactly = 1) {
            MediaStoreUtil.scanFiles(any(), listOf("/storage/emulated/0/Target/second.txt"))
        }
    }

    @Test
    fun `addCurrentFolderToFavorites favorites the folder being viewed`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addCurrentFolderToFavorites()
        testDispatcher.scheduler.advanceUntilIdle()

        // testPath = "/storage/emulated/0/Documents", stored as a directory with its on-disk name.
        coVerify { favoritesRepository.addFavorite(testPath, "Documents", true, "") }
    }

    @Test
    fun `isStorageRoot is true when the folder is a storage root`() = runTest {
        // getStorages() (setUp) reports a storage at "/storage/emulated/0", so a VM rooted there is
        // a storage root and must not offer the favorite action.
        val viewModel = createViewModel(path = "/storage/emulated/0")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.state.value.isStorageRoot)
    }

    @Test
    fun `isStorageRoot is false for a non-root folder`() = runTest {
        val viewModel = createViewModel() // testPath is a subfolder of the storage root
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.state.value.isStorageRoot)
    }

    @Test
    fun `removeCurrentFolderFromFavorites unfavorites the folder being viewed`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.removeCurrentFolderFromFavorites()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { favoritesRepository.removeFavorite(testPath) }
    }

    @Test
    fun `compress that could not read every file reports a partial success`() = runTest {
        // Scoped storage lets `list()` name the entries under `Android/data` on a removable volume
        // and then denies the open, so a selection can lose files to it silently. The archive is
        // real and holds everything that could be read — a success, not a failure — but the user
        // has to be told it is not the whole selection.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.compressFiles(any(), any(), any(), any()) } returns flow {
            emit(compressCompletion(compressedFiles = 2, skippedFiles = 1))
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCompressDialog(testFiles)

        viewModel.events.test {
            viewModel.onCompress("archive.zip")
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowCompressPartialSuccess)
            assertEquals(2, (event as FolderUiEvent.ShowCompressPartialSuccess).compressed)
            assertEquals(1, event.skipped)
        }

        assertNull(viewModel.state.value.compressProgress)
        verify { AnalyticsTracker.trackOperationFailed("compress", "partial") }
        verify(exactly = 0) { ErrorReporter.error(any(), any(), any()) }
    }

    @Test
    fun `compress that read every file reports nothing beyond the archive`() = runTest {
        // The other side of the branch above: a complete archive must stay silent, or the toast
        // that means "part of your selection is missing" appears on every compression and stops
        // meaning anything.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.compressFiles(any(), any(), any(), any()) } returns flow {
            emit(compressCompletion(compressedFiles = 3, skippedFiles = 0))
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCompressDialog(testFiles)

        viewModel.events.test {
            viewModel.onCompress("archive.zip")
            testDispatcher.scheduler.advanceUntilIdle()

            expectNoEvents()
        }

        verify { AnalyticsTracker.trackCompressCompleted(testFiles.size) }
        verify(exactly = 0) { AnalyticsTracker.trackOperationFailed("compress", any()) }
    }

    @Test
    fun `compress that runs out of space shows an actionable toast and is not reported`() = runTest {
        // A full disk is the state of the device, not an app bug. The repository has already
        // deleted the partial archive, so the user gets a message they can act on and Crashlytics
        // stays quiet.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.compressFiles(any(), any(), any(), any()) } returns flow {
            throw InsufficientStorageException("Not enough disk space")
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCompressDialog(testFiles)

        viewModel.events.test {
            viewModel.onCompress("archive.zip")
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowToastRes)
            assertEquals(
                R.string.error_not_enough_space,
                (event as FolderUiEvent.ShowToastRes).messageResId
            )
        }

        assertNull(viewModel.state.value.compressProgress)
        verify { AnalyticsTracker.trackOperationFailed("compress", "insufficient_storage") }
        verify(exactly = 0) { ErrorReporter.error(any(), any(), any()) }
    }

    @Test
    fun `compress to a destination that cannot be written shows a toast and is not reported`() = runTest {
        // The folder can vanish (deleted externally, volume unmounted) between opening it and
        // confirming the dialog. That's the state of the device, not an app bug, so the user gets
        // a failure toast and Crashlytics stays quiet.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.compressFiles(any(), any(), any(), any()) } returns flow {
            throw DestinationNotWritableException("Cannot create file")
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCompressDialog(testFiles)

        viewModel.events.test {
            viewModel.onCompress("archive.zip")
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowToastRes)
            assertEquals(
                R.string.compress_error,
                (event as FolderUiEvent.ShowToastRes).messageResId
            )
        }

        assertNull(viewModel.state.value.compressProgress)
        verify { AnalyticsTracker.trackOperationFailed("compress", "destination_not_writable") }
        verify(exactly = 0) { ErrorReporter.error(any(), any(), any()) }
    }

    @Test
    fun `compress that fails with an IO error shows a toast and is not reported`() = runTest {
        // The volume can be unmounted while the archive is being written. That's the state of the
        // device, not an app bug, so the user gets a failure toast and Crashlytics stays quiet.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.compressFiles(any(), any(), any(), any()) } returns flow {
            throw FileTransferIOException("Failed to compress files")
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCompressDialog(testFiles)

        viewModel.events.test {
            viewModel.onCompress("archive.zip")
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowToastRes)
            assertEquals(
                R.string.compress_error,
                (event as FolderUiEvent.ShowToastRes).messageResId
            )
        }

        assertNull(viewModel.state.value.compressProgress)
        verify { AnalyticsTracker.trackOperationFailed("compress", "storage_io_error") }
        verify(exactly = 0) { ErrorReporter.error(any(), any(), any()) }
    }

    @Test
    fun `compress that fails for any other reason is still reported`() = runTest {
        // Keeps the suppression above from widening: anything that isn't a full disk is still a
        // candidate app bug and has to reach Crashlytics.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.compressFiles(any(), any(), any(), any()) } returns flow {
            throw IOException("Compression failed")
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCompressDialog(testFiles)

        viewModel.events.test {
            viewModel.onCompress("archive.zip")
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowToastRes)
            assertEquals(
                R.string.compress_error,
                (event as FolderUiEvent.ShowToastRes).messageResId
            )
        }

        verify { AnalyticsTracker.trackOperationFailed("compress", "exception") }
        verify { ErrorReporter.error(any(), "compress_files", "zip") }
    }
}
