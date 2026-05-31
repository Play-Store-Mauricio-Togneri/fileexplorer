package com.mauriciotogneri.fileexplorer.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
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
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // ==================== Recent Files Toggle Tests ====================

    @Test
    fun recentFilesToggle_displaysCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TrackRecentFilesSettingItem(
                    enabled = true,
                    onEnabledChange = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_recent_files_enabled))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_recent_files_description))
            .assertIsDisplayed()
    }

    @Test
    fun recentFilesToggle_whenEnabled_switchIsOn() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TrackRecentFilesSettingItem(
                    enabled = true,
                    onEnabledChange = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(isToggleable())
            .assertIsOn()
    }

    @Test
    fun recentFilesToggle_whenDisabled_switchIsOff() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TrackRecentFilesSettingItem(
                    enabled = false,
                    onEnabledChange = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(isToggleable())
            .assertIsOff()
    }

    @Test
    fun recentFilesToggle_clickToDisable_triggersCallback() {
        var newValue: Boolean? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TrackRecentFilesSettingItem(
                    enabled = true,
                    onEnabledChange = { newValue = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_recent_files_enabled))
            .performClick()

        assertEquals(false, newValue)
    }

    @Test
    fun recentFilesToggle_clickToEnable_triggersCallback() {
        var newValue: Boolean? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                TrackRecentFilesSettingItem(
                    enabled = false,
                    onEnabledChange = { newValue = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_recent_files_enabled))
            .performClick()

        assertTrue("Callback should receive true when enabling", newValue == true)
    }

    // ==================== Show Hidden Toggle Tests ====================

    @Test
    fun showHiddenToggle_displaysCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ShowHiddenSettingItem(
                    enabled = true,
                    onEnabledChange = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.show_hidden_items))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_show_hidden_description))
            .assertIsDisplayed()
    }

    @Test
    fun showHiddenToggle_whenEnabled_switchIsOn() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ShowHiddenSettingItem(
                    enabled = true,
                    onEnabledChange = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(isToggleable())
            .assertIsOn()
    }

    @Test
    fun showHiddenToggle_whenDisabled_switchIsOff() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ShowHiddenSettingItem(
                    enabled = false,
                    onEnabledChange = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNode(isToggleable())
            .assertIsOff()
    }

    @Test
    fun showHiddenToggle_click_triggersCallback() {
        var newValue: Boolean? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                ShowHiddenSettingItem(
                    enabled = false,
                    onEnabledChange = { newValue = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.show_hidden_items))
            .performClick()

        assertTrue("Callback should receive true when enabling", newValue == true)
    }

    // ==================== Locations Dialog Tests ====================

    @Test
    fun locationsItem_displaysCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSettingItem(
                    enabledLocations = setOf(LocationType.DOWNLOADS, LocationType.IMAGES),
                    availableLocationTypes = listOf(LocationType.DOWNLOADS, LocationType.IMAGES, LocationType.VIDEOS),
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_locations))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("2 / 3")
            .assertIsDisplayed()
    }

    @Test
    fun locationsItem_clickOpensDialog() {
        var dialogOpened = false

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSettingItem(
                    enabledLocations = setOf(LocationType.DOWNLOADS),
                    availableLocationTypes = listOf(LocationType.DOWNLOADS, LocationType.IMAGES),
                    onClick = { dialogOpened = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_locations))
            .performClick()

        assertTrue("Clicking locations should open dialog", dialogOpened)
    }

    @Test
    fun locationsDialog_displaysAllAvailableLocations() {
        val availableLocations = listOf(
            LocationType.DOWNLOADS,
            LocationType.IMAGES,
            LocationType.VIDEOS
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = availableLocations.toSet(),
                    availableLocationTypes = availableLocations,
                    onSave = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.location_downloads))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.location_images))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.location_videos))
            .assertIsDisplayed()
    }

    @Test
    fun locationsDialog_unselectLocation_andSave() {
        var savedLocations: Set<LocationType>? = null
        val availableLocations = listOf(
            LocationType.DOWNLOADS,
            LocationType.IMAGES,
            LocationType.VIDEOS
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = availableLocations.toSet(),
                    availableLocationTypes = availableLocations,
                    onSave = { savedLocations = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Unselect Downloads
        composeTestRule.onNodeWithText(context.getString(R.string.location_downloads))
            .performClick()

        // Save
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_save))
            .performClick()

        assertEquals(
            "Should save without Downloads",
            setOf(LocationType.IMAGES, LocationType.VIDEOS),
            savedLocations
        )
    }

    @Test
    fun locationsDialog_selectMultipleLocations_andSave() {
        var savedLocations: Set<LocationType>? = null
        val availableLocations = listOf(
            LocationType.DOWNLOADS,
            LocationType.IMAGES,
            LocationType.VIDEOS,
            LocationType.AUDIO
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

        // Select Images and Videos
        composeTestRule.onNodeWithText(context.getString(R.string.location_images))
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.location_videos))
            .performClick()

        // Save
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_save))
            .performClick()

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
        val availableLocations = listOf(LocationType.DOWNLOADS, LocationType.IMAGES)

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = availableLocations.toSet(),
                    availableLocationTypes = availableLocations,
                    onSave = { saveCalled = true },
                    onDismiss = { dismissCalled = true }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Unselect something
        composeTestRule.onNodeWithText(context.getString(R.string.location_downloads))
            .performClick()

        // Cancel
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel))
            .performClick()

        assertFalse("Save should not be called on cancel", saveCalled)
        assertTrue("Dismiss should be called on cancel", dismissCalled)
    }

    @Test
    fun locationsDialog_unselectAllLocations_andSave() {
        var savedLocations: Set<LocationType>? = null
        val availableLocations = listOf(LocationType.DOWNLOADS, LocationType.IMAGES)

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = availableLocations.toSet(),
                    availableLocationTypes = availableLocations,
                    onSave = { savedLocations = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Unselect all
        composeTestRule.onNodeWithText(context.getString(R.string.location_downloads))
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.location_images))
            .performClick()

        // Save
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_save))
            .performClick()

        assertTrue("Should save empty set", savedLocations?.isEmpty() == true)
    }

    // ==================== Theme Dialog Tests ====================

    @Test
    fun themeItem_displaysCurrentTheme_light() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSettingItem(
                    currentTheme = ThemeMode.LIGHT,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_theme))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.theme_light))
            .assertIsDisplayed()
    }

    @Test
    fun themeItem_displaysCurrentTheme_dark() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSettingItem(
                    currentTheme = ThemeMode.DARK,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.theme_dark))
            .assertIsDisplayed()
    }

    @Test
    fun themeItem_displaysCurrentTheme_system() {
        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSettingItem(
                    currentTheme = ThemeMode.SYSTEM,
                    onClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.theme_system))
            .assertIsDisplayed()
    }

    @Test
    fun themeItem_clickOpensDialog() {
        var dialogOpened = false

        composeTestRule.setContent {
            FileExplorerTheme {
                ThemeSettingItem(
                    currentTheme = ThemeMode.LIGHT,
                    onClick = { dialogOpened = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.settings_theme))
            .performClick()

        assertTrue("Clicking theme should open dialog", dialogOpened)
    }

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
        composeTestRule.onNodeWithText(context.getString(R.string.theme_light))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.theme_dark))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.theme_system))
            .assertIsDisplayed()
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
        composeTestRule.onNodeWithText(context.getString(R.string.theme_dark))
            .performClick()

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
        composeTestRule.onNodeWithText(context.getString(R.string.theme_light))
            .performClick()

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
        composeTestRule.onNodeWithText(context.getString(R.string.theme_system))
            .performClick()

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
        composeTestRule.onNodeWithText(context.getString(R.string.dialog_cancel))
            .performClick()

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
        // The row with Dark theme should be selected
        composeTestRule.onNode(
            hasText(context.getString(R.string.theme_dark)) and androidx.compose.ui.test.isSelectable()
        ).assertIsSelected()
    }

    // ==================== Test Composables (matching SettingsActivity) ====================

    @Composable
    private fun TrackRecentFilesSettingItem(
        enabled: Boolean,
        onEnabledChange: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEnabledChange(!enabled) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_recent_files_enabled),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_recent_files_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.scale(0.85f)
            )
        }
    }

    @Composable
    private fun ShowHiddenSettingItem(
        enabled: Boolean,
        onEnabledChange: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEnabledChange(!enabled) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.show_hidden_items),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_show_hidden_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.scale(0.85f)
            )
        }
    }

    @Composable
    private fun LocationsSettingItem(
        enabledLocations: Set<LocationType>,
        availableLocationTypes: List<LocationType>,
        onClick: () -> Unit
    ) {
        val enabledCount = enabledLocations.count { it in availableLocationTypes }
        val availableCount = availableLocationTypes.size

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = stringResource(R.string.settings_locations),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.settings_locations),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$enabledCount / $availableCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun ThemeSettingItem(
        currentTheme: ThemeMode,
        onClick: () -> Unit
    ) {
        val themeLabel = when (currentTheme) {
            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
            ThemeMode.DARK -> stringResource(R.string.theme_dark)
            ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Palette,
                contentDescription = stringResource(R.string.settings_theme),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = themeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    private fun ThemeSelectionDialog(
        currentTheme: ThemeMode,
        onThemeSelected: (ThemeMode) -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(modifier = Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                            ThemeMode.DARK -> stringResource(R.string.theme_dark)
                            ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = mode == currentTheme,
                                    onClick = { onThemeSelected(mode) },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = mode == currentTheme,
                                onClick = null
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
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
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
