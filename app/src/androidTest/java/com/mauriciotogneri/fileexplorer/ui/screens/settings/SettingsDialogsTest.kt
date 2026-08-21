package com.mauriciotogneri.fileexplorer.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.ClearFavoritesConfirmDialog
import com.mauriciotogneri.fileexplorer.activities.ClearRecentFilesSettingItem
import com.mauriciotogneri.fileexplorer.activities.LocationsSelectionDialog
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.testutil.clickableWithText
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checkbox-level behavior of the real `LocationsSelectionDialog` plus the enabled/disabled states of
 * the clear rows, all imported from `SettingsActivity` as `internal` test seams.
 *
 * Where [SettingsScreenTest] covers what each row shows, this covers what the dialog *remembers*
 * between taps — the local `selectedLocations` state that only commits on Save.
 */
@RunWith(AndroidJUnit4::class)
class SettingsDialogsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    // ==================== Clear Recent Files ====================

    @Test
    fun clearRecentFiles_disabled_whenNoRecentFiles() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ClearRecentFilesSettingItem(enabled = false, onClick = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_recent_files_clear)).assertIsDisplayed()
        composeTestRule.onNode(clickableWithText(string(R.string.settings_recent_files_clear)))
            .assertIsNotEnabled()
    }

    @Test
    fun clearRecentFiles_enabled_whenTrackingOnAndHasFiles() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ClearRecentFilesSettingItem(enabled = true, onClick = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_recent_files_clear)).assertIsDisplayed()
    }

    @Test
    fun clearRecentFiles_tap_triggersCallback() {
        var clearCalled = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ClearRecentFilesSettingItem(enabled = true, onClick = { clearCalled = true })
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_recent_files_clear)).performClick()

        assertTrue("Clear callback should be invoked", clearCalled)
    }

    @Test
    fun clearRecentFiles_disabled_tapDoesNotTriggerCallback() {
        var clearCalled = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ClearRecentFilesSettingItem(enabled = false, onClick = { clearCalled = true })
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.settings_recent_files_clear)).performClick()

        assertFalse("Clear callback should not be invoked when disabled", clearCalled)
    }

    // ==================== Clear Favorites confirmation ====================

    @Test
    fun clearFavoritesDialog_confirm_triggersCallback() {
        var confirmed = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ClearFavoritesConfirmDialog(onConfirm = { confirmed = true }, onDismiss = {})
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.dialog_clear)).performClick()

        assertTrue("Confirming should invoke onConfirm", confirmed)
    }

    @Test
    fun clearFavoritesDialog_cancel_doesNotConfirm() {
        var confirmed = false
        var dismissed = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ClearFavoritesConfirmDialog(
                    onConfirm = { confirmed = true },
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).performClick()

        assertFalse("Cancelling must not clear favorites", confirmed)
        assertTrue("Cancelling should dismiss the dialog", dismissed)
    }

    // ==================== Locations dialog checkbox state ====================

    @Test
    fun locationsDialog_checkbox_initiallyChecked_whenEnabled() {
        renderLocationsDialog(enabled = setOf(LocationType.DOWNLOADS))

        composeTestRule.onNode(
            hasText(string(R.string.location_downloads)) and isToggleable()
        ).assertIsOn()
    }

    @Test
    fun locationsDialog_checkbox_initiallyUnchecked_whenNotEnabled() {
        renderLocationsDialog(enabled = setOf(LocationType.DOWNLOADS))

        composeTestRule.onNode(
            hasText(string(R.string.location_images)) and isToggleable()
        ).assertIsOff()
    }

    @Test
    fun locationsDialog_checkbox_togglesOn_whenClicked() {
        renderLocationsDialog(enabled = setOf(LocationType.DOWNLOADS))

        composeTestRule.onNode(
            hasText(string(R.string.location_images)) and isToggleable()
        ).assertIsOff()

        composeTestRule.onNodeWithText(string(R.string.location_images)).performClick()

        composeTestRule.onNode(
            hasText(string(R.string.location_images)) and isToggleable()
        ).assertIsOn()
    }

    @Test
    fun locationsDialog_checkbox_togglesOff_whenClicked() {
        renderLocationsDialog(enabled = setOf(LocationType.DOWNLOADS, LocationType.IMAGES))

        composeTestRule.onNode(
            hasText(string(R.string.location_downloads)) and isToggleable()
        ).assertIsOn()

        composeTestRule.onNodeWithText(string(R.string.location_downloads)).performClick()

        composeTestRule.onNode(
            hasText(string(R.string.location_downloads)) and isToggleable()
        ).assertIsOff()
    }

    @Test
    fun locationsDialog_multipleToggles_maintainsCorrectState() {
        var savedLocations: Set<LocationType>? = null
        val available = listOf(LocationType.DOWNLOADS, LocationType.IMAGES, LocationType.VIDEOS)

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
        composeTestRule.onNodeWithText(string(R.string.location_downloads)).performClick()
        composeTestRule.onNodeWithText(string(R.string.location_images)).performClick()
        composeTestRule.onNodeWithText(string(R.string.location_videos)).performClick()
        composeTestRule.onNodeWithText(string(R.string.dialog_save)).performClick()

        assertEquals(
            "Should save Images and Videos (Downloads toggled off)",
            setOf(LocationType.IMAGES, LocationType.VIDEOS),
            savedLocations
        )
    }

    @Test
    fun locationsDialog_cancelDiscards_previousChanges() {
        var saveCalled = false
        var dismissCalled = false

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = setOf(LocationType.DOWNLOADS, LocationType.IMAGES),
                    availableLocationTypes = listOf(
                        LocationType.DOWNLOADS,
                        LocationType.IMAGES,
                        LocationType.VIDEOS
                    ),
                    onSave = { saveCalled = true },
                    onDismiss = { dismissCalled = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.location_downloads)).performClick()
        composeTestRule.onNodeWithText(string(R.string.location_videos)).performClick()
        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).performClick()

        assertFalse("Save should not be called on cancel", saveCalled)
        assertTrue("Dismiss should be called on cancel", dismissCalled)
    }

    @Test
    fun locationsDialog_storedSelectionArrivesLate_savesThatSelection() {
        val stored = setOf(LocationType.DOWNLOADS)
        val available = listOf(LocationType.DOWNLOADS, LocationType.IMAGES, LocationType.VIDEOS)
        var enabled by mutableStateOf(LocationType.entries.toSet())
        var savedLocations: Set<LocationType>? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = enabled,
                    availableLocationTypes = available,
                    onSave = { savedLocations = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // The caller seeds its state with every location enabled and replaces it when the
        // preference flow emits, which can land after the dialog is already on screen.
        enabled = stored
        composeTestRule.waitForIdle()

        // Asserted before saving: a dialog still showing the placeholder is the failure this test
        // exists to catch, whether or not Save happens to write the right thing.
        composeTestRule.onNode(
            hasText(string(R.string.location_images)) and isToggleable()
        ).assertIsOff()

        composeTestRule.onNodeWithText(string(R.string.dialog_save)).performClick()

        assertEquals(
            "Should save the stored selection, not the all-enabled placeholder",
            stored,
            savedLocations
        )
    }

    private fun renderLocationsDialog(enabled: Set<LocationType>) {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = enabled,
                    availableLocationTypes = listOf(LocationType.DOWNLOADS, LocationType.IMAGES),
                    onSave = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }
}
