package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.Location
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.locationsCacheDataStore
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.recentFilesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreLocationsCacheSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreRecentFilesSource
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import com.mauriciotogneri.fileexplorer.util.UncompressEvent
import com.mauriciotogneri.fileexplorer.util.UncompressHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val recentFiles: List<RecentFile> = emptyList(),
    val locations: List<Location> = emptyList(),
    val storages: List<StorageDevice> = emptyList(),
    val selectedRecentFile: RecentFile? = null,
    val recentFileMode: String = "icon",
    val recentFileToDelete: RecentFile? = null,
    val showDeleteError: Boolean = false,
    val itemToUncompress: FileItem? = null,
    val uncompressEntryCount: Int = 0,
    val isPasswordProtected: Boolean = false,
    val uncompressProgress: UncompressProgress? = null,
    val pendingApkInstall: FileItem? = null
)

@Immutable
sealed class HomeUiEvent {
    @Immutable
    data class ShowToast(val messageResId: Int) : HomeUiEvent()
}

class HomeViewModel(
    application: Application,
    private val recentFilesRepository: RecentFilesRepository,
    private val locationsRepository: LocationsRepository,
    private val storageRepository: StorageRepository,
    private val preferencesRepository: PreferencesRepository,
    private val fileRepository: FileRepository
) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeUiEvent>()
    val events: SharedFlow<HomeUiEvent> = _events.asSharedFlow()

    private var currentUncompressTarget: String = ""

    private val uncompressHandler = UncompressHandler(
        context = context,
        scope = viewModelScope,
        fileRepository = fileRepository,
        getTargetDirectory = { currentUncompressTarget },
        getAllowedRoots = { storageRepository.getStorages().map { it.path } }
    )

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
        observeUncompressHandler()
    }

    private fun observeUncompressHandler() {
        viewModelScope.launch {
            uncompressHandler.state.collect { uncompressState ->
                _uiState.update {
                    it.copy(
                        itemToUncompress = uncompressState.itemToUncompress,
                        uncompressEntryCount = uncompressState.entryCount,
                        isPasswordProtected = uncompressState.isPasswordProtected,
                        uncompressProgress = uncompressState.progress
                    )
                }
            }
        }
        viewModelScope.launch {
            uncompressHandler.events.collect { event ->
                when (event) {
                    is UncompressEvent.ShowToast -> {
                        _events.emit(HomeUiEvent.ShowToast(event.messageResId))
                    }
                    is UncompressEvent.ExtractionComplete -> {
                        // Recent files don't need to refresh
                    }
                }
            }
        }
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

            val (recentFiles, locations, storages) = withContext(Dispatchers.IO) {
                locationsRepository.refreshSizeCache()
                val recentFilesEnabled = preferencesRepository.recentFilesEnabled.first()
                Triple(
                    if (recentFilesEnabled) recentFilesRepository.getRecentFiles() else emptyList(),
                    locationsRepository.getLocations(),
                    storageRepository.getStorages()
                )
            }

            _uiState.value = HomeUiState(
                isLoading = false,
                recentFiles = recentFiles,
                locations = locations,
                storages = storages
            )
            hasLoadedOnce = true
        }
    }

    fun showRecentFileActions(recentFile: RecentFile, mode: String) {
        viewModelScope.launch {
            val fileExists = withContext(Dispatchers.IO) {
                File(recentFile.path).exists()
            }
            if (!fileExists) {
                recentFilesRepository.removeRecentFile(recentFile.path)
                _uiState.update { state ->
                    state.copy(recentFiles = state.recentFiles.filter { it.path != recentFile.path })
                }
                _events.emit(HomeUiEvent.ShowToast(R.string.recent_file_not_found))
            } else {
                _uiState.update { it.copy(selectedRecentFile = recentFile, recentFileMode = mode) }
            }
        }
    }

    fun dismissRecentFileActions() {
        _uiState.update { it.copy(selectedRecentFile = null) }
    }

    fun removeFromRecents(recentFile: RecentFile) {
        viewModelScope.launch {
            recentFilesRepository.removeRecentFile(recentFile.path)
            AnalyticsTracker.trackRecentFileRemoved()
            _uiState.update { state ->
                state.copy(
                    recentFiles = state.recentFiles.filter { it.path != recentFile.path },
                    selectedRecentFile = null
                )
            }
        }
    }

    fun showDeleteConfirmation(recentFile: RecentFile) {
        _uiState.update { it.copy(recentFileToDelete = recentFile, selectedRecentFile = null) }
    }

    fun dismissDeleteConfirmation() {
        _uiState.update { it.copy(recentFileToDelete = null) }
    }

    fun confirmDeleteRecentFile() {
        val recentFile = _uiState.value.recentFileToDelete ?: return
        viewModelScope.launch {
            val file = File(recentFile.path)
            val fileItem = withContext(Dispatchers.IO) {
                FileItem(
                    path = recentFile.path,
                    name = recentFile.name,
                    isDirectory = false,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    createdTime = file.lastModified(),
                    mimeType = recentFile.mimeType
                )
            }
            val deleted = fileRepository.delete(listOf(fileItem))
            if (deleted) {
                MediaStoreUtil.notifyDeleted(context, listOf(recentFile.path))
                recentFilesRepository.removeRecentFile(recentFile.path)
                AnalyticsTracker.trackDeleteCompleted(1, "home_recent")
                _uiState.update { state ->
                    state.copy(
                        recentFiles = state.recentFiles.filter { it.path != recentFile.path },
                        recentFileToDelete = null
                    )
                }
            } else {
                AnalyticsTracker.trackOperationFailed("delete", "unknown")
                _uiState.update { it.copy(recentFileToDelete = null, showDeleteError = true) }
            }
        }
    }

    fun dismissDeleteError() {
        _uiState.update { it.copy(showDeleteError = false) }
    }

    fun showUncompressDialog(file: FileItem) {
        currentUncompressTarget = file.parentPath
        uncompressHandler.showUncompressDialog(file)
    }

    fun dismissUncompressDialog() {
        uncompressHandler.dismissUncompressDialog()
    }

    fun confirmUncompress(password: String? = null) {
        uncompressHandler.confirmUncompress(password)
    }

    fun cancelUncompression() {
        uncompressHandler.cancelUncompression()
    }

    fun setPendingApkInstall(file: FileItem?) {
        _uiState.update { it.copy(pendingApkInstall = file) }
    }

    fun clearPendingApkInstall() {
        _uiState.update { it.copy(pendingApkInstall = null) }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val preferencesRepository = PreferencesRepository(DataStorePreferencesSource(application.preferencesDataStore))
            return HomeViewModel(
                application = application,
                recentFilesRepository = RecentFilesRepository(DataStoreRecentFilesSource(application.recentFilesDataStore)),
                locationsRepository = LocationsRepository(DataStoreLocationsCacheSource(application.locationsCacheDataStore), preferencesRepository),
                storageRepository = StorageRepository(AndroidStorageSource(application)),
                preferencesRepository = preferencesRepository,
                fileRepository = FileRepository()
            ) as T
        }
    }
}
