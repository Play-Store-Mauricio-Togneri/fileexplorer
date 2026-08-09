package com.mauriciotogneri.fileexplorer.ui.screens.picker

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The destination picker's top bar, which had no test of its own.
 *
 * Its back button is the only way out of the picker overlay other than the system gesture, so a
 * regression that lost the callback would strand the user in a full-screen sheet.
 */
@RunWith(AndroidJUnit4::class)
class PickerTopBarTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    private fun render(title: String, onBackClick: () -> Unit = {}) {
        composeTestRule.setContent {
            FileExplorerTheme {
                PickerTopBar(title = title, onBackClick = onBackClick)
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun pickerTopBar_displaysTheGivenTitle() {
        render(title = string(R.string.action_move_to))

        composeTestRule.onNodeWithText(string(R.string.action_move_to)).assertIsDisplayed()
    }

    /** The bar is shared between modes, so the caller's title is what must be shown, verbatim. */
    @Test
    fun pickerTopBar_displaysTheCopyTitleWhenGivenIt() {
        render(title = string(R.string.action_copy_to))

        composeTestRule.onNodeWithText(string(R.string.action_copy_to)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_move_to)).assertDoesNotExist()
    }

    @Test
    fun pickerTopBar_displaysBackButton() {
        render(title = string(R.string.action_move_to))

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).assertIsDisplayed()
    }

    @Test
    fun pickerTopBar_backButton_triggersCallback() {
        var backClicked = false
        render(title = string(R.string.action_move_to), onBackClick = { backClicked = true })

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()

        assertTrue("The picker's only exit affordance must invoke onBackClick", backClicked)
    }

    @Test
    fun pickerTopBar_backButton_firesOncePerTap() {
        var clicks = 0
        render(title = string(R.string.action_move_to), onBackClick = { clicks++ })

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()

        assertEquals(1, clicks)
    }
}
