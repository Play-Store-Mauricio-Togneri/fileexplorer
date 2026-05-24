package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakePreferencesSource(
    initialShowHidden: Boolean = false,
    initialThemeMode: ThemeMode = ThemeMode.SYSTEM,
    initialSortMode: SortMode = SortMode.NAME_ASC,
    initialEnabledLocations: Set<LocationType> = LocationType.entries.toSet(),
    initialRecentFilesEnabled: Boolean = true,
    initialDismissedBadges: Set<String> = emptySet()
) : PreferencesSource {

    private val _showHidden = MutableStateFlow(initialShowHidden)
    override val showHidden: Flow<Boolean> = _showHidden

    override suspend fun setShowHidden(show: Boolean) {
        _showHidden.value = show
    }

    private val _themeMode = MutableStateFlow(initialThemeMode)
    override val themeMode: Flow<ThemeMode> = _themeMode

    override suspend fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    private val _sortMode = MutableStateFlow(initialSortMode)
    override val sortMode: Flow<SortMode> = _sortMode

    override suspend fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    private val _enabledLocations = MutableStateFlow(initialEnabledLocations)
    override val enabledLocations: Flow<Set<LocationType>> = _enabledLocations

    override suspend fun setEnabledLocations(enabledLocations: Set<LocationType>) {
        _enabledLocations.value = enabledLocations
    }

    private val _recentFilesEnabled = MutableStateFlow(initialRecentFilesEnabled)
    override val recentFilesEnabled: Flow<Boolean> = _recentFilesEnabled

    override suspend fun setRecentFilesEnabled(enabled: Boolean) {
        _recentFilesEnabled.value = enabled
    }

    private val _dismissedBadges = MutableStateFlow(initialDismissedBadges)

    override fun isBadgeDismissed(badgeId: String): Flow<Boolean> = _dismissedBadges.map { badgeId in it }

    override suspend fun dismissBadge(badgeId: String) {
        _dismissedBadges.value = _dismissedBadges.value + badgeId
    }
}
