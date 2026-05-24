package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.source.FakePreferencesSource
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesRepositoryTest {

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
}
