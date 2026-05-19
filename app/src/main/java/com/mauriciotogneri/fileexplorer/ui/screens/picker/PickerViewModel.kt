package com.mauriciotogneri.fileexplorer.ui.screens.picker

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.OperationMode
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PickerViewModel(
    private val context: Context,
    private val fileRepository: FileRepository,
    private val storageRepository: StorageRepository,
    private val sourceItems: List<FileItem>,
    private val operationMode: OperationMode,
    private val sortMode: SortMode,
    private val showHidden: Boolean
) : ViewModel() {

    private val _currentPath = MutableStateFlow<String?>(null)
    val currentPath: StateFlow<String?> = _currentPath.asStateFlow()

    private val _folders = MutableStateFlow<List<FileItem>>(emptyList())
    val folders: StateFlow<List<FileItem>> = _folders.asStateFlow()

    private val _storages = MutableStateFlow<List<StorageDevice>>(emptyList())
    val storages: StateFlow<List<StorageDevice>> = _storages.asStateFlow()

    val showStorageSelector: StateFlow<Boolean> = combine(_storages, _currentPath) { storages, path ->
        storages.size > 1 && path == null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    val isValidDestination: StateFlow<Boolean> = combine(_validationError, _currentPath) { error, path ->
        error == null && path != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _showCreateFolderDialog = MutableStateFlow(false)
    val showCreateFolderDialog: StateFlow<Boolean> = _showCreateFolderDialog.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadStorages()
    }

    private fun loadStorages() {
        viewModelScope.launch {
            _isLoading.value = true
            val storageList = storageRepository.getStorages()
            _storages.value = storageList

            if (storageList.size == 1) {
                navigateToPath(storageList.first().path)
            } else {
                _isLoading.value = false
            }
        }
    }

    fun navigateToStorage(storage: StorageDevice) {
        navigateToPath(storage.path)
    }

    fun navigateToFolder(folder: FileItem) {
        navigateToPath(folder.path)
    }

    fun navigateToPath(path: String) {
        _currentPath.value = path
        loadFolders(path)
        validateDestination(path)
    }

    private fun loadFolders(path: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val writableFolders = withContext(Dispatchers.IO) {
                val allItems = fileRepository.listFiles(path, showHidden, sortMode)
                // canWrite() is a best-effort check; scoped storage may still reject writes
                allItems.filter { item ->
                    item.isDirectory && File(item.path).canWrite()
                }
            }
            _folders.value = writableFolders
            _isLoading.value = false
        }
    }

    fun navigateUp(): Boolean {
        val current = _currentPath.value ?: return false

        val storageRoots = _storages.value.map { it.path }
        if (current in storageRoots) {
            return if (_storages.value.size > 1) {
                _currentPath.value = null
                _folders.value = emptyList()
                _validationError.value = null
                true
            } else {
                false
            }
        }

        val parent = File(current).parent
        if (parent != null) {
            navigateToPath(parent)
            return true
        }

        return false
    }

    private fun validateDestination(targetPath: String) {
        if (sourceItems.isEmpty()) return

        // Check if target is the parent of any source item (same-folder check)
        val isTargetASourceParent = sourceItems.any { source ->
            File(source.path).parent == targetPath
        }
        if (isTargetASourceParent) {
            _validationError.value = if (operationMode == OperationMode.MOVE) {
                context.getString(R.string.validation_same_folder_move)
            } else {
                context.getString(R.string.validation_same_folder_copy)
            }
            return
        }

        // Check for recursive operation (moving/copying into itself)
        for (sourceItem in sourceItems) {
            if (targetPath.startsWith(sourceItem.path + "/")) {
                _validationError.value = if (operationMode == OperationMode.MOVE) {
                    context.getString(R.string.validation_recursive_move)
                } else {
                    context.getString(R.string.validation_recursive_copy)
                }
                return
            }
        }

        _validationError.value = null
    }

    fun showCreateFolderDialog() {
        _showCreateFolderDialog.value = true
    }

    fun dismissCreateFolderDialog() {
        _showCreateFolderDialog.value = false
    }

    fun createFolder(name: String) {
        val currentPath = _currentPath.value ?: return
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                fileRepository.createFolder(currentPath, name)
            }
            dismissCreateFolderDialog()
            if (success) {
                val newFolderPath = File(currentPath, name).absolutePath
                navigateToPath(newFolderPath)
            }
        }
    }

    fun getCurrentPath(): String? = _currentPath.value

    fun getExistingNames(): Set<String> = _folders.value.map { it.name }.toSet()

    fun getCurrentStorageRoot(): StorageDevice? {
        val path = _currentPath.value ?: return null
        return _storages.value.find { path.startsWith(it.path) }
    }

    class Factory(
        private val context: Context,
        private val fileRepository: FileRepository,
        private val storageRepository: StorageRepository,
        private val sourceItems: List<FileItem>,
        private val operationMode: OperationMode,
        private val sortMode: SortMode,
        private val showHidden: Boolean
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PickerViewModel(
                context.applicationContext,
                fileRepository,
                storageRepository,
                sourceItems,
                operationMode,
                sortMode,
                showHidden
            ) as T
        }
    }
}
