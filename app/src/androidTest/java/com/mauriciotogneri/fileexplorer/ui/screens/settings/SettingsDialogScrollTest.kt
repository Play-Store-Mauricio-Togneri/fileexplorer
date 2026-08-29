package com.mauriciotogneri.fileexplorer.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.HomeSectionsOrderDialog
import com.mauriciotogneri.fileexplorer.activities.LocationsSelectionDialog
import com.mauriciotogneri.fileexplorer.activities.SwipeLeftActionSelectionDialog
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The settings dialogs whose option lists can outgrow the dialog.
 *
 * `AlertDialog` bounds its content slot without scrolling it, so a list that no longer fits is
 * measured with nothing left for its last rows: they collapse to no height and stop being tappable.
 * Asserting on visibility alone would not catch that, because the list fits on a tall emulator
 * either way. [performScrollTo] does catch it — it fails outright when the row has no scrollable
 * ancestor, whatever the screen it runs on.
 */
@RunWith(AndroidJUnit4::class)
class SettingsDialogScrollTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    @Test
    fun swipeActionDialog_lastOption_isReachable() {
        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeLeftActionSelectionDialog(
                    action = SwipeAction.NONE,
                    onActionSelected = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(string(R.string.action_info))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun locationsDialog_lastLocation_isReachable() {
        val lastLocation = LocationType.entries.last()

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSelectionDialog(
                    enabledLocations = emptySet(),
                    availableLocationTypes = LocationType.entries,
                    onSave = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(string(lastLocation.titleResId))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun homeSectionsOrderDialog_lastSection_isReachable() {
        val lastSection = HomeSection.DEFAULT_ORDER.last()

        composeTestRule.setContent {
            FileExplorerTheme {
                HomeSectionsOrderDialog(
                    order = HomeSection.DEFAULT_ORDER,
                    onSave = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(string(lastSection.titleResId))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
