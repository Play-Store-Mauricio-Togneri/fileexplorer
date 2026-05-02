package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileListItemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun fileListItem_displaysFileName() {
        val file = FileItem(
            path = "/storage/emulated/0/Documents/report.pdf",
            name = "report.pdf",
            isDirectory = false,
            size = 1024 * 1024L,
            lastModified = System.currentTimeMillis(),
            mimeType = "application/pdf",
            childCount = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = file,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("report.pdf").assertIsDisplayed()
    }

    @Test
    fun fileListItem_displaysFileSize() {
        val file = FileItem(
            path = "/storage/emulated/0/Documents/document.txt",
            name = "document.txt",
            isDirectory = false,
            size = 2048L,
            lastModified = System.currentTimeMillis(),
            mimeType = "text/plain",
            childCount = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = file,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("document.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 KB").assertIsDisplayed()
    }

    @Test
    fun fileListItem_displaysFolderWithItemCount() {
        val folder = FileItem(
            path = "/storage/emulated/0/Documents/MyFolder",
            name = "MyFolder",
            isDirectory = true,
            size = 0L,
            lastModified = System.currentTimeMillis(),
            mimeType = "",
            childCount = 5
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = folder,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("MyFolder").assertIsDisplayed()
        composeTestRule.onNodeWithText("5 items").assertIsDisplayed()
    }

    @Test
    fun fileListItem_displaysSingleItemCount() {
        val folder = FileItem(
            path = "/storage/emulated/0/Documents/SingleItemFolder",
            name = "SingleItemFolder",
            isDirectory = true,
            size = 0L,
            lastModified = System.currentTimeMillis(),
            mimeType = "",
            childCount = 1
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = folder,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("SingleItemFolder").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 item").assertIsDisplayed()
    }

    @Test
    fun fileListItem_displaysExtensionBadge() {
        val file = FileItem(
            path = "/storage/emulated/0/Documents/notes.txt",
            name = "notes.txt",
            isDirectory = false,
            size = 512L,
            lastModified = System.currentTimeMillis(),
            mimeType = "text/plain",
            childCount = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = file,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("TXT").assertIsDisplayed()
    }

    @Test
    fun fileListItem_clickTriggersCallback() {
        var clicked = false
        val file = FileItem(
            path = "/storage/emulated/0/Documents/test.txt",
            name = "test.txt",
            isDirectory = false,
            size = 100L,
            lastModified = System.currentTimeMillis(),
            mimeType = "text/plain",
            childCount = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = file,
                    onClick = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("test.txt").performClick()

        assertTrue(clicked)
    }

    @Test
    fun fileListItem_emptyFolderShowsZeroItems() {
        val folder = FileItem(
            path = "/storage/emulated/0/Documents/EmptyFolder",
            name = "EmptyFolder",
            isDirectory = true,
            size = 0L,
            lastModified = System.currentTimeMillis(),
            mimeType = "",
            childCount = 0
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = folder,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("EmptyFolder").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 items").assertIsDisplayed()
    }
}
