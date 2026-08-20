package com.mauriciotogneri.fileexplorer.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.locationsCacheDataStore
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.source.AndroidStorageSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreFavoriteFilesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreLocationsCacheSource
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStoreRecentFilesSource
import com.mauriciotogneri.fileexplorer.data.repository.favoriteFilesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.recentFilesDataStore
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeManager
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import com.mauriciotogneri.fileexplorer.util.StartupDestinationResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val recentFilesRepository: RecentFilesRepository,
    private val favoritesRepository: FavoritesRepository,
    private val locationsRepository: LocationsRepository,
    private val storageRepository: StorageRepository
) : ViewModel() {

    private val _availableLocationTypes = MutableStateFlow<List<LocationType>>(emptyList())
    val availableLocationTypes: StateFlow<List<LocationType>> = _availableLocationTypes

    private val _isLoadingLocations = MutableStateFlow(true)
    val isLoadingLocations: StateFlow<Boolean> = _isLoadingLocations

    private val _storages = MutableStateFlow<List<StorageDevice>>(emptyList())

    init {
        viewModelScope.launch {
            _availableLocationTypes.value = locationsRepository.getAvailableLocationTypes()
            _isLoadingLocations.value = false
        }
        viewModelScope.launch {
            _storages.value = try {
                storageRepository.getStorages()
            } catch (e: Exception) {
                // Only costs the startup folder its friendly name; the row still shows the folder's
                // own name, so this must not be surfaced to the user.
                ErrorReporter.warning(e, "read_settings_storages")
                emptyList()
            }
        }
    }

    val themeMode: StateFlow<ThemeMode> = ThemeManager.themeMode

    val enabledLocations: Flow<Set<LocationType>> = preferencesRepository.enabledLocations

    val recentFilesEnabled: Flow<Boolean> = preferencesRepository.recentFilesEnabled

    val showHidden: Flow<Boolean> = preferencesRepository.showHidden

    val folderSecondLine: Flow<FolderSecondLine> = preferencesRepository.folderSecondLine

    val fileSecondLine: Flow<FileSecondLine> = preferencesRepository.fileSecondLine

    val swipeLeftAction: Flow<SwipeAction> = preferencesRepository.swipeLeftAction

    val swipeRightAction: Flow<SwipeAction> = preferencesRepository.swipeRightAction

    val homeSectionOrder: Flow<List<HomeSection>> = preferencesRepository.homeSectionOrder

    val startupScreen: Flow<StartupScreen> = preferencesRepository.startupScreen

    /**
     * The name to show for the chosen startup folder, or null when the app starts on the home
     * screen. Resolved against the mounted storage devices so a folder that is itself a storage root
     * reads as "Internal storage" rather than as its last path segment, "0".
     */
    val startupFolderName: StateFlow<String?> = combine(
        preferencesRepository.startupFolderPath,
        _storages
    ) { path, storages ->
        path?.let { StartupDestinationResolver.label(it, storages) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val showLocationsBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_LOCATIONS)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showStartupBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_STARTUP)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showThemeBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_THEME)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showFolderSecondLineBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_FOLDER_SECOND_LINE)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showFileSecondLineBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_FILE_SECOND_LINE)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showSwipeLeftBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_SWIPE_LEFT)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showSwipeRightBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_SWIPE_RIGHT)
        .map { dismissed -> !dismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showHomeSectionsBadge: StateFlow<Boolean> = preferencesRepository
        .isBadgeDismissed(PreferencesRepository.BADGE_SETTINGS_HOME_SECTIONS)
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

    fun dismissStartupBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_SETTINGS_STARTUP)
        }
    }

    fun dismissThemeBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_SETTINGS_THEME)
        }
    }

    fun dismissFolderSecondLineBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_SETTINGS_FOLDER_SECOND_LINE)
        }
    }

    fun dismissFileSecondLineBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_SETTINGS_FILE_SECOND_LINE)
        }
    }

    fun dismissSwipeLeftBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_SETTINGS_SWIPE_LEFT)
        }
    }

    fun dismissSwipeRightBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_SETTINGS_SWIPE_RIGHT)
        }
    }

    fun dismissHomeSectionsBadge() {
        viewModelScope.launch {
            preferencesRepository.dismissBadge(PreferencesRepository.BADGE_SETTINGS_HOME_SECTIONS)
        }
    }

    /**
     * Analytics records the arrangement itself, which names only the four sections the app defines.
     * Nothing about what any of them contains is reported.
     */
    fun setHomeSectionOrder(order: List<HomeSection>) {
        val value = order.joinToString(",") { it.name.lowercase() }
        AnalyticsTracker.trackSettingsHomeSectionOrder(value)
        AnalyticsTracker.setUserProperty("home_section_order", value)
        viewModelScope.launch {
            preferencesRepository.setHomeSectionOrder(order)
        }
    }

    fun setFolderSecondLine(secondLine: FolderSecondLine) {
        val value = secondLine.name.lowercase()
        AnalyticsTracker.trackSettingsFolderSecondLine(value)
        AnalyticsTracker.setUserProperty("folder_second_line", value)
        viewModelScope.launch {
            preferencesRepository.setFolderSecondLine(secondLine)
        }
    }

    fun setFileSecondLine(secondLine: FileSecondLine) {
        val value = secondLine.name.lowercase()
        AnalyticsTracker.trackSettingsFileSecondLine(value)
        AnalyticsTracker.setUserProperty("file_second_line", value)
        viewModelScope.launch {
            preferencesRepository.setFileSecondLine(secondLine)
        }
    }

    /**
     * Analytics records which action the direction was pointed at, which names only what the app
     * itself offers. Nothing about the rows it will run against is reported.
     */
    fun setSwipeLeftAction(action: SwipeAction) {
        val value = action.name.lowercase()
        AnalyticsTracker.trackSettingsSwipeLeft(value)
        AnalyticsTracker.setUserProperty("swipe_left_action", value)
        viewModelScope.launch {
            preferencesRepository.setSwipeLeftAction(action)
        }
    }

    fun setSwipeRightAction(action: SwipeAction) {
        val value = action.name.lowercase()
        AnalyticsTracker.trackSettingsSwipeRight(value)
        AnalyticsTracker.setUserProperty("swipe_right_action", value)
        viewModelScope.launch {
            preferencesRepository.setSwipeRightAction(action)
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

    fun setStartupHome() {
        setStartupScreen(StartupScreen.HOME, null)
    }

    fun setStartupFolder(folderPath: String) {
        setStartupScreen(StartupScreen.FOLDER, folderPath)
    }

    /**
     * Screen and folder are stored in one write, so the folder is only ever committed together with
     * the screen that uses it: cancelling the folder picker leaves the previous choice untouched
     * rather than saving a folder screen with nothing to open.
     *
     * Analytics records which of the two the user chose, never [folderPath].
     */
    private fun setStartupScreen(screen: StartupScreen, folderPath: String?) {
        val value = screen.name.lowercase()
        AnalyticsTracker.trackSettingsStartupScreen(value)
        AnalyticsTracker.setUserProperty("startup_screen", value)
        viewModelScope.launch {
            preferencesRepository.setStartupScreen(screen, folderPath)
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
                ),
                storageRepository = StorageRepository(AndroidStorageSource(context))
            ) as T
        }
    }
}
