package com.mauriciotogneri.fileexplorer

import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.source.FakePreferencesSource
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reading the saved values is covered by PreferencesRepositoryTest; this pins that startup actually
 * hands them to [ThemeManager] and [SortManager]. The saved values differ from both holders'
 * defaults, so a startup that skips either step fails here.
 */
class ApplySavedPreferencesTest {

    @After
    fun tearDown() {
        ThemeManager.setTheme(ThemeMode.SYSTEM)
        SortManager.setSortMode(SortMode.NAME_ASC)
    }

    @Test
    fun `the saved theme and sort order reach their holders`() {
        val repository = PreferencesRepository(
            FakePreferencesSource(
                initialThemeMode = ThemeMode.DARK,
                initialSortMode = SortMode.SIZE_DESC
            )
        )

        applySavedPreferences(repository)

        assertEquals(ThemeMode.DARK, ThemeManager.currentTheme)
        assertEquals(SortMode.SIZE_DESC, SortManager.sortMode.value)
    }
}
