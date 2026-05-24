package com.mauriciotogneri.fileexplorer.ui.screens.settings

import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var recentFilesRepository: RecentFilesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        preferencesRepository = mockk(relaxed = true)
        recentFilesRepository = mockk(relaxed = true)
        mockkObject(AnalyticsTracker)
        every { AnalyticsTracker.trackSettingsTheme(any()) } returns Unit
        every { AnalyticsTracker.trackSettingsLocationsChanged(any()) } returns Unit
        every { AnalyticsTracker.trackSettingsRecentFilesTracking(any()) } returns Unit
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

        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }

    @Test
    fun `setThemeMode updates ThemeManager and repository`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setThemeMode(ThemeMode.LIGHT)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, ThemeManager.currentTheme)
        coVerify { preferencesRepository.setThemeMode(ThemeMode.LIGHT) }
    }

    @Test
    fun `themeMode updates when ThemeManager changes`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository)
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
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        ThemeMode.entries.forEach { mode ->
            viewModel.setThemeMode(mode)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(mode, ThemeManager.currentTheme)
            coVerify { preferencesRepository.setThemeMode(mode) }
        }
    }

    @Test
    fun `setEnabledLocations calls repository with selected locations`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        val enabledLocations = setOf(LocationType.DOWNLOADS, LocationType.IMAGES)
        viewModel.setEnabledLocations(enabledLocations)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.setEnabledLocations(enabledLocations) }
    }

    @Test
    fun `setEnabledLocations can save empty set`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setEnabledLocations(emptySet())
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.setEnabledLocations(emptySet()) }
    }

    @Test
    fun `dismissLocationsBadge calls repository with correct badge id`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissLocationsBadge()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.dismissBadge(PreferencesRepository.BADGE_SETTINGS_LOCATIONS) }
    }

    @Test
    fun `dismissThemeBadge calls repository with correct badge id`() = runTest {
        val viewModel = SettingsViewModel(preferencesRepository, recentFilesRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissThemeBadge()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.dismissBadge(PreferencesRepository.BADGE_SETTINGS_THEME) }
    }
}
