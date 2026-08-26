package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.Location
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.source.FakeMediaChangeSource
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
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

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
    private lateinit var mediaChangeSource: FakeMediaChangeSource
    private lateinit var tempDir: File

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

    private val testFavorites = listOf(
        Favorite(
            path = "/storage/emulated/0/Documents/notes.txt",
            name = "notes.txt",
            isDirectory = false,
            mimeType = "text/plain",
            favoritedTimestamp = 1_700_000_000_000L
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
    private val homeSectionOrderFlow = MutableStateFlow(HomeSection.DEFAULT_ORDER)
    private val recentFilesFlow = MutableStateFlow(testRecentFiles)
    private val favoritesFlow = MutableStateFlow(emptyList<Favorite>())
    private val createdViewModels = mutableListOf<HomeViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        tempDir = File(System.getProperty("java.io.tmpdir"), "test_home_view_model_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        application = mockk(relaxed = true)
        recentFilesRepository = mockk(relaxed = true)
        favoritesRepository = mockk(relaxed = true)
        locationsRepository = mockk(relaxed = true)
        storageRepository = mockk(relaxed = true)
        preferencesRepository = mockk(relaxed = true)
        fileRepository = mockk(relaxed = true)
        mediaChangeSource = FakeMediaChangeSource()

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
        every { preferencesRepository.homeSectionOrder } returns homeSectionOrderFlow

        mockkObject(MediaStoreUtil)
        mockkObject(IntentUtil)
        mockkObject(ErrorReporter)
        mockkObject(AnalyticsTracker)
        coEvery { MediaStoreUtil.notifyDeleted(any(), any()) } just Runs
        coEvery { MediaStoreUtil.notifyTreeDeleted(any(), any()) } just Runs
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
        tempDir.deleteRecursively()
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
            mediaChangeSource = mediaChangeSource,
            ioDispatcher = testDispatcher
        ).also { createdViewModels.add(it) }
    }

    @Test
    fun `a media change made outside this app marks the location sizes stale`() = runTest {
        // FileRepository's hook covers only what this app did. Without this the cache TTL is the
        // sole backstop, so a photo taken or a download finished elsewhere leaves a card reporting
        // the pre-change total until it lapses.
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        mediaChangeSource.emitChange()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(exactly = 1) { locationsRepository.markSizeCacheStale() }
    }

    @Test
    fun `a media change does not touch the store or reload the screen`() = runTest {
        // Every app on the device can publish these, one per file during someone else's bulk copy.
        // Reacting with a store write or a reload would hand an unmetered stream of disk work to
        // whatever else is running; marking is what makes repeating it free.
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        repeat(20) { mediaChangeSource.emitChange() }
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { locationsRepository.getLocations() }
    }

    private fun createTempFile(name: String): File {
        val file = File(tempDir, name)
        file.writeText("test content")
        return file
    }

    @Test
    fun `initial state is loading`() = runTest {
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.isLoading)
    }

    // ==================== Home section order ====================

    @Test
    fun `visibleSections keeps the stored order`() = runTest {
        homeSectionOrderFlow.value = listOf(
            HomeSection.STORAGE,
            HomeSection.LOCATIONS,
            HomeSection.RECENT,
            HomeSection.FAVORITES
        )
        favoritesFlow.value = testFavorites
        val viewModel = createViewModel()
        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(HomeSection.STORAGE, HomeSection.LOCATIONS, HomeSection.RECENT, HomeSection.FAVORITES),
            viewModel.visibleSections.value
        )
    }

    @Test
    fun `visibleSections defaults to the arrangement the home screen shipped with`() = runTest {
        favoritesFlow.value = testFavorites
        val viewModel = createViewModel()
        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(HomeSection.DEFAULT_ORDER, viewModel.visibleSections.value)
    }

    /**
     * A section with nothing to show draws nothing, so listing it would put a separator around an
     * empty space. Dropping it here is what keeps the spacing right for every arrangement.
     */
    @Test
    fun `visibleSections leaves out the sections that have nothing to show`() = runTest {
        favoritesFlow.value = emptyList()
        val viewModel = createViewModel()
        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(HomeSection.RECENT, HomeSection.LOCATIONS, HomeSection.STORAGE),
            viewModel.visibleSections.value
        )
    }

    @Test
    fun `visibleSections follows an order changed while the screen is open`() = runTest {
        favoritesFlow.value = testFavorites
        val viewModel = createViewModel()
        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(HomeSection.DEFAULT_ORDER, viewModel.visibleSections.value)

        homeSectionOrderFlow.value = listOf(
            HomeSection.FAVORITES,
            HomeSection.RECENT,
            HomeSection.STORAGE,
            HomeSection.LOCATIONS
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(HomeSection.FAVORITES, HomeSection.RECENT, HomeSection.STORAGE, HomeSection.LOCATIONS),
            viewModel.visibleSections.value
        )
    }

    /**
     * Turning recent file tracking off empties the section, which must drop it out of the layout
     * without disturbing where the user put the others.
     */
    @Test
    fun `visibleSections drops recents when tracking is turned off`() = runTest {
        favoritesFlow.value = testFavorites
        val viewModel = createViewModel()
        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()

        recentFilesEnabledFlow.value = false
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(HomeSection.FAVORITES, HomeSection.LOCATIONS, HomeSection.STORAGE),
            viewModel.visibleSections.value
        )
    }

    @Test
    fun `loadData populates state with data`() = runTest {
        val viewModel = createViewModel()
        viewModel.loadData()
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
    fun `construction does not load, leaving the lifecycle to trigger the first pass`() = runTest {
        // The screen's repeatOnLifecycle(STARTED) effect drives loading. Loading from init as well
        // ran every location's directory walk and every recents/favorites stat twice on a cold
        // start, with the two passes racing.
        createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { locationsRepository.getLocations() }
        coVerify(exactly = 0) { recentFilesRepository.pruneNonExistentFiles() }
    }

    @Test
    fun `loadData runs one pass per call when the previous one has finished`() = runTest {
        val viewModel = createViewModel()

        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { locationsRepository.getLocations() }

        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 2) { locationsRepository.getLocations() }
    }

    @Test
    fun `loadData defers a call that arrives while a load is in flight`() = runTest {
        // Resuming mid-load (backgrounded, files changed elsewhere, resumed before the load
        // finished): the in-flight pass read disk before the change, so the call must be deferred
        // into another pass rather than dropped — and must not run concurrently with it.
        val inFlight = CompletableDeferred<Unit>()
        coEvery { locationsRepository.getLocations() } coAnswers {
            inFlight.await()
            testLocations
        }

        val viewModel = createViewModel()
        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()

        // The pass is parked inside getLocations, so nothing has been re-read yet.
        coVerify(exactly = 1) { locationsRepository.getLocations() }

        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { locationsRepository.getLocations() }

        inFlight.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { locationsRepository.getLocations() }
        coVerify(exactly = 2) { recentFilesRepository.pruneNonExistentFiles() }
    }

    @Test
    fun `loadData prunes recents whose files no longer exist`() = runTest {
        // Files deleted while away from home are pruned on resume; the removal flows back through
        // the reactive recents flow (the sole source of truth) into uiState.
        coEvery { recentFilesRepository.pruneNonExistentFiles() } coAnswers {
            recentFilesFlow.value = emptyList()
        }

        val viewModel = createViewModel()
        viewModel.loadData()
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

    // Deleting from the home screen invalidates every cached location size but never leaves the
    // screen, so the repeatOnLifecycle(STARTED) effect that would otherwise reload does not fire.
    // Without a reload triggered by the delete itself, the location and storage cards keep
    // reporting pre-delete totals until the user navigates away and back.
    @Test
    fun `confirmDeleteRecentFile recomputes the location and storage cards`() = runTest {
        coEvery { fileRepository.delete(any()) } returns true

        val viewModel = createViewModel()
        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { locationsRepository.getLocations() }

        viewModel.showDeleteConfirmation(testRecentFiles[0])
        viewModel.confirmDeleteRecentFile()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { locationsRepository.getLocations() }
        coVerify(exactly = 2) { storageRepository.getStorages() }
    }

    // A delete that reports failure can still have removed part of the tree, which is where a
    // stale total is most visible. FileRepository invalidates the cache from a `finally` for the
    // same reason, so the reload sits outside the success branch too.
    @Test
    fun `confirmDeleteRecentFile recomputes the cards when the delete failed`() = runTest {
        coEvery { fileRepository.delete(any()) } returns false

        val viewModel = createViewModel()
        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showDeleteConfirmation(testRecentFiles[0])
        viewModel.confirmDeleteRecentFile()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showDeleteError)
        coVerify(exactly = 2) { locationsRepository.getLocations() }
        coVerify(exactly = 2) { storageRepository.getStorages() }
    }

    // A recents entry is a file by contract, but the store records no type of its own and
    // re-validates the stored path with exists(), which a directory satisfies. Delete decides
    // recursion from a live stat of its own, so without the guard this walks a tree behind a
    // dialog that named one item — and notifyDeleted is the file-only variant, so every
    // descendant's MediaStore row is left behind.
    @Test
    fun `confirmDeleteRecentFile refuses a path a directory now occupies`() = runTest {
        val directory = File(tempDir, "notes.md").apply { mkdirs() }
        val child = File(directory, "inside.md").apply { writeText("keep me") }
        val entry = RecentFile(
            path = directory.absolutePath,
            name = "notes.md",
            mimeType = "text/markdown",
            lastOpenedTimestamp = 1_700_000_000_000L
        )
        // Returning true is what makes this test earn its green: without the guard the delete
        // succeeds and showDeleteError stays false.
        coEvery { fileRepository.delete(any()) } returns true

        val viewModel = createViewModel()
        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { storageRepository.getStorages() }

        viewModel.showDeleteConfirmation(entry)
        viewModel.confirmDeleteRecentFile()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { fileRepository.delete(any()) }
        verify(exactly = 1) { AnalyticsTracker.trackOperationFailed("delete", "path_type_changed") }
        assertTrue(viewModel.uiState.value.showDeleteError)
        assertNull(viewModel.uiState.value.recentFileToDelete)
        // Where the neighbouring delete tests expect a second pass, this one must not: nothing was
        // deleted and the path still exists, so there is nothing to recompute and nothing to prune.
        coVerify(exactly = 1) { storageRepository.getStorages() }
        // The repository is mocked, so this cannot witness a real recursive delete; it pins
        // that the ViewModel does no deleting of its own outside the repository.
        assertTrue("The ViewModel must not delete outside the repository", child.exists())
    }

    @Test
    fun `confirmDeleteRecentFile still deletes the file the entry recorded`() = runTest {
        // The guard above must not cost the user the delete the recents sheet exists to offer.
        val file = createTempFile("notes.md")
        val entry = RecentFile(
            path = file.absolutePath,
            name = "notes.md",
            mimeType = "text/markdown",
            lastOpenedTimestamp = 1_700_000_000_000L
        )
        coEvery { fileRepository.delete(any()) } returns true

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showDeleteConfirmation(entry)
        viewModel.confirmDeleteRecentFile()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            fileRepository.delete(match { !it.single().isDirectory && it.single().path == file.absolutePath })
        }
        coVerify(exactly = 1) { MediaStoreUtil.notifyDeleted(any(), listOf(file.absolutePath)) }
        assertFalse(viewModel.uiState.value.showDeleteError)
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

    // Same gap as the recents path, and wider: a favorite can be a directory, so one delete can
    // move a card's total by the whole subtree.
    @Test
    fun `confirmDeleteFavorite recomputes the location and storage cards`() = runTest {
        val favorite = Favorite("/storage/emulated/0/Documents/reports", "reports", true, "", 1000L)
        coEvery { fileRepository.delete(any()) } returns true

        val viewModel = createViewModel()
        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { locationsRepository.getLocations() }

        viewModel.showFavoriteDeleteConfirmation(favorite)
        viewModel.confirmDeleteFavorite()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 2) { locationsRepository.getLocations() }
        coVerify(exactly = 2) { storageRepository.getStorages() }
    }

    // Favorites legitimately hold directories, so the guard tests the one direction that loses
    // data rather than directory-ness: an entry the dialog described as a file whose path a
    // directory now occupies.
    @Test
    fun `confirmDeleteFavorite refuses a directory the entry recorded as a file`() = runTest {
        val directory = File(tempDir, "notes.txt").apply { mkdirs() }
        val child = File(directory, "inside.txt").apply { writeText("keep me") }
        val favorite = Favorite(directory.absolutePath, "notes.txt", false, "text/plain", 1000L)
        coEvery { fileRepository.delete(any()) } returns true

        val viewModel = createViewModel()
        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { storageRepository.getStorages() }

        viewModel.showFavoriteDeleteConfirmation(favorite)
        viewModel.confirmDeleteFavorite()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { fileRepository.delete(any()) }
        verify(exactly = 1) { AnalyticsTracker.trackOperationFailed("delete", "path_type_changed") }
        assertTrue(viewModel.uiState.value.showDeleteError)
        assertNull(viewModel.uiState.value.favoriteToDelete)
        coVerify(exactly = 1) { storageRepository.getStorages() }
        assertTrue("The ViewModel must not delete outside the repository", child.exists())
    }

    @Test
    fun `confirmDeleteFavorite still deletes a favorited directory`() = runTest {
        // The guard must not reach the case favorites exist for: a directory the user deliberately
        // favorited, and whose confirm dialog described it as one.
        val directory = File(tempDir, "Reports").apply { mkdirs() }
        val favorite = Favorite(directory.absolutePath, "Reports", true, "", 1000L)
        coEvery { fileRepository.delete(any()) } returns true

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showFavoriteDeleteConfirmation(favorite)
        viewModel.confirmDeleteFavorite()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            fileRepository.delete(match { it.single().isDirectory && it.single().path == directory.absolutePath })
        }
        coVerify(exactly = 1) { MediaStoreUtil.notifyTreeDeleted(any(), listOf(directory.absolutePath)) }
        assertFalse(viewModel.uiState.value.showDeleteError)
    }

    @Test
    fun `confirmDeleteFavorite still deletes a favorited directory that is now a file`() = runTest {
        // The permitted direction of the drift, pinned deliberately: this one destroys only the
        // single item the dialog named, so tightening the guard to a plain inequality would take
        // away a legitimate delete. A vanished path reaches this branch too — isDirectory is false
        // for one — and that is what lets the trailing reload prune the entry.
        val file = createTempFile("Reports")
        val favorite = Favorite(file.absolutePath, "Reports", true, "", 1000L)
        coEvery { fileRepository.delete(any()) } returns true

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showFavoriteDeleteConfirmation(favorite)
        viewModel.confirmDeleteFavorite()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { fileRepository.delete(match { it.single().path == file.absolutePath }) }
        assertFalse(viewModel.uiState.value.showDeleteError)
    }

    @Test
    fun `dismissFavoriteActions clears selected favorite`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissFavoriteActions()

        assertNull(viewModel.uiState.value.selectedFavorite)
    }

    // The favorites and recents stores emit only when written, so an in-place edit would otherwise
    // leave both cards keyed by the timestamp stamped at the last emission — and Coil would serve
    // the thumbnail decoded before the edit. Resuming home re-stats them.
    @Test
    fun `loadData re-keys thumbnails whose file was edited in place`() = runTest {
        val favoriteFile = createTempFile("favorite.jpg")
        val recentFile = createTempFile("recent.jpg")
        favoritesFlow.value = listOf(
            Favorite(
                path = favoriteFile.absolutePath,
                name = "favorite.jpg",
                isDirectory = false,
                mimeType = "image/jpeg",
                favoritedTimestamp = 1000L,
                lastModified = favoriteFile.lastModified()
            )
        )
        recentFilesFlow.value = listOf(
            RecentFile(
                path = recentFile.absolutePath,
                name = "recent.jpg",
                mimeType = "image/jpeg",
                lastOpenedTimestamp = 1000L,
                lastModified = recentFile.lastModified()
            )
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val staleFavoriteKey = viewModel.uiState.value.favorites[0].thumbnailCacheKey
        val staleRecentKey = viewModel.uiState.value.recentFiles[0].thumbnailCacheKey

        assertTrue(favoriteFile.setLastModified(favoriteFile.lastModified() + 10_000))
        assertTrue(recentFile.setLastModified(recentFile.lastModified() + 10_000))

        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotEquals(staleFavoriteKey, viewModel.uiState.value.favorites[0].thumbnailCacheKey)
        assertNotEquals(staleRecentKey, viewModel.uiState.value.recentFiles[0].thumbnailCacheKey)
    }

    @Test
    fun `thumbnails are re-keyed when the lists arrive after the load pass`() = runTest {
        // On a cold start loadData() can run before either flow has emitted — both cross
        // flowOn(ioDispatcher) — and find nothing to re-stat. The entries that arrive afterwards
        // must still get their timestamps refreshed, or an edited-in-place file keeps showing its
        // previously decoded thumbnail until some later visit.
        favoritesFlow.value = emptyList()
        recentFilesFlow.value = emptyList()

        val viewModel = createViewModel()
        viewModel.loadData()
        testDispatcher.scheduler.advanceUntilIdle()

        val recentFile = createTempFile("late.jpg")
        val staleTimestamp = recentFile.lastModified() - 10_000
        recentFilesFlow.value = listOf(
            RecentFile(
                path = recentFile.absolutePath,
                name = "late.jpg",
                mimeType = "image/jpeg",
                lastOpenedTimestamp = 1000L,
                lastModified = staleTimestamp
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(recentFile.lastModified(), viewModel.uiState.value.recentFiles[0].lastModified)
    }

    // The re-stat must not undo a removal that happened while it was reading from disk: it maps
    // over the list held at update time, not the snapshot it started from.
    @Test
    fun `loadData re-stat does not resurrect a favorite removed meanwhile`() = runTest {
        val file = createTempFile("favorite.jpg")
        val favorite = Favorite(
            path = file.absolutePath,
            name = "favorite.jpg",
            isDirectory = false,
            mimeType = "image/jpeg",
            favoritedTimestamp = 1000L,
            lastModified = file.lastModified()
        )
        favoritesFlow.value = listOf(favorite)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.loadData()
        viewModel.removeFromFavorites(favorite)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.favorites.isEmpty())
    }

    @Test
    fun `setPendingApkInstall stores the pending file and its originating source`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val apk = FileItem(
            path = "/storage/emulated/0/Download/app.apk",
            name = "app.apk",
            isDirectory = false,
            size = 0,
            lastModified = 0,
            createdTime = 0,
            mimeType = "application/vnd.android.package-archive"
        )

        viewModel.setPendingApkInstall(apk, "favorite")

        assertEquals(apk, viewModel.uiState.value.pendingApkInstall)
        assertEquals("favorite", viewModel.uiState.value.pendingApkInstallSource)
    }

    // ==================== Action sheet entry type ====================

    // The sheet decides which actions to offer from the type stat'd here. A recents entry carries a
    // constant false and the store re-validates a stored path with exists() alone, which a
    // directory satisfies — so without this the sheet keeps offering Open with and Share on a path
    // the card's tap handler already navigates into.
    @Test
    fun `showRecentFileActions reports a path a directory now occupies as a directory`() = runTest {
        val directory = File(tempDir, "notes.md").apply { mkdirs() }
        val entry = RecentFile(
            path = directory.absolutePath,
            name = "notes.md",
            mimeType = "text/markdown",
            lastOpenedTimestamp = 1_700_000_000_000L
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showRecentFileActions(entry, "icon")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(entry, viewModel.uiState.value.selectedRecentFile)
        assertTrue(viewModel.uiState.value.selectedRecentFileIsDirectory)
    }

    @Test
    fun `showRecentFileActions reports an ordinary entry as a file`() = runTest {
        val file = createTempFile("notes.md")
        val entry = RecentFile(
            path = file.absolutePath,
            name = "notes.md",
            mimeType = "text/markdown",
            lastOpenedTimestamp = 1_700_000_000_000L
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showRecentFileActions(entry, "icon")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(entry, viewModel.uiState.value.selectedRecentFile)
        assertFalse(viewModel.uiState.value.selectedRecentFileIsDirectory)
    }

    // Favorite.isDirectory records the type the entry had when it was added, so both directions of
    // the drift are pinned: what the sheet gets must come from disk, not from the stored flag.
    @Test
    fun `showFavoriteActions reports the type on disk, not the stored one`() = runTest {
        val directory = File(tempDir, "notes.txt").apply { mkdirs() }
        val storedAsFile = Favorite(directory.absolutePath, "notes.txt", false, "text/plain", 1000L)
        val file = createTempFile("Reports")
        val storedAsDirectory = Favorite(file.absolutePath, "Reports", true, "", 1000L)

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showFavoriteActions(storedAsFile, "icon")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(storedAsFile, viewModel.uiState.value.selectedFavorite)
        assertTrue(viewModel.uiState.value.selectedFavoriteIsDirectory)

        viewModel.showFavoriteActions(storedAsDirectory, "icon")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(storedAsDirectory, viewModel.uiState.value.selectedFavorite)
        assertFalse(viewModel.uiState.value.selectedFavoriteIsDirectory)
    }

    // The write path has to agree with the read path above. A directory stored as a file gives the
    // user a favorite card drawn with a file icon whose delete confirmDeleteFavorite then refuses,
    // for as long as the directory occupies the path.
    @Test
    fun `addRecentToFavorites stores a path a directory now occupies as a directory`() = runTest {
        val directory = File(tempDir, "notes.md").apply { mkdirs() }
        val entry = RecentFile(
            path = directory.absolutePath,
            name = "notes.md",
            mimeType = "text/markdown",
            lastOpenedTimestamp = 1_700_000_000_000L
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addRecentToFavorites(entry)
        testDispatcher.scheduler.advanceUntilIdle()

        // Empty mimeType, as FolderViewModel stores for a favorited directory.
        coVerify(exactly = 1) {
            favoritesRepository.addFavorite(directory.absolutePath, "notes.md", true, "")
        }
    }

    @Test
    fun `addRecentToFavorites stores an ordinary entry as a file`() = runTest {
        val file = createTempFile("notes.md")
        val entry = RecentFile(
            path = file.absolutePath,
            name = "notes.md",
            mimeType = "text/markdown",
            lastOpenedTimestamp = 1_700_000_000_000L
        )

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addRecentToFavorites(entry)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) {
            favoritesRepository.addFavorite(file.absolutePath, "notes.md", false, "text/markdown")
        }
    }
}
