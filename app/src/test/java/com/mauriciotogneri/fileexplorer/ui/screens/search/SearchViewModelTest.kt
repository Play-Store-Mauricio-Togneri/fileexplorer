package com.mauriciotogneri.fileexplorer.ui.screens.search

import android.app.Application
import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var fileRepository: FileRepository
    private lateinit var storageRepository: StorageRepository

    private val testStorage = StorageDevice(
        path = "/storage/emulated/0",
        displayName = "Internal Storage",
        totalBytes = 64_000_000_000L,
        availableBytes = 32_000_000_000L
    )

    private val testFiles = listOf(
        FileItem(
            path = "/storage/emulated/0/test.txt",
            name = "test.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = 1000L,
            createdTime = 1000L,
            mimeType = "text/plain",
            childCount = null
        ),
        FileItem(
            path = "/storage/emulated/0/test2.txt",
            name = "test2.txt",
            isDirectory = false,
            size = 2048L,
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
        storageRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SearchViewModel {
        return SearchViewModel(application, fileRepository, storageRepository)
    }

    @Test
    fun `initial state is empty`() = runTest {
        coEvery { storageRepository.getStorages() } returns listOf(testStorage)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertTrue(state.results.isEmpty())
        assertFalse(state.isSearching)
        assertFalse(state.searchComplete)
    }

    @Test
    fun `onQueryChange updates query in state`() = runTest {
        coEvery { storageRepository.getStorages() } returns listOf(testStorage)
        coEvery { fileRepository.searchFilesStreaming(any(), any(), any(), any()) } returns flowOf()

        val viewModel = createViewModel()

        viewModel.onQueryChange("test")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("test", viewModel.uiState.value.query)
    }

    @Test
    fun `clearQuery resets state`() = runTest {
        coEvery { storageRepository.getStorages() } returns listOf(testStorage)
        coEvery { fileRepository.searchFilesStreaming(any(), any(), any(), any()) } returns flowOf()

        val viewModel = createViewModel()

        viewModel.onQueryChange("test")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.clearQuery()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun `search returns results after debounce`() = runTest {
        coEvery { storageRepository.getStorages() } returns listOf(testStorage)
        coEvery { fileRepository.searchFilesStreaming(any(), eq("test"), any(), any()) } returns flowOf(
            testFiles[0],
            testFiles[1]
        )

        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // Initial state

            viewModel.onQueryChange("test")
            val queryUpdated = awaitItem()
            assertEquals("test", queryUpdated.query)

            advanceTimeBy(350) // Past debounce
            testDispatcher.scheduler.advanceUntilIdle()

            // Collect remaining emissions
            var latestState = queryUpdated
            while (true) {
                val nextState = awaitItem()
                latestState = nextState
                if (latestState.searchComplete) break
            }

            assertEquals(2, latestState.results.size)
            assertTrue(latestState.searchComplete)
            assertFalse(latestState.isSearching)
        }
    }

    @Test
    fun `empty query does not trigger search`() = runTest {
        coEvery { storageRepository.getStorages() } returns listOf(testStorage)

        val viewModel = createViewModel()

        viewModel.onQueryChange("")
        advanceTimeBy(350)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.results.isEmpty())
        assertFalse(state.isSearching)
    }

    @Test
    fun `showNoResults is true when search completes with no results`() = runTest {
        coEvery { storageRepository.getStorages() } returns listOf(testStorage)
        coEvery { fileRepository.searchFilesStreaming(any(), any(), any(), any()) } returns flowOf()

        val viewModel = createViewModel()

        viewModel.onQueryChange("nonexistent")
        advanceTimeBy(350)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showNoResults)
    }

    @Test
    fun `showNoResults is false when query is empty`() = runTest {
        coEvery { storageRepository.getStorages() } returns listOf(testStorage)

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.showNoResults)
    }
}
