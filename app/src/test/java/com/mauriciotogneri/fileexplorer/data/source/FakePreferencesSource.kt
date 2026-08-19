package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
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
    initialDismissedBadges: Set<String> = emptySet(),
    initialStartupScreen: StartupScreen = StartupScreen.HOME,
    initialStartupFolderPath: String? = null,
    initialFolderSecondLine: FolderSecondLine = FolderSecondLine.ITEM_COUNT,
    initialFileSecondLine: FileSecondLine = FileSecondLine.SIZE,
    initialHomeSectionOrder: List<HomeSection> = HomeSection.DEFAULT_ORDER
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

    private val _homeSectionOrder = MutableStateFlow(initialHomeSectionOrder)
    override val homeSectionOrder: Flow<List<HomeSection>> = _homeSectionOrder

    override suspend fun setHomeSectionOrder(order: List<HomeSection>) {
        _homeSectionOrder.value = order
    }

    private val _folderSecondLine = MutableStateFlow(initialFolderSecondLine)
    override val folderSecondLine: Flow<FolderSecondLine> = _folderSecondLine

    override suspend fun setFolderSecondLine(secondLine: FolderSecondLine) {
        _folderSecondLine.value = secondLine
    }

    private val _fileSecondLine = MutableStateFlow(initialFileSecondLine)
    override val fileSecondLine: Flow<FileSecondLine> = _fileSecondLine

    override suspend fun setFileSecondLine(secondLine: FileSecondLine) {
        _fileSecondLine.value = secondLine
    }

    private val _startupScreen = MutableStateFlow(initialStartupScreen)
    override val startupScreen: Flow<StartupScreen> = _startupScreen

    private val _startupFolderPath = MutableStateFlow(initialStartupFolderPath)
    override val startupFolderPath: Flow<String?> = _startupFolderPath

    override suspend fun setStartupScreen(screen: StartupScreen, folderPath: String?) {
        _startupScreen.value = screen
        _startupFolderPath.value = folderPath
    }

    /**
     * Badge id to the version it was dismissed at. [initialDismissedBadges] seeds
     * [PreferencesSource.BADGE_FIRST_VERSION], which is what a dismissal stored before badges were
     * versioned counts as.
     */
    private val _dismissedBadges = MutableStateFlow(
        initialDismissedBadges.associateWith { PreferencesSource.BADGE_FIRST_VERSION }
    )

    override fun dismissedBadgeVersion(badgeId: String): Flow<Int> = _dismissedBadges.map { dismissed ->
        dismissed[badgeId] ?: PreferencesSource.BADGE_NEVER_DISMISSED
    }

    override suspend fun dismissBadge(badgeId: String, version: Int) {
        _dismissedBadges.value += (badgeId to version)
    }
}
