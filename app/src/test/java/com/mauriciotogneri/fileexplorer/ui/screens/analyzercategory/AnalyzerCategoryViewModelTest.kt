package com.mauriciotogneri.fileexplorer.ui.screens.analyzercategory

import android.app.Application
import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerFileEntry
import com.mauriciotogneri.fileexplorer.data.repository.AnalyzerResultsHolder
import com.mauriciotogneri.fileexplorer.data.repository.CategoryFiles
import com.mauriciotogneri.fileexplorer.data.repository.DeleteResult
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ERRNO_UNKNOWN
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        mockkObject(MediaStoreUtil)
        every { AnalyticsTracker.trackDeleteCompleted(any(), any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackOperationFailed(any(), any(), any(), any(), any()) } just Runs
        every { MediaStoreUtil.scanFiles(any(), any()) } just Runs
        coEvery { MediaStoreUtil.notifyTreeDeleted(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        AnalyzerResultsHolder.clear()
        unmockkObject(AnalyticsTracker)
        unmockkObject(MediaStoreUtil)
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
        coEvery { fileRepository.delete(listOf(target)) } returns
            DeleteResult(removedPaths = listOf(target.path))

        viewModel.showDeleteConfirmDialog(listOf(target))
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("file1.bin", "file2.bin"), state.files.map { it.name })
        assertEquals(300L, state.totalBytes)
        assertFalse(state.isSelectionMode)
    }

    @Test
    fun `a path that was already gone leaves the list the same way a deleted one does`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 3)
        advanceUntilIdle()
        val target = viewModel.uiState.value.files.first()
        coEvery { fileRepository.delete(listOf(target)) } returns
            DeleteResult(alreadyAbsentPaths = listOf(target.path))

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
        coEvery { fileRepository.delete(listOf(target)) } returns
            DeleteResult(removedPaths = listOf(target.path))

        viewModel.showDeleteConfirmDialog(listOf(target))
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        val held = AnalyzerResultsHolder.filesFor(AnalyzerCategory.IMAGES)!!
        assertEquals(300L, held.totalBytes)
        assertEquals(listOf("file1.bin", "file2.bin"), held.entries.map { File(it.path).name })
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
        coEvery { fileRepository.delete(listOf(target)) } returns
            DeleteResult(removedPaths = listOf(target.path))

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
        coEvery { fileRepository.delete(loaded) } returns
            DeleteResult(removedPaths = loaded.map { it.path })

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
        coEvery { fileRepository.delete(listOf(target)) } returns
            DeleteResult(removedPaths = listOf(target.path))

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
        coEvery { fileRepository.delete(listOf(target)) } returns
            DeleteResult(failedCount = 1, failureErrno = ERRNO_UNKNOWN)

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
        coEvery { fileRepository.delete(targets) } returns DeleteResult(
            removedPaths = listOf(targets.first().path),
            failedCount = 1,
            failureErrno = ERRNO_UNKNOWN
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
    }

    @Test
    fun `deleting the last row leaves the listing empty rather than loading`() = runTest(testDispatcher) {
        val viewModel = createViewModel(entryCount = 1)
        advanceUntilIdle()
        val target = viewModel.uiState.value.files.single()
        coEvery { fileRepository.delete(listOf(target)) } returns
            DeleteResult(removedPaths = listOf(target.path))

        viewModel.showDeleteConfirmDialog(listOf(target))
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isEmpty)
        assertEquals(0L, state.totalBytes)
    }

    /**
     * [entryCount] files, each smaller than the one before it, in the descending order the scan
     * hands over — stored in [AnalyzerResultsHolder] as a completed scan would leave them.
     */
    private fun createViewModel(entryCount: Int, held: Boolean = true): AnalyzerCategoryViewModel {
        val entries = (0 until entryCount).map { index ->
            val size = (entryCount - index) * 100L
            val file = File(root, "file$index.bin")
            file.writeBytes(ByteArray(size.toInt()))

            AnalyzerFileEntry(path = file.path, size = size)
        }
        val categoryFiles = CategoryFiles(totalBytes = entries.sumOf { it.size }, entries = entries)
        AnalyzerResultsHolder.store(mapOf(AnalyzerCategory.IMAGES to categoryFiles))

        return AnalyzerCategoryViewModel(
            application = application,
            category = AnalyzerCategory.IMAGES,
            categoryFiles = categoryFiles.takeIf { held },
            fileRepository = fileRepository,
            storageRepository = storageRepository,
            ioDispatcher = testDispatcher
        )
    }
}
