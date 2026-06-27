package com.mauriciotogneri.fileexplorer.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.locationsCacheDataStore
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStoreFavoriteFilesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreLocationsCacheSource
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreRecentFilesSource
import com.mauriciotogneri.fileexplorer.data.repository.favoriteFilesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.recentFilesDataStore
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val recentFilesRepository: RecentFilesRepository,
    private val favoritesRepository: FavoritesRepository,
    private val locationsRepository: LocationsRepository
) : ViewModel() {

    private val _availableLocationTypes = MutableStateFlow<List<LocationType>>(emptyList())
    val availableLocationTypes: StateFlow<List<LocationType>> = _availableLocationTypes

    private val _isLoadingLocations = MutableStateFlow(true)
    val isLoadingLocations: StateFlow<Boolean> = _isLoadingLocations

    init {
        viewModelScope.launch {
            _availableLocationTypes.value = locationsRepository.getAvailableLocationTypes()
            _isLoadingLocations.value = false
        }
    }

    val themeMode: StateFlow<ThemeMode> = ThemeManager.themeMode

    val enabledLocations: Flow<Set<LocationType>> = preferencesRepository.enabledLocations

    val recentFilesEnabled: Flow<Boolean> = preferencesRepository.recentFilesEnabled

    val showHidden: Flow<Boolean> = preferencesRepository.showHidden

    val showLocationsBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_LOCATIONS)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showThemeBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_THEME)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // flowOn(IO) so the repositories' File.exists() filter (downstream of the sources' own
    // flowOn) runs off the main thread rather than on the collector (viewModelScope = Main).
    val hasRecentFiles: StateFlow<Boolean> = recentFilesRepository.recentFilesFlow
        .map { files -> files.isNotEmpty() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hasFavorites: StateFlow<Boolean> = favoritesRepository.favoritesFlow
        .map { favorites -> favorites.isNotEmpty() }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun dismissLocationsBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_SETTINGS_LOCATIONS)
        }
    }

    fun dismissThemeBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_SETTINGS_THEME)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        ThemeManager.setTheme(mode)
        AnalyticsTracker.trackSettingsTheme(mode.name.lowercase())
        AnalyticsTracker.setUserProperty("theme_preference", mode.name.lowercase())
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setEnabledLocations(enabledLocations: Set<LocationType>) {
        AnalyticsTracker.trackSettingsLocationsChanged(enabledLocations.map { it.name.lowercase() }.toSet())
        AnalyticsTracker.setUserProperty("locations_count", enabledLocations.size.toString())
        viewModelScope.launch {
            preferencesRepository.setEnabledLocations(enabledLocations)
        }
    }

    fun setRecentFilesEnabled(enabled: Boolean) {
        AnalyticsTracker.trackSettingsRecentFilesTracking(enabled)
        AnalyticsTracker.setUserProperty("recent_files_enabled", enabled.toString())
        viewModelScope.launch {
            preferencesRepository.setRecentFilesEnabled(enabled)
        }
    }

    fun setShowHidden(enabled: Boolean) {
        AnalyticsTracker.trackSettingsShowHidden(enabled)
        AnalyticsTracker.setUserProperty("show_hidden_files", enabled.toString())
        viewModelScope.launch {
            preferencesRepository.setShowHidden(enabled)
        }
    }

    fun clearRecentFiles() {
        AnalyticsTracker.trackSettingsRecentFilesClear()
        viewModelScope.launch {
            recentFilesRepository.clearRecentFiles()
        }
    }

    fun clearFavorites() {
        AnalyticsTracker.trackSettingsFavoritesClear()
        viewModelScope.launch {
            favoritesRepository.clearFavorites()
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val preferencesRepository = PreferencesRepository(DataStorePreferencesSource(context.preferencesDataStore))
            return SettingsViewModel(
                preferencesRepository = preferencesRepository,
                recentFilesRepository = RecentFilesRepository(DataStoreRecentFilesSource(context.recentFilesDataStore)),
                favoritesRepository = FavoritesRepository(DataStoreFavoriteFilesSource(context.favoriteFilesDataStore)),
                locationsRepository = LocationsRepository(
                    cacheSource = DataStoreLocationsCacheSource(context.locationsCacheDataStore),
                    preferencesRepository = preferencesRepository
                )
            ) as T
        }
    }
}
