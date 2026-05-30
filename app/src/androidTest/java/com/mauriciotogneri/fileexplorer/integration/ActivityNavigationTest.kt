package com.mauriciotogneri.fileexplorer.integration

import android.app.Activity
import android.app.Instrumentation
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.AboutActivity
import com.mauriciotogneri.fileexplorer.activities.FeedbackActivity
import com.mauriciotogneri.fileexplorer.activities.ItemInfoActivity
import com.mauriciotogneri.fileexplorer.activities.SettingsActivity
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderScreen
import com.mauriciotogneri.fileexplorer.ui.screens.home.HomeScreen
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Stage 12 (Point 13): verifies that UI actions actually launch the right Activity (existing tests
 * only checked the click callbacks). The Home drawer items launch Settings/Feedback/About; the
 * folder file Info action launches ItemInfoActivity.
 *
 * Real-wiring: the real [HomeScreen] / [FolderScreen] + Espresso-Intents. `intending(anyIntent())`
 * stubs each launch so the target Activity does not actually start. Per the plan, the Info case
 * asserts the component only (the file-path extra key is private).
 */
@RunWith(AndroidJUnit4::class)
class ActivityNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(activity.cacheDir, "test_actnav_${System.currentTimeMillis()}").apply { mkdirs() }
        Intents.init()
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        testDir.deleteRecursively()
    }

    // ==================== Home drawer -> Activity ====================

    @Test
    fun drawerSettings_launchesSettingsActivity() {
        renderHome()
        openDrawerAndTap(R.string.drawer_settings)
        intended(hasComponent(SettingsActivity::class.java.name))
    }

    @Test
    fun drawerFeedback_launchesFeedbackActivity() {
        renderHome()
        openDrawerAndTap(R.string.drawer_feedback)
        intended(hasComponent(FeedbackActivity::class.java.name))
    }

    @Test
    fun drawerAbout_launchesAboutActivity() {
        renderHome()
        openDrawerAndTap(R.string.drawer_about)
        intended(hasComponent(AboutActivity::class.java.name))
    }

    // ==================== Folder file Info -> ItemInfoActivity ====================

    @Test
    fun folderFileInfo_launchesItemInfoActivity() {
        FileFixtures.createTextFile(testDir, "doc.txt", "x")
        renderFolder()
        openRowActionsSheet("doc.txt")

        // "Info" is the last action in the horizontally-scrolling LazyRow inside the sheet.
        composeTestRule.onNode(hasScrollAction() and hasAnyAncestor(hasTestTag("file_actions_sheet")))
            .performScrollToNode(hasText(string(R.string.action_info)))
        composeTestRule.onNodeWithText(string(R.string.action_info)).performClick()

        intended(hasComponent(ItemInfoActivity::class.java.name))
    }

    // ==================== Helpers ====================

    private fun renderHome() {
        composeTestRule.setContent {
            FileExplorerTheme {
                HomeScreen(onNavigateToFolder = { _, _, _, _ -> })
            }
        }
    }

    private fun renderFolder() {
        composeTestRule.setContent {
            FileExplorerTheme {
                FolderScreen(
                    path = testDir.absolutePath,
                    onNavigateToFolder = {},
                    onNavigateBack = {}
                )
            }
        }
    }

    private fun openDrawerAndTap(@StringRes labelRes: Int) {
        waitForText(string(R.string.search_placeholder)) // Home finished loading -> search bar present
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_open)).performClick()
        waitForText(string(labelRes))
        composeTestRule.onNodeWithText(string(labelRes)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun openRowActionsSheet(fileName: String) {
        waitForText(fileName)
        // The file row's overflow shares "more options" with the toolbar action; the row sits below
        // the app bar, so it is the bottom-most match.
        val menus = composeTestRule.onAllNodesWithContentDescription(string(R.string.content_description_more_options))
        val tops = menus.fetchSemanticsNodes().map { it.boundsInRoot.top }
        val rowIndex = tops.indices.maxByOrNull { tops[it] } ?: error("No file-row overflow menu found")
        menus[rowIndex].performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("file_actions_sheet").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = activity.getString(id)
}
