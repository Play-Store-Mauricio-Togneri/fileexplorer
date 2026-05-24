package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.source.PreferencesSource
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class PreferencesRepository(private val source: PreferencesSource) {

    val showHidden: Flow<Boolean> = source.showHidden

    suspend fun setShowHidden(show: Boolean) {
        source.setShowHidden(show)
    }

    val themeMode: Flow<ThemeMode> = source.themeMode

    /**
     * Blocking read for Application.onCreate() initialization only.
     * Do not call from UI thread after app startup.
     */
    fun getInitialThemeMode(): ThemeMode = runBlocking(Dispatchers.IO) {
        themeMode.first()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        source.setThemeMode(mode)
    }

    val sortMode: Flow<SortMode> = source.sortMode

    /**
     * Blocking read for Application.onCreate() initialization only.
     * Do not call from UI thread after app startup.
     */
    fun getInitialSortMode(): SortMode = runBlocking(Dispatchers.IO) {
        sortMode.first()
    }

    suspend fun setSortMode(mode: SortMode) {
        source.setSortMode(mode)
    }

    val enabledLocations: Flow<Set<LocationType>> = source.enabledLocations

    suspend fun setEnabledLocations(enabledLocations: Set<LocationType>) {
        source.setEnabledLocations(enabledLocations)
    }

    val recentFilesEnabled: Flow<Boolean> = source.recentFilesEnabled

    suspend fun setRecentFilesEnabled(enabled: Boolean) {
        source.setRecentFilesEnabled(enabled)
    }

    fun isBadgeDismissed(badgeId: String): Flow<Boolean> = source.isBadgeDismissed(badgeId)

    suspend fun dismissBadge(badgeId: String) {
        source.dismissBadge(badgeId)
    }

    companion object {
        const val BADGE_MENU_DRAWER = "menu_drawer"
        const val BADGE_DRAWER_SETTINGS = "drawer_settings"
        const val BADGE_DRAWER_FEEDBACK = "drawer_feedback"
        const val BADGE_DRAWER_ABOUT = "drawer_about"
        const val BADGE_SETTINGS_LOCATIONS = "settings_locations"
        const val BADGE_SETTINGS_THEME = "settings_theme"
        const val BADGE_ABOUT_OTHER_APPS = "about_other_apps"
        const val BADGE_FOLDER_CONTEXT_MENU = "folder_context_menu"
    }
}
