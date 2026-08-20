package com.mauriciotogneri.fileexplorer.ui.screens.settings

import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var recentFilesRepository: RecentFilesRepository
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var locationsRepository: LocationsRepository
    private lateinit var storageRepository: StorageRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferencesRepository = mockk(relaxed = true)
        recentFilesRepository = mockk(relaxed = true)
        favoritesRepository = mockk(relaxed = true)
        locationsRepository = mockk(relaxed = true)
        storageRepository = mockk(relaxed = true)
        coEvery { locationsRepository.getAvailableLocationTypes() } returns LocationType.entries
        mockkObject(AnalyticsTracker)
        every { AnalyticsTracker.trackSettingsTheme(any()) } returns Unit
        every { AnalyticsTracker.trackSettingsLocationsChanged(any()) } returns Unit
        every { AnalyticsTracker.trackSettingsRecentFilesTracking(any()) } returns Unit
        every { AnalyticsTracker.trackSettingsShowHidden(any()) } returns Unit
        every { AnalyticsTracker.trackSettingsStartupScreen(any()) } returns Unit
        every { AnalyticsTracker.trackSettingsFolderSecondLine(any()) } returns Unit
        every { AnalyticsTracker.trackSettingsFileSecondLine(any()) } returns Unit
        every { AnalyticsTracker.trackSettingsHomeSectionOrder(any()) } returns Unit
        every { AnalyticsTracker.trackSettingsSwipeLeft(any()) } returns Unit
        every { AnalyticsTracker.trackSettingsSwipeRight(any()) } returns Unit
        every { AnalyticsTracker.setUserProperty(any(), any()) } returns Unit
        ThemeManager.setTheme(ThemeMode.SYSTEM)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(AnalyticsTracker)
    }

    @Test
    fun `themeMode reflects ThemeManager value`() = runTest {
        ThemeManager.setTheme(ThemeMode.DARK)

        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }

    @Test
    fun `setThemeMode updates ThemeManager and repository`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setThemeMode(ThemeMode.LIGHT)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, ThemeManager.currentTheme)
        coVerify { preferencesRepository.setThemeMode(ThemeMode.LIGHT) }
    }

    @Test
    fun `themeMode updates when ThemeManager changes`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.themeMode.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem())

            viewModel.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem())

            viewModel.setThemeMode(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, awaitItem())
        }
    }

    @Test
    fun `all theme modes can be set`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        ThemeMode.entries.forEach { mode ->
            viewModel.setThemeMode(mode)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(mode, ThemeManager.currentTheme)
            coVerify { preferencesRepository.setThemeMode(mode) }
        }
    }

    @Test
    fun `setStartupFolder stores the folder screen with its path`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setStartupFolder("/storage/emulated/0/Download")
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.setStartupScreen(StartupScreen.FOLDER, "/storage/emulated/0/Download") }
    }

    @Test
    fun `setStartupHome clears the stored folder`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setStartupHome()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.setStartupScreen(StartupScreen.HOME, null) }
    }

    // A folder path names a location on the user's device, so it must never reach analytics.
    @Test
    fun `setStartupFolder reports the choice without the path`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setStartupFolder("/storage/emulated/0/Download")
        testDispatcher.scheduler.advanceUntilIdle()

        verify { AnalyticsTracker.trackSettingsStartupScreen("folder") }
        verify { AnalyticsTracker.setUserProperty("startup_screen", "folder") }
        verify(exactly = 0) { AnalyticsTracker.trackSettingsStartupScreen(match { it.contains("/") }) }
        verify(exactly = 0) { AnalyticsTracker.setUserProperty(any(), match { it.contains("/") }) }
    }

    @Test
    fun `startupFolderName is null when starting on the home screen`() = runTest {
        every { preferencesRepository.startupFolderPath } returns flowOf(null)

        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.startupFolderName.test {
            assertNull(awaitItem())
            // No name ever arrives: the home screen has no folder to name.
            expectNoEvents()
        }
    }

    @Test
    fun `startupFolderName is the folder name`() = runTest {
        every { preferencesRepository.startupFolderPath } returns flowOf("/storage/emulated/0/Download")
        coEvery { storageRepository.getStorages() } returns listOf(storageDevice("/storage/emulated/0", "Internal storage"))

        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // startupFolderName is WhileSubscribed, so it emits its initial null before the
        // storage lookup and the stored path have both landed.
        assertEquals("Download", viewModel.startupFolderName.filterNotNull().first())
    }

    // The last segment of a storage root is "0", which names nothing the user would recognise.
    @Test
    fun `startupFolderName names a storage root by its display name`() = runTest {
        every { preferencesRepository.startupFolderPath } returns flowOf("/storage/emulated/0")
        coEvery { storageRepository.getStorages() } returns listOf(storageDevice("/storage/emulated/0", "Internal storage"))

        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // startupFolderName is WhileSubscribed, so it emits its initial null before the
        // storage lookup and the stored path have both landed.
        assertEquals("Internal storage", viewModel.startupFolderName.filterNotNull().first())
    }

    // Naming the folder is cosmetic, so a storage lookup that fails must not surface to the user.
    @Test
    fun `startupFolderName survives a failing storage lookup`() = runTest {
        every { preferencesRepository.startupFolderPath } returns flowOf("/storage/emulated/0/Download")
        coEvery { storageRepository.getStorages() } throws IllegalStateException("unmounted")
        mockkObject(ErrorReporter)
        every { ErrorReporter.warning(any(), any(), any()) } returns Unit

        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // startupFolderName is WhileSubscribed, so it emits its initial null before the
        // storage lookup and the stored path have both landed.
        assertEquals("Download", viewModel.startupFolderName.filterNotNull().first())
        unmockkObject(ErrorReporter)
    }

    private fun storageDevice(path: String, displayName: String) = StorageDevice(
        path = path,
        displayName = displayName,
        totalBytes = 0,
        availableBytes = 0
    )

    @Test
    fun `setStartupHome reports the choice`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setStartupHome()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { AnalyticsTracker.trackSettingsStartupScreen("home") }
        verify { AnalyticsTracker.setUserProperty("startup_screen", "home") }
    }

    @Test
    fun `setEnabledLocations calls repository with selected locations`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val enabledLocations = setOf(LocationType.DOWNLOADS, LocationType.IMAGES)
        viewModel.setEnabledLocations(enabledLocations)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.setEnabledLocations(enabledLocations) }
    }

    @Test
    fun `setEnabledLocations can save empty set`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setEnabledLocations(emptySet())
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.setEnabledLocations(emptySet()) }
    }

    @Test
    fun `setHomeSectionOrder calls repository with the arranged order`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val order = listOf(
            HomeSection.STORAGE,
            HomeSection.RECENT,
            HomeSection.LOCATIONS,
            HomeSection.FAVORITES
        )
        viewModel.setHomeSectionOrder(order)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.setHomeSectionOrder(order) }
    }

    @Test
    fun `homeSectionOrder exposes what the repository holds`() = runTest {
        val order = listOf(
            HomeSection.FAVORITES,
            HomeSection.STORAGE,
            HomeSection.RECENT,
            HomeSection.LOCATIONS
        )
        every { preferencesRepository.homeSectionOrder } returns flowOf(order)
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(order, viewModel.homeSectionOrder.first())
    }

    @Test
    fun `setShowHidden calls repository with new value`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setShowHidden(true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.setShowHidden(true) }
    }

    @Test
    fun `setFolderSecondLine calls repository with new value`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setFolderSecondLine(FolderSecondLine.LAST_MODIFIED)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.setFolderSecondLine(FolderSecondLine.LAST_MODIFIED) }
    }

    @Test
    fun `setFileSecondLine calls repository with new value`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setFileSecondLine(FileSecondLine.NONE)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.setFileSecondLine(FileSecondLine.NONE) }
    }

    @Test
    fun `every second line choice can be set`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        FolderSecondLine.entries.forEach { viewModel.setFolderSecondLine(it) }
        FileSecondLine.entries.forEach { viewModel.setFileSecondLine(it) }
        testDispatcher.scheduler.advanceUntilIdle()

        FolderSecondLine.entries.forEach { coVerify { preferencesRepository.setFolderSecondLine(it) } }
        FileSecondLine.entries.forEach { coVerify { preferencesRepository.setFileSecondLine(it) } }
    }

    /** The event names the choice; it must never carry anything that identifies a file. */
    @Test
    fun `choosing a second line reports the choice`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setFolderSecondLine(FolderSecondLine.ITEM_COUNT)
        viewModel.setFileSecondLine(FileSecondLine.LAST_MODIFIED)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { AnalyticsTracker.trackSettingsFolderSecondLine("item_count") }
        verify { AnalyticsTracker.trackSettingsFileSecondLine("last_modified") }
    }

    @Test
    fun `every swipe action can be set on either direction`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        SwipeAction.entries.forEach { viewModel.setSwipeLeftAction(it) }
        SwipeAction.entries.forEach { viewModel.setSwipeRightAction(it) }
        testDispatcher.scheduler.advanceUntilIdle()

        SwipeAction.entries.forEach { coVerify { preferencesRepository.setSwipeLeftAction(it) } }
        SwipeAction.entries.forEach { coVerify { preferencesRepository.setSwipeRightAction(it) } }
    }

    /** The directions are independent: pointing both at the same action is a valid configuration. */
    @Test
    fun `both directions can hold the same action`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setSwipeLeftAction(SwipeAction.DELETE)
        viewModel.setSwipeRightAction(SwipeAction.DELETE)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.setSwipeLeftAction(SwipeAction.DELETE) }
        coVerify { preferencesRepository.setSwipeRightAction(SwipeAction.DELETE) }
    }

    /** The event names the chosen action; it must never carry anything that identifies a file. */
    @Test
    fun `choosing a swipe action reports the choice`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setSwipeLeftAction(SwipeAction.MOVE_TO)
        viewModel.setSwipeRightAction(SwipeAction.NONE)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { AnalyticsTracker.trackSettingsSwipeLeft("move_to") }
        verify { AnalyticsTracker.trackSettingsSwipeRight("none") }
    }
}
