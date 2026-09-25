package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.app.Activity
import android.app.Instrumentation
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.favoriteFilesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.recentFilesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStoreFavoriteFilesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreRecentFilesSource
import com.mauriciotogneri.fileexplorer.data.source.PreferencesSource
import com.mauriciotogneri.fileexplorer.testutil.FakeStorageSource
import com.mauriciotogneri.fileexplorer.testutil.NoExternalChanges
import com.mauriciotogneri.fileexplorer.testutil.WarmLocationSizes
import com.mauriciotogneri.fileexplorer.testutil.hasBadgeDot
import com.mauriciotogneri.fileexplorer.ui.components.HomeSearchBar
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The feature-discovery trail on the home screen, rendered with a badge actually on.
 *
 * Nothing in the suite did that before: `HomeViewModelBadgeTest` covers the flows and the dismiss
 * calls, but it answers `isBadgeDismissed` with one shared flow for every badge id, so it cannot see
 * which row a badge belongs to; `BadgeDotTest` renders the dot around a `Text` of its own rather
 * than around production UI; and no instrumentation test passed `showMenuBadge = true`. A release
 * that raises `PreferencesRepository.BADGE_VERSIONS` could therefore ship with no dot anywhere on
 * the trail, which is the regression `AboutScreenTest` records as having happened once already.
 *
 * Two deliberate choices:
 *
 * - The preferences store is held in memory instead of the app's own. The real one is shared by the
 *   whole suite and every test that taps a drawer row dismisses these badges in it permanently, so
 *   assertions against it would pass or fail by test order.
 * - What a badge counts as "already seen" *at* is never spelled out here. Seeding calls the
 *   repository's own `dismissBadge`, so production decides the version and this file stays correct
 *   when a release raises one.
 */
@RunWith(AndroidJUnit4::class)
class HomeBadgeTrailTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private lateinit var preferencesRepository: PreferencesRepository

    @Before
    fun setUp() {
        preferencesRepository = PreferencesRepository(FakeBadgeStore())
        // The drawer rows launch activities; stubbing keeps the tap a tap.
        Intents.init()
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    // ==================== The hamburger, from the real search bar ====================

    @Test
    fun searchBar_withTheMenuBadgeOn_putsTheDotOnTheHamburger() {
        renderSearchBar(showMenuBadge = true)

        val dot = singleBadgeDotCenter()

        assertTrue(
            "The dot must sit on the hamburger, the only control that opens the drawer it points at",
            iconBounds(R.string.menu_open).contains(dot)
        )
        assertFalse(
            "The dot must not sit on the search icon, which leads nowhere new",
            iconBounds(R.string.search_action).contains(dot)
        )
    }

    @Test
    fun searchBar_withTheMenuBadgeOff_showsNoDot() {
        renderSearchBar(showMenuBadge = false)

        composeTestRule.onNode(hasBadgeDot(), useUnmergedTree = true).assertDoesNotExist()
    }

    // ==================== Dismiss on tap ====================

    /**
     * Opening the drawer is what marks the hamburger's badge seen. Neither half was covered: the
     * ViewModel test verifies the call, not that the dot goes, and nothing rendered the dot at all.
     */
    @Test
    fun openingTheDrawer_takesTheHamburgerDotDown() {
        // Every drawer row already seen, so the hamburger's is the only dot in the tree.
        renderHome(seen = DRAWER_ROWS.map { (badgeId, _) -> badgeId })
        awaitBadgeDotCount(1, "the hamburger dot before the drawer is opened")

        composeTestRule.onNodeWithContentDescription(string(R.string.menu_open)).performClick()

        awaitBadgeDotCount(0, "the hamburger dot after the drawer was opened")
        assertTrue(
            "Opening the drawer must store the dismissal, or the dot returns on the next launch",
            isSeen(PreferencesRepository.BADGE_MENU_DRAWER)
        )
    }

    @Test
    fun tappingADrawerRow_dismissesOnlyItsOwnBadge() {
        // Only the hamburger is already seen, so each of the four rows carries a dot of its own.
        renderHome(seen = listOf(PreferencesRepository.BADGE_MENU_DRAWER))
        openDrawer()
        awaitBadgeDotCount(DRAWER_ROWS.size, "one dot per drawer row")

        composeTestRule.onNodeWithText(string(R.string.drawer_analyzer)).performClick()

        awaitBadgeDotCount(DRAWER_ROWS.size - 1, "the dots left after the Analyzer row was tapped")
        assertTrue(
            "The Analyzer row must store its own dismissal",
            isSeen(PreferencesRepository.BADGE_DRAWER_ANALYZER)
        )
        DRAWER_ROWS
            .map { (badgeId, _) -> badgeId }
            .filter { badgeId -> badgeId != PreferencesRepository.BADGE_DRAWER_ANALYZER }
            .forEach { badgeId ->
                assertFalse(
                    "Tapping Analyzer must leave '$badgeId' alone, or the release loses the dots " +
                        "it was pointing with the first time the drawer is opened",
                    isSeen(badgeId)
                )
            }
    }

    // ==================== One badge per drawer row ====================

    // Each row must read its own badge. The ViewModel test cannot see this — it answers
    // isBadgeDismissed with a single flow for every id, so a row wired to another row's badge passes
    // there and shows up only as a dot on the wrong row here.

    @Test
    fun settingsRow_showsTheSettingsBadgeAndNoOtherRowShowsIt() =
        assertOnlyRowWithADot(PreferencesRepository.BADGE_DRAWER_SETTINGS)

    @Test
    fun analyzerRow_showsTheAnalyzerBadgeAndNoOtherRowShowsIt() =
        assertOnlyRowWithADot(PreferencesRepository.BADGE_DRAWER_ANALYZER)

    @Test
    fun feedbackRow_showsTheFeedbackBadgeAndNoOtherRowShowsIt() =
        assertOnlyRowWithADot(PreferencesRepository.BADGE_DRAWER_FEEDBACK)

    @Test
    fun aboutRow_showsTheAboutBadgeAndNoOtherRowShowsIt() =
        assertOnlyRowWithADot(PreferencesRepository.BADGE_DRAWER_ABOUT)

    /**
     * Leaves [liveBadgeId] as the only badge on the trail that has not been seen, then asserts the
     * single dot this produces falls inside the row that owns it and inside no other row.
     */
    private fun assertOnlyRowWithADot(liveBadgeId: String) {
        renderHome(seen = TRAIL_BADGES.filter { badgeId -> badgeId != liveBadgeId })
        openDrawer()

        val dot = singleBadgeDotCenter()
        DRAWER_ROWS.forEach { (badgeId, labelRes) ->
            val label = string(labelRes)
            val hasDot = rowBounds(labelRes).contains(dot)

            if (badgeId == liveBadgeId) {
                assertTrue("The $label row owns '$liveBadgeId' and must show its dot", hasDot)
            } else {
                assertFalse(
                    "The $label row must not show the dot that belongs to '$liveBadgeId'",
                    hasDot
                )
            }
        }
    }

    // ==================== Rendering ====================

    private fun renderSearchBar(showMenuBadge: Boolean) {
        composeTestRule.setContent {
            FileExplorerTheme {
                HomeSearchBar(
                    onMenuClick = {},
                    onSearchContainerClick = {},
                    onSearchIconClick = {},
                    showMenuBadge = showMenuBadge
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    /** Renders the real home screen with every badge in [seen] already dismissed. */
    private fun renderHome(seen: List<String>) {
        runBlocking { seen.forEach { badgeId -> preferencesRepository.dismissBadge(badgeId) } }

        composeTestRule.setContent {
            val viewModel = remember { buildViewModel() }
            FileExplorerTheme {
                HomeScreen(viewModel = viewModel)
            }
        }
        // Home has finished loading once its search bar is present.
        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithText(string(R.string.search_placeholder))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    /**
     * The real ViewModel over the in-memory preferences store, with the two filesystem-bound sources
     * faked so the load the drawer waits behind is composition work rather than a storage scan.
     */
    private fun buildViewModel(): HomeViewModel {
        val app = activity.application

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

    private fun openDrawer() {
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_open)).performClick()
        // The sheet is composed even while closed, so waiting for the row's text would return at
        // once: what says the drawer is open is the row being on screen.
        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule.onNodeWithText(string(R.string.drawer_settings)).isDisplayed()
        }
    }

    // ==================== Assertions ====================

    private fun badgeDotCount(): Int =
        composeTestRule.onAllNodes(hasBadgeDot(), useUnmergedTree = true).fetchSemanticsNodes().size

    /**
     * Waits for the tree to hold [expected] dots. The badge flows start at "seen" and emit the
     * stored value once the store has been read, so a count read straight after rendering would
     * race that first emission. A timeout is reported as the count it was still stuck at.
     */
    private fun awaitBadgeDotCount(expected: Int, what: String) {
        try {
            composeTestRule.waitUntil(TIMEOUT_MS) { badgeDotCount() == expected }
        } catch (e: ComposeTimeoutException) {
            assertEquals(what, expected, badgeDotCount())
            throw e
        }
    }

    private fun singleBadgeDotCenter(): Offset {
        awaitBadgeDotCount(1, "badge dots in the tree")

        return composeTestRule
            .onAllNodes(hasBadgeDot(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first()
            .boundsInRoot
            .center
    }

    /** The bounds of the whole drawer row labelled [labelRes], which its merged node carries. */
    private fun rowBounds(@StringRes labelRes: Int): Rect =
        composeTestRule.onNodeWithText(string(labelRes)).fetchSemanticsNode().boundsInRoot

    private fun iconBounds(@StringRes descriptionRes: Int): Rect =
        composeTestRule
            .onNodeWithContentDescription(string(descriptionRes))
            .fetchSemanticsNode()
            .boundsInRoot

    /** Whether the store now holds [badgeId] as dismissed, decided by the repository's own rule. */
    private fun isSeen(badgeId: String): Boolean =
        runBlocking { preferencesRepository.isBadgeDismissed(badgeId).first() }

    private fun string(@StringRes id: Int): String = activity.getString(id)

    /**
     * The preferences store, in memory. Only the badge members hold state; the rest answer with the
     * defaults the home screen reads, so nothing here depends on — or disturbs — the app's store.
     */
    private class FakeBadgeStore : PreferencesSource {

        private val dismissed = MutableStateFlow(emptyMap<String, Int>())

        override fun dismissedBadgeVersion(badgeId: String): Flow<Int> = dismissed.map { versions ->
            versions[badgeId] ?: PreferencesSource.BADGE_NEVER_DISMISSED
        }

        override suspend fun dismissBadge(badgeId: String, version: Int) {
            dismissed.value += (badgeId to version)
        }

        override val showHidden: Flow<Boolean> = flowOf(false)
        override suspend fun setShowHidden(show: Boolean) = Unit

        override val themeMode: Flow<ThemeMode> = flowOf(ThemeMode.SYSTEM)
        override suspend fun setThemeMode(mode: ThemeMode) = Unit

        override val sortMode: Flow<SortMode> = flowOf(SortMode.NAME_ASC)
        override suspend fun setSortMode(mode: SortMode) = Unit

        override val enabledLocations: Flow<Set<LocationType>> = flowOf(LocationType.entries.toSet())
        override suspend fun setEnabledLocations(enabledLocations: Set<LocationType>) = Unit

        override val recentFilesEnabled: Flow<Boolean> = flowOf(true)
        override suspend fun setRecentFilesEnabled(enabled: Boolean) = Unit

        override val homeSectionOrder: Flow<List<HomeSection>> = flowOf(HomeSection.DEFAULT_ORDER)
        override suspend fun setHomeSectionOrder(order: List<HomeSection>) = Unit

        override val folderSecondLine: Flow<FolderSecondLine> = flowOf(FolderSecondLine.ITEM_COUNT)
        override suspend fun setFolderSecondLine(secondLine: FolderSecondLine) = Unit

        override val fileSecondLine: Flow<FileSecondLine> = flowOf(FileSecondLine.SIZE)
        override suspend fun setFileSecondLine(secondLine: FileSecondLine) = Unit

        override val swipeLeftAction: Flow<SwipeAction> = flowOf(SwipeAction.RENAME)
        override suspend fun setSwipeLeftAction(action: SwipeAction) = Unit

        override val swipeRightAction: Flow<SwipeAction> = flowOf(SwipeAction.DELETE)
        override suspend fun setSwipeRightAction(action: SwipeAction) = Unit

        override val startupScreen: Flow<StartupScreen> = flowOf(StartupScreen.HOME)
        override val startupFolderPath: Flow<String?> = flowOf(null)
        override suspend fun setStartupScreen(screen: StartupScreen, folderPath: String?) = Unit
    }

    /**
     * Answers every location with a valid cached size, which is what keeps `getLocations()` from
     * walking a tree: it measures only on a cache miss. Nothing is written back.
     */

    /**
     * Neither a media notification nor a volume broadcast during a badge test, so nothing reloads
     * underneath the assertions. One object for both interfaces: they declare the same member.
     */

    private companion object {
        // A ceiling on composition, the drawer's animation and the store's first read.
        const val TIMEOUT_MS = 10_000L

        /**
         * Which badge each drawer row is expected to own — the contract under test, stated here so a
         * row reading another row's flow fails rather than passing on a dot in the wrong place.
         */
        val DRAWER_ROWS = listOf(
            PreferencesRepository.BADGE_DRAWER_SETTINGS to R.string.drawer_settings,
            PreferencesRepository.BADGE_DRAWER_ANALYZER to R.string.drawer_analyzer,
            PreferencesRepository.BADGE_DRAWER_FEEDBACK to R.string.drawer_feedback,
            PreferencesRepository.BADGE_DRAWER_ABOUT to R.string.drawer_about
        )

        /** The whole trail: the hamburger that opens the drawer, and the rows inside it. */
        val TRAIL_BADGES = listOf(PreferencesRepository.BADGE_MENU_DRAWER) +
            DRAWER_ROWS.map { (badgeId, _) -> badgeId }
    }
}
