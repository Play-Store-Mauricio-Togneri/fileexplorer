package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow

interface PreferencesSource {

    val showHidden: Flow<Boolean>
    suspend fun setShowHidden(show: Boolean)

    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    val sortMode: Flow<SortMode>
    suspend fun setSortMode(mode: SortMode)

    val enabledLocations: Flow<Set<LocationType>>
    suspend fun setEnabledLocations(enabledLocations: Set<LocationType>)

    val recentFilesEnabled: Flow<Boolean>
    suspend fun setRecentFilesEnabled(enabled: Boolean)

    fun isBadgeDismissed(badgeId: String): Flow<Boolean>
    suspend fun dismissBadge(badgeId: String)
}
