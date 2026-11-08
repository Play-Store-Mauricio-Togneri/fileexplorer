package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.data.source.FakePreferencesSource
import com.mauriciotogneri.fileexplorer.data.source.PreferencesSource
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PreferencesRepositoryTest {

    @Before
    fun setUp() {
        mockkObject(ErrorReporter)
        every { ErrorReporter.error(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkObject(ErrorReporter)
    }

    @Test
    fun `setShowHidden updates showHidden flow`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        repository.setShowHidden(true)

        assertTrue(repository.showHidden.first())
    }

    @Test
    fun `setFolderSecondLine updates folderSecondLine flow`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        repository.setFolderSecondLine(FolderSecondLine.LAST_MODIFIED)

        assertEquals(FolderSecondLine.LAST_MODIFIED, repository.folderSecondLine.first())
    }

    @Test
    fun `setFileSecondLine updates fileSecondLine flow`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        repository.setFileSecondLine(FileSecondLine.NONE)

        assertEquals(FileSecondLine.NONE, repository.fileSecondLine.first())
    }

    @Test
    fun `setSwipeLeftAction updates swipeLeftAction flow`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        repository.setSwipeLeftAction(SwipeAction.INFO)

        assertEquals(SwipeAction.INFO, repository.swipeLeftAction.first())
    }

    @Test
    fun `setSwipeRightAction updates swipeRightAction flow`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        repository.setSwipeRightAction(SwipeAction.NONE)

        assertEquals(SwipeAction.NONE, repository.swipeRightAction.first())
    }

    @Test
    fun `setThemeMode updates themeMode flow`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.themeMode.first())
    }

    @Test
    fun `getInitialThemeMode returns current theme`() = runTest {
        val source = FakePreferencesSource(initialThemeMode = ThemeMode.LIGHT)
        val repository = PreferencesRepository(source)

        val result = repository.getInitialThemeMode()

        assertEquals(ThemeMode.LIGHT, result)
    }

    @Test
    fun `getInitialThemeMode falls back to SYSTEM when read fails`() = runTest {
        val source = object : PreferencesSource by FakePreferencesSource() {
            override val themeMode: Flow<ThemeMode> = flow { throw IOException("corrupt") }
        }
        val repository = PreferencesRepository(source)

        val result = repository.getInitialThemeMode()

        assertEquals(ThemeMode.SYSTEM, result)
    }

    @Test
    fun `setSortMode updates sortMode flow`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        repository.setSortMode(SortMode.SIZE_DESC)

        assertEquals(SortMode.SIZE_DESC, repository.sortMode.first())
    }

    @Test
    fun `getInitialSortMode returns current sort mode`() = runTest {
        val source = FakePreferencesSource(initialSortMode = SortMode.DATE_DESC)
        val repository = PreferencesRepository(source)

        val result = repository.getInitialSortMode()

        assertEquals(SortMode.DATE_DESC, result)
    }

    @Test
    fun `getInitialSortMode falls back to NAME_ASC when read fails`() = runTest {
        val source = object : PreferencesSource by FakePreferencesSource() {
            override val sortMode: Flow<SortMode> = flow { throw IOException("corrupt") }
        }
        val repository = PreferencesRepository(source)

        val result = repository.getInitialSortMode()

        assertEquals(SortMode.NAME_ASC, result)
    }

    @Test
    fun `setEnabledLocations updates enabledLocations flow`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())
        val subset = setOf(LocationType.DOWNLOADS, LocationType.DOCUMENTS)

        repository.setEnabledLocations(subset)

        assertEquals(subset, repository.enabledLocations.first())
    }

    @Test
    fun `setRecentFilesEnabled updates recentFilesEnabled flow`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        repository.setRecentFilesEnabled(false)

        assertFalse(repository.recentFilesEnabled.first())
    }

    @Test
    fun `isBadgeDismissed returns false for new badge`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        val result = repository.isBadgeDismissed("test_badge").first()

        assertFalse(result)
    }

    @Test
    fun `setStartupScreen stores the screen and its folder`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        repository.setStartupScreen(StartupScreen.FOLDER, "/storage/emulated/0/Download")

        assertEquals(StartupScreen.FOLDER, repository.startupScreen.first())
        assertEquals("/storage/emulated/0/Download", repository.startupFolderPath.first())
    }

    @Test
    fun `setStartupScreen to HOME clears the stored folder`() = runTest {
        val repository = PreferencesRepository(
            FakePreferencesSource(
                initialStartupScreen = StartupScreen.FOLDER,
                initialStartupFolderPath = "/storage/emulated/0/Download"
            )
        )

        repository.setStartupScreen(StartupScreen.HOME, null)

        assertEquals(StartupScreen.HOME, repository.startupScreen.first())
        assertNull(repository.startupFolderPath.first())
    }

    @Test
    fun `getInitialStartupFolderPath returns null when starting on home`() {
        val repository = PreferencesRepository(FakePreferencesSource())

        assertNull(repository.getInitialStartupFolderPath())
    }

    @Test
    fun `getInitialStartupFolderPath returns the stored folder`() {
        val repository = PreferencesRepository(
            FakePreferencesSource(
                initialStartupScreen = StartupScreen.FOLDER,
                initialStartupFolderPath = "/storage/emulated/0/Download"
            )
        )

        assertEquals("/storage/emulated/0/Download", repository.getInitialStartupFolderPath())
    }

    // A store left half-written must open the home screen rather than a folder screen with no
    // folder to show.
    @Test
    fun `getInitialStartupFolderPath returns null when the folder screen has no folder`() {
        val repository = PreferencesRepository(
            FakePreferencesSource(
                initialStartupScreen = StartupScreen.FOLDER,
                initialStartupFolderPath = null
            )
        )

        assertNull(repository.getInitialStartupFolderPath())
    }

    @Test
    fun `getInitialStartupFolderPath returns null when the store fails`() {
        val source = object : PreferencesSource by FakePreferencesSource() {
            override val startupScreen: Flow<StartupScreen> = flow { throw IOException("corrupt") }
        }
        val repository = PreferencesRepository(source)

        assertNull(repository.getInitialStartupFolderPath())
    }

    @Test
    fun `dismissBadge marks badge as dismissed`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        repository.dismissBadge("test_badge")

        assertTrue(repository.isBadgeDismissed("test_badge").first())
    }

    @Test
    fun `dismissBadge does not affect other badges`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        repository.dismissBadge("badge_one")

        assertTrue(repository.isBadgeDismissed("badge_one").first())
        assertFalse(repository.isBadgeDismissed("badge_two").first())
    }

    @Test
    fun `multiple badges can be dismissed`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        repository.dismissBadge(PreferencesRepository.BADGE_MENU_DRAWER)
        repository.dismissBadge(PreferencesRepository.BADGE_DRAWER_SETTINGS)

        assertTrue(repository.isBadgeDismissed(PreferencesRepository.BADGE_MENU_DRAWER).first())
        assertTrue(repository.isBadgeDismissed(PreferencesRepository.BADGE_DRAWER_SETTINGS).first())
        assertFalse(repository.isBadgeDismissed(PreferencesRepository.BADGE_DRAWER_ABOUT).first())
    }

    // ==================== Showing a badge again after an update ====================

    /**
     * What a user who updates from a version before [PreferencesRepository.BADGE_VERSIONS] raised a
     * badge sees: their old dismissal is at the first version, so a raised badge comes back. Without
     * this, a release has no way to point them at anything it added.
     *
     * Every raised badge is checked, not just the first: an entry silently left at the first version
     * points at nothing, and the trail it belongs to breaks at that step.
     */
    @Test
    fun `a raised badge is shown again to a user who dismissed the previous version`() = runTest {
        PreferencesRepository.BADGE_VERSIONS.keys.forEach { raised ->
            val source = FakePreferencesSource(initialDismissedBadges = setOf(raised))
            val repository = PreferencesRepository(source)

            assertFalse("$raised was raised, so it must show again", repository.isBadgeDismissed(raised).first())
        }
    }

    @Test
    fun `dismissing a raised badge hides it again`() = runTest {
        PreferencesRepository.BADGE_VERSIONS.keys.forEach { raised ->
            val source = FakePreferencesSource(initialDismissedBadges = setOf(raised))
            val repository = PreferencesRepository(source)

            repository.dismissBadge(raised)

            assertTrue("$raised stays hidden once dismissed at its new version", repository.isBadgeDismissed(raised).first())
        }
    }

    /**
     * A badge the release did not raise must stay dismissed. Showing every badge again would leave
     * the raised ones pointing at nothing in particular.
     */
    @Test
    fun `a badge that was not raised stays dismissed`() = runTest {
        val untouched = PreferencesRepository.BADGE_DRAWER_ABOUT
        val source = FakePreferencesSource(initialDismissedBadges = setOf(untouched))
        val repository = PreferencesRepository(source)

        assertFalse(PreferencesRepository.BADGE_VERSIONS.containsKey(untouched))
        assertTrue(repository.isBadgeDismissed(untouched).first())
    }

    /** A typo'd id would be raised in the table and never reach the badge it was meant for. */
    @Test
    fun `every raised badge id is a real badge id`() {
        assertTrue(
            "Raised ids not declared as badges: ${PreferencesRepository.BADGE_VERSIONS.keys - ALL_BADGES}",
            ALL_BADGES.containsAll(PreferencesRepository.BADGE_VERSIONS.keys)
        )
    }

    // ==================== The badge trail ====================

    /**
     * The trail checks below only hold a badge to the rule once it is placed: under the badge whose
     * dot leads to it, or at the top of a trail of its own.
     */
    @Test
    fun `every badge sits on a trail`() {
        assertEquals(ALL_BADGES, ROOTS + TRAIL.keys)
    }

    /**
     * A user who dismissed everything sees a raised badge only if the badge leading to it was raised
     * too, so no badge can be ahead of its parent. Raising a drawer row past the hamburger leaves
     * the new dot inside a drawer nothing tells that user to open.
     */
    @Test
    fun `no badge is raised past the badge that leads to it`() = runTest {
        TRAIL.forEach { (badge, parent) ->
            val version = currentVersion(badge)
            val parentVersion = currentVersion(parent)

            assertTrue(
                "$badge is at $version, past $parent at $parentVersion, which leads to it",
                parentVersion >= version
            )
        }
    }

    /**
     * The exact form of the rule, which the check above cannot give: the hamburger leads by enough
     * that a drawer row could be raised on its own for releases without passing it, and each time
     * the dot would be unreachable. Measured against what the last release shipped, every badge
     * raised since — or added since, which counts as raised from never dismissed — needs the badge
     * leading to it raised in the same release.
     */
    @Test
    fun `a badge raised since the last release has the badge that leads to it raised too`() = runTest {
        TRAIL.forEach { (badge, parent) ->
            if (currentVersion(badge) > shippedVersion(badge)) {
                assertTrue(
                    "$badge was raised since the last release, but $parent, which leads to it, was not",
                    currentVersion(parent) > shippedVersion(parent)
                )
            }
        }
    }

    /**
     * The version [badgeId] is at now, read through the repository rather than off
     * [PreferencesRepository.BADGE_VERSIONS]: what a dismissal stores, so a badge the table does not
     * list reads at the first version it defaults to.
     */
    private suspend fun currentVersion(badgeId: String): Int {
        val source = FakePreferencesSource()
        PreferencesRepository(source).dismissBadge(badgeId)
        return source.dismissedBadgeVersion(badgeId).first()
    }

    private fun shippedVersion(badgeId: String): Int =
        LAST_RELEASE_VERSIONS[badgeId] ?: PreferencesSource.BADGE_NEVER_DISMISSED

    private companion object {
        /** Every badge the app declares. Add new ones here as they are added to the repository. */
        val ALL_BADGES = setOf(
            PreferencesRepository.BADGE_MENU_DRAWER,
            PreferencesRepository.BADGE_DRAWER_SETTINGS,
            PreferencesRepository.BADGE_DRAWER_ANALYZER,
            PreferencesRepository.BADGE_DRAWER_ABOUT,
            PreferencesRepository.BADGE_ABOUT_OTHER_APPS,
            PreferencesRepository.BADGE_FOLDER_CONTEXT_MENU
        )

        /**
         * Each badge to the badge whose dot leads to it: the hamburger opens the drawer, and the
         * drawer's About row opens the About screen.
         */
        val TRAIL = mapOf(
            PreferencesRepository.BADGE_DRAWER_SETTINGS to PreferencesRepository.BADGE_MENU_DRAWER,
            PreferencesRepository.BADGE_DRAWER_ANALYZER to PreferencesRepository.BADGE_MENU_DRAWER,
            PreferencesRepository.BADGE_DRAWER_ABOUT to PreferencesRepository.BADGE_MENU_DRAWER,
            PreferencesRepository.BADGE_ABOUT_OTHER_APPS to PreferencesRepository.BADGE_DRAWER_ABOUT
        )

        /** The badges at the top of a trail, on screen without opening anything first. */
        val ROOTS = setOf(
            PreferencesRepository.BADGE_MENU_DRAWER,
            PreferencesRepository.BADGE_FOLDER_CONTEXT_MENU
        )

        /**
         * Every badge's version as the last release shipped it: 2.7.3, the tip of `release/2.7.0`.
         * The table has not changed since 2.6.0.
         *
         * Update this every release, once it ships: each badge at its BADGE_VERSIONS entry, or 1
         * when it has none, badges that release added included. A table left at an older release
         * lets a badge be raised without its parent and still pass.
         */
        val LAST_RELEASE_VERSIONS = mapOf(
            PreferencesRepository.BADGE_MENU_DRAWER to 6,
            PreferencesRepository.BADGE_DRAWER_SETTINGS to 5,
            PreferencesRepository.BADGE_DRAWER_ANALYZER to 1,
            PreferencesRepository.BADGE_DRAWER_ABOUT to 1,
            PreferencesRepository.BADGE_ABOUT_OTHER_APPS to 1,
            PreferencesRepository.BADGE_FOLDER_CONTEXT_MENU to 1
        )
    }
}
