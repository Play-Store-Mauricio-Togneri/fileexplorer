package com.mauriciotogneri.fileexplorer.ui.screens.permission

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
     * The screen's one render check: title, message and an enabled grant button, under every theme
     * mode. Four single-mode tests that each found one or all of these nodes under the default mode
     * only repeated a subset of this, and were removed; the grant button's enabled check, which was
     * theirs alone, moved here.
     *
     * This began as two tests named `lightTheme_rendersCorrectly` / `darkTheme_rendersCorrectly`
     * whose bodies were byte-identical to each other apart from the [ThemeMode] argument: three
     * `assertIsDisplayed` calls. Nothing they asserted depended on the theme, so black-on-black
     * passed both — the same defect that had already been cleaned out of twelve sibling
     * `*_rendersCorrectly` tests, which this pair survived.
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
            // A grant button drawn but disabled would leave the user no way past this screen.
            composeTestRule.onNodeWithText(context.getString(R.string.permission_grant))
                .assertIsEnabled()
        }
    }

    /**
     * Below Android 11 a permanently denied runtime dialog returns at once with no UI, so the screen
     * must say so and relabel the button as the route to Settings — otherwise tapping it does
     * nothing. The flag is set from the system dialog's result, which a Compose test cannot drive, so
     * only the content is reachable here.
     */
    @Test
    fun permissionScreen_permanentlyDenied_pointsToSettings() {
        var callbackTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                PermissionScreenContent(
                    onGrantClick = { callbackTriggered = true },
                    permanentlyDenied = true
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.permission_denied_message))
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText(context.getString(R.string.permission_message))
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText(context.getString(R.string.permission_grant))
            .assertCountEquals(0)
        composeTestRule.onNodeWithText(context.getString(R.string.apk_permission_settings))
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        assertTrue("Open Settings button callback should be triggered", callbackTriggered)
    }
}
