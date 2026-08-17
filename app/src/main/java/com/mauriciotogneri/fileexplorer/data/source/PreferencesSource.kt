package com.mauriciotogneri.fileexplorer.data.source

import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow

interface PreferencesSource {

    val showHidden: Flow<Boolean>
    suspend fun setShowHidden(show: Boolean)

    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    val sortMode: Flow<SortMode>
    suspend fun setSortMode(mode: SortMode)

    val enabledLocations: Flow<Set<LocationType>>
    suspend fun setEnabledLocations(enabledLocations: Set<LocationType>)

    val recentFilesEnabled: Flow<Boolean>
    suspend fun setRecentFilesEnabled(enabled: Boolean)

    val startupScreen: Flow<StartupScreen>
    val startupFolderPath: Flow<String?>

    /**
     * Stores the startup screen and its folder together, so [StartupScreen.FOLDER] without a path is
     * never persisted. [folderPath] must be null for [StartupScreen.HOME], which clears any
     * previously chosen folder.
     */
    suspend fun setStartupScreen(screen: StartupScreen, folderPath: String?)

    /**
     * The version of [badgeId] the user has already dismissed, or [BADGE_NEVER_DISMISSED] when they
     * never have. Which version a badge is currently at is the repository's decision, not the
     * store's: this only records what was dismissed.
     */
    fun dismissedBadgeVersion(badgeId: String): Flow<Int>

    /** Records that the user dismissed [badgeId] as it is at [version]. */
    suspend fun dismissBadge(badgeId: String, version: Int)

    companion object {
        /** Returned for a badge the user has never dismissed. Below every real version. */
        const val BADGE_NEVER_DISMISSED = 0

        /**
         * Where badge versions start. Also what a dismissal stored before badges were versioned
         * counts as, so bumping a badge past it shows it again to users who updated.
         */
        const val BADGE_FIRST_VERSION = 1
    }
}
