package com.mauriciotogneri.fileexplorer.ui.screens.analyzercategory

import com.mauriciotogneri.fileexplorer.data.model.AnalyzerFileEntry
import com.mauriciotogneri.fileexplorer.data.repository.CategoryFiles
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
 * there to bound.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyzerCategoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var root: File

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        root = Files.createTempDirectory("analyzer-category").toFile()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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

    /**
     * [entryCount] files, each smaller than the one before it, in the descending order the scan
     * hands over.
     */
    private fun createViewModel(entryCount: Int): AnalyzerCategoryViewModel {
        val entries = (0 until entryCount).map { index ->
            val size = (entryCount - index) * 100L
            val file = File(root, "file$index.bin")
            file.writeBytes(ByteArray(size.toInt()))

            AnalyzerFileEntry(path = file.path, size = size)
        }

        return AnalyzerCategoryViewModel(
            categoryFiles = CategoryFiles(totalBytes = entries.sumOf { it.size }, entries = entries),
            ioDispatcher = testDispatcher
        )
    }
}
