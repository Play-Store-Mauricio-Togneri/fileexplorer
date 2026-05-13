package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.Location
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.locationsCacheDataStore
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.recentFilesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val recentFiles: List<RecentFile> = emptyList(),
    val locations: List<Location> = emptyList(),
    val storages: List<StorageDevice> = emptyList()
)

class HomeViewModel(
    private val recentFilesRepository: RecentFilesRepository,
    private val locationsRepository: LocationsRepository,
    private val storageRepository: StorageRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val showMenuBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_MENU_DRAWER)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showSettingsBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_DRAWER_SETTINGS)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showFeedbackBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_DRAWER_FEEDBACK)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showAboutBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_DRAWER_ABOUT)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var hasLoadedOnce = false

    init {
        loadData()
    }

    fun dismissMenuBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_MENU_DRAWER)
        }
    }

    fun dismissSettingsBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_DRAWER_SETTINGS)
        }
    }

    fun dismissFeedbackBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_DRAWER_FEEDBACK)
        }
    }

    fun dismissAboutBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_DRAWER_ABOUT)
        }
    }

    fun loadData() {
        viewModelScope.launch {
            if (!hasLoadedOnce) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            locationsRepository.refreshSizeCache()
            val recentFiles = recentFilesRepository.getRecentFiles()
            val locations = locationsRepository.getLocations()
            val storages = storageRepository.getStorages()

            _uiState.value = HomeUiState(
                isLoading = false,
                recentFiles = recentFiles,
                locations = locations,
                storages = storages
            )
            hasLoadedOnce = true
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            locationsRepository.refreshSizeCache()

            val recentFiles = recentFilesRepository.getRecentFiles()
            val locations = locationsRepository.getLocations()
            val storages = storageRepository.getStorages()

            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                recentFiles = recentFiles,
                locations = locations,
                storages = storages
            )
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val preferencesRepository = PreferencesRepository(context.preferencesDataStore)
            return HomeViewModel(
                recentFilesRepository = RecentFilesRepository(context.recentFilesDataStore),
                locationsRepository = LocationsRepository(context.locationsCacheDataStore, preferencesRepository),
                storageRepository = StorageRepository(context),
                preferencesRepository = preferencesRepository
            ) as T
        }
    }
}
