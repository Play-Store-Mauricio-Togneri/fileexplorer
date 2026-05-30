package com.mauriciotogneri.fileexplorer.ui.screens.permission

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 10 (Point 11) — permission screen grant action. On Android R+ the grant button launches
 * `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` (the only fully controllable, observable effect);
 * Espresso-Intents stubs the launch so Settings never opens.
 *
 * Out of scope (documented): the resume re-check that calls `onPermissionGranted()` needs
 * `MANAGE_EXTERNAL_STORAGE` actually granted, which can't be toggled in instrumentation on R+.
 */
@RunWith(AndroidJUnit4::class)
class PermissionScreenActionsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    @Before
    fun setUp() {
        Intents.init()
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun grantButton_onRplus_firesManageAllFilesAccessIntent() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        renderPermissionScreen()

        composeTestRule.onNodeWithText(string(R.string.permission_grant)).performClick()

        intended(
            allOf(
                hasAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION),
                hasData("package:${activity.packageName}")
            )
        )
    }

    @Test
    fun grantButton_isDisplayedAndClickable() {
        renderPermissionScreen()

        composeTestRule.onNodeWithText(string(R.string.permission_grant))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    private fun renderPermissionScreen() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PermissionScreen(onPermissionGranted = {})
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun string(@StringRes id: Int): String = activity.getString(id)
}
