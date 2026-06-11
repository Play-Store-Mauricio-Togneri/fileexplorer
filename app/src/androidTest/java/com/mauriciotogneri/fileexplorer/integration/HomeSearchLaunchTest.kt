package com.mauriciotogneri.fileexplorer.integration

import android.app.Activity
import android.app.Instrumentation
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
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
import com.mauriciotogneri.fileexplorer.activities.SearchActivity
import com.mauriciotogneri.fileexplorer.ui.screens.home.HomeScreen
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 8 (Point 8) — Home -> Search launch. Verifies the real [HomeScreen] search bar launches
 * `SearchActivity` (both the container tap and the search-icon tap), via Espresso-Intents.
 *
 * Uses the real `HomeViewModel` (default). The search bar appears once storage loading completes;
 * the test waits for the search placeholder before interacting.
 */
@RunWith(AndroidJUnit4::class)
class HomeSearchLaunchTest {

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
    fun searchContainerTap_launchesSearchActivity() {
        renderHome()
        waitForText(string(R.string.search_placeholder))

        composeTestRule.onNodeWithText(string(R.string.search_placeholder)).performClick()

        intended(hasComponent(SearchActivity::class.java.name))
    }

    @Test
    fun searchIconTap_launchesSearchActivity() {
        renderHome()
        waitForText(string(R.string.search_placeholder))

        composeTestRule.onNodeWithContentDescription(string(R.string.search_action)).performClick()

        intended(hasComponent(SearchActivity::class.java.name))
    }

    private fun renderHome() {
        composeTestRule.setContent {
            FileExplorerTheme {
                HomeScreen()
            }
        }
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = activity.getString(id)
}
