package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwipeableFileListItemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createTestFile(
        name: String = "document.pdf",
        isDirectory: Boolean = false,
        size: Long = 1024L,
        mimeType: String = "application/pdf",
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
    fun swipeRight_revealsDeleteAction() {
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onDelete = {},
                    onRename = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Delete").assertIsDisplayed()
    }

    @Test
    fun swipeLeft_revealsRenameAction() {
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onDelete = {},
                    onRename = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeLeft(startX = centerX, endX = left)
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Rename").assertIsDisplayed()
    }

    @Test
    fun partialSwipe_doesNotRevealAction() {
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onDelete = {},
                    onRename = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = centerX + 20f)
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Delete").assertDoesNotExist()
    }

    @Test
    fun swipeInSelectionMode_disabled() {
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onDelete = {},
                    onRename = {},
                    isSelected = true
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Delete").assertDoesNotExist()
    }

    @Test
    fun tapWhileRevealed_collapsesAction() {
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onDelete = {},
                    onRename = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Delete").assertIsDisplayed()

        composeTestRule.onNodeWithText("document.pdf").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Delete").assertDoesNotExist()
    }

    @Test
    fun swipeOneItem_othersUnaffected() {
        val file1 = createTestFile(name = "file1.pdf")
        val file2 = createTestFile(name = "file2.pdf")
        val file3 = createTestFile(name = "file3.pdf")

        composeTestRule.setContent {
            FileExplorerTheme {
                Column {
                    SwipeableFileListItem(
                        file = file1,
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        onDelete = {},
                        onRename = {},
                        isSelected = false
                    )
                    SwipeableFileListItem(
                        file = file2,
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        onDelete = {},
                        onRename = {},
                        isSelected = false
                    )
                    SwipeableFileListItem(
                        file = file3,
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        onDelete = {},
                        onRename = {},
                        isSelected = false
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("file1.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("file2.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithText("file3.pdf").assertIsDisplayed()
    }

    @Test
    fun folder_swipeActionsWork() {
        val testFolder = createTestFile(
            name = "MyFolder",
            isDirectory = true,
            size = 0L,
            mimeType = "",
            childCount = 5
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFolder,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onDelete = {},
                    onRename = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("MyFolder").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Delete").assertIsDisplayed()
    }

    @Test
    fun folder_swipeLeftShowsRename() {
        val testFolder = createTestFile(
            name = "MyFolder",
            isDirectory = true,
            size = 0L,
            mimeType = "",
            childCount = 5
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFolder,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onDelete = {},
                    onRename = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("MyFolder").performTouchInput {
            swipeLeft(startX = centerX, endX = left)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Rename").assertIsDisplayed()
    }

    @Test
    fun tapWhileRevealed_doesNotTriggerOnClick() {
        var clickTriggered = false
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = { clickTriggered = true },
                    onLongClick = {},
                    onMenuClick = {},
                    onDelete = {},
                    onRename = {},
                    isSelected = false
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("document.pdf").performClick()
        composeTestRule.waitForIdle()

        assertFalse(clickTriggered)
    }
}
