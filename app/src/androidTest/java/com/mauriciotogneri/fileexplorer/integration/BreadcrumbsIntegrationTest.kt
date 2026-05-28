package com.mauriciotogneri.fileexplorer.integration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
                    Text(text = "Current: $currentPath")
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
        val pathStack = mutableStateListOf("/storage/emulated/0")

        composeTestRule.setContent {
            FileExplorerTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    Breadcrumbs(
                        currentPath = currentPath,
                        onNavigateToPath = { path ->
                            currentPath = path
                            val index = pathStack.indexOf(path)
                            if (index >= 0) {
                                while (pathStack.size > index + 1) {
                                    pathStack.removeAt(pathStack.lastIndex)
                                }
                            }
                        },
                        rootPath = null,
                        rootDisplayName = null
                    )
                }
            }
        }

        currentPath = "/storage/emulated/0/Documents"
        pathStack.add("/storage/emulated/0/Documents")
        composeTestRule.waitForIdle()

        currentPath = "/storage/emulated/0/Documents/Work"
        pathStack.add("/storage/emulated/0/Documents/Work")
        composeTestRule.waitForIdle()

        currentPath = "/storage/emulated/0/Documents/Work/Projects"
        pathStack.add("/storage/emulated/0/Documents/Work/Projects")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Projects").assertIsDisplayed()

        composeTestRule.onNodeWithText("Internal Storage").performClick()
        composeTestRule.waitForIdle()

        assertEquals("/storage/emulated/0", currentPath)
        assertEquals(1, pathStack.size)
        assertEquals("/storage/emulated/0", pathStack[0])
    }

    @Test
    fun breadcrumbs_backButton_matchesBreadcrumbState() {
        var currentPath by mutableStateOf("/storage/emulated/0/Documents/Work/Projects")
        val backStack = mutableStateListOf(
            "/storage/emulated/0",
            "/storage/emulated/0/Documents",
            "/storage/emulated/0/Documents/Work",
            "/storage/emulated/0/Documents/Work/Projects"
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                Column(modifier = Modifier.fillMaxSize()) {
                    Breadcrumbs(
                        currentPath = currentPath,
                        onNavigateToPath = { path ->
                            val index = backStack.indexOf(path)
                            if (index >= 0) {
                                while (backStack.size > index + 1) {
                                    backStack.removeAt(backStack.lastIndex)
                                }
                                currentPath = path
                            }
                        },
                        rootPath = null,
                        rootDisplayName = null
                    )
                    Text(text = "Stack size: ${backStack.size}")
                }
            }
        }

        composeTestRule.onNodeWithText("Stack size: 4").assertIsDisplayed()
        composeTestRule.onNodeWithText("Projects").assertIsDisplayed()

        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            currentPath = backStack.last()
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Stack size: 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()

        composeTestRule.onNodeWithText("Documents").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Stack size: 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Documents").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertDoesNotExist()

        assertEquals("/storage/emulated/0/Documents", currentPath)
        assertEquals(2, backStack.size)
    }
}
