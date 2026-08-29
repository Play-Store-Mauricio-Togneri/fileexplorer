package com.mauriciotogneri.fileexplorer.ui.screens.permission

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    /**
     * These were two tests named `lightTheme_rendersCorrectly` / `darkTheme_rendersCorrectly` whose
     * bodies were byte-identical to each other and to [permissionScreen_allElementsDisplayedTogether]
     * apart from the [ThemeMode] argument: three `assertIsDisplayed` calls. Nothing they asserted
     * depended on the theme, so black-on-black passed all three — the same defect that had already
     * been cleaned out of twelve sibling `*_rendersCorrectly` tests, which this pair survived.
     *
     * Legibility belongs to the palette and is asserted against contrast ratios in
     * `ThemeRenderingTest`; `PermissionScreen` adds no colour of its own, taking all three from
     * `MaterialTheme.colorScheme`. What is still worth catching per-screen is a crash or a node that
     * goes missing under one scheme, so the render is kept for every mode — including SYSTEM, the
     * default — under a name that promises only that.
     */
    @Test
    fun permissionScreen_rendersEveryElement_inEveryThemeMode() {
        // The rule permits a single setContent per test, and rendering the three modes side by side
        // would make every text matcher ambiguous — so one composition is re-themed in place.
        var mode by mutableStateOf(ThemeMode.entries.first())

        composeTestRule.setContent {
            FileExplorerTheme(themeMode = mode) {
                PermissionScreenContent(onGrantClick = {})
            }
        }

        ThemeMode.entries.forEach { themeMode ->
            composeTestRule.runOnIdle { mode = themeMode }
            composeTestRule.waitForIdle()

            listOf(R.string.permission_title, R.string.permission_message, R.string.permission_grant)
                .forEach { id ->
                    composeTestRule.onNodeWithText(context.getString(id))
                        .assertIsDisplayed()
                }
        }
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
