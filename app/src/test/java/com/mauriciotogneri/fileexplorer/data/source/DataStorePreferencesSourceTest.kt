package com.mauriciotogneri.fileexplorer.data.source

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataStorePreferencesSourceTest {

    @Before
    fun setUp() {
        mockkObject(ErrorReporter)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkObject(ErrorReporter)
    }

    @Test
    fun `showHidden falls back to false when the store fails`() = runTest {
        val source = DataStorePreferencesSource(FakeThrowingDataStore())
        assertFalse(source.showHidden.first())
    }

    @Test
    fun `themeMode falls back to SYSTEM when the store fails`() = runTest {
        val source = DataStorePreferencesSource(FakeThrowingDataStore())
        assertEquals(ThemeMode.SYSTEM, source.themeMode.first())
    }

    @Test
    fun `sortMode falls back to NAME_ASC when the store fails`() = runTest {
        val source = DataStorePreferencesSource(FakeThrowingDataStore())
        assertEquals(SortMode.NAME_ASC, source.sortMode.first())
    }

    @Test
    fun `enabledLocations falls back to all locations when the store fails`() = runTest {
        val source = DataStorePreferencesSource(FakeThrowingDataStore())
        assertEquals(LocationType.entries.toSet(), source.enabledLocations.first())
    }

    @Test
    fun `recentFilesEnabled falls back to true when the store fails`() = runTest {
        val source = DataStorePreferencesSource(FakeThrowingDataStore())
        assertTrue(source.recentFilesEnabled.first())
    }

    @Test
    fun `startupScreen falls back to HOME when the store fails`() = runTest {
        val source = DataStorePreferencesSource(FakeThrowingDataStore())
        assertEquals(StartupScreen.HOME, source.startupScreen.first())
    }

    @Test
    fun `startupFolderPath falls back to null when the store fails`() = runTest {
        val source = DataStorePreferencesSource(FakeThrowingDataStore())
        assertNull(source.startupFolderPath.first())
    }

    @Test
    fun `dismissedBadgeVersion falls back to never dismissed when the store fails`() = runTest {
        val source = DataStorePreferencesSource(FakeThrowingDataStore())
        assertEquals(PreferencesSource.BADGE_NEVER_DISMISSED, source.dismissedBadgeVersion("any_badge").first())
    }

    // ==================== Dismissed badge versions ====================

    @Test
    fun `a badge that was never dismissed is at no version`() = runTest {
        val source = DataStorePreferencesSource(FakeInMemoryDataStore())

        assertEquals(PreferencesSource.BADGE_NEVER_DISMISSED, source.dismissedBadgeVersion("menu_drawer").first())
    }

    @Test
    fun `dismissing a badge records the version it was dismissed at`() = runTest {
        val source = DataStorePreferencesSource(FakeInMemoryDataStore())

        source.dismissBadge("menu_drawer", 2)

        assertEquals(2, source.dismissedBadgeVersion("menu_drawer").first())
    }

    /**
     * The upgrade path this whole encoding exists for: users who dismissed a badge before it was
     * versioned hold a bare id, and must count as being behind any version above the first — that
     * is what shows the badge again after an update.
     */
    @Test
    fun `a dismissal stored before versioning counts as the first version`() = runTest {
        val dataStore = FakeInMemoryDataStore(dismissedBadges("menu_drawer"))
        val source = DataStorePreferencesSource(dataStore)

        assertEquals(PreferencesSource.BADGE_FIRST_VERSION, source.dismissedBadgeVersion("menu_drawer").first())
    }

    @Test
    fun `a version that cannot be parsed counts as the first version`() = runTest {
        val dataStore = FakeInMemoryDataStore(dismissedBadges("menu_drawer:not_a_number"))
        val source = DataStorePreferencesSource(dataStore)

        assertEquals(PreferencesSource.BADGE_FIRST_VERSION, source.dismissedBadgeVersion("menu_drawer").first())
    }

    @Test
    fun `dismissing a badge again replaces its previous entry`() = runTest {
        val dataStore = FakeInMemoryDataStore(dismissedBadges("menu_drawer"))
        val source = DataStorePreferencesSource(dataStore)

        source.dismissBadge("menu_drawer", 2)

        assertEquals(2, source.dismissedBadgeVersion("menu_drawer").first())
        // One entry per badge, however many releases have shown it again.
        assertEquals(setOf("menu_drawer:2"), dataStore.data.first()[DISMISSED_BADGES_KEY])
    }

    @Test
    fun `dismissing a badge leaves the other badges alone`() = runTest {
        val dataStore = FakeInMemoryDataStore(dismissedBadges("drawer_about", "settings_theme:3"))
        val source = DataStorePreferencesSource(dataStore)

        source.dismissBadge("menu_drawer", 2)

        assertEquals(PreferencesSource.BADGE_FIRST_VERSION, source.dismissedBadgeVersion("drawer_about").first())
        assertEquals(3, source.dismissedBadgeVersion("settings_theme").first())
    }

    /**
     * Ids share prefixes (`settings_startup`, `settings_startup_folder` would), so the separator has
     * to be part of the match rather than the id alone.
     */
    @Test
    fun `a badge whose id is a prefix of another is read on its own`() = runTest {
        val dataStore = FakeInMemoryDataStore(dismissedBadges("settings_startup_folder:4"))
        val source = DataStorePreferencesSource(dataStore)

        assertEquals(
            PreferencesSource.BADGE_NEVER_DISMISSED,
            source.dismissedBadgeVersion("settings_startup").first()
        )
    }

    @Test
    fun `writes do not throw when the store fails`() = runTest {
        val source = DataStorePreferencesSource(FakeThrowingDataStore())
        // None of these may propagate the IOException raised by the failing store.
        source.setShowHidden(true)
        source.setThemeMode(ThemeMode.SYSTEM)
        source.setSortMode(SortMode.NAME_ASC)
        source.setEnabledLocations(setOf(LocationType.DOWNLOADS))
        source.setRecentFilesEnabled(false)
        source.setStartupScreen(StartupScreen.FOLDER, "/storage/emulated/0/Download")
        source.dismissBadge("any_badge", 1)
    }

    @Test
    fun `failure is reported to ErrorReporter`() = runTest {
        val source = DataStorePreferencesSource(FakeThrowingDataStore())
        source.showHidden.first()
        verify { ErrorReporter.warning(any(), any(), any()) }
    }

    @Test
    fun `non-IOException is not swallowed`() = runTest {
        val source = DataStorePreferencesSource(FakeThrowingDataStore { IllegalStateException("boom") })
        var thrown: Throwable? = null
        try {
            source.showHidden.first()
        } catch (e: IllegalStateException) {
            thrown = e
        }
        assertNotNull(thrown)
    }

    /**
     * Seeds the store the way a previous version of the app left it. The key name is repeated here
     * deliberately rather than exposed: it is the on-disk contract, so a rename that this notices is
     * a rename that would have stranded every installed user's dismissals.
     */
    private fun dismissedBadges(vararg entries: String): Preferences =
        preferencesOf(DISMISSED_BADGES_KEY to entries.toSet())

    private companion object {
        private val DISMISSED_BADGES_KEY = stringSetPreferencesKey("dismissed_badges")
    }
}
