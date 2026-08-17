package com.mauriciotogneri.fileexplorer.ui.screens.settings

import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `setShowHidden calls repository with new value`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setShowHidden(true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.setShowHidden(true) }
    }

    @Test
    fun `dismissLocationsBadge calls repository with correct badge id`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissLocationsBadge()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.dismissBadge(PreferencesRepository.BADGE_SETTINGS_LOCATIONS) }
    }

    @Test
    fun `dismissThemeBadge calls repository with correct badge id`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissThemeBadge()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.dismissBadge(PreferencesRepository.BADGE_SETTINGS_THEME) }
    }

    @Test
    fun `dismissStartupBadge calls repository with correct badge id`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissStartupBadge()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.dismissBadge(PreferencesRepository.BADGE_SETTINGS_STARTUP) }
    }

    // Collected rather than read through .value: the badge flows are shared WhileSubscribed, so
    // without a collector they never advance past their initial value.
    @Test
    fun `showStartupBadge is true while the startup badge is undismissed`() = runTest(testDispatcher) {
        every { preferencesRepository.isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_STARTUP) } returns flowOf(false)

        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)

        viewModel.showStartupBadge.test {
            assertFalse("The badge must not flash before the stored value arrives", awaitItem())
            assertTrue("An undismissed badge should show", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showStartupBadge is false once the startup badge is dismissed`() = runTest(testDispatcher) {
        every { preferencesRepository.isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_STARTUP) } returns flowOf(true)

        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository, favoritesRepository, locationsRepository, storageRepository)

        viewModel.showStartupBadge.test {
            assertFalse("A dismissed badge stays hidden", awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
