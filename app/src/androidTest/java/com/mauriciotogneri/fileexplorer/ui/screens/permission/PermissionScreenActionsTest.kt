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
import org.junit.Assert.assertTrue
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

    /**
     * The R+ arm only. The guard here used to be `assumeTrue(SDK_INT >= R)`, which is true on every
     * emulator this suite is run on — so it never skipped, and it read as "the pre-R path is handled
     * by an SDK check" when in fact nothing covers it. `PermissionScreen.kt:96-100`'s pre-R arm,
     * `permissionLauncher.launch(WRITE_EXTERNAL_STORAGE)`, ships to API 24-29 (minSdk is 24) and is
     * untestable as written: `SDK_INT` is fixed on a device, so reaching it needs the SDK decision
     * hoisted out of the composable into a parameter or a lambda the screen is handed.
     * `AndroidPermissionCheckerTest` covers the equivalent branch in the checker, which was
     * extractable; this one is not, and saying so is better than a guard that hides it.
     */
    @Test
    fun grantButton_onRplus_firesManageAllFilesAccessIntent() {
        assertTrue(
            "This asserts the R+ arm; minSdk is 24, so run it on an API 30+ image.",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        )
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
