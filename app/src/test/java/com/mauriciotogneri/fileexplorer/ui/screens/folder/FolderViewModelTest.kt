package com.mauriciotogneri.fileexplorer.ui.screens.folder

import android.app.Application
import android.os.StatFs
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.model.FileAction
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.model.SwipeCloseReason
import com.mauriciotogneri.fileexplorer.data.model.SwipeSide
import com.mauriciotogneri.fileexplorer.ui.components.SwipeRowEvent
import com.mauriciotogneri.fileexplorer.data.model.OperationMode
import com.mauriciotogneri.fileexplorer.data.model.OperationProgress
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.StorageType
import com.mauriciotogneri.fileexplorer.data.repository.CompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.CopyProgress
import com.mauriciotogneri.fileexplorer.data.repository.DeleteProgress
import com.mauriciotogneri.fileexplorer.data.repository.DestinationNotWritableException
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.util.ERRNO_UNKNOWN
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileTransferIOException
import com.mauriciotogneri.fileexplorer.data.repository.InsufficientStorageException
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RenameMoveResult
import com.mauriciotogneri.fileexplorer.data.repository.RenameResult
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.ZipInfo
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.DeleteFailure
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import com.mauriciotogneri.fileexplorer.util.PROGRESS_DIALOG_DELAY_MS
import com.mauriciotogneri.fileexplorer.util.ReviewPrompt
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private val ENOTEMPTY = 39


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

    /**
     * Every ViewModel a test created, shut down in [tearDown]. Left running they stay subscribed to
     * the app-wide [SortManager] after Main is reset, so the next class to change the sort mode
     * (`ApplySavedPreferencesTest`) crashes resuming them on a Main dispatcher that is gone.
     */
    private val createdViewModels = mutableListOf<FolderViewModel>()

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
                availableBytes = 32_000_000_000L,
                type = StorageType.INTERNAL
            )
        )
        every { application.getString(R.string.error_load_files) } returns "Failed to load files"
        coEvery { fileRepository.countChildren(any(), any()) } returns 0
        coEvery { fileRepository.listNames(any()) } returns emptySet()
        // A move renames before it copies. By default nothing renames — every item is left for the
        // copy, as a move to another volume leaves it — so each move test keeps exercising the copy
        // path it was written for. The tests of the rename step stub these themselves.
        coEvery { fileRepository.findMoveCollisions(any(), any()) } returns emptyList()
        coEvery { fileRepository.moveByRename(any(), any(), any(), any()) } answers {
            RenameMoveResult(renamed = emptyList(), remaining = firstArg(), movedFiles = 0)
        }
        mockkObject(ErrorReporter)
        mockkObject(AnalyticsTracker)
        mockkObject(IntentUtil)
        mockkObject(ReviewPrompt)
        every { ErrorReporter.critical(any(), any(), any()) } just Runs
        every { ErrorReporter.error(any(), any(), any()) } just Runs
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { ErrorReporter.setCount(any(), any()) } just Runs
        every { ErrorReporter.recordHeap() } just Runs
        every { AnalyticsTracker.trackScreenFolder() } just Runs
        every { AnalyticsTracker.trackRenameCompleted(any(), any()) } just Runs
        every { AnalyticsTracker.trackDeleteCompleted(any(), any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackOperationFailed(any(), any(), any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackDeleteFailed(any(), any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackDestinationPickerOperationFinished(any(), any()) } just Runs
        every { AnalyticsTracker.trackCompressCompleted(any()) } just Runs
        every { AnalyticsTracker.trackUncompressCompleted(any()) } just Runs
        every { IntentUtil.trackRecentFile(any(), any()) } just Runs
        every { AnalyticsTracker.setUserProperty(any(), any()) } just Runs
        // StatFs is an Android class with no JVM stub; mock its constructor so executeOperation's
        // space pre-check returns ample free space instead of throwing.
        mockkConstructor(StatFs::class)
        every { anyConstructed<StatFs>().availableBytes } returns Long.MAX_VALUE
        SortManager.setSortMode(SortMode.NAME_ASC)
    }

    @After
    fun tearDown() {
        // While Main is still the test dispatcher: each cancel resumes its coroutine on Main.
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        SortManager.setSortMode(SortMode.NAME_ASC)
        Dispatchers.resetMain()
        unmockkObject(ErrorReporter)
        unmockkObject(AnalyticsTracker)
        unmockkObject(IntentUtil)
        unmockkObject(ReviewPrompt)
        unmockkConstructor(StatFs::class)
    }

    private fun createViewModel(
        path: String = testPath,
        // A recorder by default, so no test reaches the real analytics sink by accident.
        swipeEvents: SwipeEvents = RecordingSwipeEvents()
    ): FolderViewModel {
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
            countDispatcher = testDispatcher,
            swipeEvents = swipeEvents
        ).also { createdViewModels.add(it) }
    }

    /**
     * The single emission a finished compression ends on, with the counts the tests below vary and
     * everything else fixed: the progress emissions before it carry no decision the ViewModel makes.
     *
     * [unreadableDirectories] is a parameter and not a constant because the partial-success rule
     * adds it to [skippedFiles]; a helper that pinned it at zero would let the second half of that
     * sum be deleted without a test noticing. It stays out of `totalFiles`, as the repository
     * documents: a directory the walk could not list contributed nothing to the tally either.
     */
    private fun compressCompletion(
        compressedFiles: Int,
        skippedFiles: Int,
        unreadableDirectories: Int = 0
    ) = CompressProgress(
        currentFile = "",
        compressedFiles = compressedFiles,
        totalFiles = compressedFiles + skippedFiles,
        compressedBytes = 0,
        totalBytes = 0,
        isComplete = true,
        outputPath = "$testPath/archive.zip",
        skippedFiles = skippedFiles,
        unreadableDirectories = unreadableDirectories
    )

    /**
     * The single emission a finished copy or move ends on, with the counts the tests below vary.
     * [unreadableDirectories] is exposed for the reason [compressCompletion] gives.
     */
    private fun transferCompletion(
        copiedFiles: Int,
        skippedFiles: Int,
        sourceDeleteFailed: Boolean = false,
        skippedErrno: Int? = null,
        unreadableDirectories: Int = 0
    ) = CopyProgress(
        currentFile = "",
        copiedFiles = copiedFiles,
        totalFiles = copiedFiles + skippedFiles,
        copiedBytes = 0,
        totalBytes = 0,
        isComplete = true,
        sourceDeleteFailed = sourceDeleteFailed,
        skippedFiles = skippedFiles,
        skippedErrno = skippedErrno,
        unreadableDirectories = unreadableDirectories
    )

    /**
     * Every non-null value [select] reads off [viewModel]'s state from now on, collected eagerly so
     * that one published and replaced at the same instant is caught too: for asserting that a
     * progress dialog never appeared at all.
     */
    private fun <T : Any> TestScope.shownValues(
        viewModel: FolderViewModel,
        select: (FolderUiState) -> T?
    ): List<T> {
        val shown = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { state -> select(state)?.let(shown::add) }
        }
        return shown
    }

    /** Moves virtual time on by [millis] and runs everything due by then. */
    private fun advanceBy(millis: Long) {
        testDispatcher.scheduler.advanceTimeBy(millis)
        testDispatcher.scheduler.runCurrent()
    }

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

    /**
     * Hidden files are off, so the rows hold only the two listed items; the hidden name can only
     * reach the dialogs' duplicate check through the full name listing.
     */
    @Test
    fun `existing names cover entries the rows leave out`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.listNames(testPath) } returns setOf("Folder1", "file.txt", ".hidden")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf("Folder1", "file.txt", ".hidden"), viewModel.state.value.existingFileNames)
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
            // Named: an uncounted tail is precisely the case where a bare "expected 37, was null"
            // does not say how far down the list the workers stopped.
            assertEquals("child count for ${directory.name}", index + 1, counts[directory.path])
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

    // ==================== Swipe analytics ====================

    /** Entering selection mode closes the open row, which only the ViewModel sees start. */
    @Test
    fun `starting selection mode closes an open swipe as a selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        val swipes = RecordingSwipeEvents()
        val viewModel = createViewModel(swipeEvents = swipes)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSwipeRowEvent(testFiles[0].path, SwipeRowEvent.Opened(SwipeSide.RIGHT, SwipeAction.DELETE))
        viewModel.toggleSelection(testFiles[1])

        assertEquals(listOf("opened RIGHT DELETE", "closed RIGHT DELETE SELECTION"), swipes.untimed())
    }

    @Test
    fun `select all closes an open swipe as a selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        val swipes = RecordingSwipeEvents()
        val viewModel = createViewModel(swipeEvents = swipes)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSwipeRowEvent(testFiles[0].path, SwipeRowEvent.Opened(SwipeSide.RIGHT, SwipeAction.DELETE))
        viewModel.selectAll()

        assertEquals(listOf("opened RIGHT DELETE", "closed RIGHT DELETE SELECTION"), swipes.untimed())
    }

    /**
     * Only the change that starts selection mode is a close: toggling inside a selection, selecting
     * all while selecting, and leaving selection mode close nothing. The opening is held throughout
     * to show it, though the row cannot actually be swiped open while selecting.
     */
    @Test
    fun `changes inside or out of selection mode close no swipe`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        val swipes = RecordingSwipeEvents()
        val viewModel = createViewModel(swipeEvents = swipes)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        viewModel.onSwipeRowEvent(testFiles[1].path, SwipeRowEvent.Opened(SwipeSide.RIGHT, SwipeAction.DELETE))
        viewModel.toggleSelection(testFiles[1])
        viewModel.toggleSelection(testFiles[1])
        viewModel.selectAll()
        viewModel.clearSelection()

        assertEquals(listOf("opened RIGHT DELETE"), swipes.untimed())
    }

    @Test
    fun `swipe reports from the screen reach the tracker`() = runTest {
        val swipes = RecordingSwipeEvents()
        val viewModel = createViewModel(swipeEvents = swipes)
        val rowA = testFiles[0].path
        val rowB = testFiles[1].path

        viewModel.onSwipeRowEvent(rowA, SwipeRowEvent.Opened(SwipeSide.RIGHT, SwipeAction.DELETE))
        viewModel.onSwipeRowClaimed(rowA)
        viewModel.onSwipeRowClaimed(rowB)
        viewModel.onSwipeRowEvent(rowB, SwipeRowEvent.Opened(SwipeSide.LEFT, SwipeAction.RENAME))
        viewModel.onSwipeRowsClosed(SwipeCloseReason.SCROLL)
        viewModel.onSwipeRowEvent(rowA, SwipeRowEvent.Tapped(SwipeSide.RIGHT, SwipeAction.DELETE))

        assertEquals(
            listOf(
                "opened RIGHT DELETE",
                "closed RIGHT DELETE OTHER_ROW",
                "opened LEFT RENAME",
                "closed LEFT RENAME SCROLL",
                "tapped RIGHT DELETE"
            ),
            swipes.untimed()
        )
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
    fun `a listing that fails blocks the rating ask`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } throws RuntimeException("Access denied")

        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // No failure event reports this, so it has to block the rating ask itself.
        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
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

    /**
     * No published state may pair a sort mode with a listing taken under a different one.
     *
     * The folder list is a keyed `LazyColumn`, so the screen resets its scroll anchor when the mode
     * changes. While the mode was published before the reload, that reset ran against the outgoing
     * rows and re-stamped the anchor on the old top row, which the next measure then chased to its
     * new position — the jump-to-bottom the reset exists to prevent, surviving on any folder whose
     * re-listing outlasts a frame.
     *
     * Asserted over every emission rather than the final state, because the defect *is* an
     * intermediate value: the end state was always correct.
     */
    @Test
    fun `a sort change never publishes the new mode beside the old listing`() = runTest {
        val ascending = listOf(sortFixture("a.txt"), sortFixture("z.txt"))
        val descending = ascending.reversed()
        coEvery { fileRepository.listFiles(any(), any(), SortMode.NAME_ASC) } returns ascending
        coEvery { fileRepository.listFiles(any(), any(), SortMode.NAME_DESC) } returns descending

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val seen = mutableListOf<Pair<SortMode, List<String>>>()
        backgroundScope.launch(testDispatcher) {
            viewModel.state.collect { seen += it.sortMode to it.files.map { file -> file.name } }
        }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setSortMode(SortMode.NAME_DESC)
        testDispatcher.scheduler.advanceUntilIdle()

        val expectedFor = mapOf(
            SortMode.NAME_ASC to ascending.map { it.name },
            SortMode.NAME_DESC to descending.map { it.name }
        )
        seen.filter { it.second.isNotEmpty() }.forEach { (mode, names) ->
            assertEquals("$mode was published beside a listing sorted some other way", expectedFor[mode], names)
        }
        assertEquals(SortMode.NAME_DESC, viewModel.state.value.sortMode)
        assertEquals(descending.map { it.name }, viewModel.state.value.files.map { it.name })
    }

    /**
     * A sort mode reverted while the listing it superseded is still running must still reload.
     *
     * Since the mode is published only once a listing completes, the still-published mode is the
     * outgoing one for as long as the load runs. A guard reading it would see the revert as a
     * no-op and drop it, leaving the folder sorted by the superseded mode with no way back: the
     * revert tap conflates in [SortManager] and the re-tap compares equal to what was published.
     */
    @Test
    fun `a sort mode reverted while its listing is in flight still reloads`() = runTest {
        val ascending = listOf(sortFixture("a.txt"), sortFixture("z.txt"))
        val descending = ascending.reversed()
        val descendingListing = CompletableDeferred<Unit>()
        coEvery { fileRepository.listFiles(any(), any(), SortMode.NAME_ASC) } returns ascending
        coEvery { fileRepository.listFiles(any(), any(), SortMode.NAME_DESC) } coAnswers {
            descendingListing.await()
            descending
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setSortMode(SortMode.NAME_DESC)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setSortMode(SortMode.NAME_ASC)
        testDispatcher.scheduler.advanceUntilIdle()

        // Releases the superseded listing. Its rows must not reach the state: a revert that was
        // dropped instead of reloading leaves that load running and publishing on its own.
        descendingListing.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SortMode.NAME_ASC, viewModel.state.value.sortMode)
        assertEquals(ascending.map { it.name }, viewModel.state.value.files.map { it.name })
    }

    /**
     * A reload that fails drops the listing it was going to replace, so the error it publishes is
     * reachable: `FolderScreen` renders [FolderUiState.error] only in the branch guarded by an
     * empty `files`, and a failure that kept its rows would show the user nothing at all.
     *
     * Selection goes with the rows. The success path likewise drops every selected path the new
     * listing lacks, and a retained path with no row to show would keep
     * [FolderUiState.isSelectionMode] true and the action bar raised over an empty list.
     */
    @Test
    fun `a reload that fails drops the listing it was replacing, with its selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleSelection(testFiles.first())
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.files.isNotEmpty())
        assertTrue(viewModel.state.value.isSelectionMode)

        coEvery { fileRepository.listFiles(any(), any(), any()) } throws IOException("Access denied")
        reload(viewModel)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("Failed to load files", state.error)
        assertTrue(state.files.isEmpty())
        assertFalse(state.isSelectionMode)
    }

    private fun sortFixture(name: String) = FileItem(
        path = "$testPath/$name",
        name = name,
        isDirectory = false,
        size = 1024L,
        lastModified = 1000L,
        createdTime = 1000L,
        mimeType = "text/plain",
        childCount = null
    )

    /**
     * The sort choice outlives the folder it was made in, so the next folder opened has to list in
     * it. Every other test opens on the NAME_ASC that setUp writes, which a ViewModel ignoring the
     * stored choice would match just as well.
     */
    @Test
    fun `a folder opens in the sort mode last chosen`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        SortManager.setSortMode(SortMode.SIZE_DESC)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SortMode.SIZE_DESC, viewModel.state.value.sortMode)
        coVerify { fileRepository.listFiles(testPath, false, SortMode.SIZE_DESC) }
    }

    @Test
    fun `all sort modes can be set`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        SortMode.entries.forEach { mode ->
            viewModel.setSortMode(mode)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals("State sortMode should reflect mode: $mode", mode, viewModel.state.value.sortMode)
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
            assertTrue("${file.name} was not selected by selectAll", file.path in state.selectedPaths)
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

    /**
     * The screen reloads on every return to RESUMED, so coming back from the recent-apps screen is
     * a reload: a selection it dropped would undo the user's picks for merely leaving the app.
     */
    @Test
    fun `onScreenResumed keeps the selection of files still listed`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        assertTrue(viewModel.state.value.isSelectionMode)

        reload(viewModel)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf(testFiles[0].path), viewModel.state.value.selectedPaths)
    }

    @Test
    fun `a reload drops selected files that are no longer listed`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectAll()
        // file.txt was deleted by another app while the screen was away.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns listOf(testFiles[0])
        reload(viewModel)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(setOf(testFiles[0].path), viewModel.state.value.selectedPaths)
    }

    @Test
    fun `setSortMode keeps selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        assertTrue(viewModel.state.value.isSelectionMode)

        viewModel.setSortMode(SortMode.DATE_DESC)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(SortMode.DATE_DESC, viewModel.state.value.sortMode)
        assertEquals(setOf(testFiles[0].path), viewModel.state.value.selectedPaths)
    }

    @Test
    fun `showHidden change keeps the selection of files still listed`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        assertTrue(viewModel.state.value.isSelectionMode)

        showHiddenFlow.value = true
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { fileRepository.listFiles(testPath, true, any()) }
        assertEquals(setOf(testFiles[0].path), viewModel.state.value.selectedPaths)
    }

    /**
     * The reload after an extraction keeps the archive selected, since it is still listed, so the
     * finished extraction clears the selection itself — as compress, rename and delete do.
     */
    @Test
    fun `a finished extraction clears the selection`() = runTest {
        val zip = FileItem(
            path = "$testPath/archive.zip",
            name = "archive.zip",
            isDirectory = false,
            size = 1024L,
            lastModified = 1000L,
            createdTime = 1000L,
            mimeType = "application/zip",
            childCount = null
        )
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles + zip
        coEvery { fileRepository.getZipInfo(zip.path) } returns ZipInfo(entryCount = 1, isEncrypted = false)
        coEvery { fileRepository.uncompressFile(zip.path, testPath, null, any()) } returns flowOf(
            UncompressProgress(
                currentFile = "file.txt",
                extractedFiles = 1,
                totalFiles = 1,
                extractedBytes = 1024L,
                totalBytes = 1024L,
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(zip)
        viewModel.onAction(FileAction.Uncompress)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.confirmUncompress()
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
        verify(exactly = 1) { ReviewPrompt.onActionSucceeded() }
    }

    // A failure has to reach the user and the rating rule, not only leave the stored paths alone:
    // without the toast the rename silently does nothing, and without the failure event the rating
    // ask never learns that it failed.
    @Test
    fun `onRename failure leaves favorites and recents untouched`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.rename(any(), any()) } returns null

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showRenameDialog(testFiles[0])

        viewModel.events.test {
            viewModel.onRename("newName.txt")
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowToastRes)
            assertEquals(R.string.rename_error, (event as FolderUiEvent.ShowToastRes).messageResId)
        }

        coVerify(exactly = 0) { favoritesRepository.updatePath(any(), any()) }
        coVerify(exactly = 0) { recentFilesRepository.updatePath(any(), any()) }
        // AnalyticsTracker is mocked here, so the failure event is verified rather than the
        // ReviewPrompt.onActionFailed it forwards to.
        verify(exactly = 1) { AnalyticsTracker.trackOperationFailed("rename", "unknown") }
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
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
    fun `a failed folder creation blocks the rating ask`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.createFolder(any(), any()) } returns false

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onCreateFolder("NewFolder")
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `onDeleteConfirmed dismisses dialog and clears selection`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 0,
                totalFiles = 0,
                removedRootPaths = listOf(testFiles[0].path),
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[0])
        viewModel.showDeleteConfirmDialog(listOf(testFiles[0]))

        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.itemsToDelete.isEmpty())
        assertFalse(viewModel.state.value.isSelectionMode)
    }

    // A root that was already empty is not one this app removed, and is counted apart from one.
    @Test
    fun `a delete counts an already absent root apart from a removed one`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
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
    // twelve folders as having removed nothing and found nothing already gone.
    @Test
    fun `a delete of empty directories counts roots, not leaf files`() = runTest {
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

    // This walk's leaf tallies are a different population from the selected roots the confirmation
    // counted, and the plural they would fill has "items" for its only unit word — so what reaches
    // the toast is roots, as it is for `FileRepository.delete` on the other screens.
    @Test
    fun `a delete that partly succeeded reports roots, not leaf files`() = runTest {
        val folders = (1..4).map { index ->
            FileItem(
                path = "/storage/emulated/0/Documents/Folder$index",
                name = "Folder$index",
                isDirectory = true,
                size = 0L,
                lastModified = 1000L,
                createdTime = 1000L,
                mimeType = "",
                childCount = 225
            )
        }
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns folders
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 412,
                totalFiles = 900,
                failedFiles = 488,
                // Both halves of the cleared set, so neither is what the count happens to match:
                // two roots this walk emptied and one something else had already taken.
                removedRootPaths = folders.take(2).map { it.path },
                absentRootPaths = listOf(folders[2].path),
                failureErrno = EROFS,
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(folders)
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowDeletePartialSuccess)
            event as FolderUiEvent.ShowDeletePartialSuccess
            // Three of the four selected folders came away — not 412 of 900 leaves.
            assertEquals(3, event.deleted)
            assertEquals(1, event.failed)
        }

        verify {
            AnalyticsTracker.trackDeleteFailed(any(), "partial", "folder", match { it.errno == EROFS })
        }
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
    }

    // Leaves deleted inside a root that still stands leave nothing to count in the unit the toast
    // speaks, and "Deleted 0 items, 1 failed" is not a message. `FileRepository.delete` draws the
    // same line at `clearedCount > 0` and reports the classified error instead.
    @Test
    fun `a delete of a single root that partly failed reports an error`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 5,
                totalFiles = 12,
                failedFiles = 7,
                failureErrno = EROFS,
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(listOf(testFiles[0]))
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is FolderUiEvent.ShowToastRes)
        }

        verify {
            AnalyticsTracker.trackDeleteFailed(any(), "all_failed", "folder", match { it.errno == EROFS })
        }
        // The error toast does not rule out a success counted beside it: a successful delete shows
        // no toast and is only these two calls, and either one would count this failure towards
        // the rating ask.
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
        verify(exactly = 0) { AnalyticsTracker.trackDeleteCompleted(any(), any(), any(), any()) }
    }

    // A failure the platform gave no errno for keeps the generic message. It is the honest answer,
    // not a placeholder to be removed.
    @Test
    fun `a delete that failed without an errno keeps the generic message and the marker`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 0,
                totalFiles = 1,
                failedFiles = 1,
                failureErrno = ERRNO_UNKNOWN,
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(listOf(testFiles[0]))
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem() as FolderUiEvent.ShowToastRes
            assertEquals(R.string.delete_error, event.messageResId)
        }

        // The marker reaches the tracker as it came, and the tracker leaves it off the event rather
        // than reporting 0, which would read on the dashboard as a cause rather than as the absence
        // of one — DeleteFailureParamsTest pins that half.
        verify {
            AnalyticsTracker.trackDeleteFailed(
                "all_failed",
                "all_failed",
                "folder",
                match { it.errno == ERRNO_UNKNOWN }
            )
        }
    }

    // The progress path keeps reporting the shape of the failure — only it can tell all_failed
    // from partial — and gains the cause as the errno the two paths now share.
    @Test
    fun `a delete that failed keeps its shape label and adds the errno`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
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
            AnalyticsTracker.trackDeleteFailed("all_failed", "all_failed", "folder", match { it.errno == EACCES })
        }
    }

    @Test
    fun `a delete that fully succeeds counts as a success`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
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

        verify(exactly = 1) { ReviewPrompt.onActionSucceeded() }
    }

    @Test
    fun `a large delete that reports every file still finishes on the last one`() = runTest {
        // The progress flow is conflated before it reaches the state, so intermediate values are
        // dropped when the UI cannot keep up. Everything that ends the operation — closing the
        // dialog, reporting the outcome, reloading the folder — hangs off the terminal value, so
        // this pins that conflation cannot drop it.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
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

    // Neither root list holds a root that failed, so both being empty means nothing in the
    // selection came away — whatever the leaf tallies say about the nodes underneath. One leaf
    // failure per selected root, because the walk strands a root only through its own subtree: a
    // single failure over this two-root selection would leave the other root in one of the lists,
    // and the state the empty lists describe would be one no walk can reach.
    @Test
    fun `a delete where no root came away reports an error`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 10,
                totalFiles = 12,
                failedFiles = 2,
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Collect events so the failure emission has a subscriber and the flow completes.
        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(testFiles)
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(awaitItem() is FolderUiEvent.ShowToastRes)
        }
    }

    @Test
    fun `a delete reports an error when only a directory could not be removed`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
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
    }

    // The kind of failure that stranded the rest of the selection is this walk's own distinction:
    // `FileRepository.delete` classifies a root by whether an errno came back, so on the other
    // screens the same mixed outcome is a partial success. Gating this path on a
    // failed *leaf* sent a selection whose only casualty was an unremovable directory to the flat
    // error instead, which reads as "nothing happened" about a folder that is gone.
    @Test
    fun `a delete reports a partial success when a root came away and only a directory failed`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        // Every leaf file deleted (failedFiles == 0), including everything inside Folder1, but
        // Folder1's own entry could not be unlinked — the one failure a directory can take on its
        // own, so it is in neither list while the plain file came away whole. Only a directory or
        // symlink can be stranded this way: a leaf that fails moves failedFiles instead.
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 12,
                totalFiles = 12,
                failedFiles = 0,
                structuralDeleteFailed = true,
                removedRootPaths = listOf(testFiles[1].path),
                failureErrno = EROFS,
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
            assertTrue(event is FolderUiEvent.ShowDeletePartialSuccess)
            event as FolderUiEvent.ShowDeletePartialSuccess
            assertEquals(1, event.deleted)
            assertEquals(1, event.failed)
        }

        verify {
            AnalyticsTracker.trackDeleteFailed("partial", "partial", "folder", match { it.errno == EROFS })
        }
    }

    // Part of the selection went and all that stayed is a system folder. The count would say
    // "1 failed", which is the misleading claim; the list reloads to show what went, so the reason
    // is all the toast has to say.
    @Test
    fun `a partial delete leaving only a system folder gives the reason, not a count`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 1,
                totalFiles = 1,
                structuralDeleteFailed = true,
                removedRootPaths = listOf(testFiles[1].path),
                systemFolders = 1,
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(testFiles)
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(FolderUiEvent.ShowToastRes(R.string.delete_error_system_folder), awaitItem())
        }

        verify(exactly = 1) {
            AnalyticsTracker.trackDeleteFailed(
                "partial",
                "partial",
                "folder",
                match { it.cause == DeleteFailure.SYSTEM_FOLDER }
            )
        }
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
    }

    // A visible failure outranks the system folder beside it: only it is about an item the user can
    // see, so the partial count keeps saying what it always did. The cause reported is that
    // failure's, not the folder's.
    @Test
    fun `a partial delete with a visible failure beside a system folder still counts`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 1,
                totalFiles = 2,
                failedFiles = 1,
                structuralDeleteFailed = true,
                removedRootPaths = listOf(testFiles[1].path),
                failureErrno = EROFS,
                unexplainedErrno = EROFS,
                systemFolders = 1,
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(testFiles)
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(FolderUiEvent.ShowDeletePartialSuccess(deleted = 1, failed = 1), awaitItem())
        }

        verify(exactly = 1) {
            AnalyticsTracker.trackDeleteFailed(
                any(),
                "partial",
                "folder",
                match { it.errno == EROFS && it.systemFolders == 1 }
            )
        }
        verify(exactly = 0) {
            AnalyticsTracker.trackDeleteFailed(any(), any(), any(), match { it.cause == DeleteFailure.SYSTEM_FOLDER })
        }
    }

    // A protected root is never handed to the remove call, so the walk strands it with no file
    // failed and no errno — the structural branch, whose errno message would be the generic error.
    // The error type stays the shape for the dashboards built on it; the cause rides beside it.
    @Test
    fun `a delete of a system folder alone gives the reason and stays structural`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 0,
                totalFiles = 0,
                failedFiles = 0,
                structuralDeleteFailed = true,
                systemFolders = 1,
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(listOf(testFiles[0]))
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(FolderUiEvent.ShowToastRes(R.string.delete_error_system_folder), awaitItem())
        }

        verify(exactly = 1) {
            AnalyticsTracker.trackDeleteFailed(
                "structural",
                "structural",
                "folder",
                match { it.cause == DeleteFailure.SYSTEM_FOLDER }
            )
        }
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
    }

    // A folder whose listing failed only looked empty, so its removal failed "not empty" about
    // items the user cannot see. With a root cleared beside it, the walk's partial branch gives the
    // reason instead of the count, as the one-call path does.
    @Test
    fun `a partial delete leaving hidden contents gives the reason, not a count`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.deleteWithProgress(any()) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 12,
                totalFiles = 12,
                failedFiles = 0,
                structuralDeleteFailed = true,
                removedRootPaths = listOf(testFiles[1].path),
                failureErrno = ENOTEMPTY,
                unlistedFolders = 1,
                isComplete = true
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(testFiles)
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(FolderUiEvent.ShowToastRes(R.string.delete_error_hidden_items), awaitItem())
        }

        verify(exactly = 1) {
            AnalyticsTracker.trackDeleteFailed(
                "partial",
                "partial",
                "folder",
                match { it.cause == DeleteFailure.HIDDEN_CONTENTS && it.errno == ENOTEMPTY }
            )
        }
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
    }

    // === The delete's delayed progress dialog ===

    /** A delete part-way through its walk, on [currentFile]. */
    private fun deleteUnderway(currentFile: String) =
        DeleteProgress(currentFile = currentFile, deletedFiles = 1, totalFiles = 4)

    /** The final emission of a delete that removed every root in [roots]. */
    private fun deleteFinished(roots: List<FileItem>) = DeleteProgress(
        currentFile = "",
        deletedFiles = 4,
        totalFiles = 4,
        removedRootPaths = roots.map { it.path },
        isComplete = true
    )

    @Test
    fun `a delete that ends within the delay never shows its dialog`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.deleteWithProgress(any()) } returns flow {
            emit(deleteUnderway("a.txt"))
            delay(PROGRESS_DIALOG_DELAY_MS - 1)
            emit(deleteFinished(testFiles))
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val shown = shownValues(viewModel) { it.deleteProgress }
        viewModel.showDeleteConfirmDialog(testFiles)
        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<DeleteProgress>(), shown)
        verify(exactly = 1) { AnalyticsTracker.trackDeleteCompleted(testFiles.size, "folder", any(), any()) }
    }

    @Test
    fun `a delete still running after the delay shows its latest progress`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.deleteWithProgress(any()) } returns flow {
            emit(deleteUnderway("a.txt"))
            emit(deleteUnderway("b.txt"))
            awaitCancellation()
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.showDeleteConfirmDialog(testFiles)
        viewModel.onDeleteConfirmed()
        advanceBy(PROGRESS_DIALOG_DELAY_MS - 1)

        assertNull("The dialog waits out the delay", viewModel.state.value.deleteProgress)

        advanceBy(1)

        assertEquals("b.txt", viewModel.state.value.deleteProgress?.currentFile)
    }

    // The walk counts the selection before it reports anything, and on a large tree the count can
    // outlast the delay by itself.
    @Test
    fun `a delete still counting when the delay ends shows a dialog with no total yet`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.deleteWithProgress(any()) } returns flow<DeleteProgress> { awaitCancellation() }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.showDeleteConfirmDialog(testFiles)
        viewModel.onDeleteConfirmed()
        advanceBy(PROGRESS_DIALOG_DELAY_MS)

        assertEquals(
            DeleteProgress(currentFile = "", deletedFiles = 0, totalFiles = 0),
            viewModel.state.value.deleteProgress
        )
    }

    // A delete of a few files used to run in one call, with no dialog and nothing to stop it with
    // however long it took. Every delete now runs the walk the dialog's Cancel stops.
    @Test
    fun `a delete of a single file runs the walk the dialog can cancel`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        var walkStopped = false
        every { fileRepository.deleteWithProgress(listOf(testFiles[1])) } returns flow {
            emit(DeleteProgress(currentFile = "file.txt", deletedFiles = 0, totalFiles = 1))
            try {
                awaitCancellation()
            } finally {
                walkStopped = true
            }
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(listOf(testFiles[1]))
            viewModel.onDeleteConfirmed()
            advanceBy(PROGRESS_DIALOG_DELAY_MS)

            assertEquals("file.txt", viewModel.state.value.deleteProgress?.currentFile)

            viewModel.cancelDelete()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(FolderUiEvent.ShowToastRes(R.string.delete_cancelled), awaitItem())
        }

        assertTrue("Cancel must stop the walk", walkStopped)
        assertNull(viewModel.state.value.deleteProgress)
        coVerify(exactly = 0) { fileRepository.delete(any()) }
        verify(exactly = 0) { AnalyticsTracker.trackDeleteCompleted(any(), any(), any(), any()) }
    }

    /**
     * Until a delete's dialog appears nothing covers the screen, so a second delete can start. The
     * first must then stay out of the second one's dialog — its only Cancel button: neither its
     * progress nor its end may reach it.
     */
    @Test
    fun `a delete that finishes while a newer one runs leaves that one its dialog and its cancel`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        val firstWalk = CompletableDeferred<Unit>()
        var secondWalkStopped = false
        every { fileRepository.deleteWithProgress(any()) } returnsMany listOf(
            flow {
                emit(deleteUnderway("a.txt"))
                firstWalk.await()
                emit(deleteUnderway("a-later.txt"))
                emit(deleteFinished(listOf(testFiles[0])))
            },
            flow {
                emit(deleteUnderway("b.txt"))
                try {
                    awaitCancellation()
                } finally {
                    secondWalkStopped = true
                }
            }
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.showDeleteConfirmDialog(listOf(testFiles[0]))
        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.runCurrent()
        viewModel.showDeleteConfirmDialog(listOf(testFiles[1]))
        viewModel.onDeleteConfirmed()
        advanceBy(PROGRESS_DIALOG_DELAY_MS)

        assertEquals("b.txt", viewModel.state.value.deleteProgress?.currentFile)

        firstWalk.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "The first delete must neither overwrite nor close the second one's dialog",
            "b.txt",
            viewModel.state.value.deleteProgress?.currentFile
        )
        verify(exactly = 1) { AnalyticsTracker.trackDeleteCompleted(1, "folder", any(), any()) }

        viewModel.cancelDelete()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("The second delete's Cancel must still stop it", secondWalkStopped)
        assertNull(viewModel.state.value.deleteProgress)
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
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flow {
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

    /**
     * A cancel that only clears the dialog leaves the work running and later counts it as a
     * success: the walk goes on deleting what the user chose to keep, then reports a completed
     * delete and asks for a rating.
     *
     * The cancel toast is emitted from inside the coroutine being cancelled, into a flow with no
     * buffer, so it has to be shielded from that cancel: emitted plainly, its suspension threw
     * instead, losing the toast and skipping the reload, which left rows for files already deleted.
     * The cancel runs with a subscriber on the events, as the screen always has one: without it the
     * emit returns at once, and the reload after it runs here even where the screen's would not.
     */
    @Test
    fun `cancelling a delete stops it and counts nothing`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        val walk = CompletableDeferred<Unit>()
        every { fileRepository.deleteWithProgress(any()) } returns flow {
            emit(DeleteProgress(currentFile = "a.txt", deletedFiles = 0, totalFiles = 12))
            // Holds the walk open so the cancel lands mid-delete.
            walk.await()
            emit(
                DeleteProgress(
                    currentFile = "",
                    deletedFiles = 12,
                    totalFiles = 12,
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

        assertEquals("a.txt", viewModel.state.value.deleteProgress?.currentFile)

        viewModel.events.test {
            viewModel.cancelDelete()

            assertNull(viewModel.state.value.deleteProgress)

            // Releases the walk: a delete the cancel did not stop runs on to its completion here.
            walk.complete(Unit)
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is FolderUiEvent.ShowToastRes)
            assertEquals(R.string.delete_cancelled, (event as FolderUiEvent.ShowToastRes).messageResId)
        }

        assertNull(viewModel.state.value.deleteProgress)
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
        verify(exactly = 0) { AnalyticsTracker.trackDeleteCompleted(any(), any(), any(), any()) }
        verify(exactly = 0) { ErrorReporter.error(any(), any(), any()) }
        verify(exactly = 0) { AnalyticsTracker.trackOperationFailed(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { AnalyticsTracker.trackDeleteFailed(any(), any(), any(), any()) }
        // Once on opening and once after the cancel, so the rows show what the stopped walk left.
        coVerify(exactly = 2) { fileRepository.listFiles(any(), any(), any()) }
    }

    /**
     * A cancelled delete can still be unwinding — its walk stuck in a slow I/O call — when the user
     * starts the next one. Its cleanup used to clear the delete handle whichever delete it pointed
     * at by then, so Cancel on the second delete only closed the dialog: that walk went on deleting
     * what the user chose to keep, then counted as a completed delete and asked for a rating. The
     * same cleanup also closed the progress dialog whichever
     * delete it showed, hiding the second delete's — its only Cancel button — while that walk went
     * on deleting, until its next progress update brought the dialog back.
     */
    @Test
    fun `cancelling a delete started while a cancelled one unwinds stops it`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        val firstWalk = CompletableDeferred<Unit>()
        val secondWalk = CompletableDeferred<Unit>()
        var secondWalkStopped = false
        every { fileRepository.deleteWithProgress(any()) } returnsMany listOf(
            flow {
                emit(DeleteProgress(currentFile = "a.txt", deletedFiles = 0, totalFiles = 12))
                // Ignores the cancel, like a walk inside an I/O call: its delete cannot finish
                // unwinding until this returns.
                withContext(NonCancellable) { firstWalk.await() }
            },
            flow {
                emit(DeleteProgress(currentFile = "b.txt", deletedFiles = 0, totalFiles = 12))
                try {
                    secondWalk.await()
                } catch (e: CancellationException) {
                    secondWalkStopped = true
                    throw e
                }
                emit(
                    DeleteProgress(
                        currentFile = "",
                        deletedFiles = 12,
                        totalFiles = 12,
                        removedRootPaths = testFiles.map { it.path },
                        isComplete = true
                    )
                )
            }
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showDeleteConfirmDialog(testFiles)
        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("a.txt", viewModel.state.value.deleteProgress?.currentFile)

        viewModel.events.test {
            viewModel.cancelDelete()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.showDeleteConfirmDialog(testFiles)
            viewModel.onDeleteConfirmed()
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("b.txt", viewModel.state.value.deleteProgress?.currentFile)

            // The first walk returns, so its delete finishes unwinding while the second one runs.
            firstWalk.complete(Unit)
            testDispatcher.scheduler.advanceUntilIdle()

            val firstCancelled = awaitItem()
            assertTrue(firstCancelled is FolderUiEvent.ShowToastRes)
            assertEquals(
                R.string.delete_cancelled,
                (firstCancelled as FolderUiEvent.ShowToastRes).messageResId
            )
            assertEquals(
                "A cancelled delete finishing its unwind must not hide the next one's progress",
                "b.txt",
                viewModel.state.value.deleteProgress?.currentFile
            )

            viewModel.cancelDelete()

            // Releases the second walk: a delete the cancel did not stop runs on to its completion.
            secondWalk.complete(Unit)
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue("Cancel must stop the second delete's walk", secondWalkStopped)
            val secondCancelled = awaitItem()
            assertTrue(secondCancelled is FolderUiEvent.ShowToastRes)
            assertEquals(
                R.string.delete_cancelled,
                (secondCancelled as FolderUiEvent.ShowToastRes).messageResId
            )
        }

        assertNull(viewModel.state.value.deleteProgress)
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
        verify(exactly = 0) { AnalyticsTracker.trackDeleteCompleted(any(), any(), any(), any()) }
    }

    /**
     * A cancel that only clears the dialog leaves the work running and later counts it as a
     * success: the archive is finished anyway, tracked as completed and counted towards the rating
     * ask.
     */
    @Test
    fun `cancelling a compression stops it and counts nothing`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        val archive = CompletableDeferred<Unit>()
        every { fileRepository.compressFiles(any(), any(), any(), any()) } returns flow {
            emit(
                CompressProgress(
                    currentFile = "file.txt",
                    compressedFiles = 0,
                    totalFiles = 2,
                    compressedBytes = 0,
                    totalBytes = 0
                )
            )
            // Holds the archive open so the cancel lands mid-compression.
            archive.await()
            emit(compressCompletion(compressedFiles = 2, skippedFiles = 0))
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCompressDialog(testFiles)
        viewModel.onCompress("archive.zip")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("file.txt", viewModel.state.value.compressProgress?.currentFile)

        viewModel.cancelCompression()

        assertNull(viewModel.state.value.compressProgress)

        // Releases the archive: a compression the cancel did not stop runs on to its completion.
        archive.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.compressProgress)
        verify(exactly = 0) { AnalyticsTracker.trackCompressCompleted(any()) }
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
        verify(exactly = 0) { ErrorReporter.error(any(), any(), any()) }
        verify(exactly = 0) { AnalyticsTracker.trackOperationFailed(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { AnalyticsTracker.trackDeleteFailed(any(), any(), any(), any()) }
    }

    /**
     * A cancelled compression can still be unwinding — its walk stuck in a slow I/O call — when the
     * user starts the next one. Its cleanup used to close the progress dialog and clear the
     * compression handle whichever compression they belonged to by then: the second compression's
     * dialog — its only Cancel button — vanished while it went on archiving, and Cancel on it only
     * closed the dialog, so its archive was finished anyway, tracked as completed and counted
     * towards the rating ask.
     */
    @Test
    fun `cancelling a compression started while a cancelled one unwinds stops it`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        val firstArchive = CompletableDeferred<Unit>()
        val secondArchive = CompletableDeferred<Unit>()
        var secondArchiveStopped = false
        every { fileRepository.compressFiles(any(), any(), any(), any()) } returnsMany listOf(
            flow {
                emit(
                    CompressProgress(
                        currentFile = "a.txt",
                        compressedFiles = 0,
                        totalFiles = 2,
                        compressedBytes = 0,
                        totalBytes = 0
                    )
                )
                // Ignores the cancel, like a walk inside an I/O call: its compression cannot finish
                // unwinding until this returns, and then stops at its next emission.
                withContext(NonCancellable) { firstArchive.await() }
                emit(compressCompletion(compressedFiles = 2, skippedFiles = 0))
            },
            flow {
                emit(
                    CompressProgress(
                        currentFile = "b.txt",
                        compressedFiles = 0,
                        totalFiles = 2,
                        compressedBytes = 0,
                        totalBytes = 0
                    )
                )
                try {
                    secondArchive.await()
                } catch (e: CancellationException) {
                    secondArchiveStopped = true
                    throw e
                }
                emit(compressCompletion(compressedFiles = 2, skippedFiles = 0))
            }
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCompressDialog(testFiles)
        viewModel.onCompress("archive.zip")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("a.txt", viewModel.state.value.compressProgress?.currentFile)

        viewModel.cancelCompression()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCompressDialog(testFiles)
        viewModel.onCompress("archive.zip")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("b.txt", viewModel.state.value.compressProgress?.currentFile)

        // The first walk returns, so its compression finishes unwinding while the second one runs.
        firstArchive.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "A cancelled compression finishing its unwind must not hide the next one's progress",
            "b.txt",
            viewModel.state.value.compressProgress?.currentFile
        )

        viewModel.cancelCompression()

        // Releases the second walk: a compression the cancel did not stop runs on to its completion.
        secondArchive.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Cancel must stop the second compression's walk", secondArchiveStopped)
        verify(exactly = 0) { AnalyticsTracker.trackCompressCompleted(any()) }
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
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
    fun `move that fails to delete source reports failure`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flowOf(
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

        coVerify { AnalyticsTracker.trackDestinationPickerOperationFinished("move", false) }
        // The copy reached the destination and only the source removal failed, so the move
        // survived in part — the same dimension the branch that also skipped files reports, and
        // the one a dashboard filters transfers on.
        verify {
            AnalyticsTracker.trackOperationFailed(
                "move",
                "source_delete_failed",
                null,
                null,
                "partial"
            )
        }
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
    }

    @Test
    fun `copy that could not read every file reports a partial success`() = runTest {
        // Scoped storage lets `list()` name the entries under `Android/data` on a removable volume
        // and then denies the open, so a selection can lose files to it silently. Everything
        // readable reached the destination — a success, not a failure — but the user has to be
        // told it is not the whole selection.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flowOf(
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
        verify { AnalyticsTracker.trackOperationFailed("copy", "partial", null, null, "partial") }
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
    }

    @Test
    fun `move that could not read every file reports a partial success in its own words`() = runTest {
        // The same branch on the other mode: a move that left files behind must not borrow the
        // copy wording, exactly as the failure toasts on this path already choose between
        // error_move_failed and error_copy_failed.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flowOf(
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

        verify { AnalyticsTracker.trackOperationFailed("move", "partial", null, null, "partial") }
    }

    @Test
    fun `a transfer that could not list a directory reports a partial success`() = runTest {
        // The other half of the partial-success input, and the one no test reached: a directory the
        // walk could not list is in no other count — its contents were never seen, so they are not
        // in `skippedFiles` and never made it into `totalFiles` either. With `skippedFiles` at zero
        // the branch is entered on `unreadableDirectories` alone, so dropping that term from the
        // sum turns a transfer that lost a whole subtree into a clean success the user is never
        // told about.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flowOf(
            transferCompletion(copiedFiles = 2, skippedFiles = 0, unreadableDirectories = 1)
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
        verify { AnalyticsTracker.trackOperationFailed("copy", "partial", null, null, "partial") }
    }

    @Test
    fun `a partial transfer counts unreadable directories alongside skipped files`() = runTest {
        // Both kinds of loss in one transfer. The count the user reads is how much of the selection
        // is missing, not what kind of thing it was, so the two are summed rather than reported
        // separately — and a sum is only pinned by a case where both terms are non-zero and differ.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flowOf(
            transferCompletion(copiedFiles = 2, skippedFiles = 1, unreadableDirectories = 2)
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
            assertEquals(R.plurals.move_partial_success, event.pluralResId)
            assertEquals(2, event.transferred)
            assertEquals(3, event.skipped)
        }
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
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flowOf(
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
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flowOf(
            transferCompletion(copiedFiles = 2, skippedFiles = 1, skippedErrno = EACCES)
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.CopyTo)

        viewModel.executeOperation("/storage/emulated/0/Target")
        testDispatcher.scheduler.advanceUntilIdle()

        verify {
            AnalyticsTracker.trackOperationFailed("copy", "partial", EACCES, null, "partial")
        }
    }

    @Test
    fun `a transfer that read every file reports nothing beyond the result`() = runTest {
        // The other side of the branch: a complete transfer must stay silent, or the toast that
        // means "part of your selection is missing" appears every time and stops meaning anything.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flowOf(
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
        // Every argument matched, not just the first two: the partial-transfer branches now pass
        // an `outcome`, and a two-argument matcher pins the other three at their `null` defaults —
        // which the very emission this guards against would no longer match.
        verify(exactly = 0) { AnalyticsTracker.trackOperationFailed("copy", any(), any(), any(), any()) }
        verify(exactly = 1) { ReviewPrompt.onActionSucceeded() }
    }

    @Test
    fun `copy that runs out of space shows an actionable toast and is not reported`() = runTest {
        // The pre-flight space check can be overtaken by another app filling the volume, so the
        // repository can still report a full device once the transfer is under way. That's the
        // state of the device, not an app bug: actionable toast, and Crashlytics stays quiet.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        every { fileRepository.copyFiles(any(), any(), any(), any()) } returns flow {
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
    fun `copy whose selection does not fit stops before the transfer starts`() = runTest {
        // The pre-flight check the test above says can be overtaken — which nothing reached, because
        // every other operation test leaves `totalSize` at 0 against the ample `availableBytes` the
        // fixture stubs, so the comparison could be deleted and stay green. Refusing here is what
        // keeps a doomed copy from writing until the volume is full and then failing halfway.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 4_000L
        every { anyConstructed<StatFs>().availableBytes } returns 1_000L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flowOf(
            transferCompletion(copiedFiles = 1, skippedFiles = 0)
        )

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

        verify(exactly = 0) { fileRepository.copyFiles(any(), any(), any(), any()) }
        assertNull(viewModel.state.value.operationProgress)
        // No failure event reports this refusal, so it has to block the rating ask itself.
        verify { ReviewPrompt.onActionFailed() }
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
        verify(exactly = 0) { fileRepository.copyFiles(any(), any(), any(), any()) }
        verify(exactly = 0) { ErrorReporter.error(any(), any(), any()) }
        verify { ReviewPrompt.onActionFailed() }
    }

    @Test
    fun `move that deletes source reports success`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flowOf(
            CopyProgress(
                currentFile = "",
                copiedFiles = 1,
                totalFiles = 1,
                copiedBytes = 10L,
                totalBytes = 10L,
                isComplete = true,
                sourceDeleteFailed = false
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.MoveTo)

        viewModel.executeOperation("/storage/emulated/0/Target")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { AnalyticsTracker.trackDestinationPickerOperationFinished("move", true) }
    }

    // === Move by rename ===
    //
    // A move renames what it can before it copies anything. The stubs in setUp rename nothing,
    // which keeps every move test above on the copy path; the tests below stub the rename step.

    private val moveTarget = "/storage/emulated/0/Target"

    /** What the rename step answers when it renamed [renamed] and left [remaining] to the copy. */
    private fun renameResult(
        renamed: List<FileItem> = emptyList(),
        remaining: List<FileItem> = emptyList(),
        movedFiles: Int = renamed.size
    ) = RenameMoveResult(renamed = renamed, remaining = remaining, movedFiles = movedFiles)

    /** Selects [items] and opens the picker to move them, as the user does before confirming. */
    private fun selectForMove(viewModel: FolderViewModel, items: List<FileItem>) {
        items.forEach { viewModel.toggleSelection(it) }
        viewModel.onAction(FileAction.MoveTo)
    }

    @Test
    fun `a move whose every item renames copies nothing and reports one success`() = runTest {
        var listings = 0
        coEvery { fileRepository.listFiles(any(), any(), any()) } answers {
            listings++
            testFiles
        }
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.moveByRename(any(), any(), any(), any()) } returns
            renameResult(renamed = listOf(testFiles[1]))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val listingsBefore = listings

        selectForMove(viewModel, listOf(testFiles[1]))
        viewModel.executeOperation(moveTarget)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 0) { fileRepository.copyFiles(any(), any(), any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackDestinationPickerOperationFinished(any(), any()) }
        verify { AnalyticsTracker.trackDestinationPickerOperationFinished("move", true) }
        verify(exactly = 1) { ReviewPrompt.onActionSucceeded() }
        assertNull(viewModel.state.value.operationProgress)
        assertTrue("The folder must reload to show the move", listings > listingsBefore)
    }

    // The reported problem: a move on one drive was refused for want of space a rename never uses.
    @Test
    fun `a move that renames succeeds even when its selection would not fit as a copy`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        // No name is taken, so nothing has to fit up front; the selection itself is four times the
        // free space, which a copy could never fit into.
        coEvery { fileRepository.totalSize(emptyList()) } returns 0L
        coEvery { fileRepository.totalSize(listOf(testFiles[1])) } returns 4_000L
        every { anyConstructed<StatFs>().availableBytes } returns 1_000L
        coEvery { fileRepository.moveByRename(any(), any(), any(), any()) } returns
            renameResult(renamed = listOf(testFiles[1]))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectForMove(viewModel, listOf(testFiles[1]))

        viewModel.events.test {
            viewModel.executeOperation(moveTarget)
            testDispatcher.scheduler.advanceUntilIdle()

            expectNoEvents()
        }

        coVerify(exactly = 1) {
            fileRepository.moveByRename(listOf(testFiles[1]), moveTarget, any(), any())
        }
        verify { AnalyticsTracker.trackDestinationPickerOperationFinished("move", true) }
    }

    /**
     * A move still renaming when the delay ends shows the progress dialog, with a bar that has no
     * fraction to show, since a rename copies no bytes. Cancelling it has to stop the step and
     * report nothing, as cancelling a copy does.
     */
    @Test
    fun `cancelling a move while it renames clears the dialog and reports nothing`() = runTest {
        var listings = 0
        coEvery { fileRepository.listFiles(any(), any(), any()) } answers {
            listings++
            testFiles
        }
        coEvery { fileRepository.totalSize(any()) } returns 0L
        var renameStopped = false
        coEvery { fileRepository.moveByRename(any(), any(), any(), any()) } coAnswers {
            arg<(FileItem) -> Unit>(3)(testFiles[0])
            try {
                awaitCancellation()
            } finally {
                renameStopped = true
            }
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val listingsBefore = listings
        selectForMove(viewModel, testFiles)
        viewModel.executeOperation(moveTarget)
        testDispatcher.scheduler.advanceUntilIdle()

        val inFlight = requireNotNull(viewModel.state.value.operationProgress)
        assertEquals(OperationMode.MOVE, inFlight.mode)
        assertTrue("A rename has no byte progress to show", inFlight.isIndeterminate)
        assertEquals("Folder1", inFlight.currentFile)

        viewModel.cancelOperation()
        assertTrue(viewModel.state.value.operationProgress?.isCancelling == true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("Cancel must stop the rename step", renameStopped)
        assertNull(viewModel.state.value.operationProgress)
        verify(exactly = 0) { fileRepository.copyFiles(any(), any(), any(), any()) }
        verify(exactly = 0) { AnalyticsTracker.trackDestinationPickerOperationFinished(any(), any()) }
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
        assertTrue("Items renamed before the cancel must show", listings > listingsBefore)
    }

    // One rename is normally instant, but a folder on a slow volume is not: the item count says
    // nothing about how long a move takes, so a single item gets the dialog like any other move
    // that is still running when the delay ends.
    @Test
    fun `a move of a single item shows the progress dialog once the delay has passed`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        val renameGate = CompletableDeferred<Unit>()
        coEvery { fileRepository.moveByRename(any(), any(), any(), any()) } coAnswers {
            arg<(FileItem) -> Unit>(3)(testFiles[0])
            renameGate.await()
            renameResult(renamed = listOf(testFiles[0]))
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        // A folder, which can hold any number of files.
        selectForMove(viewModel, listOf(testFiles[0]))
        viewModel.executeOperation(moveTarget)
        testDispatcher.scheduler.advanceTimeBy(PROGRESS_DIALOG_DELAY_MS - 1)
        testDispatcher.scheduler.runCurrent()

        assertNull("The dialog waits out the delay", viewModel.state.value.operationProgress)

        testDispatcher.scheduler.advanceTimeBy(1)
        testDispatcher.scheduler.runCurrent()

        val inFlight = requireNotNull(viewModel.state.value.operationProgress)
        assertEquals(OperationMode.MOVE, inFlight.mode)
        assertTrue("A rename has no byte progress to show", inFlight.isIndeterminate)
        assertEquals("Folder1", inFlight.currentFile)

        renameGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.operationProgress)
        verify { AnalyticsTracker.trackDestinationPickerOperationFinished("move", true) }
    }

    @Test
    fun `a move that ends within the delay never shows the progress dialog`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.moveByRename(any(), any(), any(), any()) } coAnswers {
            arg<(FileItem) -> Unit>(3)(testFiles[0])
            delay(PROGRESS_DIALOG_DELAY_MS - 1)
            arg<(FileItem) -> Unit>(3)(testFiles[1])
            renameResult(renamed = testFiles)
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val shown = shownValues(viewModel) { it.operationProgress }
        selectForMove(viewModel, testFiles)
        viewModel.executeOperation(moveTarget)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<OperationProgress>(), shown)
        verify { AnalyticsTracker.trackDestinationPickerOperationFinished("move", true) }
    }

    // The pre-flight measurement runs before anything is transferred, and on a large selection it
    // can outlast the delay by itself: the dialog then opens with no fraction until bytes move.
    @Test
    fun `a copy still measuring when the delay ends shows a dialog with no fraction yet`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        val measuring = CompletableDeferred<Long>()
        coEvery { fileRepository.totalSize(any()) } coAnswers { measuring.await() }
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flowOf(
            transferCompletion(copiedFiles = 1, skippedFiles = 0)
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.CopyTo)
        viewModel.executeOperation(moveTarget)
        testDispatcher.scheduler.advanceTimeBy(PROGRESS_DIALOG_DELAY_MS)
        testDispatcher.scheduler.runCurrent()

        assertEquals(
            OperationProgress(
                mode = OperationMode.COPY,
                currentFile = "",
                copiedBytes = 0L,
                totalBytes = 0L,
                isIndeterminate = true
            ),
            viewModel.state.value.operationProgress
        )

        measuring.complete(0L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.operationProgress)
        verify { AnalyticsTracker.trackDestinationPickerOperationFinished("copy", true) }
    }

    // The same measurement can end in a refusal after the dialog appeared, which has to close it.
    @Test
    fun `a copy refused once its dialog is up closes the dialog`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        val measuring = CompletableDeferred<Long>()
        coEvery { fileRepository.totalSize(any()) } coAnswers { measuring.await() }
        every { anyConstructed<StatFs>().availableBytes } returns 1_000L

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.CopyTo)
        viewModel.executeOperation(moveTarget)
        testDispatcher.scheduler.advanceTimeBy(PROGRESS_DIALOG_DELAY_MS)
        testDispatcher.scheduler.runCurrent()
        assertNotNull(viewModel.state.value.operationProgress)

        viewModel.events.test {
            measuring.complete(4_000L)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(FolderUiEvent.ShowToastRes(R.string.error_not_enough_space), awaitItem())
        }
        assertNull(viewModel.state.value.operationProgress)
    }

    // Cancel raises the flag on the dialog at once, while the move is still unwinding. A rename the
    // step reports in that time must not lower it again and bring the Cancel button back.
    @Test
    fun `a rename reported after the cancel leaves the dialog cancelling`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        val beforeSecondRename = CompletableDeferred<Unit>()
        val beforeReturning = CompletableDeferred<Unit>()
        coEvery { fileRepository.moveByRename(any(), any(), any(), any()) } coAnswers {
            val onRenaming = arg<(FileItem) -> Unit>(3)
            // The step reports a rename from the IO thread, so one can land just after the cancel.
            withContext(NonCancellable) {
                onRenaming(testFiles[0])
                beforeSecondRename.await()
                onRenaming(testFiles[1])
                beforeReturning.await()
            }
            renameResult(renamed = testFiles)
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectForMove(viewModel, testFiles)
        viewModel.executeOperation(moveTarget)
        testDispatcher.scheduler.advanceTimeBy(PROGRESS_DIALOG_DELAY_MS)
        testDispatcher.scheduler.runCurrent()
        viewModel.cancelOperation()

        beforeSecondRename.complete(Unit)
        testDispatcher.scheduler.runCurrent()

        val unwinding = requireNotNull(viewModel.state.value.operationProgress)
        assertTrue("The cancel must stay accepted", unwinding.isCancelling)
        assertEquals("Folder1", unwinding.currentFile)

        beforeReturning.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.state.value.operationProgress)
    }

    /**
     * Until a move's dialog appears nothing covers the screen, so the user can start a copy while
     * the move runs. The move must then stay out of the copy's dialog: a rename it reports must not
     * overwrite the copy's progress, and its end must not close the copy's dialog or take its
     * handle, which would leave the copy's Cancel stopping nothing.
     */
    @Test
    fun `a move that ends while a copy runs leaves the copy its dialog and its cancel`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        val renameGate = CompletableDeferred<Unit>()
        coEvery { fileRepository.moveByRename(any(), any(), any(), any()) } coAnswers {
            renameGate.await()
            arg<(FileItem) -> Unit>(3)(testFiles[0])
            renameResult(renamed = listOf(testFiles[0]))
        }
        var copyStopped = false
        every { fileRepository.copyFiles(any(), any(), any(), any()) } returns flow {
            emit(
                CopyProgress(
                    currentFile = "file.txt",
                    copiedFiles = 0,
                    totalFiles = 1,
                    copiedBytes = 1L,
                    totalBytes = 10L
                )
            )
            try {
                awaitCancellation()
            } finally {
                copyStopped = true
            }
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectForMove(viewModel, listOf(testFiles[0]))
        viewModel.executeOperation(moveTarget)
        testDispatcher.scheduler.runCurrent()
        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.CopyTo)
        viewModel.executeOperation(moveTarget)
        testDispatcher.scheduler.advanceTimeBy(PROGRESS_DIALOG_DELAY_MS)
        testDispatcher.scheduler.runCurrent()

        assertEquals("file.txt", viewModel.state.value.operationProgress?.currentFile)

        renameGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        val copyProgress = viewModel.state.value.operationProgress
        assertEquals("The copy's dialog must stay up", "file.txt", copyProgress?.currentFile)
        assertEquals(OperationMode.COPY, copyProgress?.mode)
        viewModel.cancelOperation()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("The copy's Cancel must still stop it", copyStopped)
        assertNull(viewModel.state.value.operationProgress)
    }

    // The real transfer runs on another dispatcher, so its emissions are collected through a
    // channel, under a child of the operation's job rather than the job itself: closing the dialog
    // from there has to recognise the operation as its own all the same.
    @Test
    fun `a finished transfer collected through a channel closes its dialog`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flow {
            emit(
                CopyProgress(
                    currentFile = "file.txt",
                    copiedFiles = 0,
                    totalFiles = 1,
                    copiedBytes = 1L,
                    totalBytes = 10L
                )
            )
            emit(transferCompletion(copiedFiles = 1, skippedFiles = 0))
        }.buffer()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectForMove(viewModel, listOf(testFiles[1]))
        viewModel.executeOperation(moveTarget)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { AnalyticsTracker.trackDestinationPickerOperationFinished("move", true) }
        assertNull("The dialog must close once the move is done", viewModel.state.value.operationProgress)
    }

    @Test
    fun `a partly renamed move copies only what is left, with byte progress`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.findMoveCollisions(any(), any()) } returns listOf(testFiles[1])
        coEvery { fileRepository.moveByRename(any(), any(), any(), any()) } returns
            renameResult(renamed = listOf(testFiles[0]), remaining = listOf(testFiles[1]))
        every { fileRepository.copyFiles(any(), any(), any(), any()) } returns flow {
            emit(
                CopyProgress(
                    currentFile = "file.txt",
                    copiedFiles = 0,
                    totalFiles = 1,
                    copiedBytes = 1L,
                    totalBytes = 1024L
                )
            )
            awaitCancellation()
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectForMove(viewModel, testFiles)
        viewModel.executeOperation(moveTarget)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) {
            fileRepository.copyFiles(listOf(testFiles[1]), moveTarget, true, any())
        }
        val progress = requireNotNull(viewModel.state.value.operationProgress)
        assertFalse("The copy reports bytes, so the bar shows them", progress.isIndeterminate)
        assertEquals("file.txt", progress.currentFile)
        assertEquals(1024L, progress.totalBytes)
    }

    @Test
    fun `a partly renamed move counts its renamed files in a partial success`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.findMoveCollisions(any(), any()) } returns listOf(testFiles[1])
        coEvery { fileRepository.moveByRename(any(), any(), any(), any()) } returns
            renameResult(renamed = listOf(testFiles[0]), remaining = listOf(testFiles[1]), movedFiles = 5)
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flowOf(
            transferCompletion(copiedFiles = 1, skippedFiles = 1)
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectForMove(viewModel, testFiles)

        viewModel.events.test {
            viewModel.executeOperation(moveTarget)
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem() as FolderUiEvent.ShowTransferPartialSuccess
            assertEquals(R.plurals.move_partial_success, event.pluralResId)
            assertEquals("Five renamed and one copied", 6, event.transferred)
            assertEquals(1, event.skipped)
        }
        verify(exactly = 1) { AnalyticsTracker.trackDestinationPickerOperationFinished("move", false) }
    }

    @Test
    fun `a partly renamed move whose copied originals stay reports that`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.findMoveCollisions(any(), any()) } returns listOf(testFiles[1])
        coEvery { fileRepository.moveByRename(any(), any(), any(), any()) } returns
            renameResult(renamed = listOf(testFiles[0]), remaining = listOf(testFiles[1]))
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flowOf(
            transferCompletion(copiedFiles = 1, skippedFiles = 0, sourceDeleteFailed = true)
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectForMove(viewModel, testFiles)

        viewModel.events.test {
            viewModel.executeOperation(moveTarget)
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem() as FolderUiEvent.ShowToastRes
            assertEquals(R.string.error_move_source_not_deleted, event.messageResId)
        }
    }

    @Test
    fun `a copy that runs out of space after renames reports it once`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.findMoveCollisions(any(), any()) } returns listOf(testFiles[1])
        coEvery { fileRepository.moveByRename(any(), any(), any(), any()) } returns
            renameResult(renamed = listOf(testFiles[0]), remaining = listOf(testFiles[1]))
        every { fileRepository.copyFiles(any(), any(), any(), any()) } returns flow {
            throw InsufficientStorageException("Not enough disk space")
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectForMove(viewModel, testFiles)

        viewModel.events.test {
            viewModel.executeOperation(moveTarget)
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem() as FolderUiEvent.ShowToastRes
            assertEquals(R.string.error_not_enough_space, event.messageResId)
        }
        verify(exactly = 1) { AnalyticsTracker.trackDestinationPickerOperationFinished(any(), any()) }
        verify { AnalyticsTracker.trackOperationFailed("move", "insufficient_storage", any(), any(), any()) }
        assertNull(viewModel.state.value.operationProgress)
    }

    @Test
    fun `a move whose taken names do not fit is refused before anything moves`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.findMoveCollisions(any(), any()) } returns listOf(testFiles[1])
        coEvery { fileRepository.totalSize(listOf(testFiles[1])) } returns 4_000L
        every { anyConstructed<StatFs>().availableBytes } returns 1_000L

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectForMove(viewModel, testFiles)

        viewModel.events.test {
            viewModel.executeOperation(moveTarget)
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem() as FolderUiEvent.ShowToastRes
            assertEquals(R.string.error_not_enough_space, event.messageResId)
        }
        coVerify(exactly = 0) { fileRepository.moveByRename(any(), any(), any(), any()) }
        verify(exactly = 0) { fileRepository.copyFiles(any(), any(), any(), any()) }
        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
        // Refused up front, as a move always was: no event, so the counts stay comparable.
        verify(exactly = 0) { AnalyticsTracker.trackDestinationPickerOperationFinished(any(), any()) }
    }

    /**
     * A rename that failed for a reason the pre-flight check could not see leaves its item to the
     * copy, which then does not fit. What was renamed stays moved, and this is reported as a move
     * that ran out of space part-way, since part of it did happen.
     */
    @Test
    fun `a move whose unrenamed remainder does not fit stops after the renames`() = runTest {
        var listings = 0
        coEvery { fileRepository.listFiles(any(), any(), any()) } answers {
            listings++
            testFiles
        }
        coEvery { fileRepository.totalSize(emptyList()) } returns 0L
        coEvery { fileRepository.totalSize(listOf(testFiles[1])) } returns 4_000L
        every { anyConstructed<StatFs>().availableBytes } returns 1_000L
        coEvery { fileRepository.moveByRename(any(), any(), any(), any()) } returns
            renameResult(renamed = listOf(testFiles[0]), remaining = listOf(testFiles[1]))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val listingsBefore = listings
        selectForMove(viewModel, testFiles)

        viewModel.events.test {
            viewModel.executeOperation(moveTarget)
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem() as FolderUiEvent.ShowToastRes
            assertEquals(R.string.error_not_enough_space, event.messageResId)
        }
        verify(exactly = 0) { fileRepository.copyFiles(any(), any(), any(), any()) }
        verify(exactly = 1) { AnalyticsTracker.trackDestinationPickerOperationFinished(any(), any()) }
        verify { AnalyticsTracker.trackDestinationPickerOperationFinished("move", false) }
        verify { AnalyticsTracker.trackOperationFailed("move", "insufficient_storage", any(), any(), any()) }
        assertNull(viewModel.state.value.operationProgress)
        assertTrue("The renamed item must show at its new place", listings > listingsBefore)
    }

    // Every rename fails on a move to another volume, so nothing moves before the remainder is
    // measured: the refusal is the one a move always got, silent as before.
    @Test
    fun `a move to another volume that does not fit is refused as before`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(emptyList()) } returns 0L
        coEvery { fileRepository.totalSize(listOf(testFiles[1])) } returns 4_000L
        every { anyConstructed<StatFs>().availableBytes } returns 1_000L

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectForMove(viewModel, listOf(testFiles[1]))

        viewModel.events.test {
            viewModel.executeOperation(moveTarget)
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem() as FolderUiEvent.ShowToastRes
            assertEquals(R.string.error_not_enough_space, event.messageResId)
        }
        verify(exactly = 0) { fileRepository.copyFiles(any(), any(), any(), any()) }
        verify(exactly = 1) { ReviewPrompt.onActionFailed() }
        verify(exactly = 0) { AnalyticsTracker.trackDestinationPickerOperationFinished(any(), any()) }
        verify(exactly = 0) { AnalyticsTracker.trackOperationFailed(any(), any(), any(), any(), any()) }
        assertNull(viewModel.state.value.operationProgress)
    }

    @Test
    fun `a move to a destination that is gone never starts renaming`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        every { anyConstructed<StatFs>().availableBytes } throws IllegalArgumentException("gone")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectForMove(viewModel, testFiles)

        viewModel.events.test {
            viewModel.executeOperation(moveTarget)
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem() as FolderUiEvent.ShowToastRes
            assertEquals(R.string.error_invalid_target_path, event.messageResId)
        }
        coVerify(exactly = 0) { fileRepository.moveByRename(any(), any(), any(), any()) }
    }

    @Test
    fun `a rename step refused for its destination reports an invalid target`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.moveByRename(any(), any(), any(), any()) } throws
            SecurityException("Target directory is outside allowed storage paths")

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        selectForMove(viewModel, testFiles)

        viewModel.events.test {
            viewModel.executeOperation(moveTarget)
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem() as FolderUiEvent.ShowToastRes
            assertEquals(R.string.error_invalid_target_path, event.messageResId)
        }
        verify(exactly = 0) { fileRepository.copyFiles(any(), any(), any(), any()) }
        verify { AnalyticsTracker.trackDestinationPickerOperationFinished("move", false) }
        verify { AnalyticsTracker.trackOperationFailed("move", "invalid_target_path", any(), any(), any()) }
        verify { ErrorReporter.error(any(), "file_operation", "invalid_target_path") }
        assertNull(viewModel.state.value.operationProgress)
    }

    @Test
    fun `a copy never goes through the rename step`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        coEvery { fileRepository.totalSize(any()) } returns 0L
        coEvery { fileRepository.copyFiles(any(), any(), any(), any()) } returns flowOf(
            transferCompletion(copiedFiles = 1, skippedFiles = 0)
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.toggleSelection(testFiles[1])
        viewModel.onAction(FileAction.CopyTo)
        viewModel.executeOperation(moveTarget)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { fileRepository.findMoveCollisions(any(), any()) }
        coVerify(exactly = 0) { fileRepository.moveByRename(any(), any(), any(), any()) }
        verify { fileRepository.copyFiles(listOf(testFiles[1]), moveTarget, false, any()) }
        verify { AnalyticsTracker.trackDestinationPickerOperationFinished("copy", true) }
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
        // Reported as completed before the check, so the rating ask must not count it.
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
    }

    @Test
    fun `compress that could not list a directory reports a partial success`() = runTest {
        // The same loss on the compress path, and the one the archive hides best: a directory the
        // walk could not list contributed nothing to `skippedFiles` and nothing to `totalFiles`
        // either, so with `skippedFiles` at zero the branch is entered on `unreadableDirectories`
        // alone. Drop that term and an archive missing a whole subtree is announced as complete.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.compressFiles(any(), any(), any(), any()) } returns flow {
            emit(compressCompletion(compressedFiles = 2, skippedFiles = 0, unreadableDirectories = 1))
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

        verify { AnalyticsTracker.trackOperationFailed("compress", "partial") }
    }

    @Test
    fun `a partial archive counts unreadable directories alongside skipped files`() = runTest {
        // Both kinds of loss in one archive; the two are summed into the single count the user
        // reads, which only a case with two different non-zero terms can pin.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.compressFiles(any(), any(), any(), any()) } returns flow {
            emit(compressCompletion(compressedFiles = 2, skippedFiles = 1, unreadableDirectories = 2))
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
            assertEquals(3, event.skipped)
        }
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
        verify(exactly = 1) { ReviewPrompt.onActionSucceeded() }
    }

    @Test
    fun `compress to an invalid target shows an actionable toast and is not reported`() = runTest {
        // A removable volume can disappear between showing its folder and confirming compression.
        // The repository rejects the now-unlisted target before writing, so this is expected device
        // state rather than an app defect worth sending to Crashlytics.
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.compressFiles(any(), any(), any(), any()) } returns flow {
            throw SecurityException("Target directory is outside allowed storage paths")
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
                R.string.error_invalid_target_path,
                (event as FolderUiEvent.ShowToastRes).messageResId
            )
        }

        assertNull(viewModel.state.value.compressProgress)
        verify { AnalyticsTracker.trackOperationFailed("compress", "invalid_target_path") }
        verify(exactly = 0) { ErrorReporter.error(any(), any(), any()) }
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

    // === The compression's delayed progress dialog ===

    /** A compression part-way through its archive, on [currentFile]. */
    private fun compressUnderway(currentFile: String) = CompressProgress(
        currentFile = currentFile,
        compressedFiles = 1,
        totalFiles = 4,
        compressedBytes = 1L,
        totalBytes = 4L
    )

    @Test
    fun `a compression that ends within the delay never shows its dialog`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.compressFiles(any(), any(), any(), any()) } returns flow {
            emit(compressUnderway("a.txt"))
            delay(PROGRESS_DIALOG_DELAY_MS - 1)
            emit(compressCompletion(compressedFiles = 2, skippedFiles = 0))
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val shown = shownValues(viewModel) { it.compressProgress }
        viewModel.showCompressDialog(testFiles)
        viewModel.onCompress("archive.zip")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<CompressProgress>(), shown)
        verify(exactly = 1) { AnalyticsTracker.trackCompressCompleted(testFiles.size) }
    }

    @Test
    fun `a compression still running after the delay shows its latest progress`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.compressFiles(any(), any(), any(), any()) } returns flow {
            emit(compressUnderway("a.txt"))
            emit(compressUnderway("b.txt"))
            awaitCancellation()
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.showCompressDialog(testFiles)
        viewModel.onCompress("archive.zip")
        advanceBy(PROGRESS_DIALOG_DELAY_MS - 1)

        assertNull("The dialog waits out the delay", viewModel.state.value.compressProgress)

        advanceBy(1)

        assertEquals("b.txt", viewModel.state.value.compressProgress?.currentFile)
    }

    // The archive's walk measures the selection before it writes anything, and on a large tree the
    // measurement can outlast the delay by itself.
    @Test
    fun `a compression still measuring when the delay ends shows a dialog with no total yet`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        every { fileRepository.compressFiles(any(), any(), any(), any()) } returns
            flow<CompressProgress> { awaitCancellation() }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.showCompressDialog(testFiles)
        viewModel.onCompress("archive.zip")
        advanceBy(PROGRESS_DIALOG_DELAY_MS)

        assertEquals(
            CompressProgress(
                currentFile = "",
                compressedFiles = 0,
                totalFiles = 0,
                compressedBytes = 0L,
                totalBytes = 0L
            ),
            viewModel.state.value.compressProgress
        )
    }

    /**
     * Until a compression's dialog appears nothing covers the screen, so a second one can start.
     * The first must then stay out of the second one's dialog — its only Cancel button: neither its
     * progress nor its end may reach it.
     */
    @Test
    fun `a compression that finishes while a newer one runs leaves that one its dialog and its cancel`() = runTest {
        coEvery { fileRepository.listFiles(any(), any(), any()) } returns testFiles
        val firstArchive = CompletableDeferred<Unit>()
        var secondArchiveStopped = false
        every { fileRepository.compressFiles(any(), any(), any(), any()) } returnsMany listOf(
            flow {
                emit(compressUnderway("a.txt"))
                firstArchive.await()
                emit(compressUnderway("a-later.txt"))
                emit(compressCompletion(compressedFiles = 1, skippedFiles = 0))
            },
            flow {
                emit(compressUnderway("b.txt"))
                try {
                    awaitCancellation()
                } finally {
                    secondArchiveStopped = true
                }
            }
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.showCompressDialog(listOf(testFiles[0]))
        viewModel.onCompress("first.zip")
        testDispatcher.scheduler.runCurrent()
        viewModel.showCompressDialog(listOf(testFiles[1]))
        viewModel.onCompress("second.zip")
        advanceBy(PROGRESS_DIALOG_DELAY_MS)

        assertEquals("b.txt", viewModel.state.value.compressProgress?.currentFile)

        firstArchive.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "The first compression must neither overwrite nor close the second one's dialog",
            "b.txt",
            viewModel.state.value.compressProgress?.currentFile
        )
        verify(exactly = 1) { AnalyticsTracker.trackCompressCompleted(1) }

        viewModel.cancelCompression()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("The second compression's Cancel must still stop it", secondArchiveStopped)
        assertNull(viewModel.state.value.compressProgress)
    }
}
