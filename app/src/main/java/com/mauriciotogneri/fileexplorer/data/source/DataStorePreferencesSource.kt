package com.mauriciotogneri.fileexplorer.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStorePreferencesSource(
    private val dataStore: DataStore<Preferences>
) : PreferencesSource {

    override val showHidden: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_HIDDEN_KEY] ?: false
    }.catchIO("read_show_hidden", false)

    override suspend fun setShowHidden(show: Boolean) {
        dataStore.editSafely("write_show_hidden") { preferences ->
            preferences[SHOW_HIDDEN_KEY] = show
        }
    }

    override val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        val themeName = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
        ThemeMode.entries.find { it.name == themeName } ?: ThemeMode.SYSTEM
    }.catchIO("read_theme_mode", ThemeMode.SYSTEM)

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.editSafely("write_theme_mode") { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    override val sortMode: Flow<SortMode> = dataStore.data.map { preferences ->
        val sortName = preferences[SORT_MODE_KEY] ?: SortMode.NAME_ASC.name
        SortMode.entries.find { it.name == sortName } ?: SortMode.NAME_ASC
    }.catchIO("read_sort_mode", SortMode.NAME_ASC)

    override suspend fun setSortMode(mode: SortMode) {
        dataStore.editSafely("write_sort_mode") { preferences ->
            preferences[SORT_MODE_KEY] = mode.name
        }
    }

    override val enabledLocations: Flow<Set<LocationType>> = dataStore.data.map { preferences ->
        preferences[ENABLED_LOCATIONS_KEY]?.mapNotNull { name ->
            LocationType.entries.find { it.name == name }
        }?.toSet() ?: LocationType.entries.toSet()
    }.catchIO("read_enabled_locations", LocationType.entries.toSet())

    override suspend fun setEnabledLocations(enabledLocations: Set<LocationType>) {
        dataStore.editSafely("write_enabled_locations") { preferences ->
            preferences[ENABLED_LOCATIONS_KEY] = enabledLocations.map { it.name }.toSet()
        }
    }

    override val recentFilesEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[RECENT_FILES_ENABLED_KEY] ?: true
    }.catchIO("read_recent_files_enabled", true)

    override suspend fun setRecentFilesEnabled(enabled: Boolean) {
        dataStore.editSafely("write_recent_files_enabled") { preferences ->
            preferences[RECENT_FILES_ENABLED_KEY] = enabled
        }
    }

    // Stored as one delimited string rather than a preference set, which has no order to read back.
    // Every value the store can hold is accepted: absent, empty, unknown names and duplicates all
    // reconcile to a full order, so the read has no failure mode of its own beyond IO.
    override val homeSectionOrder: Flow<List<HomeSection>> = dataStore.data.map { preferences ->
        HomeSection.reconcile(preferences[HOME_SECTION_ORDER_KEY]?.split(SECTION_SEPARATOR).orEmpty())
    }.catchIO("read_home_section_order", HomeSection.DEFAULT_ORDER)

    override suspend fun setHomeSectionOrder(order: List<HomeSection>) {
        dataStore.editSafely("write_home_section_order") { preferences ->
            preferences[HOME_SECTION_ORDER_KEY] = order.joinToString(SECTION_SEPARATOR) { it.name }
        }
    }

    override val folderSecondLine: Flow<FolderSecondLine> = dataStore.data.map { preferences ->
        val name = preferences[FOLDER_SECOND_LINE_KEY] ?: DEFAULT_FOLDER_SECOND_LINE.name
        FolderSecondLine.entries.find { it.name == name } ?: DEFAULT_FOLDER_SECOND_LINE
    }.catchIO("read_folder_second_line", DEFAULT_FOLDER_SECOND_LINE)

    override suspend fun setFolderSecondLine(secondLine: FolderSecondLine) {
        dataStore.editSafely("write_folder_second_line") { preferences ->
            preferences[FOLDER_SECOND_LINE_KEY] = secondLine.name
        }
    }

    override val fileSecondLine: Flow<FileSecondLine> = dataStore.data.map { preferences ->
        val name = preferences[FILE_SECOND_LINE_KEY] ?: DEFAULT_FILE_SECOND_LINE.name
        FileSecondLine.entries.find { it.name == name } ?: DEFAULT_FILE_SECOND_LINE
    }.catchIO("read_file_second_line", DEFAULT_FILE_SECOND_LINE)

    override suspend fun setFileSecondLine(secondLine: FileSecondLine) {
        dataStore.editSafely("write_file_second_line") { preferences ->
            preferences[FILE_SECOND_LINE_KEY] = secondLine.name
        }
    }

    override val swipeLeftAction: Flow<SwipeAction> = dataStore.data.map { preferences ->
        val name = preferences[SWIPE_LEFT_ACTION_KEY] ?: DEFAULT_SWIPE_LEFT_ACTION.name
        SwipeAction.entries.find { it.name == name } ?: DEFAULT_SWIPE_LEFT_ACTION
    }.catchIO("read_swipe_left_action", DEFAULT_SWIPE_LEFT_ACTION)

    override suspend fun setSwipeLeftAction(action: SwipeAction) {
        dataStore.editSafely("write_swipe_left_action") { preferences ->
            preferences[SWIPE_LEFT_ACTION_KEY] = action.name
        }
    }

    override val swipeRightAction: Flow<SwipeAction> = dataStore.data.map { preferences ->
        val name = preferences[SWIPE_RIGHT_ACTION_KEY] ?: DEFAULT_SWIPE_RIGHT_ACTION.name
        SwipeAction.entries.find { it.name == name } ?: DEFAULT_SWIPE_RIGHT_ACTION
    }.catchIO("read_swipe_right_action", DEFAULT_SWIPE_RIGHT_ACTION)

    override suspend fun setSwipeRightAction(action: SwipeAction) {
        dataStore.editSafely("write_swipe_right_action") { preferences ->
            preferences[SWIPE_RIGHT_ACTION_KEY] = action.name
        }
    }

    override val startupScreen: Flow<StartupScreen> = dataStore.data.map { preferences ->
        val screenName = preferences[STARTUP_SCREEN_KEY] ?: StartupScreen.HOME.name
        StartupScreen.entries.find { it.name == screenName } ?: StartupScreen.HOME
    }.catchIO("read_startup_screen", StartupScreen.HOME)

    override val startupFolderPath: Flow<String?> = dataStore.data.map { preferences ->
        preferences[STARTUP_FOLDER_PATH_KEY]
    }.catchIO("read_startup_folder_path", null)

    override suspend fun setStartupScreen(screen: StartupScreen, folderPath: String?) {
        dataStore.editSafely("write_startup_screen") { preferences ->
            preferences[STARTUP_SCREEN_KEY] = screen.name
            if (folderPath != null) {
                preferences[STARTUP_FOLDER_PATH_KEY] = folderPath
            } else {
                preferences.remove(STARTUP_FOLDER_PATH_KEY)
            }
        }
    }

    override fun dismissedBadgeVersion(badgeId: String): Flow<Int> = dataStore.data.map { preferences ->
        val entries = preferences[DISMISSED_BADGES_KEY] ?: emptySet()
        entries.mapNotNull { entry -> versionOf(entry, badgeId) }.maxOrNull()
            ?: PreferencesSource.BADGE_NEVER_DISMISSED
    }.catchIO("read_dismissed_badges", PreferencesSource.BADGE_NEVER_DISMISSED)

    override suspend fun dismissBadge(badgeId: String, version: Int) {
        dataStore.editSafely("write_dismiss_badge") { preferences ->
            val entries = preferences[DISMISSED_BADGES_KEY] ?: emptySet()
            // Replaces this badge's own entry rather than adding to it, so the set holds one entry
            // per badge however many releases have shown it again.
            val otherBadges = entries.filter { versionOf(it, badgeId) == null }.toSet()
            preferences[DISMISSED_BADGES_KEY] = otherBadges + "$badgeId$VERSION_SEPARATOR$version"
        }
    }

    /**
     * The version an entry of [DISMISSED_BADGES_KEY] records for [badgeId], or null when it belongs
     * to another badge.
     *
     * Entries are `id:version`, except those written before badges were versioned, which are a bare
     * `id`. A version that will not parse is read the same way as a bare id, which errs towards
     * showing the badge again rather than hiding it forever.
     */
    private fun versionOf(entry: String, badgeId: String): Int? {
        val prefix = "$badgeId$VERSION_SEPARATOR"
        return when {
            entry == badgeId -> PreferencesSource.BADGE_FIRST_VERSION
            entry.startsWith(prefix) ->
                entry.removePrefix(prefix).toIntOrNull() ?: PreferencesSource.BADGE_FIRST_VERSION
            else -> null
        }
    }

    companion object {
        private val SHOW_HIDDEN_KEY = booleanPreferencesKey("show_hidden")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val SORT_MODE_KEY = stringPreferencesKey("sort_mode")
        private val ENABLED_LOCATIONS_KEY = stringSetPreferencesKey("enabled_locations")
        private val RECENT_FILES_ENABLED_KEY = booleanPreferencesKey("recent_files_enabled")
        private val DISMISSED_BADGES_KEY = stringSetPreferencesKey("dismissed_badges")
        private val STARTUP_SCREEN_KEY = stringPreferencesKey("startup_screen")
        private val STARTUP_FOLDER_PATH_KEY = stringPreferencesKey("startup_folder_path")
        private val FOLDER_SECOND_LINE_KEY = stringPreferencesKey("folder_second_line")
        private val FILE_SECOND_LINE_KEY = stringPreferencesKey("file_second_line")
        private val HOME_SECTION_ORDER_KEY = stringPreferencesKey("home_section_order")
        private val SWIPE_LEFT_ACTION_KEY = stringPreferencesKey("swipe_left_action")
        private val SWIPE_RIGHT_ACTION_KEY = stringPreferencesKey("swipe_right_action")

        /** Separates a dismissed badge's id from the version it was dismissed at. */
        private const val VERSION_SEPARATOR = ":"

        /** Separates the section names of a stored home section order. */
        private const val SECTION_SEPARATOR = ","

        /** What rows showed before the setting existed, so updating changes nothing on its own. */
        private val DEFAULT_FOLDER_SECOND_LINE = FolderSecondLine.ITEM_COUNT
        private val DEFAULT_FILE_SECOND_LINE = FileSecondLine.SIZE

        /** What each direction did before the setting existed, for the same reason. */
        private val DEFAULT_SWIPE_LEFT_ACTION = SwipeAction.RENAME
        private val DEFAULT_SWIPE_RIGHT_ACTION = SwipeAction.DELETE
    }
}
