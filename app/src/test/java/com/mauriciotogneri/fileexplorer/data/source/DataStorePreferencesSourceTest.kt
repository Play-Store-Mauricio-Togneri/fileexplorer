package com.mauriciotogneri.fileexplorer.data.source

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
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
    fun `folderSecondLine falls back to the item count when the store fails`() = runTest {
        val source = DataStorePreferencesSource(FakeThrowingDataStore())
        assertEquals(FolderSecondLine.ITEM_COUNT, source.folderSecondLine.first())
    }

    @Test
    fun `fileSecondLine falls back to the size when the store fails`() = runTest {
        val source = DataStorePreferencesSource(FakeThrowingDataStore())
        assertEquals(FileSecondLine.SIZE, source.fileSecondLine.first())
    }

    @Test
    fun `an unset second line reads as what rows showed before the setting existed`() = runTest {
        val source = DataStorePreferencesSource(FakeInMemoryDataStore())

        assertEquals(FolderSecondLine.ITEM_COUNT, source.folderSecondLine.first())
        assertEquals(FileSecondLine.SIZE, source.fileSecondLine.first())
    }

    @Test
    fun `a stored second line is read back`() = runTest {
        val source = DataStorePreferencesSource(FakeInMemoryDataStore())

        source.setFolderSecondLine(FolderSecondLine.LAST_MODIFIED)
        source.setFileSecondLine(FileSecondLine.NONE)

        assertEquals(FolderSecondLine.LAST_MODIFIED, source.folderSecondLine.first())
        assertEquals(FileSecondLine.NONE, source.fileSecondLine.first())
    }

    /**
     * Enum names are stored, so a downgrade — or a value written by a later version — can leave a
     * name this build does not know. Falling back beats propagating a null the row would have to
     * handle.
     */
    @Test
    fun `an unknown stored second line falls back to the default`() = runTest {
        val dataStore = FakeInMemoryDataStore(
            preferencesOf(
                stringPreferencesKey("folder_second_line") to "TOTAL_SIZE",
                stringPreferencesKey("file_second_line") to "DIMENSIONS"
            )
        )
        val source = DataStorePreferencesSource(dataStore)

        assertEquals(FolderSecondLine.ITEM_COUNT, source.folderSecondLine.first())
        assertEquals(FileSecondLine.SIZE, source.fileSecondLine.first())
    }

    @Test
    fun `homeSectionOrder falls back to the default order when the store fails`() = runTest {
        val source = DataStorePreferencesSource(FakeThrowingDataStore())
        assertEquals(HomeSection.DEFAULT_ORDER, source.homeSectionOrder.first())
    }

    @Test
    fun `an unset home section order reads as the arrangement the home screen shipped with`() = runTest {
        val source = DataStorePreferencesSource(FakeInMemoryDataStore())

        assertEquals(HomeSection.DEFAULT_ORDER, source.homeSectionOrder.first())
    }

    @Test
    fun `a stored home section order is read back in order`() = runTest {
        val source = DataStorePreferencesSource(FakeInMemoryDataStore())
        val order = listOf(
            HomeSection.STORAGE,
            HomeSection.LOCATIONS,
            HomeSection.RECENT,
            HomeSection.FAVORITES
        )

        source.setHomeSectionOrder(order)

        assertEquals(order, source.homeSectionOrder.first())
    }

    /**
     * The on-disk shape, asserted against a literal rather than a round trip: reading back what this
     * same class wrote would pass just as well if both halves changed together, and the value has to
     * survive an update by whatever the installed version left behind.
     */
    @Test
    fun `a home section order is stored as its section names in order`() = runTest {
        val dataStore = FakeInMemoryDataStore()
        val source = DataStorePreferencesSource(dataStore)

        source.setHomeSectionOrder(listOf(HomeSection.STORAGE, HomeSection.RECENT))

        assertEquals(
            "STORAGE,RECENT",
            dataStore.data.first()[stringPreferencesKey("home_section_order")]
        )
    }

    /**
     * A section added by a later release leaves an order that names fewer sections than this build
     * has. It is appended rather than resetting the arrangement, so an update never rearranges a
     * home screen the user set up by hand.
     */
    @Test
    fun `a stored order missing a section keeps its arrangement and gains the rest`() = runTest {
        val dataStore = FakeInMemoryDataStore(
            preferencesOf(stringPreferencesKey("home_section_order") to "STORAGE,RECENT")
        )
        val source = DataStorePreferencesSource(dataStore)

        assertEquals(
            listOf(HomeSection.STORAGE, HomeSection.RECENT, HomeSection.FAVORITES, HomeSection.LOCATIONS),
            source.homeSectionOrder.first()
        )
    }

    @Test
    fun `an unknown name in a stored order is dropped rather than failing the read`() = runTest {
        val dataStore = FakeInMemoryDataStore(
            preferencesOf(stringPreferencesKey("home_section_order") to "CLOUD,STORAGE")
        )
        val source = DataStorePreferencesSource(dataStore)

        assertEquals(
            listOf(HomeSection.STORAGE, HomeSection.RECENT, HomeSection.FAVORITES, HomeSection.LOCATIONS),
            source.homeSectionOrder.first()
        )
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
