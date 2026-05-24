package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.app.Application
import com.mauriciotogneri.fileexplorer.data.model.Location
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
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
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var recentFilesRepository: RecentFilesRepository
    private lateinit var locationsRepository: LocationsRepository
    private lateinit var storageRepository: StorageRepository
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var fileRepository: FileRepository

    private val testRecentFiles = listOf(
        RecentFile(
            path = "/storage/emulated/0/Documents/test.pdf",
            name = "test.pdf",
            mimeType = "application/pdf",
            lastOpenedTimestamp = System.currentTimeMillis()
        )
    )

    private val testLocations = listOf(
        Location(
            type = LocationType.DOWNLOADS,
            path = "/storage/emulated/0/Download",
            totalSizeBytes = 1024 * 1024L
        )
    )

    private val testStorages = listOf(
        StorageDevice(
            path = "/storage/emulated/0",
            displayName = "Internal Storage",
            totalBytes = 64_000_000_000L,
            availableBytes = 32_000_000_000L
        )
    )

    private val badgeDismissedFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        recentFilesRepository = mockk(relaxed = true)
        locationsRepository = mockk(relaxed = true)
        storageRepository = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        fileRepository = mockk(relaxed = true)

        coEvery { recentFilesRepository.getRecentFiles() } returns testRecentFiles
        coEvery { locationsRepository.getLocations() } returns testLocations
        coEvery { storageRepository.getStorages() } returns testStorages
        every { preferencesRepository.isBadgeDismissed(any()) } returns badgeDismissedFlow

        mockkObject(MediaStoreUtil)
        mockkObject(IntentUtil)
        mockkObject(ErrorReporter)
        coEvery { MediaStoreUtil.notifyDeleted(any(), any()) } just Runs
        every { MediaStoreUtil.scanFiles(any(), any()) } just Runs
        every { IntentUtil.trackRecentFile(any(), any()) } just Runs
        every { ErrorReporter.error(any(), any(), any()) } just Runs
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(MediaStoreUtil)
        unmockkObject(IntentUtil)
        unmockkObject(ErrorReporter)
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            application = application,
            recentFilesRepository = recentFilesRepository,
            locationsRepository = locationsRepository,
            storageRepository = storageRepository,
            preferencesRepository = preferencesRepository,
            fileRepository = fileRepository
        )
    }

    @Test
    fun `initial state is loading`() = runTest {
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadData populates state with data`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.recentFiles.size)
        assertEquals(1, state.locations.size)
        assertEquals(1, state.storages.size)
    }

    @Test
    fun `removeFromRecents removes file from list`() = runTest {
        val recentFile = testRecentFiles[0]

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.recentFiles.size)

        viewModel.removeFromRecents(recentFile)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.recentFiles.isEmpty())
        coVerify { recentFilesRepository.removeRecentFile(recentFile.path) }
    }

    @Test
    fun `showDeleteConfirmation sets recentFileToDelete`() = runTest {
        val recentFile = testRecentFiles[0]

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showDeleteConfirmation(recentFile)

        assertEquals(recentFile, viewModel.uiState.value.recentFileToDelete)
    }

    @Test
    fun `dismissDeleteConfirmation clears recentFileToDelete`() = runTest {
        val recentFile = testRecentFiles[0]

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showDeleteConfirmation(recentFile)
        assertNotNull(viewModel.uiState.value.recentFileToDelete)

        viewModel.dismissDeleteConfirmation()

        assertNull(viewModel.uiState.value.recentFileToDelete)
    }

    @Test
    fun `confirmDeleteRecentFile does nothing without selection`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.confirmDeleteRecentFile()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { fileRepository.delete(any()) }
    }

    @Test
    fun `dismissDeleteError clears error state`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissDeleteError()

        assertFalse(viewModel.uiState.value.showDeleteError)
    }

    @Test
    fun `dismissUncompressDialog clears uncompress state`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissUncompressDialog()

        assertNull(viewModel.uiState.value.itemToUncompress)
    }

    @Test
    fun `cancelUncompression clears progress`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.cancelUncompression()

        assertNull(viewModel.uiState.value.uncompressProgress)
    }

    @Test
    fun `dismissRecentFileActions clears selected file`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissRecentFileActions()

        assertNull(viewModel.uiState.value.selectedRecentFile)
    }
}
