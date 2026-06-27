package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.Location
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
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
import kotlinx.coroutines.cancel
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
    private lateinit var favoritesRepository: FavoritesRepository
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
    private val recentFilesEnabledFlow = MutableStateFlow(true)
    private val recentFilesFlow = MutableStateFlow(testRecentFiles)
    private val favoritesFlow = MutableStateFlow(emptyList<Favorite>())
    private val createdViewModels = mutableListOf<HomeViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        application = mockk(relaxed = true)
        recentFilesRepository = mockk(relaxed = true)
        favoritesRepository = mockk(relaxed = true)
        locationsRepository = mockk(relaxed = true)
        storageRepository = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        fileRepository = mockk(relaxed = true)

        every { recentFilesRepository.recentFilesFlow } returns recentFilesFlow
        every { favoritesRepository.favoritesFlow } returns favoritesFlow
        coEvery { recentFilesRepository.removeRecentFile(any()) } coAnswers {
            val path = firstArg<String>()
            recentFilesFlow.value = recentFilesFlow.value.filter { it.path != path }
        }
        coEvery { locationsRepository.getLocations() } returns testLocations
        coEvery { storageRepository.getStorages() } returns testStorages
        every { preferencesRepository.isBadgeDismissed(any()) } returns badgeDismissedFlow
        every { preferencesRepository.recentFilesEnabled } returns recentFilesEnabledFlow

        mockkObject(MediaStoreUtil)
        mockkObject(IntentUtil)
        mockkObject(ErrorReporter)
        mockkObject(AnalyticsTracker)
        coEvery { MediaStoreUtil.notifyDeleted(any(), any()) } just Runs
        every { MediaStoreUtil.scanFiles(any(), any()) } just Runs
        every { IntentUtil.trackRecentFile(any(), any()) } just Runs
        every { ErrorReporter.error(any(), any(), any()) } just Runs
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { AnalyticsTracker.trackScreenHome() } just Runs
        every { AnalyticsTracker.trackRecentFileRemoved() } just Runs
        every { AnalyticsTracker.trackFavoriteRemoved() } just Runs
        every { AnalyticsTracker.trackDeleteCompleted(any(), any()) } just Runs
        every { AnalyticsTracker.trackOperationFailed(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
        unmockkObject(MediaStoreUtil)
        unmockkObject(IntentUtil)
        unmockkObject(ErrorReporter)
        unmockkObject(AnalyticsTracker)
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            application = application,
            recentFilesRepository = recentFilesRepository,
            favoritesRepository = favoritesRepository,
            locationsRepository = locationsRepository,
            storageRepository = storageRepository,
            preferencesRepository = preferencesRepository,
            fileRepository = fileRepository,
            ioDispatcher = testDispatcher
        ).also { createdViewModels.add(it) }
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
    fun `loadData does not overwrite the reactive recents list`() = runTest {
        // Recents are owned by the reactive flow. A reload must never overwrite them with
        // its own snapshot, even when that snapshot is stale/different from the flow value.
        coEvery { recentFilesRepository.getRecentFiles() } returns emptyList()

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(testRecentFiles, viewModel.uiState.value.recentFiles)

        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(testRecentFiles, viewModel.uiState.value.recentFiles)
    }

    @Test
    fun `loadData prunes recents whose files no longer exist`() = runTest {
        // Files deleted while away from home are pruned on resume; the removal flows back through
        // the reactive recents flow (the sole source of truth) into uiState.
        coEvery { recentFilesRepository.pruneNonExistentFiles() } coAnswers {
            recentFilesFlow.value = emptyList()
        }

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.recentFiles.isEmpty())
        coVerify { recentFilesRepository.pruneNonExistentFiles() }
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

    @Test
    fun `observeFavorites populates favorites and favoritePaths`() = runTest {
        val favorite = Favorite("/storage/emulated/0/Documents/test.pdf", "test.pdf", false, "application/pdf", 1000L)
        favoritesFlow.value = listOf(favorite)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.favorites.size)
        assertTrue(viewModel.uiState.value.favoritePaths.contains(favorite.path))
    }

    @Test
    fun `removeFromFavorites removes favorite from list`() = runTest {
        val favorite = Favorite("/storage/emulated/0/Documents/test.pdf", "test.pdf", false, "application/pdf", 1000L)
        favoritesFlow.value = listOf(favorite)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.favorites.size)

        viewModel.removeFromFavorites(favorite)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.favorites.isEmpty())
        coVerify { favoritesRepository.removeFavorite(favorite.path) }
    }

    @Test
    fun `showFavoriteDeleteConfirmation sets favoriteToDelete`() = runTest {
        val favorite = Favorite("/storage/emulated/0/Documents/test.pdf", "test.pdf", false, "application/pdf", 1000L)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showFavoriteDeleteConfirmation(favorite)

        assertEquals(favorite, viewModel.uiState.value.favoriteToDelete)
    }

    @Test
    fun `dismissFavoriteActions clears selected favorite`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissFavoriteActions()

        assertNull(viewModel.uiState.value.selectedFavorite)
    }
}
