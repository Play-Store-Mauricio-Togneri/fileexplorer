package com.mauriciotogneri.fileexplorer.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStorePreferencesSource(
    private val dataStore: DataStore<Preferences>
) : PreferencesSource {

    override val showHidden: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_HIDDEN_KEY] ?: false
    }

    override suspend fun setShowHidden(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_HIDDEN_KEY] = show
        }
    }

    override val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        val themeName = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
        ThemeMode.entries.find { it.name == themeName } ?: ThemeMode.SYSTEM
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    override val sortMode: Flow<SortMode> = dataStore.data.map { preferences ->
        val sortName = preferences[SORT_MODE_KEY] ?: SortMode.NAME_ASC.name
        SortMode.entries.find { it.name == sortName } ?: SortMode.NAME_ASC
    }

    override suspend fun setSortMode(mode: SortMode) {
        dataStore.edit { preferences ->
            preferences[SORT_MODE_KEY] = mode.name
        }
    }

    override val enabledLocations: Flow<Set<LocationType>> = dataStore.data.map { preferences ->
        preferences[ENABLED_LOCATIONS_KEY]?.mapNotNull { name ->
            LocationType.entries.find { it.name == name }
        }?.toSet() ?: LocationType.entries.toSet()
    }

    override suspend fun setEnabledLocations(enabledLocations: Set<LocationType>) {
        dataStore.edit { preferences ->
            preferences[ENABLED_LOCATIONS_KEY] = enabledLocations.map { it.name }.toSet()
        }
    }

    override val recentFilesEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[RECENT_FILES_ENABLED_KEY] ?: true
    }

    override suspend fun setRecentFilesEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[RECENT_FILES_ENABLED_KEY] = enabled
        }
    }

    override fun isBadgeDismissed(badgeId: String): Flow<Boolean> = dataStore.data.map { preferences ->
        val dismissedBadges = preferences[DISMISSED_BADGES_KEY] ?: emptySet()
        dismissedBadges.contains(badgeId)
    }

    override suspend fun dismissBadge(badgeId: String) {
        dataStore.edit { preferences ->
            val current = preferences[DISMISSED_BADGES_KEY] ?: emptySet()
            preferences[DISMISSED_BADGES_KEY] = current + badgeId
        }
    }

    companion object {
        private val SHOW_HIDDEN_KEY = booleanPreferencesKey("show_hidden")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val SORT_MODE_KEY = stringPreferencesKey("sort_mode")
        private val ENABLED_LOCATIONS_KEY = stringSetPreferencesKey("enabled_locations")
        private val RECENT_FILES_ENABLED_KEY = booleanPreferencesKey("recent_files_enabled")
        private val DISMISSED_BADGES_KEY = stringSetPreferencesKey("dismissed_badges")
    }
}
