package com.mauriciotogneri.fileexplorer.integration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.ui.components.Breadcrumbs
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The [Breadcrumbs] contract only: tapping a segment reports that ancestor's path, and the trail
 * rendered for a path is exactly its segments.
 *
 * Everything the *host* does with that path — pushing and popping the folder stack, the back button,
 * re-listing the directory — belongs to `FolderScreen` and is asserted against the real screen in
 * [NavigationIntegrationTest]. A third test here once simulated a back stack with a
 * `mutableStateListOf` the test itself popped, and asserted a `Text("Stack size: n")` the test
 * itself rendered; it would have stayed green with the app's back navigation deleted. Assert host
 * behaviour through the host, never through a stand-in declared here.
 */
@RunWith(AndroidJUnit4::class)
class BreadcrumbsIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun breadcrumbs_tapAncestor_navigatesBackCorrectLevels() {
        val navigationHistory = mutableListOf<String>()
        var currentPath by mutableStateOf("/storage/emulated/0/Documents/Work/Projects/App")

        composeTestRule.setContent {
            FileExplorerTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    Breadcrumbs(
                        currentPath = currentPath,
                        onNavigateToPath = { path ->
                            navigationHistory.add(path)
                            currentPath = path
                        },
                        rootPath = null,
                        rootDisplayName = null
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("App").assertIsDisplayed()

        composeTestRule.onNodeWithText("Documents").performClick()
        composeTestRule.waitForIdle()

        assertEquals("/storage/emulated/0/Documents", navigationHistory.last())
        assertEquals("/storage/emulated/0/Documents", currentPath)

        composeTestRule.onNodeWithText("Documents").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertDoesNotExist()
        composeTestRule.onNodeWithText("Projects").assertDoesNotExist()
        composeTestRule.onNodeWithText("App").assertDoesNotExist()
    }

    @Test
    fun breadcrumbs_navigateDeep_thenTapRoot_returnsToRoot() {
        var currentPath by mutableStateOf("/storage/emulated/0")

        composeTestRule.setContent {
            FileExplorerTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    Breadcrumbs(
                        currentPath = currentPath,
                        onNavigateToPath = { path -> currentPath = path },
                        rootPath = null,
                        rootDisplayName = null
                    )
                }
            }
        }

        currentPath = "/storage/emulated/0/Documents"
        composeTestRule.waitForIdle()

        currentPath = "/storage/emulated/0/Documents/Work"
        composeTestRule.waitForIdle()

        currentPath = "/storage/emulated/0/Documents/Work/Projects"
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Projects").assertIsDisplayed()

        composeTestRule.onNodeWithText("Internal Storage").performClick()
        composeTestRule.waitForIdle()

        assertEquals("/storage/emulated/0", currentPath)
        composeTestRule.onNodeWithText("Documents").assertDoesNotExist()
        composeTestRule.onNodeWithText("Work").assertDoesNotExist()
        composeTestRule.onNodeWithText("Projects").assertDoesNotExist()
    }
}
