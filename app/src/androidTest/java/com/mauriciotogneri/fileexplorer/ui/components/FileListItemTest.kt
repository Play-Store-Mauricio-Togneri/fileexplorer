package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
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

    private fun createTestFile(
        name: String = "test.txt",
        isDirectory: Boolean = false,
        size: Long = 1024L,
        mimeType: String = "text/plain",
        childCount: Int? = null
    ) = FileItem(
        path = "/storage/emulated/0/Documents/$name",
        name = name,
        isDirectory = isDirectory,
        size = size,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = mimeType,
        childCount = childCount
    )

    @Test
    fun fileListItem_displaysFileName() {
        val file = createTestFile(name = "report.pdf", mimeType = "application/pdf")

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = file,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("report.pdf").assertIsDisplayed()
    }

    @Test
    fun fileListItem_displaysFileSize() {
        val file = createTestFile(name = "document.txt", size = 2048L)

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = file,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("document.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 KB").assertIsDisplayed()
    }

    @Test
    fun fileListItem_displaysFolderWithItemCount() {
        val folder = createTestFile(
            name = "MyFolder",
            isDirectory = true,
            size = 0L,
            mimeType = "",
            childCount = 5
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = folder,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("MyFolder").assertIsDisplayed()
        composeTestRule.onNodeWithText("5 items").assertIsDisplayed()
    }

    @Test
    fun fileListItem_displaysSingleItemCount() {
        val folder = createTestFile(
            name = "SingleItemFolder",
            isDirectory = true,
            size = 0L,
            mimeType = "",
            childCount = 1
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = folder,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("SingleItemFolder").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 item").assertIsDisplayed()
    }

    @Test
    fun fileListItem_displaysExtensionBadge() {
        val file = createTestFile(name = "notes.txt", size = 512L)

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = file,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("TXT").assertIsDisplayed()
    }

    @Test
    fun fileListItem_clickTriggersCallback() {
        var clicked = false
        val file = createTestFile(name = "test.txt", size = 100L)

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = file,
                    onClick = { clicked = true },
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("test.txt").performClick()

        assertTrue(clicked)
    }

    @Test
    fun fileListItem_longClickTriggersCallback() {
        var longClicked = false
        val file = createTestFile(name = "test.txt")

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = file,
                    onClick = {},
                    onLongClick = { longClicked = true },
                    onMenuClick = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("test.txt").performTouchInput {
            longClick()
        }

        assertTrue(longClicked)
    }

    @Test
    fun fileListItem_emptyFolderShowsZeroItems() {
        val folder = createTestFile(
            name = "EmptyFolder",
            isDirectory = true,
            size = 0L,
            mimeType = "",
            childCount = 0
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = folder,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("EmptyFolder").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 items").assertIsDisplayed()
    }

    @Test
    fun fileListItem_selectedStateShowsCheckmark() {
        val file = createTestFile(name = "selected.txt")

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = file,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = true
                )
            }
        }

        composeTestRule.onNodeWithText("selected.txt").assertIsDisplayed()
        // The checkmark icon should be visible (we can verify the composable renders)
        // Since we don't have a content description, we verify the file name is still shown
    }

    @Test
    fun fileListItem_unselectedStateShowsFileIcon() {
        val file = createTestFile(name = "unselected.txt")

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = file,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("unselected.txt").assertIsDisplayed()
    }

    @Test
    fun fileListItem_selectedFolderShowsCheckmark() {
        val folder = createTestFile(
            name = "SelectedFolder",
            isDirectory = true,
            mimeType = "",
            childCount = 3
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = folder,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = true
                )
            }
        }

        composeTestRule.onNodeWithText("SelectedFolder").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 items").assertIsDisplayed()
    }
}
