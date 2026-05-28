package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BreadcrumbsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun breadcrumbs_displaysAllSegments() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Documents/Work",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText("Internal Storage").assertIsDisplayed()
        composeTestRule.onNodeWithText("Documents").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_rootSegment_showsStorageName() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Downloads",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText("Internal Storage").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_segmentTap_triggersNavigation() {
        var navigatedPath: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Documents/Work",
                    onNavigateToPath = { navigatedPath = it },
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText("Documents").performClick()

        assertEquals("/storage/emulated/0/Documents", navigatedPath)
    }

    @Test
    fun breadcrumbs_currentSegment_notClickable() {
        var navigatedPath: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Documents/Work",
                    onNavigateToPath = { navigatedPath = it },
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText("Work").performClick()

        assertNull(navigatedPath)
    }

    @Test
    fun breadcrumbs_horizontalScroll_works() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Level1/Level2/Level3/Level4/Level5/Level6/Level7/Level8",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNode(hasScrollAction()).assertExists()
        composeTestRule.onNode(hasScrollAction()).performScrollToIndex(0)
        composeTestRule.onNodeWithText("Internal Storage").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_autoScrollsToEnd() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Level1/Level2/Level3/Level4/Level5/Level6/Level7/DeepFolder",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("DeepFolder").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_singleSegment_displaysCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText("Internal Storage").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_deepPath_displaysAllSegments() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/L1/L2/L3/L4/L5/L6/L7/L8",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNode(hasScrollAction()).performScrollToIndex(0)
        composeTestRule.onNodeWithText("Internal Storage").assertIsDisplayed()

        composeTestRule.onNode(hasScrollAction()).performScrollToIndex(8)
        composeTestRule.onNodeWithText("L8").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_separatorIcons_displayed() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Documents/Work",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText("Internal Storage").assertIsDisplayed()
        composeTestRule.onNodeWithText("Documents").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_sdCardRoot_showsSdCardPath() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/1234-5678/DCIM",
                    onNavigateToPath = {},
                    rootPath = "/storage/1234-5678",
                    rootDisplayName = "SD Card"
                )
            }
        }

        composeTestRule.onNodeWithText("SD Card").assertIsDisplayed()
        composeTestRule.onNodeWithText("DCIM").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_specialCharactersInPath_displayCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/My Files & Documents",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText("My Files & Documents").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_customRootDisplayName_displaysCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Downloads/Work",
                    onNavigateToPath = {},
                    rootPath = "/storage/emulated/0/Downloads",
                    rootDisplayName = "Downloads"
                )
            }
        }

        composeTestRule.onNodeWithText("Downloads").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
        composeTestRule.onNodeWithText("Internal Storage").assertDoesNotExist()
    }

    @Test
    fun breadcrumbs_rootSegmentTap_triggersNavigation() {
        var navigatedPath: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Documents/Work",
                    onNavigateToPath = { navigatedPath = it },
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText("Internal Storage").performClick()

        assertEquals("/storage/emulated/0", navigatedPath)
    }

    @Test
    fun breadcrumbs_emptyPath_displaysNothing() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText("Internal Storage").assertDoesNotExist()
    }
}
