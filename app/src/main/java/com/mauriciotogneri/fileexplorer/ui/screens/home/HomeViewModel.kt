package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.Location
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val storageRepository: StorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var hasLoadedOnce = false

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            if (!hasLoadedOnce) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

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
            return HomeViewModel(
                recentFilesRepository = RecentFilesRepository(context),
                locationsRepository = LocationsRepository(context),
                storageRepository = StorageRepository(context)
            ) as T
        }
    }
}
