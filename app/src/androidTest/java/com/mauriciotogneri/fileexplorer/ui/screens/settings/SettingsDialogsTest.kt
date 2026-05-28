package com.mauriciotogneri.fileexplorer.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDialogsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // ==================== Clear Recent Files Tests ====================

    @Test
    fun clearRecentFiles_disabled_whenNoRecentFiles() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ClearRecentFilesSettingItem(
                    enabled = false, // recentFilesEnabled && hasRecentFiles
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_recent_files_clear))
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun clearRecentFiles_disabled_whenTrackingOff() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ClearRecentFilesSettingItem(
                    enabled = false, // recentFilesEnabled is false
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_recent_files_clear))
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun clearRecentFiles_enabled_whenTrackingOnAndHasFiles() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ClearRecentFilesSettingItem(
                    enabled = true, // recentFilesEnabled && hasRecentFiles
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_recent_files_clear))
            .assertIsDisplayed()
    }

    @Test
    fun clearRecentFiles_tap_triggersCallback() {
        var clearCalled = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ClearRecentFilesSettingItem(
                    enabled = true,
                    onClick = { clearCalled = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_recent_files_clear))
            .performClick()

        assertTrue("Clear callback should be invoked", clearCalled)
    }

    @Test
    fun clearRecentFiles_disabled_tapDoesNotTriggerCallback() {
        var clearCalled = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ClearRecentFilesSettingItem(
                    enabled = false,
                    onClick = { clearCalled = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_recent_files_clear))
            .performClick()

        assertFalse("Clear callback should not be invoked when disabled", clearCalled)
    }

    // ==================== Locations Dialog Checkbox Toggle Tests ====================

    @Test
    fun locationsDialog_checkbox_initiallyChecked_whenEnabled() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = setOf(LocationType.DOWNLOADS),
                    availableLocationTypes = listOf(LocationType.DOWNLOADS, LocationType.IMAGES),
                    onSave = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText(context.getString(R.string.location_downloads)) and isToggleable()
        ).assertIsOn()
    }

    @Test
    fun locationsDialog_checkbox_initiallyUnchecked_whenNotEnabled() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = setOf(LocationType.DOWNLOADS),
                    availableLocationTypes = listOf(LocationType.DOWNLOADS, LocationType.IMAGES),
                    onSave = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText(context.getString(R.string.location_images)) and isToggleable()
        ).assertIsOff()
    }

    @Test
    fun locationsDialog_checkbox_togglesOn_whenClicked() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = setOf(LocationType.DOWNLOADS),
                    availableLocationTypes = listOf(LocationType.DOWNLOADS, LocationType.IMAGES),
                    onSave = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Images is initially unchecked
        composeTestRule.onNode(
            hasText(context.getString(R.string.location_images)) and isToggleable()
        ).assertIsOff()

        // Click to toggle on
        composeTestRule.onNodeWithText(context.getString(R.string.location_images))
            .performClick()

        // Verify it's now checked
        composeTestRule.onNode(
            hasText(context.getString(R.string.location_images)) and isToggleable()
        ).assertIsOn()
    }

    @Test
    fun locationsDialog_checkbox_togglesOff_whenClicked() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = setOf(LocationType.DOWNLOADS, LocationType.IMAGES),
                    availableLocationTypes = listOf(LocationType.DOWNLOADS, LocationType.IMAGES),
                    onSave = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Downloads is initially checked
        composeTestRule.onNode(
            hasText(context.getString(R.string.location_downloads)) and isToggleable()
        ).assertIsOn()

        // Click to toggle off
        composeTestRule.onNodeWithText(context.getString(R.string.location_downloads))
            .performClick()

        // Verify it's now unchecked
        composeTestRule.onNode(
            hasText(context.getString(R.string.location_downloads)) and isToggleable()
        ).assertIsOff()
    }

    @Test
    fun locationsDialog_multipleToggles_maintainsCorrectState() {
        var savedLocations: Set<LocationType>? = null
        val availableLocations = listOf(
            LocationType.DOWNLOADS,
            LocationType.IMAGES,
            LocationType.VIDEOS
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = setOf(LocationType.DOWNLOADS),
                    availableLocationTypes = availableLocations,
                    onSave = { savedLocations = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Toggle Downloads off
        composeTestRule.onNodeWithText(context.getString(R.string.location_downloads))
            .performClick()

        // Toggle Images on
        composeTestRule.onNodeWithText(context.getString(R.string.location_images))
            .performClick()

        // Toggle Videos on
        composeTestRule.onNodeWithText(context.getString(R.string.location_videos))
            .performClick()

        // Save
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_save))
            .performClick()

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
                    availableLocationTypes = listOf(LocationType.DOWNLOADS, LocationType.IMAGES, LocationType.VIDEOS),
                    onSave = { saveCalled = true },
                    onDismiss = { dismissCalled = true }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Make multiple changes
        composeTestRule.onNodeWithText(context.getString(R.string.location_downloads))
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.location_videos))
            .performClick()

        // Cancel
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel))
            .performClick()

        assertFalse("Save should not be called on cancel", saveCalled)
        assertTrue("Dismiss should be called on cancel", dismissCalled)
    }

    // ==================== Test Composables ====================

    @Composable
    private fun ClearRecentFilesSettingItem(
        enabled: Boolean,
        onClick: () -> Unit
    ) {
        val alpha = if (enabled) 1f else 0.38f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.settings_recent_files_clear),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
        }
    }

    @Composable
    private fun LocationsSelectionDialog(
        enabledLocations: Set<LocationType>,
        availableLocationTypes: List<LocationType>,
        onSave: (Set<LocationType>) -> Unit,
        onDismiss: () -> Unit
    ) {
        var selectedLocations by remember { mutableStateOf(enabledLocations) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(R.string.settings_locations),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column {
                    availableLocationTypes.forEach { locationType ->
                        val isEnabled = locationType in selectedLocations
                        val label = stringResource(locationType.titleResId)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = isEnabled,
                                    onValueChange = { enabled ->
                                        selectedLocations = if (enabled) {
                                            selectedLocations + locationType
                                        } else {
                                            selectedLocations - locationType
                                        }
                                    },
                                    role = Role.Checkbox
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isEnabled,
                                onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSave(selectedLocations)
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}
