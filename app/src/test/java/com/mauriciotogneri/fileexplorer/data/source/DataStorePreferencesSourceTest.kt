package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.SortMode
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
    fun `isBadgeDismissed falls back to false when the store fails`() = runTest {
        val source = DataStorePreferencesSource(FakeThrowingDataStore())
        assertFalse(source.isBadgeDismissed("any_badge").first())
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
        source.dismissBadge("any_badge")
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
}
