package com.mauriciotogneri.fileexplorer.ui.screens.about

import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.source.FakePreferencesSource
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
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

/**
 * [AboutViewModel] had no test at any level, which is why the "other apps" badge could ship with
 * neither its display nor its dismissal covered.
 *
 * The badge's whole point is that it appears once and stays gone, so both halves matter: it derives
 * from the *inverse* of the dismissed flag, and dismissing must persist through the repository
 * rather than only flipping local state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(dismissedBadges: Set<String> = emptySet()): Pair<AboutViewModel, FakePreferencesSource> {
        val source = FakePreferencesSource(initialDismissedBadges = dismissedBadges)
        return AboutViewModel(PreferencesRepository(source)) to source
    }

    @Test
    fun showOtherAppsBadge_startsFalseBeforeThePreferenceIsRead() {
        val (viewModel, _) = viewModel()

        assertFalse(
            "The badge must not flash before the stored value arrives",
            viewModel.showOtherAppsBadge.value
        )
    }

    @Test
    fun showOtherAppsBadge_trueWhenNotYetDismissed() = runTest(testDispatcher) {
        val (viewModel, _) = viewModel(dismissedBadges = emptySet())

        viewModel.showOtherAppsBadge.test {
            assertFalse("Initial value before collection starts", awaitItem())
            assertTrue("An undismissed badge should show", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun showOtherAppsBadge_falseOnceDismissed() = runTest(testDispatcher) {
        val (viewModel, _) = viewModel(
            dismissedBadges = setOf(PreferencesRepository.BADGE_ABOUT_OTHER_APPS)
        )

        viewModel.showOtherAppsBadge.test {
            assertFalse("A dismissed badge stays hidden", awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun dismissOtherAppsBadge_persistsAndHidesTheBadge() = runTest(testDispatcher) {
        val (viewModel, source) = viewModel()

        viewModel.showOtherAppsBadge.test {
            awaitItem() // initial false
            assertTrue(awaitItem())

            viewModel.dismissOtherAppsBadge()
            advanceUntilIdle()

            assertFalse("Dismissing should hide the badge", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // Persisted, not merely hidden in memory: a fresh view model over the same source agrees.
        val reopened = AboutViewModel(PreferencesRepository(source))
        reopened.showOtherAppsBadge.test {
            assertFalse(awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Dismissing the About badge must not disturb any other badge's state. */
    @Test
    fun dismissOtherAppsBadge_leavesOtherBadgesAlone() = runTest(testDispatcher) {
        val source = FakePreferencesSource()
        val repository = PreferencesRepository(source)
        val viewModel = AboutViewModel(repository)

        viewModel.dismissOtherAppsBadge()
        advanceUntilIdle()

        repository.isBadgeDismissed(PreferencesRepository.BADGE_ABOUT_OTHER_APPS).test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        repository.isBadgeDismissed(PreferencesRepository.BADGE_FOLDER_CONTEXT_MENU).test {
            assertFalse("Only the About badge should be marked seen", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun themeMode_reflectsTheGlobalThemeManager() = runTest(testDispatcher) {
        val original = ThemeManager.currentTheme
        try {
            ThemeManager.setTheme(ThemeMode.DARK)
            val (viewModel, _) = viewModel()

            assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
        } finally {
            ThemeManager.setTheme(original)
        }
    }
}
