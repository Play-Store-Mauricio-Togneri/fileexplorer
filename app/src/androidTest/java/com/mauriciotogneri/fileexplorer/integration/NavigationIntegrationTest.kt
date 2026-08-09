package com.mauriciotogneri.fileexplorer.integration

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.testutil.FolderScreenRobot
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderScreen
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Folder-to-folder navigation through the real [FolderScreen], including the back stack a caller
 * builds on top of it and the breadcrumb trail.
 *
 * The previous version of this file contained seven private `@Composable` mock screens
 * (`TestHomeScreen`, `TestFolderScreen`, `TestNavigationStack`, `TestDeepLinkHandler`, …) and
 * asserted on strings such as `"Go to Documents"` and `"Invalid path"` that exist nowhere in the
 * app. Those tests could not fail for any production reason. The home-drawer and activity-launch
 * cases they nominally covered are handled for real by [ActivityNavigationTest]; what is left —
 * navigating between folders — is exercised here against the real screen.
 */
@RunWith(AndroidJUnit4::class)
class NavigationIntegrationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var rootDir: File

    @Before
    fun setUp() {
        SortManager.setSortMode(SortMode.NAME_ASC)
        rootDir = File(composeTestRule.activity.cacheDir, "test_nav_${System.currentTimeMillis()}")
            .apply { mkdirs() }
    }

    @After
    fun tearDown() {
        SortManager.setSortMode(SortMode.NAME_ASC)
        rootDir.deleteRecursively()
    }

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ==================== Descending into folders ====================

    @Test
    fun folder_tapFolder_reportsTheChildPath() {
        FileFixtures.createFolder(rootDir, "Documents")
        FileFixtures.createFolder(rootDir, "Pictures")
        FileFixtures.createTextFile(rootDir, "readme.txt", "r")

        var navigatedPath: String? = null
        val robot = FolderScreenRobot(composeTestRule, rootDir)
        robot.render(onNavigateToFolder = { navigatedPath = it })
        robot.waitForText("Documents")

        robot.click("Documents")

        assertEquals(File(rootDir, "Documents").absolutePath, navigatedPath)
    }

    @Test
    fun folder_tapFile_doesNotNavigate() {
        FileFixtures.createTextFile(rootDir, "readme.txt", "r")

        var navigatedPath: String? = null
        val robot = FolderScreenRobot(composeTestRule, rootDir)
        robot.render(onNavigateToFolder = { navigatedPath = it })
        robot.waitForText("readme.txt")

        robot.click("readme.txt")

        assertNull("Only folders navigate", navigatedPath)
    }

    // ==================== Back stack ====================

    /**
     * Drives the real screen through a stack the caller owns (as `FolderActivity` does), so each
     * hop re-renders `FolderScreen` at the new path and back returns to the previous listing.
     */
    @Test
    fun folder_navigateDeep_thenBack_returnsThroughEveryLevel() {
        val documents = FileFixtures.createFolder(rootDir, "Documents")
        val work = FileFixtures.createFolder(documents, "Work")
        FileFixtures.createFolder(work, "Projects")
        FileFixtures.createTextFile(rootDir, "root_marker.txt", "r")
        FileFixtures.createTextFile(documents, "documents_marker.txt", "d")
        FileFixtures.createTextFile(work, "work_marker.txt", "w")

        val stack = androidx.compose.runtime.mutableStateListOf(rootDir.absolutePath)

        composeTestRule.setContent {
            FileExplorerTheme {
                FolderScreen(
                    path = stack.last(),
                    onNavigateToFolder = { stack.add(it) },
                    onNavigateBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
                )
            }
        }
        composeTestRule.waitForIdle()

        waitForText("root_marker.txt")
        composeTestRule.onNodeWithText("Documents").performClick()
        waitForText("documents_marker.txt")
        composeTestRule.onNodeWithText("Work").performClick()
        waitForText("work_marker.txt")
        composeTestRule.onNodeWithText("Projects").performClick()
        composeTestRule.waitForIdle()

        assertEquals(4, stack.size)
        assertEquals(File(work, "Projects").absolutePath, stack.last())

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()
        waitForText("work_marker.txt")
        assertEquals(work.absolutePath, stack.last())

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()
        waitForText("documents_marker.txt")
        assertEquals(documents.absolutePath, stack.last())

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()
        waitForText("root_marker.txt")
        assertEquals(rootDir.absolutePath, stack.last())
        assertEquals(1, stack.size)
    }

    /**
     * The other half of [folder_systemBack_inSelectionMode_exitsSelectionOnly]: together they pin
     * `BackHandler(enabled = state.isSelectionMode)` from both sides. Outside selection mode the
     * screen registers no enabled callback, so the press falls through to whatever hosts it — the
     * `NavHost` in `FolderActivity`, which pops the folder stack.
     *
     * Asserted as "registers no enabled callback" rather than by dispatching a back press, for the
     * reason `FolderActivityTest` documents: with nothing to consume it the press reaches the
     * platform fallback, whose behavior on a task-root Activity is not what this is about.
     *
     * `hasEnabledCallbacks()` is activity-wide, so this holds the whole host to the claim, not the
     * screen alone. Nothing else here registers one, which is what makes it stand for the screen.
     */
    @Test
    fun folder_systemBack_outsideSelectionMode_isNotConsumed() {
        FileFixtures.createTextFile(rootDir, "readme.txt", "r")

        val robot = FolderScreenRobot(composeTestRule, rootDir)
        robot.render()
        robot.waitForText("readme.txt")

        val consumesBack = AtomicBoolean(true)
        composeTestRule.activityRule.scenario.onActivity {
            consumesBack.set(it.onBackPressedDispatcher.hasEnabledCallbacks())
        }

        assertFalse("A folder outside selection mode must not consume system back", consumesBack.get())
    }

    /** In selection mode, back must consume the gesture to clear the selection instead of leaving. */
    @Test
    fun folder_systemBack_inSelectionMode_exitsSelectionOnly() {
        FileFixtures.createTextFile(rootDir, "readme.txt", "r")
        var backNavigated = false

        val robot = FolderScreenRobot(composeTestRule, rootDir)
        robot.render(onNavigateBack = { backNavigated = true })
        robot.waitForText("readme.txt")
        robot.longClick("readme.txt")
        robot.waitForText(string(R.string.action_move_to))

        composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        robot.waitForTextToDisappear(string(R.string.action_move_to))
        assertFalse("Back in selection mode must not leave the folder", backNavigated)
    }

    // ==================== Breadcrumbs ====================

    @Test
    fun folder_breadcrumbs_showTheCurrentTrail() {
        val documents = FileFixtures.createFolder(rootDir, "Documents")
        val work = FileFixtures.createFolder(documents, "Work")
        FileFixtures.createTextFile(work, "work_marker.txt", "w")

        val robot = FolderScreenRobot(composeTestRule, work)
        robot.render()
        robot.waitForText("work_marker.txt")

        composeTestRule.onNodeWithText("Documents").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
    }

    @Test
    fun folder_tapBreadcrumb_navigatesToThatAncestor() {
        val documents = FileFixtures.createFolder(rootDir, "Documents")
        val work = FileFixtures.createFolder(documents, "Work")
        FileFixtures.createTextFile(work, "work_marker.txt", "w")

        var navigatedPath: String? = null
        val robot = FolderScreenRobot(composeTestRule, work)
        robot.render(onNavigateToFolder = { navigatedPath = it })
        robot.waitForText("work_marker.txt")

        composeTestRule.onNodeWithText("Documents").performClick()
        composeTestRule.waitForIdle()

        assertEquals(documents.absolutePath, navigatedPath)
    }
}
