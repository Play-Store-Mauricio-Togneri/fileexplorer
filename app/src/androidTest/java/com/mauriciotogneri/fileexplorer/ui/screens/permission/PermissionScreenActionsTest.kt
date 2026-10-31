package com.mauriciotogneri.fileexplorer.ui.screens.permission

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.util.PermissionChecker
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 10 (Point 11) — permission screen grant action. On Android R+ the grant button launches
 * `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`; Espresso-Intents stubs the launch so Settings
 * never opens.
 *
 * The return from Settings is driven by moving the activity to CREATED and back to RESUMED, with a
 * fake [PermissionChecker] standing in for `MANAGE_EXTERNAL_STORAGE`, which can't be toggled in
 * instrumentation on R+. The fake is read on the main thread, so it is only changed there.
 *
 * Only Settings intents are stubbed: the move to CREATED works by launching an empty activity on
 * top, which a stub on every intent swallowed, leaving the screen resumed. The return tests also
 * keep their launch away from Espresso-Intents altogether, through [SettingsSink]: a stub answers
 * with a result, and delivering it pauses and resumes the activity at the tap, ending the trip
 * before the test chooses how it ends.
 */
@RunWith(AndroidJUnit4::class)
class PermissionScreenActionsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private var permissionGranted = false

    private val sink by lazy { SettingsSink(activity) }

    private val permissionChecker = object : PermissionChecker {
        override fun hasStoragePermission(): Boolean = permissionGranted
    }

    @Before
    fun setUp() {
        Intents.init()
        intending(hasAction(startsWith("android.settings.")))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    /**
     * The R+ arm only. The guard here used to be `assumeTrue(SDK_INT >= R)`, which is true on every
     * emulator this suite is run on — so it never skipped, and it read as "the pre-R path is handled
     * by an SDK check" when in fact nothing covers it. `PermissionScreen`'s pre-R arm,
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
    fun returnWithoutAccess_showsTheHint() {
        renderWithFakeChecker()

        composeTestRule.onNodeWithText(string(R.string.permission_grant)).performClick()
        assertEquals(listOf(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION), sink.launched)
        // Still away: the screen has not come back yet, so nothing has changed.
        assertHintCount(0)

        leaveAndReturn()

        composeTestRule.onNodeWithText(string(R.string.permission_all_files_hint)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.permission_grant)).assertIsDisplayed()
    }

    @Test
    fun returnWithAccess_grants_andNeverShowsTheHint() {
        var grants = 0
        renderWithFakeChecker(onPermissionGranted = { grants++ })

        composeTestRule.onNodeWithText(string(R.string.permission_grant)).performClick()
        leaveAndReturn(grantWhileAway = true)

        assertEquals("The return with access must move on, once", 1, grants)
        assertHintCount(0)
    }

    // The screen turning off and on is a resume too, but not a return from Settings.
    @Test
    fun resumeWithoutATrip_showsNoHint() {
        renderWithFakeChecker()

        leaveAndReturn()

        assertHintCount(0)
    }

    /**
     * Android may kill the process while Settings is in front, or a theme or language change
     * rebuilds the activity. The trip must survive that, and the rebuilt screen's first resume is
     * where it ends: plain `remember` lost the trip, and the old code skipped the first resume.
     */
    @Test
    fun returnIntoARebuiltScreen_stillShowsTheHint() {
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent(screenWithFakes(onPermissionGranted = {}))
        composeTestRule.onNodeWithText(string(R.string.permission_grant)).performClick()
        assertEquals(listOf(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION), sink.launched)
        assertHintCount(0)

        restorationTester.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText(string(R.string.permission_all_files_hint)).assertIsDisplayed()
    }

    private fun renderPermissionScreen() {
        composeTestRule.setContent {
            FileExplorerTheme {
                PermissionScreen(onPermissionGranted = {})
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun renderWithFakeChecker(onPermissionGranted: () -> Unit = {}) {
        composeTestRule.setContent(screenWithFakes(onPermissionGranted))
        composeTestRule.waitForIdle()
    }

    /** The production screen, with the fake checker and its Settings launches kept by [sink]. */
    private fun screenWithFakes(onPermissionGranted: () -> Unit): @Composable () -> Unit = {
        FileExplorerTheme {
            CompositionLocalProvider(LocalContext provides sink) {
                PermissionScreen(
                    onPermissionGranted = onPermissionGranted,
                    permissionChecker = permissionChecker
                )
            }
        }
    }

    /** What a trip to Settings does to the screen: it stops, then resumes. */
    private fun leaveAndReturn(grantWhileAway: Boolean = false) {
        val scenario = composeTestRule.activityRule.scenario
        scenario.moveToState(Lifecycle.State.CREATED)
        if (grantWhileAway) {
            composeTestRule.runOnUiThread { permissionGranted = true }
        }
        scenario.moveToState(Lifecycle.State.RESUMED)
        composeTestRule.waitForIdle()
    }

    private fun assertHintCount(count: Int) {
        composeTestRule.onAllNodesWithText(string(R.string.permission_all_files_hint))
            .assertCountEquals(count)
    }

    private fun string(@StringRes id: Int): String = activity.getString(id)

    /**
     * The screen's context in the return tests: a Settings launch is recorded and dropped, so the
     * grant button opens "Settings" without the activity leaving the foreground, and the test then
     * decides when and how the user comes back.
     */
    private class SettingsSink(base: Context) : ContextWrapper(base) {
        val launched = mutableListOf<String?>()

        override fun startActivity(intent: Intent) {
            launched += intent.action
        }
    }
}
