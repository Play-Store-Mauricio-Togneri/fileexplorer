package com.mauriciotogneri.fileexplorer.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.ClearFavoritesSettingItem
import com.mauriciotogneri.fileexplorer.activities.ClearRecentFilesSettingItem
import com.mauriciotogneri.fileexplorer.activities.FileSecondLineSelectionDialog
import com.mauriciotogneri.fileexplorer.activities.FileSecondLineSettingItem
import com.mauriciotogneri.fileexplorer.activities.FolderSecondLineSelectionDialog
import com.mauriciotogneri.fileexplorer.activities.FolderSecondLineSettingItem
import com.mauriciotogneri.fileexplorer.activities.LocationsSelectionDialog
import com.mauriciotogneri.fileexplorer.activities.LocationsSettingItem
import com.mauriciotogneri.fileexplorer.activities.SettingsScreen
import com.mauriciotogneri.fileexplorer.activities.ShowHiddenSettingItem
import com.mauriciotogneri.fileexplorer.activities.StartupScreenSelectionDialog
import com.mauriciotogneri.fileexplorer.activities.HomeSectionsSettingItem
import com.mauriciotogneri.fileexplorer.activities.homeSectionRowTag
import com.mauriciotogneri.fileexplorer.activities.StartupScreenSettingItem
import com.mauriciotogneri.fileexplorer.activities.ThemeSelectionDialog
import com.mauriciotogneri.fileexplorer.activities.ThemeSettingItem
import com.mauriciotogneri.fileexplorer.activities.TrackRecentFilesSettingItem
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.testutil.hasBadgeDot
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/**
 * Exercises the real setting rows and dialogs from `SettingsActivity`, which are exposed as
 * `internal` test seams.
 *
 * This file previously asserted against private `@Composable` copies declared inside the test class.
 * Those copies had already drifted: production had grown `isLoading` on [LocationsSettingItem] and
 * `showBadge` on both [LocationsSettingItem] and [ThemeSettingItem], so the loading spinner and both
 * badge dots shipped with no coverage while every test here stayed green.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    private val allLocations = listOf(
        LocationType.DOWNLOADS,
        LocationType.IMAGES,
        LocationType.VIDEOS
    )

    // ==================== Recent Files Toggle ====================

    @Test
    fun recentFilesToggle_displaysCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TrackRecentFilesSettingItem(enabled = true, onEnabledChange = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_recent_files_enabled)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.settings_recent_files_description)).assertIsDisplayed()
    }

    @Test
    fun recentFilesToggle_whenEnabled_switchIsOn() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TrackRecentFilesSettingItem(enabled = true, onEnabledChange = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun recentFilesToggle_whenDisabled_switchIsOff() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TrackRecentFilesSettingItem(enabled = false, onEnabledChange = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun recentFilesToggle_clickToDisable_triggersCallback() {
        var newValue: Boolean? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TrackRecentFilesSettingItem(enabled = true, onEnabledChange = { newValue = it })
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_recent_files_enabled)).performClick()

        assertEquals(false, newValue)
    }

    @Test
    fun recentFilesToggle_clickToEnable_triggersCallback() {
        var newValue: Boolean? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TrackRecentFilesSettingItem(enabled = false, onEnabledChange = { newValue = it })
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_recent_files_enabled)).performClick()

        assertEquals(true, newValue)
    }

    // ==================== Show Hidden Toggle ====================

    @Test
    fun showHiddenToggle_displaysCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ShowHiddenSettingItem(enabled = true, onEnabledChange = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.show_hidden_items)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.settings_show_hidden_description)).assertIsDisplayed()
    }

    @Test
    fun showHiddenToggle_whenEnabled_switchIsOn() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ShowHiddenSettingItem(enabled = true, onEnabledChange = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(isToggleable()).assertIsOn()
    }

    @Test
    fun showHiddenToggle_whenDisabled_switchIsOff() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ShowHiddenSettingItem(enabled = false, onEnabledChange = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(isToggleable()).assertIsOff()
    }

    @Test
    fun showHiddenToggle_click_triggersCallback() {
        var newValue: Boolean? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ShowHiddenSettingItem(enabled = false, onEnabledChange = { newValue = it })
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.show_hidden_items)).performClick()

        assertEquals(true, newValue)
    }

    // ==================== Clear rows (enabled/disabled state) ====================

    @Test
    fun clearRecentFiles_whenNoRecentFiles_isNotClickable() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ClearRecentFilesSettingItem(enabled = false, onClick = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_recent_files_clear)).assertIsDisplayed()
        composeTestRule.onNode(
            hasText(string(R.string.settings_recent_files_clear)) and hasClickAction()
        ).assertIsNotEnabled()
    }

    @Test
    fun clearRecentFiles_whenEnabled_triggersCallback() {
        var clicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ClearRecentFilesSettingItem(enabled = true, onClick = { clicked = true })
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_recent_files_clear)).performClick()

        assertTrue("Clearing recent files should invoke the callback", clicked)
    }

    @Test
    fun clearFavorites_whenNoFavorites_isNotClickable() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ClearFavoritesSettingItem(enabled = false, onClick = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText(string(R.string.settings_favorite_files_clear)) and hasClickAction()
        ).assertIsNotEnabled()
    }

    @Test
    fun clearFavorites_whenEnabled_triggersCallback() {
        var clicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ClearFavoritesSettingItem(enabled = true, onClick = { clicked = true })
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_favorite_files_clear)).performClick()

        assertTrue("Clearing favorites should invoke the callback", clicked)
    }

    // ==================== Locations row ====================

    @Test
    fun locationsItem_displaysEnabledCount() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSettingItem(
                    enabledLocations = setOf(LocationType.DOWNLOADS, LocationType.IMAGES),
                    availableLocationTypes = allLocations,
                    isLoading = false,
                    showBadge = false,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_locations)).assertIsDisplayed()
        composeTestRule.onNodeWithText("2 / 3").assertIsDisplayed()
    }

    /**
     * The count is replaced by a spinner while the location sizes are still being computed. The
     * previous test copy had no `isLoading` parameter, so this branch was never rendered.
     */
    @Test
    fun locationsItem_whileLoading_hidesCountForSpinner() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSettingItem(
                    enabledLocations = setOf(LocationType.DOWNLOADS, LocationType.IMAGES),
                    availableLocationTypes = allLocations,
                    isLoading = true,
                    showBadge = false,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_locations)).assertIsDisplayed()
        composeTestRule.onNodeWithText("2 / 3").assertDoesNotExist()
    }

    @Test
    fun locationsItem_countOnlyIncludesAvailableTypes() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSettingItem(
                    // AUDIO is enabled but not available on this device: it must not be counted.
                    enabledLocations = setOf(LocationType.DOWNLOADS, LocationType.AUDIO),
                    availableLocationTypes = allLocations,
                    isLoading = false,
                    showBadge = false,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 / 3").assertIsDisplayed()
    }

    @Test
    fun locationsItem_whenBadgeRequested_showsBadgeDot() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSettingItem(
                    enabledLocations = setOf(LocationType.DOWNLOADS),
                    availableLocationTypes = allLocations,
                    isLoading = false,
                    showBadge = true,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasBadgeDot(), useUnmergedTree = true).assertExists()
    }

    @Test
    fun locationsItem_withoutBadge_showsNoBadgeDot() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSettingItem(
                    enabledLocations = setOf(LocationType.DOWNLOADS),
                    availableLocationTypes = allLocations,
                    isLoading = false,
                    showBadge = false,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasBadgeDot(), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun locationsItem_clickOpensDialog() {
        var dialogOpened = false

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSettingItem(
                    enabledLocations = setOf(LocationType.DOWNLOADS),
                    availableLocationTypes = allLocations,
                    isLoading = false,
                    showBadge = false,
                    onClick = { dialogOpened = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_locations)).performClick()

        assertTrue("Clicking locations should open the dialog", dialogOpened)
    }

    // ==================== Locations dialog ====================

    @Test
    fun locationsDialog_displaysAllAvailableLocations() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = allLocations.toSet(),
                    availableLocationTypes = allLocations,
                    onSave = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.location_downloads)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.location_images)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.location_videos)).assertIsDisplayed()
    }

    @Test
    fun locationsDialog_unselectLocation_andSave() {
        var savedLocations: Set<LocationType>? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = allLocations.toSet(),
                    availableLocationTypes = allLocations,
                    onSave = { savedLocations = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.location_downloads)).performClick()
        composeTestRule.onNodeWithText(string(R.string.dialog_save)).performClick()

        assertEquals(
            "Should save without Downloads",
            setOf(LocationType.IMAGES, LocationType.VIDEOS),
            savedLocations
        )
    }

    @Test
    fun locationsDialog_selectMultipleLocations_andSave() {
        var savedLocations: Set<LocationType>? = null
        val available = allLocations + LocationType.AUDIO

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = setOf(LocationType.DOWNLOADS),
                    availableLocationTypes = available,
                    onSave = { savedLocations = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.location_images)).performClick()
        composeTestRule.onNodeWithText(string(R.string.location_videos)).performClick()
        composeTestRule.onNodeWithText(string(R.string.dialog_save)).performClick()

        assertEquals(
            "Should save with Downloads, Images, and Videos",
            setOf(LocationType.DOWNLOADS, LocationType.IMAGES, LocationType.VIDEOS),
            savedLocations
        )
    }

    @Test
    fun locationsDialog_cancel_doesNotSave() {
        var saveCalled = false
        var dismissCalled = false

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = allLocations.toSet(),
                    availableLocationTypes = allLocations,
                    onSave = { saveCalled = true },
                    onDismiss = { dismissCalled = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.location_downloads)).performClick()
        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).performClick()

        assertFalse("Save should not be called on cancel", saveCalled)
        assertTrue("Dismiss should be called on cancel", dismissCalled)
    }

    @Test
    fun locationsDialog_unselectAllLocations_andSave() {
        var savedLocations: Set<LocationType>? = null
        val available = listOf(LocationType.DOWNLOADS, LocationType.IMAGES)

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = available.toSet(),
                    availableLocationTypes = available,
                    onSave = { savedLocations = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.location_downloads)).performClick()
        composeTestRule.onNodeWithText(string(R.string.location_images)).performClick()
        composeTestRule.onNodeWithText(string(R.string.dialog_save)).performClick()

        assertTrue("Should save empty set", savedLocations?.isEmpty() == true)
    }

    // ==================== Theme row ====================

    @Test
    fun themeItem_displaysCurrentTheme_light() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSettingItem(currentTheme = ThemeMode.LIGHT, showBadge = false, onClick = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_theme)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.theme_light)).assertIsDisplayed()
    }

    @Test
    fun themeItem_displaysCurrentTheme_dark() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSettingItem(currentTheme = ThemeMode.DARK, showBadge = false, onClick = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.theme_dark)).assertIsDisplayed()
    }

    @Test
    fun themeItem_displaysCurrentTheme_system() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSettingItem(currentTheme = ThemeMode.SYSTEM, showBadge = false, onClick = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.theme_system)).assertIsDisplayed()
    }

    @Test
    fun themeItem_whenBadgeRequested_showsBadgeDot() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSettingItem(currentTheme = ThemeMode.LIGHT, showBadge = true, onClick = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasBadgeDot(), useUnmergedTree = true).assertExists()
    }

    @Test
    fun themeItem_withoutBadge_showsNoBadgeDot() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSettingItem(currentTheme = ThemeMode.LIGHT, showBadge = false, onClick = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasBadgeDot(), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun themeItem_clickOpensDialog() {
        var dialogOpened = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSettingItem(
                    currentTheme = ThemeMode.LIGHT,
                    showBadge = false,
                    onClick = { dialogOpened = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_theme)).performClick()

        assertTrue("Clicking theme should open the dialog", dialogOpened)
    }

    // ==================== Theme dialog ====================

    @Test
    fun themeDialog_displaysAllThemeOptions() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSelectionDialog(
                    currentTheme = ThemeMode.LIGHT,
                    onThemeSelected = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.theme_light)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.theme_dark)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.theme_system)).assertIsDisplayed()
    }

    @Test
    fun themeDialog_selectDark_triggersCallback() {
        var selectedTheme: ThemeMode? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSelectionDialog(
                    currentTheme = ThemeMode.LIGHT,
                    onThemeSelected = { selectedTheme = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.theme_dark)).performClick()

        assertEquals("Should select dark theme", ThemeMode.DARK, selectedTheme)
    }

    @Test
    fun themeDialog_selectLight_triggersCallback() {
        var selectedTheme: ThemeMode? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSelectionDialog(
                    currentTheme = ThemeMode.DARK,
                    onThemeSelected = { selectedTheme = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.theme_light)).performClick()

        assertEquals("Should select light theme", ThemeMode.LIGHT, selectedTheme)
    }

    @Test
    fun themeDialog_selectSystem_triggersCallback() {
        var selectedTheme: ThemeMode? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSelectionDialog(
                    currentTheme = ThemeMode.LIGHT,
                    onThemeSelected = { selectedTheme = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.theme_system)).performClick()

        assertEquals("Should select system theme", ThemeMode.SYSTEM, selectedTheme)
    }

    @Test
    fun themeDialog_cancel_dismissesDialog() {
        var dismissCalled = false
        var themeCalled = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSelectionDialog(
                    currentTheme = ThemeMode.LIGHT,
                    onThemeSelected = { themeCalled = true },
                    onDismiss = { dismissCalled = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).performClick()

        assertTrue("Dismiss should be called on cancel", dismissCalled)
        assertFalse("Theme selection should not be called on cancel", themeCalled)
    }

    @Test
    fun themeDialog_currentThemeIsSelected() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSelectionDialog(
                    currentTheme = ThemeMode.DARK,
                    onThemeSelected = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasText(string(R.string.theme_dark)) and isSelectable()).assertIsSelected()
    }

    // ==================== Home sections ====================

    @Test
    fun homeSectionsItem_summarisesTheArrangement() {
        composeTestRule.setContent {
            FileExplorerTheme {
                HomeSectionsSettingItem(
                    order = listOf(HomeSection.STORAGE, HomeSection.RECENT),
                    showBadge = false,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_home_sections)).assertIsDisplayed()
        composeTestRule
            .onNodeWithText("${string(R.string.section_storage)}, ${string(R.string.section_recent)}")
            .assertIsDisplayed()
    }

    @Test
    fun homeSectionsItem_summaryFollowsTheOrder() {
        composeTestRule.setContent {
            FileExplorerTheme {
                HomeSectionsSettingItem(
                    order = listOf(HomeSection.RECENT, HomeSection.STORAGE),
                    showBadge = false,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("${string(R.string.section_recent)}, ${string(R.string.section_storage)}")
            .assertIsDisplayed()
    }

    @Test
    fun homeSectionsItem_withBadge_showsBadgeDot() {
        composeTestRule.setContent {
            FileExplorerTheme {
                HomeSectionsSettingItem(
                    order = HomeSection.DEFAULT_ORDER,
                    showBadge = true,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasBadgeDot(), useUnmergedTree = true).assertExists()
    }

    @Test
    fun homeSectionsItem_withoutBadge_showsNoBadgeDot() {
        composeTestRule.setContent {
            FileExplorerTheme {
                HomeSectionsSettingItem(
                    order = HomeSection.DEFAULT_ORDER,
                    showBadge = false,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasBadgeDot(), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun homeSectionsItem_clickOpensDialog() {
        renderSettingsScreen(startupScreen = StartupScreen.HOME)

        composeTestRule.onNodeWithText(string(R.string.settings_home_sections))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        // The dialog's own title repeats the row's label, so the rows it lists are what tells the
        // two apart.
        composeTestRule.onNodeWithTag(homeSectionRowTag(HomeSection.RECENT)).assertIsDisplayed()
    }

    @Test
    fun homeSectionsItem_clickDismissesItsBadge() {
        var dismissed = false
        renderSettingsScreen(
            startupScreen = StartupScreen.HOME,
            showHomeSectionsBadge = true,
            onHomeSectionsBadgeDismiss = { dismissed = true }
        )

        composeTestRule.onNodeWithText(string(R.string.settings_home_sections))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        assertTrue(dismissed)
    }

    // ==================== Startup screen ====================

    @Test
    fun startupItem_home_summarisesTheHomeScreen() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StartupScreenSettingItem(
                    startupScreen = StartupScreen.HOME,
                    folderName = null,
                    showBadge = false,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_startup)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.settings_startup_home)).assertIsDisplayed()
    }

    @Test
    fun startupItem_folder_summarisesTheFolderName() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StartupScreenSettingItem(
                    startupScreen = StartupScreen.FOLDER,
                    folderName = "Download",
                    showBadge = false,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Download").assertIsDisplayed()
    }

    // A folder screen with no folder opens the home screen, so the summary has to say so.
    @Test
    fun startupItem_folderWithoutName_fallsBackToHomeSummary() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StartupScreenSettingItem(
                    startupScreen = StartupScreen.FOLDER,
                    folderName = null,
                    showBadge = false,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_startup_home)).assertIsDisplayed()
    }

    @Test
    fun startupItem_clickOpensDialog() {
        var dialogOpened = false

        composeTestRule.setContent {
            FileExplorerTheme {
                StartupScreenSettingItem(
                    startupScreen = StartupScreen.HOME,
                    folderName = null,
                    showBadge = false,
                    onClick = { dialogOpened = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_startup)).performClick()

        assertTrue("Clicking startup screen should open the dialog", dialogOpened)
    }

    @Test
    fun startupItem_whenBadgeRequested_showsBadgeDot() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StartupScreenSettingItem(
                    startupScreen = StartupScreen.HOME,
                    folderName = null,
                    showBadge = true,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasBadgeDot(), useUnmergedTree = true).assertExists()
    }

    @Test
    fun startupItem_withoutBadge_showsNoBadgeDot() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StartupScreenSettingItem(
                    startupScreen = StartupScreen.HOME,
                    folderName = null,
                    showBadge = false,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasBadgeDot(), useUnmergedTree = true).assertDoesNotExist()
    }

    // ==================== Startup screen dialog ====================

    @Test
    fun startupDialog_displaysBothOptions() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StartupScreenSelectionDialog(
                    startupScreen = StartupScreen.HOME,
                    onHomeSelected = {},
                    onFolderSelected = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_startup_home)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.settings_startup_folder)).assertIsDisplayed()
    }

    @Test
    fun startupDialog_currentOptionIsSelected() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StartupScreenSelectionDialog(
                    startupScreen = StartupScreen.FOLDER,
                    onHomeSelected = {},
                    onFolderSelected = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNode(hasText(string(R.string.settings_startup_folder)) and isSelectable())
            .assertIsSelected()
    }

    @Test
    fun startupDialog_selectHome_triggersCallback() {
        var homeSelected = false
        var folderSelected = false

        composeTestRule.setContent {
            FileExplorerTheme {
                StartupScreenSelectionDialog(
                    startupScreen = StartupScreen.FOLDER,
                    onHomeSelected = { homeSelected = true },
                    onFolderSelected = { folderSelected = true },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_startup_home)).performClick()

        assertTrue("Selecting home should call the home callback", homeSelected)
        assertFalse("Selecting home should not open the folder picker", folderSelected)
    }

    // Choosing the folder option only opens the picker; nothing is stored until it is confirmed.
    @Test
    fun startupDialog_selectFolder_triggersCallback() {
        var homeSelected = false
        var folderSelected = false

        composeTestRule.setContent {
            FileExplorerTheme {
                StartupScreenSelectionDialog(
                    startupScreen = StartupScreen.HOME,
                    onHomeSelected = { homeSelected = true },
                    onFolderSelected = { folderSelected = true },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_startup_folder)).performClick()

        assertTrue("Selecting a folder should open the picker", folderSelected)
        assertFalse("Selecting a folder should not store the home screen", homeSelected)
    }

    // ==================== Startup screen, wired through SettingsScreen ====================

    /**
     * The tests above drive [StartupScreenSettingItem] and [StartupScreenSelectionDialog] in
     * isolation, with callbacks the test supplies. That leaves the wiring between them untested:
     * [SettingsScreen] owns the `showStartupDialog` state, and a row hooked to the wrong callback,
     * or a dialog that never opens, would keep every isolated test green.
     */
    @Test
    fun settingsScreen_startupRow_opensTheDialog() {
        renderSettingsScreen(startupScreen = StartupScreen.HOME)

        composeTestRule.onNodeWithText(string(R.string.settings_startup)).performClick()

        composeTestRule.onNodeWithText(string(R.string.settings_startup_folder)).assertIsDisplayed()
    }

    /**
     * Selecting the folder option must close the dialog and hand off to the picker without storing
     * anything: the folder is only known once the picker confirms, and a startup screen saved
     * without one would silently behave as home forever.
     */
    @Test
    fun settingsScreen_choosingFolder_closesTheDialogAndAsksForAFolder() {
        var pickerRequested = false
        var homeStored = false

        renderSettingsScreen(
            startupScreen = StartupScreen.HOME,
            onStartupHomeSelected = { homeStored = true },
            onStartupFolderSelected = { pickerRequested = true }
        )

        composeTestRule.onNodeWithText(string(R.string.settings_startup)).performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_startup_folder)).performClick()

        assertTrue("Choosing a specific folder must open the folder picker", pickerRequested)
        assertFalse("Choosing a specific folder must not store the home screen", homeStored)
        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).assertDoesNotExist()
    }

    @Test
    fun settingsScreen_choosingHome_closesTheDialogAndStoresHome() {
        var pickerRequested = false
        var homeStored = false

        renderSettingsScreen(
            startupScreen = StartupScreen.FOLDER,
            startupFolderName = "Reports",
            onStartupHomeSelected = { homeStored = true },
            onStartupFolderSelected = { pickerRequested = true }
        )

        composeTestRule.onNodeWithText("Reports").performClick()
        composeTestRule.onNodeWithText(string(R.string.settings_startup_home)).performClick()

        assertTrue("Choosing the home screen must store it", homeStored)
        assertFalse("Choosing the home screen must not open the folder picker", pickerRequested)
        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).assertDoesNotExist()
    }

    /**
     * Tapping the row is what marks the startup badge seen. Without this the dot would survive the
     * visit it was meant to end on and reappear forever, with every isolated row test still green —
     * they supply their own `onClick` and never exercise the wiring.
     */
    @Test
    fun settingsScreen_startupRow_dismissesItsBadge() {
        var dismissed = false

        renderSettingsScreen(
            startupScreen = StartupScreen.HOME,
            showStartupBadge = true,
            onStartupBadgeDismiss = { dismissed = true }
        )

        composeTestRule.onNodeWithText(string(R.string.settings_startup)).performClick()

        assertTrue("Tapping the startup row should dismiss its badge", dismissed)
    }

    @Test
    fun settingsScreen_otherRows_doNotDismissTheStartupBadge() {
        var dismissed = false

        renderSettingsScreen(
            startupScreen = StartupScreen.HOME,
            showStartupBadge = true,
            onStartupBadgeDismiss = { dismissed = true }
        )

        composeTestRule.onNodeWithText(string(R.string.settings_locations)).performClick()

        assertFalse("Only the startup row owns that badge", dismissed)
    }

    // ==================== Second line rows and dialogs ====================

    @Test
    fun folderSecondLineItem_summarisesTheCurrentChoice() {
        composeTestRule.setContent {
            FileExplorerTheme {
                FolderSecondLineSettingItem(
                    secondLine = FolderSecondLine.ITEM_COUNT,
                    showBadge = false,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_folder_second_line)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.settings_second_line_item_count)).assertIsDisplayed()
    }

    @Test
    fun fileSecondLineItem_summarisesTheCurrentChoice() {
        composeTestRule.setContent {
            FileExplorerTheme {
                FileSecondLineSettingItem(
                    secondLine = FileSecondLine.LAST_MODIFIED,
                    showBadge = false,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_file_second_line)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.settings_second_line_last_modified)).assertIsDisplayed()
    }

    @Test
    fun secondLineItems_withBadge_showBadgeDot() {
        composeTestRule.setContent {
            FileExplorerTheme {
                FolderSecondLineSettingItem(
                    secondLine = FolderSecondLine.NONE,
                    showBadge = true,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasBadgeDot(), useUnmergedTree = true).assertExists()
    }

    @Test
    fun folderSecondLineDialog_showsEveryChoiceWithTheCurrentOneSelected() {
        composeTestRule.setContent {
            FileExplorerTheme {
                FolderSecondLineSelectionDialog(
                    secondLine = FolderSecondLine.LAST_MODIFIED,
                    onSecondLineSelected = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_second_line_none)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.settings_second_line_item_count)).assertIsDisplayed()
        composeTestRule.onNode(
            isSelectable() and hasText(string(R.string.settings_second_line_last_modified))
        ).assertIsSelected()
    }

    /** Folders count items; files have a size. Neither dialog may offer the other's choice. */
    @Test
    fun secondLineDialogs_offerOnlyTheChoicesThatApply() {
        composeTestRule.setContent {
            FileExplorerTheme {
                FolderSecondLineSelectionDialog(
                    secondLine = FolderSecondLine.NONE,
                    onSecondLineSelected = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_second_line_size)).assertDoesNotExist()
    }

    @Test
    fun fileSecondLineDialog_offersNoItemCount() {
        composeTestRule.setContent {
            FileExplorerTheme {
                FileSecondLineSelectionDialog(
                    secondLine = FileSecondLine.SIZE,
                    onSecondLineSelected = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_second_line_item_count)).assertDoesNotExist()
    }

    @Test
    fun folderSecondLineDialog_selectingAChoiceReportsIt() {
        var selected: FolderSecondLine? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                FolderSecondLineSelectionDialog(
                    secondLine = FolderSecondLine.ITEM_COUNT,
                    onSecondLineSelected = { selected = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_second_line_none)).performClick()

        assertEquals(FolderSecondLine.NONE, selected)
    }

    @Test
    fun fileSecondLineDialog_cancelDoesNotSelect() {
        var selected: FileSecondLine? = null
        var dismissed = false

        composeTestRule.setContent {
            FileExplorerTheme {
                FileSecondLineSelectionDialog(
                    secondLine = FileSecondLine.SIZE,
                    onSecondLineSelected = { selected = it },
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).performClick()

        assertTrue(dismissed)
        assertEquals(null, selected)
    }

    @Test
    fun settingsScreen_tappingFolderSecondLineDismissesOnlyItsBadge() {
        var folderDismissed = false
        var fileDismissed = false

        renderSettingsScreen(
            startupScreen = StartupScreen.HOME,
            showFolderSecondLineBadge = true,
            onFolderSecondLineBadgeDismiss = { folderDismissed = true },
            showFileSecondLineBadge = true,
            onFileSecondLineBadgeDismiss = { fileDismissed = true }
        )

        composeTestRule.onNodeWithText(string(R.string.settings_folder_second_line)).performClick()

        assertTrue(folderDismissed)
        assertFalse("Only the folder row owns that badge", fileDismissed)
    }

    private fun renderSettingsScreen(
        startupScreen: StartupScreen,
        startupFolderName: String? = null,
        onStartupHomeSelected: () -> Unit = {},
        onStartupFolderSelected: () -> Unit = {},
        showStartupBadge: Boolean = false,
        onStartupBadgeDismiss: () -> Unit = {},
        folderSecondLine: FolderSecondLine = FolderSecondLine.ITEM_COUNT,
        fileSecondLine: FileSecondLine = FileSecondLine.SIZE,
        onFolderSecondLineChange: (FolderSecondLine) -> Unit = {},
        onFileSecondLineChange: (FileSecondLine) -> Unit = {},
        showFolderSecondLineBadge: Boolean = false,
        onFolderSecondLineBadgeDismiss: () -> Unit = {},
        showFileSecondLineBadge: Boolean = false,
        onFileSecondLineBadgeDismiss: () -> Unit = {},
        homeSectionOrder: List<HomeSection> = HomeSection.DEFAULT_ORDER,
        onHomeSectionOrderSave: (List<HomeSection>) -> Unit = {},
        showHomeSectionsBadge: Boolean = false,
        onHomeSectionsBadgeDismiss: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FileExplorerTheme {
                SettingsScreen(
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    startupScreen = startupScreen,
                    startupFolderName = startupFolderName,
                    onStartupHomeSelected = onStartupHomeSelected,
                    onStartupFolderSelected = onStartupFolderSelected,
                    enabledLocations = allLocations.toSet(),
                    availableLocationTypes = allLocations,
                    isLoadingLocations = false,
                    onEnabledLocationsSave = {},
                    showHidden = false,
                    onShowHiddenChange = {},
                    folderSecondLine = folderSecondLine,
                    onFolderSecondLineChange = onFolderSecondLineChange,
                    fileSecondLine = fileSecondLine,
                    onFileSecondLineChange = onFileSecondLineChange,
                    recentFilesEnabled = true,
                    hasRecentFiles = false,
                    onRecentFilesEnabledChange = {},
                    onClearRecentFiles = {},
                    hasFavorites = false,
                    onClearFavorites = {},
                    showLocationsBadge = false,
                    onLocationsBadgeDismiss = {},
                    showStartupBadge = showStartupBadge,
                    onStartupBadgeDismiss = onStartupBadgeDismiss,
                    showThemeBadge = false,
                    onThemeBadgeDismiss = {},
                    showFolderSecondLineBadge = showFolderSecondLineBadge,
                    onFolderSecondLineBadgeDismiss = onFolderSecondLineBadgeDismiss,
                    showFileSecondLineBadge = showFileSecondLineBadge,
                    onFileSecondLineBadgeDismiss = onFileSecondLineBadgeDismiss,
                    homeSectionOrder = homeSectionOrder,
                    onHomeSectionOrderSave = onHomeSectionOrderSave,
                    showHomeSectionsBadge = showHomeSectionsBadge,
                    onHomeSectionsBadgeDismiss = onHomeSectionsBadgeDismiss,
                    onBackClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun startupDialog_cancelDoesNotSelect() {
        var dismissCalled = false
        var homeSelected = false
        var folderSelected = false

        composeTestRule.setContent {
            FileExplorerTheme {
                StartupScreenSelectionDialog(
                    startupScreen = StartupScreen.HOME,
                    onHomeSelected = { homeSelected = true },
                    onFolderSelected = { folderSelected = true },
                    onDismiss = { dismissCalled = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).performClick()

        assertTrue("Dismiss should be called on cancel", dismissCalled)
        assertFalse("Cancel should not store the home screen", homeSelected)
        assertFalse("Cancel should not open the folder picker", folderSelected)
    }
}
