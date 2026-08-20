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
    fun `showHidden defaults to false`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        val result = repository.showHidden.first()

        assertFalse(result)
    }

    @Test
    fun `setShowHidden updates showHidden flow`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        repository.setShowHidden(true)

        assertTrue(repository.showHidden.first())
    }

    @Test
    fun `folderSecondLine defaults to the item count`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        assertEquals(FolderSecondLine.ITEM_COUNT, repository.folderSecondLine.first())
    }

    @Test
    fun `fileSecondLine defaults to the size`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        assertEquals(FileSecondLine.SIZE, repository.fileSecondLine.first())
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

    /** The two settings are independent: choosing one must not move the other. */
    @Test
    fun `setting one second line leaves the other alone`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        repository.setFolderSecondLine(FolderSecondLine.NONE)

        assertEquals(FileSecondLine.SIZE, repository.fileSecondLine.first())
    }

    /** The defaults are what each direction did before the setting existed. */
    @Test
    fun `swipe actions default to rename on the left and delete on the right`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        assertEquals(SwipeAction.RENAME, repository.swipeLeftAction.first())
        assertEquals(SwipeAction.DELETE, repository.swipeRightAction.first())
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

    /** The two directions are independent: switching one off must not touch the other. */
    @Test
    fun `switching one swipe direction off leaves the other alone`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        repository.setSwipeLeftAction(SwipeAction.NONE)

        assertEquals(SwipeAction.DELETE, repository.swipeRightAction.first())
    }

    @Test
    fun `themeMode defaults to SYSTEM`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        val result = repository.themeMode.first()

        assertEquals(ThemeMode.SYSTEM, result)
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
    fun `sortMode defaults to NAME_ASC`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        val result = repository.sortMode.first()

        assertEquals(SortMode.NAME_ASC, result)
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
    fun `enabledLocations defaults to all locations`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        val result = repository.enabledLocations.first()

        assertEquals(LocationType.entries.toSet(), result)
    }

    @Test
    fun `setEnabledLocations updates enabledLocations flow`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())
        val subset = setOf(LocationType.DOWNLOADS, LocationType.DOCUMENTS)

        repository.setEnabledLocations(subset)

        assertEquals(subset, repository.enabledLocations.first())
    }

    @Test
    fun `recentFilesEnabled defaults to true`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        val result = repository.recentFilesEnabled.first()

        assertTrue(result)
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
    fun `startupScreen defaults to HOME with no folder`() = runTest {
        val repository = PreferencesRepository(FakePreferencesSource())

        assertEquals(StartupScreen.HOME, repository.startupScreen.first())
        assertNull(repository.startupFolderPath.first())
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
     */
    @Test
    fun `a raised badge is shown again to a user who dismissed the previous version`() = runTest {
        val raised = PreferencesRepository.BADGE_VERSIONS.keys.first()
        val source = FakePreferencesSource(initialDismissedBadges = setOf(raised))
        val repository = PreferencesRepository(source)

        assertFalse(repository.isBadgeDismissed(raised).first())
    }

    @Test
    fun `dismissing a raised badge hides it again`() = runTest {
        val raised = PreferencesRepository.BADGE_VERSIONS.keys.first()
        val source = FakePreferencesSource(initialDismissedBadges = setOf(raised))
        val repository = PreferencesRepository(source)

        repository.dismissBadge(raised)

        assertTrue(repository.isBadgeDismissed(raised).first())
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

    /**
     * The whole point of the release that added the second-line settings, from the point of view of
     * a user who had dismissed every badge the app shipped before them: every step of the trail to
     * the new settings shows again, so they can actually be found.
     */
    @Test
    fun `the trail to the second line settings shows after an update`() = runTest {
        val newBadges = setOf(
            PreferencesRepository.BADGE_SETTINGS_FOLDER_SECOND_LINE,
            PreferencesRepository.BADGE_SETTINGS_FILE_SECOND_LINE
        )
        val source = FakePreferencesSource(initialDismissedBadges = ALL_BADGES - newBadges)
        val repository = PreferencesRepository(source)

        assertFalse(repository.isBadgeDismissed(PreferencesRepository.BADGE_MENU_DRAWER).first())
        assertFalse(repository.isBadgeDismissed(PreferencesRepository.BADGE_DRAWER_SETTINGS).first())
        newBadges.forEach { badge ->
            assertFalse(repository.isBadgeDismissed(badge).first())
        }
    }

    /**
     * The same for the release that added the home section order: the trail to a new setting is only
     * reachable if every step of it shows again.
     */
    @Test
    fun `the trail to the home sections setting shows after an update`() = runTest {
        val newBadge = PreferencesRepository.BADGE_SETTINGS_HOME_SECTIONS
        val source = FakePreferencesSource(initialDismissedBadges = ALL_BADGES - newBadge)
        val repository = PreferencesRepository(source)

        assertFalse(repository.isBadgeDismissed(PreferencesRepository.BADGE_MENU_DRAWER).first())
        assertFalse(repository.isBadgeDismissed(PreferencesRepository.BADGE_DRAWER_SETTINGS).first())
        assertFalse(repository.isBadgeDismissed(newBadge).first())
    }

    /**
     * The counterpart: a release points at what it added and nothing else. Every setting badge below
     * was some earlier release's destination, and a user who dismissed one must not see it again —
     * dots that lead to nothing already seen are how users learn to ignore dots.
     */
    @Test
    fun `the previous releases' setting badges stay dismissed`() = runTest {
        val source = FakePreferencesSource(initialDismissedBadges = ALL_BADGES)
        val repository = PreferencesRepository(source)

        assertTrue(repository.isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_STARTUP).first())
        assertTrue(repository.isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_THEME).first())
        assertTrue(repository.isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_LOCATIONS).first())
        assertTrue(repository.isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_FOLDER_SECOND_LINE).first())
        assertTrue(repository.isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_FILE_SECOND_LINE).first())
    }

    private companion object {
        /** Every badge the app declares. Add new ones here as they are added to the repository. */
        val ALL_BADGES = setOf(
            PreferencesRepository.BADGE_MENU_DRAWER,
            PreferencesRepository.BADGE_DRAWER_SETTINGS,
            PreferencesRepository.BADGE_DRAWER_FEEDBACK,
            PreferencesRepository.BADGE_DRAWER_ABOUT,
            PreferencesRepository.BADGE_SETTINGS_LOCATIONS,
            PreferencesRepository.BADGE_SETTINGS_THEME,
            PreferencesRepository.BADGE_SETTINGS_STARTUP,
            PreferencesRepository.BADGE_SETTINGS_FOLDER_SECOND_LINE,
            PreferencesRepository.BADGE_SETTINGS_FILE_SECOND_LINE,
            PreferencesRepository.BADGE_SETTINGS_HOME_SECTIONS,
            PreferencesRepository.BADGE_SETTINGS_SWIPE_LEFT,
            PreferencesRepository.BADGE_SETTINGS_SWIPE_RIGHT,
            PreferencesRepository.BADGE_ABOUT_OTHER_APPS,
            PreferencesRepository.BADGE_FOLDER_CONTEXT_MENU
        )
    }
}
