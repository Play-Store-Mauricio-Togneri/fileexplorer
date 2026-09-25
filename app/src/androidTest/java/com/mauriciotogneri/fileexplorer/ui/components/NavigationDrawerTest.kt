package com.mauriciotogneri.fileexplorer.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.favoriteFilesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.recentFilesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStoreFavoriteFilesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreRecentFilesSource
import com.mauriciotogneri.fileexplorer.testutil.FakeStorageSource
import com.mauriciotogneri.fileexplorer.testutil.NoExternalChanges
import com.mauriciotogneri.fileexplorer.testutil.WarmLocationSizes
import com.mauriciotogneri.fileexplorer.ui.screens.home.HomeScreen
import com.mauriciotogneri.fileexplorer.ui.screens.home.HomeViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The navigation drawer as it exists in the app — inside the real `HomeScreen`.
 *
 * This file previously built its own `ModalNavigationDrawer` + `NavigationDrawerItem` inline in
 * every test. There is no `NavigationDrawer` component in this codebase, so those tests verified
 * that Compose Material3 renders a label and fires `onClick`; they would have stayed green with the
 * app's drawer deleted. Here the drawer is opened through the real menu button.
 *
 * **Subject: opening the drawer and what it contains.** Each item's `startActivity` is asserted by
 * `integration/ActivityNavigationTest`, which covers all four with the same real `HomeScreen` and
 * Espresso-Intents; this file's four copies of those cases were deleted rather than kept as a
 * second set of the same 20-second home loads.
 *
 * **Seeded, not scanning.** The injected [HomeViewModel] is the real one, with the two things that
 * decide how long the load behind `uiState.isLoading` takes replaced: `FakeStorageSource` for the
 * volume enumeration, and [WarmLocationSizes] so every location reports a cache hit instead of
 * walking the device's real storage tree. The drawer is reachable only once that load has finished,
 * so with the walk in place the wait below was open-ended and every test here carried `@Retry`.
 * Nothing about the drawer depends on what those two report, so the fixture costs no coverage — and
 * because none of it is timing-dependent any more, none of these tests is retried.
 */
@RunWith(AndroidJUnit4::class)
class NavigationDrawerTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private fun string(id: Int): String = activity.getString(id)

    @Test
    fun drawer_opensViaMenuButton() {
        renderHome()

        openDrawer()

        composeTestRule.onNodeWithText(string(R.string.drawer_settings)).assertIsDisplayed()
    }

    @Test
    fun drawer_displaysAllNavigationItems() {
        renderHome()

        openDrawer()

        composeTestRule.onNodeWithText(string(R.string.drawer_settings)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.drawer_analyzer)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.drawer_feedback)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.drawer_about)).assertIsDisplayed()
    }

    // ==================== Helpers ====================

    private fun renderHome() {
        composeTestRule.setContent {
            val viewModel = remember { buildViewModel() }
            FileExplorerTheme {
                HomeScreen(viewModel = viewModel)
            }
        }
        // Home finished loading once its search bar is present.
        waitForText(string(R.string.search_placeholder))
    }

    /**
     * The real ViewModel with its two filesystem-bound sources faked, so the load the drawer waits
     * behind is composition work rather than a storage scan. The rest stays real: the drawer's
     * badges come from the preferences store, and the sections it sits over from recents and
     * favorites.
     */
    private fun buildViewModel(): HomeViewModel {
        val app = activity.application
        val preferencesRepository = PreferencesRepository(DataStorePreferencesSource(app.preferencesDataStore))

        return HomeViewModel(
            application = app,
            recentFilesRepository = RecentFilesRepository(DataStoreRecentFilesSource(app.recentFilesDataStore)),
            favoritesRepository = FavoritesRepository(DataStoreFavoriteFilesSource(app.favoriteFilesDataStore)),
            locationsRepository = LocationsRepository(WarmLocationSizes, preferencesRepository),
            storageRepository = StorageRepository(FakeStorageSource(app.cacheDir)),
            preferencesRepository = preferencesRepository,
            fileRepository = FileRepository(),
            mediaChangeSource = NoExternalChanges,
            storageVolumeChangeSource = NoExternalChanges
        )
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openDrawer() {
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_open)).performClick()
        composeTestRule.waitForIdle()
        waitForText(string(R.string.drawer_settings))
    }

    private companion object {
        // A ceiling on composition and the preference store's first read, not on a directory walk:
        // the fixture above removed the scan this used to wait 20 seconds for.
        const val TIMEOUT_MS = 10_000L
    }
}
