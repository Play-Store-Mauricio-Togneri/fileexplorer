package com.mauriciotogneri.fileexplorer.ui.screens.storage

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.util.AndroidPermissionChecker
import com.mauriciotogneri.fileexplorer.util.PermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class StorageUiState(
    val isLoading: Boolean = true,
    val storages: List<StorageDevice> = emptyList(),
    val hasPermission: Boolean = false,
    val error: String? = null
)

class StorageViewModel(
    private val storageRepository: StorageRepository,
    private val permissionChecker: PermissionChecker
) : ViewModel() {

    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    init {
        checkPermissionAndLoad()
    }

    fun checkPermissionAndLoad() {
        val hasPermission = permissionChecker.hasStoragePermission()
        _state.update { it.copy(hasPermission = hasPermission) }

        if (hasPermission) {
            loadStorages()
        } else {
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(hasPermission = granted) }
        if (granted) {
            loadStorages()
        }
    }

    private fun loadStorages() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val storages = storageRepository.getStorages()
                _state.update {
                    it.copy(
                        isLoading = false,
                        storages = storages,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    /**
     * Returns true if there's exactly one storage device, indicating
     * the UI should auto-navigate to it.
     */
    fun shouldAutoNavigate(): Boolean {
        val currentState = _state.value
        return currentState.hasPermission &&
                !currentState.isLoading &&
                currentState.storages.size == 1
    }

    /**
     * Get the single storage path for auto-navigation.
     */
    fun getSingleStoragePath(): String? {
        return if (shouldAutoNavigate()) {
            _state.value.storages.firstOrNull()?.path
        } else {
            null
        }
    }

    class Factory(
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = StorageRepository(context)
            val permissionChecker = AndroidPermissionChecker(context)
            return StorageViewModel(repository, permissionChecker) as T
        }
    }
}
