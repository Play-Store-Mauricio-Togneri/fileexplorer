package com.mauriciotogneri.fileexplorer.testutil

import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.source.FavoriteFilesSource
import com.mauriciotogneri.fileexplorer.data.source.PreferencesSource
import com.mauriciotogneri.fileexplorer.data.source.RecentFilesSource
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Preferences held in memory, for tests that render the real `HomeScreen` and tap its drawer.
 *
 * The app's own preferences store is shared by the whole suite and outlives every test process, so
 * a drawer tap made against it dismisses that row's badge on the device for good: any later test
 * asserting a badge then passes or fails by test order. Badge dismissals are recorded here and die
 * with the test. Every other setting answers the app's default and ignores writes.
 */
internal class InMemoryPreferencesSource : PreferencesSource {

    private val dismissed = MutableStateFlow(emptyMap<String, Int>())

    override fun dismissedBadgeVersion(badgeId: String): Flow<Int> = dismissed.map { versions ->
        versions[badgeId] ?: PreferencesSource.BADGE_NEVER_DISMISSED
    }

    override suspend fun dismissBadge(badgeId: String, version: Int) {
        dismissed.value += (badgeId to version)
    }

    override val showHidden: Flow<Boolean> = flowOf(false)
    override suspend fun setShowHidden(show: Boolean) = Unit

    override val themeMode: Flow<ThemeMode> = flowOf(ThemeMode.SYSTEM)
    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    override val sortMode: Flow<SortMode> = flowOf(SortMode.NAME_ASC)
    override suspend fun setSortMode(mode: SortMode) = Unit

    override val enabledLocations: Flow<Set<LocationType>> = flowOf(LocationType.entries.toSet())
    override suspend fun setEnabledLocations(enabledLocations: Set<LocationType>) = Unit

    override val recentFilesEnabled: Flow<Boolean> = flowOf(true)
    override suspend fun setRecentFilesEnabled(enabled: Boolean) = Unit

    override val homeSectionOrder: Flow<List<HomeSection>> = flowOf(HomeSection.DEFAULT_ORDER)
    override suspend fun setHomeSectionOrder(order: List<HomeSection>) = Unit

    override val folderSecondLine: Flow<FolderSecondLine> = flowOf(FolderSecondLine.ITEM_COUNT)
    override suspend fun setFolderSecondLine(secondLine: FolderSecondLine) = Unit

    override val fileSecondLine: Flow<FileSecondLine> = flowOf(FileSecondLine.SIZE)
    override suspend fun setFileSecondLine(secondLine: FileSecondLine) = Unit

    override val swipeLeftAction: Flow<SwipeAction> = flowOf(SwipeAction.RENAME)
    override suspend fun setSwipeLeftAction(action: SwipeAction) = Unit

    override val swipeRightAction: Flow<SwipeAction> = flowOf(SwipeAction.DELETE)
    override suspend fun setSwipeRightAction(action: SwipeAction) = Unit

    override val startupScreen: Flow<StartupScreen> = flowOf(StartupScreen.HOME)
    override val startupFolderPath: Flow<String?> = flowOf(null)
    override suspend fun setStartupScreen(screen: StartupScreen, folderPath: String?) = Unit
}

/**
 * Recents exactly as the test seeded them. Seeding through the app's own store would both depend on
 * the device's existing recents and outlive the test.
 */
internal class SeededRecentFiles(private val files: List<RecentFile> = emptyList()) : RecentFilesSource {
    override val recentFilesFlow: Flow<List<RecentFile>> = flowOf(files)

    override suspend fun getRecentFiles(): List<RecentFile> = files

    override suspend fun updateRecentFiles(transform: (List<RecentFile>) -> List<RecentFile>) = Unit

    override suspend fun clearRecentFiles() = Unit
}

/** Favorites exactly as the test seeded them. See [SeededRecentFiles]. */
internal class SeededFavorites(private val favorites: List<Favorite> = emptyList()) : FavoriteFilesSource {
    override val favoritesFlow: Flow<List<Favorite>> = flowOf(favorites)

    override suspend fun getFavorites(): List<Favorite> = favorites

    override suspend fun updateFavorites(transform: (List<Favorite>) -> List<Favorite>) = Unit

    override suspend fun clearFavorites() = Unit
}
