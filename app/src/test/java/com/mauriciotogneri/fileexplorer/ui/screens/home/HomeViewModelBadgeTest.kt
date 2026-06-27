package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.app.Application
import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelBadgeTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var recentFilesRepository: RecentFilesRepository
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var locationsRepository: LocationsRepository
    private lateinit var storageRepository: StorageRepository
    private lateinit var preferencesRepository: PreferencesRepository
    private lateinit var fileRepository: FileRepository

    private val badgeDismissedFlow = MutableStateFlow(false)
    private val recentFilesEnabledFlow = MutableStateFlow(true)
    private val recentFilesFlow = MutableStateFlow<List<RecentFile>>(emptyList())
    private val favoritesFlow = MutableStateFlow<List<Favorite>>(emptyList())
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
        coEvery { locationsRepository.getLocations() } returns emptyList()
        coEvery { storageRepository.getStorages() } returns emptyList()
        every { preferencesRepository.isBadgeDismissed(any()) } returns badgeDismissedFlow
        every { preferencesRepository.recentFilesEnabled } returns recentFilesEnabledFlow
    }

    @After
    fun tearDown() {
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `showMenuBadge is true when badge not dismissed`() = runTest {
        badgeDismissedFlow.value = false

        val viewModel = createViewModel()

        viewModel.showMenuBadge.test {
            skipItems(1)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(true, awaitItem())
        }
    }

    @Test
    fun `showMenuBadge is false when badge already dismissed`() = runTest {
        badgeDismissedFlow.value = true

        val viewModel = createViewModel()

        viewModel.showMenuBadge.test {
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `dismissMenuBadge calls repository with correct badge id`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissMenuBadge()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.dismissBadge(PreferencesRepository.BADGE_MENU_DRAWER) }
    }

    @Test
    fun `dismissSettingsBadge calls repository with correct badge id`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissSettingsBadge()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.dismissBadge(PreferencesRepository.BADGE_DRAWER_SETTINGS) }
    }

    @Test
    fun `dismissFeedbackBadge calls repository with correct badge id`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissFeedbackBadge()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.dismissBadge(PreferencesRepository.BADGE_DRAWER_FEEDBACK) }
    }

    @Test
    fun `dismissAboutBadge calls repository with correct badge id`() = runTest {
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.dismissAboutBadge()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferencesRepository.dismissBadge(PreferencesRepository.BADGE_DRAWER_ABOUT) }
    }

    @Test
    fun `showMenuBadge updates when badge is dismissed`() = runTest {
        badgeDismissedFlow.value = false

        val viewModel = createViewModel()

        viewModel.showMenuBadge.test {
            skipItems(1)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(true, awaitItem())

            badgeDismissedFlow.value = true
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(false, awaitItem())
        }
    }

    private fun createViewModel() = HomeViewModel(
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
