package com.mauriciotogneri.fileexplorer.ui.screens.analyzercategory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerFileEntry
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.AnalyzerResultsHolder
import com.mauriciotogneri.fileexplorer.data.repository.CategoryFiles
import com.mauriciotogneri.fileexplorer.data.repository.DeleteProgress
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.ZipInfo
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.DeleteFailure
import com.mauriciotogneri.fileexplorer.data.util.ERRNO_UNKNOWN
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.util.PROGRESS_DIALOG_DELAY_MS
import com.mauriciotogneri.fileexplorer.util.ReviewPrompt
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Real files in a temp tree rather than a stubbed reader: the paging exists because reading a file
 * back costs something, and a test that never touches the disk would not exercise the step it is
 * there to bound. The delete itself is stubbed — [FileRepository] has its own tests, and what
 * matters here is what the listing, its total and the chart behind it do with each outcome.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyzerCategoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var root: File
    private lateinit var application: Application
    private lateinit var fileRepository: FileRepository
    private lateinit var storageRepository: StorageRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        root = Files.createTempDirectory("analyzer-category").toFile()
        application = mockk(relaxed = true)
        fileRepository = mockk()
        storageRepository = mockk(relaxed = true)
        mockkObject(AnalyticsTracker)
        mockkObject(ReviewPrompt)
        every { AnalyticsTracker.trackDeleteCompleted(any(), any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackOperationFailed(any(), any(), any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackDeleteFailed(any(), any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AnalyzerResultsHolder.clear()
        unmockkObject(AnalyticsTracker)
        unmockkObject(ReviewPrompt)
        root.deleteRecursively()
    }

    @Test
    fun `loads the first page and stops there`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 250)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AnalyzerCategoryViewModel.PAGE_SIZE, state.files.size)
        assertTrue(state.hasMore)
        assertFalse(state.isLoadingPage)
    }

    @Test
    fun `each request appends the next page`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 250)
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(200, viewModel.uiState.value.files.size)
        assertTrue(viewModel.uiState.value.hasMore)
    }

    @Test
    fun `the last page is short and ends the list`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 250)
        advanceUntilIdle()

        repeat(2) {
            viewModel.loadNextPage()
            advanceUntilIdle()
        }

        assertEquals(250, viewModel.uiState.value.files.size)
        assertFalse(viewModel.uiState.value.hasMore)
    }

    @Test
    fun `asking again past the end changes nothing`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 5)
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.files.size)
        assertFalse(viewModel.uiState.value.hasMore)
    }

    @Test
    fun `a request while a page is in flight is dropped`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 250)

        // No advance in between: both land while the first page is still being read, and a second
        // page appended from the same starting index would repeat those hundred rows.
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(AnalyzerCategoryViewModel.PAGE_SIZE, viewModel.uiState.value.files.size)
    }

    @Test
    fun `keeps the order and the sizes the scan measured`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 3)
        advanceUntilIdle()

        val files = viewModel.uiState.value.files
        assertEquals(listOf("file0.bin", "file1.bin", "file2.bin"), files.map { it.name })
        assertEquals(listOf(300L, 200L, 100L), files.map { it.size })
    }

    @Test
    fun `a category with no files reports itself empty`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 0)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.files.isEmpty())
        assertFalse(state.hasMore)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `a file deleted since the scan keeps the size it was listed at`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 1)
        File(root, "file0.bin").delete()
        advanceUntilIdle()

        assertEquals(100L, viewModel.uiState.value.files.single().size)
    }

    @Test
    fun `selecting a row twice leaves it unselected`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 3)
        advanceUntilIdle()
        val file = viewModel.uiState.value.files.first()

        viewModel.toggleSelection(file)
        assertEquals(listOf(file), viewModel.uiState.value.selectedFiles)
        assertTrue(viewModel.uiState.value.isSelectionMode)

        viewModel.toggleSelection(file)
        assertFalse(viewModel.uiState.value.isSelectionMode)
    }

    @Test
    fun `select all covers the rows loaded so far, not the whole category`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 250)
        advanceUntilIdle()

        viewModel.selectAll()

        val state = viewModel.uiState.value
        assertEquals(AnalyzerCategoryViewModel.PAGE_SIZE, state.selectedCount)
        assertTrue(state.allSelected)
    }

    @Test
    fun `a page arriving behind a full selection reopens select all`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 250)
        advanceUntilIdle()
        viewModel.selectAll()

        viewModel.loadNextPage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AnalyzerCategoryViewModel.PAGE_SIZE, state.selectedCount)
        assertFalse(state.allSelected)
    }

    @Test
    fun `deleting takes the rows off the list and their bytes off the total`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 3)
        advanceUntilIdle()
        val target = viewModel.uiState.value.files.first()
        every { fileRepository.deleteWithProgress(listOf(target)) } returns
            flowOf(walkFinished(removed = listOf(target)))

        viewModel.showDeleteConfirmDialog(listOf(target))
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("file1.bin", "file2.bin"), state.files.map { it.name })
        assertEquals(300L, state.totalBytes)
        assertFalse(state.isSelectionMode)
    }

    @Test
    fun `a row reselected during delete is pruned when that row is removed`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 3)
        advanceUntilIdle()
        val target = viewModel.uiState.value.files.first()
        val survivor = viewModel.uiState.value.files[1]
        every { fileRepository.deleteWithProgress(listOf(target)) } returns flow {
            delay(1)
            emit(walkFinished(removed = listOf(target)))
        }

        viewModel.showDeleteConfirmDialog(listOf(target))
        viewModel.onDeleteConfirmed()
        runCurrent()
        viewModel.toggleSelection(target)
        viewModel.toggleSelection(survivor)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(setOf(survivor.path), state.selectedPaths)
        assertEquals(listOf(survivor), state.selectedFiles)
    }

    @Test
    fun `a path that was already gone leaves the list the same way a deleted one does`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 3)
        advanceUntilIdle()
        val target = viewModel.uiState.value.files.first()
        every { fileRepository.deleteWithProgress(listOf(target)) } returns
            flowOf(walkFinished(absent = listOf(target)))

        viewModel.showDeleteConfirmDialog(listOf(target))
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.files.size)
        assertEquals(300L, viewModel.uiState.value.totalBytes)
    }

    @Test
    fun `deleting corrects the chart the listing was opened from`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 3)
        advanceUntilIdle()
        val target = viewModel.uiState.value.files.first()
        every { fileRepository.deleteWithProgress(listOf(target)) } returns
            flowOf(walkFinished(removed = listOf(target)))

        viewModel.showDeleteConfirmDialog(listOf(target))
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        val held = AnalyzerResultsHolder.filesFor(AnalyzerCategory.IMAGES)!!
        assertEquals(300L, held.totalBytes)
        assertEquals(listOf("file1.bin", "file2.bin"), held.entries.map { File(it.path).name })
        verify(exactly = 1) { ReviewPrompt.onActionSucceeded() }
    }

    @Test
    fun `a screen built with no results held says so instead of reading as an empty category`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(entryCount = 3, held = false)
            advanceUntilIdle()

            // The activity closes on this. An empty category is a different answer — it has results
            // and none of them are files of this type — and isEmpty is what reports that one.
            assertFalse(viewModel.hasResults)
        }

    @Test
    fun `a screen built from held results reports them`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 3)
        advanceUntilIdle()

        assertTrue(viewModel.hasResults)
        assertEquals(600L, viewModel.uiState.value.totalBytes)
    }

    @Test
    fun `a delete does not make the next page repeat rows already shown`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = AnalyzerCategoryViewModel.PAGE_SIZE + 20)
        advanceUntilIdle()
        val target = viewModel.uiState.value.files.first()
        every { fileRepository.deleteWithProgress(listOf(target)) } returns
            flowOf(walkFinished(removed = listOf(target)))

        viewModel.showDeleteConfirmDialog(listOf(target))
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        viewModel.loadNextPage()
        advanceUntilIdle()

        val names = viewModel.uiState.value.files.map { it.name }
        assertEquals(AnalyzerCategoryViewModel.PAGE_SIZE + 19, names.size)
        assertEquals(names.size, names.toSet().size)
        assertEquals("file119.bin", names.last())
        assertFalse(viewModel.uiState.value.hasMore)
    }

    @Test
    fun `deleting every loaded row fetches the next page instead of stopping`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 150)
        advanceUntilIdle()
        val loaded = viewModel.uiState.value.files
        every { fileRepository.deleteWithProgress(loaded) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = loaded.size,
                totalFiles = loaded.size,
                removedRootPaths = loaded.map { it.path },
                isComplete = true
            )
        )

        viewModel.showDeleteConfirmDialog(loaded)
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        // The request for a page comes from the list's trailing item, so a list emptied down to
        // nothing has nobody left to ask — and the screen would sit on a loading indicator with
        // the other fifty files unreachable.
        val state = viewModel.uiState.value
        assertEquals(50, state.files.size)
        assertEquals("file100.bin", state.files.first().name)
        assertFalse(state.hasMore)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `a delete landing under an in-flight page skips no entries`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 250)
        advanceUntilIdle()
        val target = viewModel.uiState.value.files.first()
        every { fileRepository.deleteWithProgress(listOf(target)) } returns
            flowOf(walkFinished(removed = listOf(target)))

        // The second page is asked for and left in flight; the delete then lands underneath it,
        // rewriting the list the page is indexing into.
        viewModel.loadNextPage()
        viewModel.showDeleteConfirmDialog(listOf(target))
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        repeat(4) {
            if (viewModel.uiState.value.hasMore) {
                viewModel.loadNextPage()
                advanceUntilIdle()
            }
        }

        val names = viewModel.uiState.value.files.map { it.name }
        assertEquals(249, names.size)
        assertEquals(names.size, names.toSet().size)
        // The entry the delete's recount stepped back over: committing the in-flight page against
        // the shortened list would advance the cursor past it and lose it silently.
        assertTrue("file200.bin" in names)
    }

    @Test
    fun `a delete that failed leaves the row where it was and says so`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 3)
        advanceUntilIdle()
        val target = viewModel.uiState.value.files.first()
        every { fileRepository.deleteWithProgress(listOf(target)) } returns flowOf(
            walkFinished(failedFiles = 1, failureErrno = ERRNO_UNKNOWN)
        )

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(listOf(target))
            viewModel.onDeleteConfirmed()
            advanceUntilIdle()

            assertTrue(awaitItem() is AnalyzerCategoryUiEvent.ShowToastRes)
        }

        val state = viewModel.uiState.value
        assertEquals(3, state.files.size)
        assertEquals(600L, state.totalBytes)
    }

    @Test
    fun `a delete that only partly succeeded reports both halves`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 3)
        advanceUntilIdle()
        val targets = viewModel.uiState.value.files.take(2)
        every { fileRepository.deleteWithProgress(targets) } returns flowOf(
            walkFinished(removed = listOf(targets.first()), failedFiles = 1, failureErrno = ERRNO_UNKNOWN)
        )

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(targets)
            viewModel.onDeleteConfirmed()
            advanceUntilIdle()

            val event = awaitItem() as AnalyzerCategoryUiEvent.ShowDeletePartialSuccess
            assertEquals(1, event.deleted)
            assertEquals(1, event.failed)
        }

        // Only the one that came away leaves the list; the other is still there to try again.
        assertEquals(listOf("file1.bin", "file2.bin"), viewModel.uiState.value.files.map { it.name })
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
    }

    // Rows are files, so this is rare here, but the screen follows the folder screen's rule: a system
    // folder is left in place without a syscall and carries no errno, so the user gets the reason
    // rather than the generic error that errno alone would give.
    @Test
    fun `a delete that left only a system folder says Android keeps it`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 3)
        advanceUntilIdle()
        val target = viewModel.uiState.value.files.first()
        every { fileRepository.deleteWithProgress(listOf(target)) } returns flowOf(
            walkFinished(structuralDeleteFailed = true, systemFolders = 1)
        )

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(listOf(target))
            viewModel.onDeleteConfirmed()
            advanceUntilIdle()

            assertEquals(
                AnalyzerCategoryUiEvent.ShowToastRes(R.string.delete_error_system_folder),
                awaitItem()
            )
        }

        verify(exactly = 1) {
            AnalyticsTracker.trackDeleteFailed(
                "structural",
                "structural",
                "analyzer_category",
                match { it.cause == DeleteFailure.SYSTEM_FOLDER }
            )
        }
        assertEquals(3, viewModel.uiState.value.files.size)
        verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
    }

    // A selection of a few rows used to be deleted in one call, with no dialog and nothing to stop
    // it with however long it took. Every delete now runs the walk the dialog's Cancel stops.
    @Test
    fun `a delete of one row runs the walk the dialog can cancel`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 3)
        advanceUntilIdle()
        val target = viewModel.uiState.value.files.first()
        var walkStopped = false
        every { fileRepository.deleteWithProgress(listOf(target)) } returns flow {
            emit(DeleteProgress(currentFile = target.name, deletedFiles = 0, totalFiles = 1))
            try {
                awaitCancellation()
            } finally {
                walkStopped = true
            }
        }

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(listOf(target))
            viewModel.onDeleteConfirmed()
            advanceTimeBy(PROGRESS_DIALOG_DELAY_MS)
            runCurrent()

            assertEquals(target.name, viewModel.uiState.value.deleteProgress?.currentFile)

            viewModel.cancelDelete()
            advanceUntilIdle()

            assertEquals(AnalyzerCategoryUiEvent.ShowToastRes(R.string.delete_cancelled), awaitItem())
        }

        assertTrue("Cancel must stop the walk", walkStopped)
        assertEquals(3, viewModel.uiState.value.files.size)
        coVerify(exactly = 0) { fileRepository.delete(any()) }
        verify(exactly = 0) { AnalyticsTracker.trackDeleteCompleted(any(), any(), any(), any()) }
    }

    @Test
    fun `a delete still running after the delay shows its dialog, which closes as the rows go`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(entryCount = 10)
            advanceUntilIdle()
            val targets = viewModel.uiState.value.files
            every { fileRepository.deleteWithProgress(targets) } returns flow {
                emit(DeleteProgress(currentFile = "file0.bin", deletedFiles = 0, totalFiles = 10))
                delay(PROGRESS_DIALOG_DELAY_MS + 1)
                emit(
                    DeleteProgress(
                        currentFile = "",
                        deletedFiles = 10,
                        totalFiles = 10,
                        removedRootPaths = targets.map { it.path },
                        isComplete = true
                    )
                )
            }

            viewModel.showDeleteConfirmDialog(targets)
            viewModel.onDeleteConfirmed()
            advanceTimeBy(PROGRESS_DIALOG_DELAY_MS - 1)
            runCurrent()

            assertNull("The dialog waits out the delay", viewModel.uiState.value.deleteProgress)

            advanceTimeBy(1)
            runCurrent()

            // Mid-walk the dialog is up, so the rows under it cannot be selected and deleted again.
            assertEquals(0, viewModel.uiState.value.deleteProgress?.deletedFiles)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertNull(state.deleteProgress)
            assertTrue(state.isEmpty)
            assertEquals(0L, AnalyzerResultsHolder.filesFor(AnalyzerCategory.IMAGES)!!.totalBytes)
            coVerify(exactly = 0) { fileRepository.delete(any()) }
            verify(exactly = 1) {
                AnalyticsTracker.trackDeleteCompleted(10, "analyzer_category", 10, 0)
            }
            verify(exactly = 1) { ReviewPrompt.onActionSucceeded() }
        }

    @Test
    fun `cancelling drops the rows whose files went and keeps the rest`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 10)
        advanceUntilIdle()
        val targets = viewModel.uiState.value.files
        val deletedBeforeCancel = targets.take(4)

        // A walk that took four files and was then cancelled. It reports none of them — its root
        // lists come only with the final emission — so the files on disk are the only record.
        every { fileRepository.deleteWithProgress(targets) } returns flow {
            deletedBeforeCancel.forEach { File(it.path).delete() }
            emit(DeleteProgress(currentFile = "file4.bin", deletedFiles = 4, totalFiles = 10))
            awaitCancellation()
        }

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(targets)
            viewModel.onDeleteConfirmed()
            advanceTimeBy(PROGRESS_DIALOG_DELAY_MS)
            runCurrent()
            assertEquals(4, viewModel.uiState.value.deleteProgress?.deletedFiles)

            viewModel.cancelDelete()
            advanceUntilIdle()

            assertEquals(AnalyzerCategoryUiEvent.ShowToastRes(R.string.delete_cancelled), awaitItem())
        }

        // Sizes run 1000 down to 100: the four that went took 3400 of the 5500 with them.
        val state = viewModel.uiState.value
        assertNull(state.deleteProgress)
        assertEquals(targets.drop(4).map { it.name }, state.files.map { it.name })
        assertEquals(2100L, state.totalBytes)
        assertEquals(2100L, AnalyzerResultsHolder.filesFor(AnalyzerCategory.IMAGES)!!.totalBytes)
        verify(exactly = 0) { AnalyticsTracker.trackDeleteCompleted(any(), any(), any(), any()) }
    }

    // Leaving the screen clears this ViewModel and stops the walk part-way, so a way out taken while
    // the dialog is held back shows it at once instead.
    @Test
    fun `a delete inside its delay shows its dialog at once when the screen is about to be left`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(entryCount = 10)
            advanceUntilIdle()
            val targets = viewModel.uiState.value.files
            every { fileRepository.deleteWithProgress(targets) } returns flow {
                emit(DeleteProgress(currentFile = "file1.bin", deletedFiles = 1, totalFiles = 10))
                awaitCancellation()
            }

            assertEquals(0, viewModel.uiState.value.runningDeletes)
            assertFalse("Nothing runs, so the screen may be left", viewModel.showPendingProgress())

            viewModel.showDeleteConfirmDialog(targets)
            viewModel.onDeleteConfirmed()
            runCurrent()

            assertEquals(1, viewModel.uiState.value.runningDeletes)
            assertNull("The dialog is still held back", viewModel.uiState.value.deleteProgress)
            assertTrue("A held-back dialog must keep the screen", viewModel.showPendingProgress())
            assertEquals("file1.bin", viewModel.uiState.value.deleteProgress?.currentFile)

            advanceTimeBy(PROGRESS_DIALOG_DELAY_MS)
            runCurrent()

            assertTrue("A dialog already up must keep the screen too", viewModel.showPendingProgress())

            viewModel.cancelDelete()

            assertFalse("A cancelled delete has given its dialog up", viewModel.showPendingProgress())
            assertEquals("A cancelled delete is counted until it unwinds", 1, viewModel.uiState.value.runningDeletes)

            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.runningDeletes)
            assertFalse(viewModel.showPendingProgress())
        }

    /**
     * Started, a second delete would take the first one's dialog and its Cancel and leave that one
     * running with neither, so the first one's dialog is shown instead and the second is not started.
     */
    @Test
    fun `a second delete asked for inside the first one's delay shows its dialog instead`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(entryCount = 10)
            advanceUntilIdle()
            val targets = viewModel.uiState.value.files
            every { fileRepository.deleteWithProgress(any()) } returns flow {
                emit(DeleteProgress(currentFile = "file1.bin", deletedFiles = 1, totalFiles = 10))
                awaitCancellation()
            }

            viewModel.showDeleteConfirmDialog(targets.take(2))
            viewModel.onDeleteConfirmed()
            runCurrent()

            assertNull("The dialog is still held back", viewModel.uiState.value.deleteProgress)

            viewModel.showDeleteConfirmDialog(targets.drop(2))
            viewModel.onDeleteConfirmed()
            runCurrent()

            assertEquals("file1.bin", viewModel.uiState.value.deleteProgress?.currentFile)
            assertTrue("No second delete is asked to be confirmed", viewModel.uiState.value.itemsToDelete.isEmpty())
            verify(exactly = 1) { fileRepository.deleteWithProgress(any()) }

            viewModel.cancelDelete()
            advanceUntilIdle()
            viewModel.showDeleteConfirmDialog(targets.drop(2))

            assertEquals(
                "Once the first is over a delete can be asked for again",
                targets.drop(2),
                viewModel.uiState.value.itemsToDelete
            )
        }

    @Test
    fun `leaving the listing mid-walk still drops what the walk removed`() = runTest(testDispatcher) {
        val store = ViewModelStore()
        val viewModel = createViewModel(entryCount = 10, store = store)
        advanceUntilIdle()
        val targets = viewModel.uiState.value.files
        every { fileRepository.deleteWithProgress(targets) } returns flow {
            File(targets.first().path).delete()
            emit(DeleteProgress(currentFile = "file1.bin", deletedFiles = 1, totalFiles = 10))
            awaitCancellation()
        }

        viewModel.showDeleteConfirmDialog(targets)
        viewModel.onDeleteConfirmed()
        runCurrent()

        // The screen going cancels the walk the way Cancel does, and the holder it leaves behind is
        // what the chart and a re-entered listing read for the rest of the analyzer session.
        store.clear()
        advanceUntilIdle()

        val held = AnalyzerResultsHolder.filesFor(AnalyzerCategory.IMAGES)!!
        assertEquals(targets.drop(1).map { it.path }, held.entries.map { it.path })
        assertEquals(4500L, held.totalBytes)
    }

    @Test
    fun `a walk that left rows behind reports them the way the folder screen does`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(entryCount = 10)
            advanceUntilIdle()
            val targets = viewModel.uiState.value.files
            every { fileRepository.deleteWithProgress(targets) } returns flowOf(
                DeleteProgress(
                    currentFile = "",
                    deletedFiles = 8,
                    totalFiles = 10,
                    failedFiles = 2,
                    removedRootPaths = targets.take(8).map { it.path },
                    failureErrno = 13,
                    isComplete = true
                )
            )

            viewModel.events.test {
                viewModel.showDeleteConfirmDialog(targets)
                viewModel.onDeleteConfirmed()
                advanceUntilIdle()

                assertEquals(
                    AnalyzerCategoryUiEvent.ShowDeletePartialSuccess(deleted = 8, failed = 2),
                    awaitItem()
                )
            }

            // The shape as the error type and the cause as the errno — the folder screen's walked
            // delete — so one query reads both screens' walked deletes alike.
            verify(exactly = 1) {
                AnalyticsTracker.trackDeleteFailed(
                    "partial",
                    "partial",
                    "analyzer_category",
                    match { it.errno == 13 }
                )
            }
            assertEquals(targets.drop(8).map { it.name }, viewModel.uiState.value.files.map { it.name })
            verify(exactly = 0) { ReviewPrompt.onActionSucceeded() }
        }

    @Test
    fun `a walk where every file failed reports it and leaves every row`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 10)
        advanceUntilIdle()
        val targets = viewModel.uiState.value.files
        every { fileRepository.deleteWithProgress(targets) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 0,
                totalFiles = 10,
                failedFiles = 10,
                failureErrno = 13,
                isComplete = true
            )
        )

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(targets)
            viewModel.onDeleteConfirmed()
            advanceUntilIdle()

            assertTrue(awaitItem() is AnalyzerCategoryUiEvent.ShowToastRes)
        }

        verify(exactly = 1) {
            AnalyticsTracker.trackDeleteFailed(
                "all_failed",
                "all_failed",
                "analyzer_category",
                match { it.errno == 13 }
            )
        }
        assertEquals(10, viewModel.uiState.value.files.size)
        assertEquals(5500L, viewModel.uiState.value.totalBytes)
    }

    // Nothing came away and no file failed: what held the walk back was a node that is not a file,
    // a row that has become a directory since the scan. The folder screen calls that "structural".
    @Test
    fun `a walk stopped only by a directory reports a structural failure`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 10)
        advanceUntilIdle()
        val targets = viewModel.uiState.value.files
        every { fileRepository.deleteWithProgress(targets) } returns flowOf(
            DeleteProgress(
                currentFile = "",
                deletedFiles = 0,
                totalFiles = 0,
                structuralDeleteFailed = true,
                failureErrno = 39,
                isComplete = true
            )
        )

        viewModel.events.test {
            viewModel.showDeleteConfirmDialog(targets)
            viewModel.onDeleteConfirmed()
            advanceUntilIdle()

            assertTrue(awaitItem() is AnalyzerCategoryUiEvent.ShowToastRes)
        }

        verify(exactly = 1) {
            AnalyticsTracker.trackDeleteFailed(
                "structural",
                "structural",
                "analyzer_category",
                match { it.errno == 39 }
            )
        }
        assertEquals(10, viewModel.uiState.value.files.size)
    }

    @Test
    fun `a walk that throws drops the rows whose files went and reports no path`() =
        runTest(testDispatcher) {
            mockkObject(ErrorReporter)
            try {
                every { ErrorReporter.error(any(), any(), any()) } just Runs
                val viewModel = createViewModel(entryCount = 10)
                advanceUntilIdle()
                val targets = viewModel.uiState.value.files
                val deletedBeforeFailure = targets.take(2)

                // An exception's message is where a path would leak: this one carries the path of
                // the file the walk was on, the way an I/O failure's often does.
                every { fileRepository.deleteWithProgress(targets) } returns flow {
                    deletedBeforeFailure.forEach { File(it.path).delete() }
                    emit(DeleteProgress(currentFile = "file2.bin", deletedFiles = 2, totalFiles = 10))
                    throw IllegalStateException(targets[2].path)
                }

                viewModel.events.test {
                    viewModel.showDeleteConfirmDialog(targets)
                    viewModel.onDeleteConfirmed()
                    advanceUntilIdle()

                    assertEquals(AnalyzerCategoryUiEvent.ShowToastRes(R.string.delete_error), awaitItem())
                }

                val reported = slot<Throwable>()
                verify(exactly = 1) { ErrorReporter.error(capture(reported), "delete_files", any()) }
                assertEquals(IllegalStateException::class.java.name, reported.captured.message)
                assertNull(reported.captured.cause)
                verify(exactly = 1) {
                    AnalyticsTracker.trackOperationFailed("delete", "exception", null, "analyzer_category", null)
                }

                val state = viewModel.uiState.value
                assertNull(state.deleteProgress)
                assertEquals(targets.drop(2).map { it.name }, state.files.map { it.name })
                assertEquals(3600L, state.totalBytes)
            } finally {
                unmockkObject(ErrorReporter)
            }
        }

    @Test
    fun `deleting the last row leaves the listing empty rather than loading`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 1)
        advanceUntilIdle()
        val target = viewModel.uiState.value.files.single()
        every { fileRepository.deleteWithProgress(listOf(target)) } returns
            flowOf(walkFinished(removed = listOf(target)))

        viewModel.showDeleteConfirmDialog(listOf(target))
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isEmpty)
        assertEquals(0L, state.totalBytes)
    }

    // A category spans folders and has none of its own, so opening the dialog has to point the
    // extraction at the archive's folder; a target left unset is "", and every extraction fails.
    @Test
    fun `an archive extracts into the folder that holds it`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 1)
        advanceUntilIdle()
        val zipFile = File(root, "sub/archive.zip").apply {
            parentFile?.mkdirs()
            writeText("Content")
        }
        val zip = FileItem.from(zipFile)
        coEvery { fileRepository.getZipInfo(zip.path) } returns ZipInfo(entryCount = 1, isEncrypted = false)
        coEvery { fileRepository.uncompressFile(any(), any(), any(), any()) } returns emptyFlow()

        viewModel.showUncompressDialog(zip)
        advanceUntilIdle()
        viewModel.confirmUncompress()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            fileRepository.uncompressFile(zip.path, File(root, "sub").absolutePath, null, any())
        }
    }

    @Test
    fun `an extraction inside its delay shows its dialog at once when the screen is about to be left`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 1)
        advanceUntilIdle()
        val zipFile = File(root, "sub/archive.zip").apply {
            parentFile?.mkdirs()
            writeText("Content")
        }
        val zip = FileItem.from(zipFile)
        coEvery { fileRepository.getZipInfo(zip.path) } returns ZipInfo(entryCount = 1, isEncrypted = false)
        coEvery { fileRepository.uncompressFile(any(), any(), any(), any()) } returns flow {
            emit(
                UncompressProgress(
                    currentFile = "file.txt",
                    extractedFiles = 0,
                    totalFiles = 1,
                    extractedBytes = 0L,
                    totalBytes = 1024L
                )
            )
            awaitCancellation()
        }

        assertFalse("Nothing runs, so the screen may be left", viewModel.showPendingProgress())

        viewModel.showUncompressDialog(zip)
        advanceUntilIdle()
        viewModel.confirmUncompress()
        runCurrent()

        assertEquals(1, viewModel.uiState.value.runningExtractions)
        assertNull("The dialog is still held back", viewModel.uiState.value.uncompressProgress)
        assertTrue("A held-back dialog must keep the screen", viewModel.showPendingProgress())
        runCurrent()
        assertEquals("file.txt", viewModel.uiState.value.uncompressProgress?.currentFile)

        viewModel.cancelUncompression()
        advanceUntilIdle()

        assertFalse(viewModel.showPendingProgress())
        assertEquals(0, viewModel.uiState.value.runningExtractions)
    }

    @Test
    fun `a delete that ends within the delay never shows its dialog`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 3)
        advanceUntilIdle()
        val target = viewModel.uiState.value.files.first()
        every { fileRepository.deleteWithProgress(listOf(target)) } returns flow {
            emit(DeleteProgress(currentFile = target.name, deletedFiles = 0, totalFiles = 1))
            delay(PROGRESS_DIALOG_DELAY_MS - 1)
            emit(walkFinished(removed = listOf(target)))
        }
        // Collected eagerly, so a dialog published and replaced at the same instant is caught too.
        val shown = mutableListOf<DeleteProgress>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { state -> state.deleteProgress?.let(shown::add) }
        }

        viewModel.showDeleteConfirmDialog(listOf(target))
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        assertEquals(emptyList<DeleteProgress>(), shown)
        assertEquals(listOf("file1.bin", "file2.bin"), viewModel.uiState.value.files.map { it.name })
    }

    @Test
    fun `a delete still counting when the delay ends shows a dialog with no total yet`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(entryCount = 3)
            advanceUntilIdle()
            val target = viewModel.uiState.value.files.first()
            every { fileRepository.deleteWithProgress(listOf(target)) } returns
                flow<DeleteProgress> { awaitCancellation() }

            viewModel.showDeleteConfirmDialog(listOf(target))
            viewModel.onDeleteConfirmed()
            advanceTimeBy(PROGRESS_DIALOG_DELAY_MS)
            runCurrent()

            assertEquals(
                DeleteProgress(currentFile = "", deletedFiles = 0, totalFiles = 0),
                viewModel.uiState.value.deleteProgress
            )
            viewModel.cancelDelete()
            advanceUntilIdle()
        }

    /**
     * A cancelled delete can still be unwinding — its walk stuck in a slow I/O call — when the user
     * starts the next one. It must stay out of the second one's dialog — its only Cancel button:
     * neither its progress nor its end may reach it.
     */
    @Test
    fun `a cancelled delete that finishes unwinding while a newer one runs leaves that one its dialog and its cancel`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(entryCount = 3)
            advanceUntilIdle()
            val (first, second) = viewModel.uiState.value.files
            val firstWalk = CompletableDeferred<Unit>()
            var secondWalkStopped = false
            every { fileRepository.deleteWithProgress(listOf(first)) } returns flow {
                emit(DeleteProgress(currentFile = first.name, deletedFiles = 0, totalFiles = 1))
                // Ignores the cancel, like a walk inside an I/O call: its delete cannot finish
                // unwinding until this returns.
                withContext(NonCancellable) { firstWalk.await() }
            }
            every { fileRepository.deleteWithProgress(listOf(second)) } returns flow {
                emit(DeleteProgress(currentFile = second.name, deletedFiles = 0, totalFiles = 1))
                try {
                    awaitCancellation()
                } finally {
                    secondWalkStopped = true
                }
            }

            viewModel.showDeleteConfirmDialog(listOf(first))
            viewModel.onDeleteConfirmed()
            advanceTimeBy(PROGRESS_DIALOG_DELAY_MS)
            runCurrent()
            assertEquals(first.name, viewModel.uiState.value.deleteProgress?.currentFile)

            viewModel.cancelDelete()
            runCurrent()
            viewModel.showDeleteConfirmDialog(listOf(second))
            viewModel.onDeleteConfirmed()
            advanceTimeBy(PROGRESS_DIALOG_DELAY_MS)
            runCurrent()

            assertEquals(second.name, viewModel.uiState.value.deleteProgress?.currentFile)

            firstWalk.complete(Unit)
            advanceUntilIdle()

            assertEquals(
                "The first delete must neither overwrite nor close the second one's dialog",
                second.name,
                viewModel.uiState.value.deleteProgress?.currentFile
            )

            viewModel.cancelDelete()
            advanceUntilIdle()

            assertTrue("The second delete's Cancel must still stop it", secondWalkStopped)
            assertNull(viewModel.uiState.value.deleteProgress)
        }

    /**
     * The final emission of a walk that removed [removed], found [absent] already gone, and left
     * the rest of the selection behind for the reasons the other fields give.
     */
    private fun walkFinished(
        removed: List<FileItem> = emptyList(),
        absent: List<FileItem> = emptyList(),
        failedFiles: Int = 0,
        failureErrno: Int? = null,
        structuralDeleteFailed: Boolean = false,
        systemFolders: Int = 0
    ) = DeleteProgress(
        currentFile = "",
        deletedFiles = removed.size + absent.size,
        totalFiles = removed.size + absent.size + failedFiles,
        failedFiles = failedFiles,
        structuralDeleteFailed = structuralDeleteFailed,
        removedRootPaths = removed.map { it.path },
        absentRootPaths = absent.map { it.path },
        failureErrno = failureErrno,
        systemFolders = systemFolders,
        isComplete = true
    )

    /**
     * [entryCount] files, each smaller than the one before it, in the descending order the scan
     * hands over — stored in [AnalyzerResultsHolder] as a completed scan would leave them.
     *
     * Always owned by a `ViewModelStore`, so that a test with something to check after the screen
     * is finished with can pass its own [store] and clear it the way the framework does rather
     * than reach for `onCleared` itself.
     */
    private fun createViewModel(
        entryCount: Int,
        held: Boolean = true,
        store: ViewModelStore? = null
    ): AnalyzerCategoryViewModel {
        val entries = (0 until entryCount).map { index ->
            val size = (entryCount - index) * 100L
            val file = File(root, "file$index.bin")
            file.writeBytes(ByteArray(size.toInt()))

            AnalyzerFileEntry(path = file.path, size = size)
        }
        val categoryFiles = CategoryFiles(totalBytes = entries.sumOf { it.size }, entries = entries)
        AnalyzerResultsHolder.store(mapOf(AnalyzerCategory.IMAGES to categoryFiles))

        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AnalyzerCategoryViewModel(
                application = application,
                category = AnalyzerCategory.IMAGES,
                categoryFiles = categoryFiles.takeIf { held },
                fileRepository = fileRepository,
                storageRepository = storageRepository,
                ioDispatcher = testDispatcher
            ) as T
        }

        val owner = store ?: ViewModelStore()

        return ViewModelProvider(owner, factory)[AnalyzerCategoryViewModel::class.java]
    }
}
