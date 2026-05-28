package com.mauriciotogneri.fileexplorer.ui.screens.permission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun permissionScreen_displaysTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PermissionScreenContent(onGrantClick = {})
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.permission_title))
            .assertIsDisplayed()
    }

    @Test
    fun permissionScreen_displaysMessage() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PermissionScreenContent(onGrantClick = {})
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.permission_message))
            .assertIsDisplayed()
    }

    @Test
    fun permissionScreen_displaysGrantButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PermissionScreenContent(onGrantClick = {})
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.permission_grant))
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun permissionScreen_grantButtonClick_triggersCallback() {
        var callbackTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                PermissionScreenContent(onGrantClick = { callbackTriggered = true })
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.permission_grant))
            .performClick()

        assertTrue("Grant button callback should be triggered", callbackTriggered)
    }

    @Test
    fun permissionScreen_lightTheme_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.LIGHT) {
                PermissionScreenContent(onGrantClick = {})
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.permission_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.permission_message))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.permission_grant))
            .assertIsDisplayed()
    }

    @Test
    fun permissionScreen_darkTheme_rendersCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme(themeMode = ThemeMode.DARK) {
                PermissionScreenContent(onGrantClick = {})
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.permission_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.permission_message))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.permission_grant))
            .assertIsDisplayed()
    }

    @Test
    fun permissionScreen_allElementsDisplayedTogether() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PermissionScreenContent(onGrantClick = {})
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.permission_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.permission_message))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.permission_grant))
            .assertIsDisplayed()
            .assertIsEnabled()
    }
}
