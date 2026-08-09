package com.mauriciotogneri.fileexplorer.ui.components

import android.app.Activity
import android.app.Instrumentation
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.AboutActivity
import com.mauriciotogneri.fileexplorer.activities.FeedbackActivity
import com.mauriciotogneri.fileexplorer.activities.SettingsActivity
import com.mauriciotogneri.fileexplorer.testutil.Retry
import com.mauriciotogneri.fileexplorer.testutil.RetryRunner
import com.mauriciotogneri.fileexplorer.ui.screens.home.HomeScreen
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The navigation drawer as it exists in the app — inside the real `HomeScreen`.
 *
 * This file previously built its own `ModalNavigationDrawer` + `NavigationDrawerItem` inline in
 * every test. There is no `NavigationDrawer` component in this codebase, so those tests verified
 * that Compose Material3 renders a label and fires `onClick`; they would have stayed green with the
 * app's drawer deleted. Here the drawer is opened through the real menu button and each item is
 * asserted by the Activity it launches.
 */
@RunWith(RetryRunner::class)
class NavigationDrawerTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    @Before
    fun setUp() {
        Intents.init()
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    private fun renderHome() {
        composeTestRule.setContent {
            FileExplorerTheme {
                HomeScreen()
            }
        }
        // Home finished loading once its search bar is present.
        waitForText(string(R.string.search_placeholder))
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openDrawer() {
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_open)).performClick()
        composeTestRule.waitForIdle()
        waitForText(string(R.string.drawer_settings))
    }

    @Test
    @Retry
    fun drawer_opensViaMenuButton() {
        renderHome()

        openDrawer()

        composeTestRule.onNodeWithText(string(R.string.drawer_settings)).assertIsDisplayed()
    }

    @Test
    @Retry
    fun drawer_displaysAllNavigationItems() {
        renderHome()

        openDrawer()

        composeTestRule.onNodeWithText(string(R.string.drawer_settings)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.drawer_feedback)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.drawer_about)).assertIsDisplayed()
    }

    @Test
    @Retry
    fun drawer_settingsItem_launchesSettingsActivity() {
        renderHome()
        openDrawer()

        composeTestRule.onNodeWithText(string(R.string.drawer_settings)).performClick()
        composeTestRule.waitForIdle()

        intended(hasComponent(SettingsActivity::class.java.name))
    }

    @Test
    @Retry
    fun drawer_feedbackItem_launchesFeedbackActivity() {
        renderHome()
        openDrawer()

        composeTestRule.onNodeWithText(string(R.string.drawer_feedback)).performClick()
        composeTestRule.waitForIdle()

        intended(hasComponent(FeedbackActivity::class.java.name))
    }

    @Test
    @Retry
    fun drawer_aboutItem_launchesAboutActivity() {
        renderHome()
        openDrawer()

        composeTestRule.onNodeWithText(string(R.string.drawer_about)).performClick()
        composeTestRule.waitForIdle()

        intended(hasComponent(AboutActivity::class.java.name))
    }
}
