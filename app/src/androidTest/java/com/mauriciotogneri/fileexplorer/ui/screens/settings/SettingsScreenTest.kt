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
import androidx.compose.ui.test.performClick
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.ClearFavoritesSettingItem
import com.mauriciotogneri.fileexplorer.activities.ClearRecentFilesSettingItem
import com.mauriciotogneri.fileexplorer.activities.LocationsSelectionDialog
import com.mauriciotogneri.fileexplorer.activities.LocationsSettingItem
import com.mauriciotogneri.fileexplorer.activities.ShowHiddenSettingItem
import com.mauriciotogneri.fileexplorer.activities.ThemeSelectionDialog
import com.mauriciotogneri.fileexplorer.activities.ThemeSettingItem
import com.mauriciotogneri.fileexplorer.activities.TrackRecentFilesSettingItem
import com.mauriciotogneri.fileexplorer.data.model.LocationType
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
        composeTestRule.onNode(hasBadgeDot()).assertExists()
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
        composeTestRule.onNode(hasBadgeDot()).assertDoesNotExist()
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
        composeTestRule.onNode(hasBadgeDot()).assertExists()
    }

    @Test
    fun themeItem_withoutBadge_showsNoBadgeDot() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSettingItem(currentTheme = ThemeMode.LIGHT, showBadge = false, onClick = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasBadgeDot()).assertDoesNotExist()
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
}
