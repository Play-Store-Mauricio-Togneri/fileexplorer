package com.mauriciotogneri.fileexplorer.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.recentFilesDataStore
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val recentFilesRepository: RecentFilesRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = ThemeManager.themeMode

    val enabledLocations: Flow<Set<LocationType>> = preferencesRepository.enabledLocations

    val recentFilesEnabled: Flow<Boolean> = preferencesRepository.recentFilesEnabled

    val showLocationsBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_LOCATIONS)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showThemeBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_THEME)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showRecentFilesBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_RECENT_FILES)
        .map { dismissed -> !dismissed }
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

    fun dismissRecentFilesBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_SETTINGS_RECENT_FILES)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        ThemeManager.setTheme(mode)
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setEnabledLocations(enabledLocations: Set<LocationType>) {
        viewModelScope.launch {
            preferencesRepository.setEnabledLocations(enabledLocations)
        }
    }

    fun setRecentFilesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setRecentFilesEnabled(enabled)
        }
    }

    fun clearRecentFiles() {
        viewModelScope.launch {
            recentFilesRepository.clearRecentFiles()
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                preferencesRepository = PreferencesRepository(context.preferencesDataStore),
                recentFilesRepository = RecentFilesRepository(context.recentFilesDataStore)
            ) as T
        }
    }
}
