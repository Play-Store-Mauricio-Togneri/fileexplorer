package com.mauriciotogneri.fileexplorer.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.source.PreferencesSource
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class PreferencesRepository(private val source: PreferencesSource) {

    val showHidden: Flow<Boolean> = source.showHidden

    suspend fun setShowHidden(show: Boolean) {
        source.setShowHidden(show)
    }

    val themeMode: Flow<ThemeMode> = source.themeMode

    /**
     * Blocking read for Application.onCreate() initialization only.
     * Do not call from UI thread after app startup.
     */
    fun getInitialThemeMode(): ThemeMode = runBlocking(Dispatchers.IO) {
        try {
            themeMode.first()
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        source.setThemeMode(mode)
    }

    val sortMode: Flow<SortMode> = source.sortMode

    /**
     * Blocking read for Application.onCreate() initialization only.
     * Do not call from UI thread after app startup.
     */
    fun getInitialSortMode(): SortMode = runBlocking(Dispatchers.IO) {
        try {
            sortMode.first()
        } catch (_: Exception) {
            SortMode.NAME_ASC
        }
    }

    suspend fun setSortMode(mode: SortMode) {
        source.setSortMode(mode)
    }

    val enabledLocations: Flow<Set<LocationType>> = source.enabledLocations

    suspend fun setEnabledLocations(enabledLocations: Set<LocationType>) {
        source.setEnabledLocations(enabledLocations)
    }

    val recentFilesEnabled: Flow<Boolean> = source.recentFilesEnabled

    suspend fun setRecentFilesEnabled(enabled: Boolean) {
        source.setRecentFilesEnabled(enabled)
    }

    val homeSectionOrder: Flow<List<HomeSection>> = source.homeSectionOrder

    suspend fun setHomeSectionOrder(order: List<HomeSection>) {
        source.setHomeSectionOrder(order)
    }

    val folderSecondLine: Flow<FolderSecondLine> = source.folderSecondLine

    suspend fun setFolderSecondLine(secondLine: FolderSecondLine) {
        source.setFolderSecondLine(secondLine)
    }

    val fileSecondLine: Flow<FileSecondLine> = source.fileSecondLine

    suspend fun setFileSecondLine(secondLine: FileSecondLine) {
        source.setFileSecondLine(secondLine)
    }

    val swipeLeftAction: Flow<SwipeAction> = source.swipeLeftAction

    suspend fun setSwipeLeftAction(action: SwipeAction) {
        source.setSwipeLeftAction(action)
    }

    val swipeRightAction: Flow<SwipeAction> = source.swipeRightAction

    suspend fun setSwipeRightAction(action: SwipeAction) {
        source.setSwipeRightAction(action)
    }

    val startupScreen: Flow<StartupScreen> = source.startupScreen

    val startupFolderPath: Flow<String?> = source.startupFolderPath

    suspend fun setStartupScreen(screen: StartupScreen, folderPath: String?) {
        source.setStartupScreen(screen, folderPath)
    }

    /**
     * Blocking read for MainActivity.onCreate() startup routing only.
     * Do not call from UI thread after app startup.
     *
     * Returns the configured startup folder path, or null when the app should open on the home
     * screen. Screen and path are written together, so [StartupScreen.FOLDER] without a path is not
     * representable; the null path still falls back to the home screen rather than trusting a store
     * left half-written by a failed edit.
     */
    fun getInitialStartupFolderPath(): String? = runBlocking(Dispatchers.IO) {
        try {
            if (startupScreen.first() == StartupScreen.FOLDER) startupFolderPath.first() else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Whether the user has already dismissed [badgeId] as it is now. Having dismissed an earlier
     * version does not count, which is how a release points existing users at something new.
     */
    fun isBadgeDismissed(badgeId: String): Flow<Boolean> = source.dismissedBadgeVersion(badgeId)
        .map { dismissedVersion -> dismissedVersion >= badgeVersion(badgeId) }

    suspend fun dismissBadge(badgeId: String) {
        source.dismissBadge(badgeId, badgeVersion(badgeId))
    }

    companion object {
        const val BADGE_MENU_DRAWER = "menu_drawer"
        const val BADGE_DRAWER_SETTINGS = "drawer_settings"
        const val BADGE_DRAWER_FEEDBACK = "drawer_feedback"
        const val BADGE_DRAWER_ABOUT = "drawer_about"
        const val BADGE_ABOUT_OTHER_APPS = "about_other_apps"
        const val BADGE_FOLDER_CONTEXT_MENU = "folder_context_menu"

        /**
         * The badges a release has shown again, and the version it moved them to. A badge shows
         * until the user dismisses it at the version listed here, so raising an entry brings the
         * badge back for everyone who dismissed the previous one.
         *
         * Two rules when a release adds something worth pointing at:
         *
         * - Raise the whole trail leading to it, not just the destination. A dot on a drawer row
         *   is unreachable if the hamburger that opens the drawer no longer has one of its own.
         * - Leave every other badge alone. A dot that leads to nothing the user has not already
         *   seen teaches them to ignore dots, and the next release then has nothing to point with.
         *
         * A badge is listed only once it has been raised; the rest are at
         * [PreferencesSource.BADGE_FIRST_VERSION].
         */
        internal val BADGE_VERSIONS = mapOf(
            // Raised by the release that added the swipe-action settings, back when the settings
            // rows carried badges of their own: the hamburger dot opens the drawer and the
            // drawer's dot opens Settings. Versions 2 to 4 were the startup screen, the two
            // second-line settings and the home section order, each pointed at the same way.
            //
            // The settings screen no longer marks individual rows, so this trail now ends at the
            // screen itself.
            BADGE_MENU_DRAWER to 5,
            BADGE_DRAWER_SETTINGS to 5
        )

        private fun badgeVersion(badgeId: String): Int =
            BADGE_VERSIONS[badgeId] ?: PreferencesSource.BADGE_FIRST_VERSION
    }
}
