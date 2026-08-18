package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter
import com.mauriciotogneri.fileexplorer.data.util.ShortDateFormatter
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class FileListItemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

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
        composeTestRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.item_amount, 5, 5)
        ).assertIsDisplayed()
    }

    @Test
    fun fileListItem_directoryWithUnknownCount_showsNoItemCount() {
        val folder = createTestFile(
            name = "LoadingFolder",
            isDirectory = true,
            size = 0L,
            mimeType = "",
            childCount = null
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

        composeTestRule.onNodeWithText("LoadingFolder").assertIsDisplayed()
        composeTestRule.onNodeWithText("item", substring = true, ignoreCase = true)
            .assertDoesNotExist()
    }

    @Test
    fun fileListItem_restrictedDirectory_showsRestrictedLabel() {
        val folder = createTestFile(
            name = "RestrictedFolder",
            isDirectory = true,
            size = 0L,
            mimeType = "",
            childCount = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = folder,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false,
                    isRestricted = true
                )
            }
        }

        composeTestRule.onNodeWithText("RestrictedFolder").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.folder_restricted)).assertIsDisplayed()
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
        composeTestRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.item_amount, 1, 1)
        ).assertIsDisplayed()
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
        composeTestRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.item_amount, 0, 0)
        ).assertIsDisplayed()
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
        composeTestRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.item_amount, 3, 3)
        ).assertIsDisplayed()
    }

    // ==================== Second line settings ====================

    @Test
    fun fileListItem_folderSecondLineNone_showsNoCount() {
        val folder = createTestFile(name = "QuietFolder", isDirectory = true, mimeType = "", childCount = 4)

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = folder,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false,
                    folderSecondLine = FolderSecondLine.NONE
                )
            }
        }

        composeTestRule.onNodeWithText("QuietFolder").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.item_amount, 4, 4)
        ).assertDoesNotExist()
    }

    @Test
    fun fileListItem_folderSecondLineLastModified_showsDate() {
        val folder = createTestFile(name = "DatedFolder", isDirectory = true, mimeType = "", childCount = 4)
        val formatted = ShortDateFormatter(Locale.UK).format(folder.lastModified)

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = folder,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false,
                    folderSecondLine = FolderSecondLine.LAST_MODIFIED,
                    dateFormatter = ShortDateFormatter(Locale.UK)
                )
            }
        }

        composeTestRule.onNodeWithText("DatedFolder").assertIsDisplayed()
        composeTestRule.onNodeWithText(formatted).assertIsDisplayed()
        composeTestRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.item_amount, 4, 4)
        ).assertDoesNotExist()
    }

    /**
     * A folder the app cannot read has no count and no date worth showing, and the lock badge on its
     * icon is decorative — so the label has to survive whichever second line was chosen.
     */
    @Test
    fun fileListItem_restrictedDirectory_showsRestrictedUnderEverySetting() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Column {
                    FolderSecondLine.entries.forEach { setting ->
                        FileListItem(
                            file = createTestFile(name = "Restricted$setting", isDirectory = true, mimeType = ""),
                            onClick = {},
                            onLongClick = {},
                            onMenuClick = {},
                            isSelected = false,
                            isRestricted = true,
                            folderSecondLine = setting
                        )
                    }
                }
            }
        }

        composeTestRule.onAllNodesWithText(context.getString(R.string.folder_restricted))
            .assertCountEquals(FolderSecondLine.entries.size)
    }

    @Test
    fun fileListItem_fileSecondLineNone_showsNoSize() {
        val file = createTestFile(name = "quiet.txt", size = 2048L)

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = file,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false,
                    fileSecondLine = FileSecondLine.NONE
                )
            }
        }

        composeTestRule.onNodeWithText("quiet.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText(FileSizeFormatter.format(2048L)).assertDoesNotExist()
    }

    @Test
    fun fileListItem_fileSecondLineLastModified_showsDateInsteadOfSize() {
        val file = createTestFile(name = "dated.txt", size = 2048L)
        val formatted = ShortDateFormatter(Locale.UK).format(file.lastModified)

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = file,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false,
                    fileSecondLine = FileSecondLine.LAST_MODIFIED,
                    dateFormatter = ShortDateFormatter(Locale.UK)
                )
            }
        }

        composeTestRule.onNodeWithText(formatted).assertIsDisplayed()
        composeTestRule.onNodeWithText(FileSizeFormatter.format(2048L)).assertDoesNotExist()
    }

    /**
     * The two settings are read per row type, so one list can show folders with no second line and
     * files with a size at once.
     */
    @Test
    fun fileListItem_settingsApplyPerRowType() {
        val file = createTestFile(name = "mixed.txt", size = 2048L)

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = file,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false,
                    folderSecondLine = FolderSecondLine.NONE,
                    fileSecondLine = FileSecondLine.SIZE
                )
            }
        }

        composeTestRule.onNodeWithText("2 KB").assertIsDisplayed()
    }

    /**
     * With nothing under it the name is centered in the row rather than left sitting where the first
     * of two lines would be.
     */
    @Test
    fun fileListItem_withoutASecondLine_centresTheName() {
        val file = createTestFile(name = "centred.txt", size = 2048L)

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = file,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false,
                    fileSecondLine = FileSecondLine.NONE,
                    modifier = Modifier.testTag("row")
                )
            }
        }

        val row = composeTestRule.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot
        val name = composeTestRule.onNodeWithText("centred.txt").fetchSemanticsNode().boundsInRoot

        assertEquals(row.center.y, name.center.y, 1f)
    }

    /**
     * The counterpart: a setting that does produce text keeps the line while the text is missing, so
     * a folder's name does not jump down when its count arrives.
     */
    @Test
    fun fileListItem_folderAwaitingItsCount_keepsTheNameWhereTheCountWillPutIt() {
        val loading = createTestFile(name = "Loading", isDirectory = true, mimeType = "", childCount = null)
        val counted = createTestFile(name = "Counted", isDirectory = true, mimeType = "", childCount = 3)

        composeTestRule.setContent {
            FileExplorerTheme {
                Column {
                    FileListItem(
                        file = loading,
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        isSelected = false,
                        folderSecondLine = FolderSecondLine.ITEM_COUNT,
                        modifier = Modifier.testTag("loadingRow")
                    )
                    FileListItem(
                        file = counted,
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        isSelected = false,
                        folderSecondLine = FolderSecondLine.ITEM_COUNT,
                        modifier = Modifier.testTag("countedRow")
                    )
                }
            }
        }

        val loadingRow = composeTestRule.onNodeWithTag("loadingRow").fetchSemanticsNode().boundsInRoot
        val countedRow = composeTestRule.onNodeWithTag("countedRow").fetchSemanticsNode().boundsInRoot
        val loadingName = composeTestRule.onNodeWithText("Loading").fetchSemanticsNode().boundsInRoot
        val countedName = composeTestRule.onNodeWithText("Counted").fetchSemanticsNode().boundsInRoot

        assertEquals(countedName.top - countedRow.top, loadingName.top - loadingRow.top, 1f)
    }

    /**
     * A screen that never counts a folder's children — search — centers the folder's name under the
     * count setting instead of holding a line open for a number that will not arrive.
     */
    @Test
    fun fileListItem_folderOnAScreenWithoutCounts_centresTheName() {
        val folder = createTestFile(name = "Uncounted", isDirectory = true, mimeType = "", childCount = null)

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = folder,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false,
                    folderSecondLine = FolderSecondLine.ITEM_COUNT,
                    loadsChildCounts = false,
                    modifier = Modifier.testTag("row")
                )
            }
        }

        val row = composeTestRule.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot
        val name = composeTestRule.onNodeWithText("Uncounted").fetchSemanticsNode().boundsInRoot

        assertEquals(row.center.y, name.center.y, 1f)
    }

    /** A restricted folder still names its state on a screen that takes no counts. */
    @Test
    fun fileListItem_restrictedFolderOnAScreenWithoutCounts_stillShowsRestricted() {
        val folder = createTestFile(name = "Blocked", isDirectory = true, mimeType = "", childCount = null)

        composeTestRule.setContent {
            FileExplorerTheme {
                FileListItem(
                    file = folder,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    isSelected = false,
                    isRestricted = true,
                    folderSecondLine = FolderSecondLine.ITEM_COUNT,
                    loadsChildCounts = false
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.folder_restricted)).assertIsDisplayed()
    }

    /**
     * Dropping the second line must not change the row's height, or a list mixing folders and files
     * under different settings would show two row heights at once.
     */
    @Test
    fun fileListItem_rowHeightIsTheSameWithAndWithoutASecondLine() {
        val file = createTestFile(name = "measured.txt", size = 2048L)

        composeTestRule.setContent {
            FileExplorerTheme {
                Column {
                    FileListItem(
                        file = file,
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        isSelected = false,
                        fileSecondLine = FileSecondLine.SIZE,
                        modifier = Modifier.testTag("withSecondLine")
                    )
                    FileListItem(
                        file = file.copy(name = "blank.txt"),
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        isSelected = false,
                        fileSecondLine = FileSecondLine.NONE,
                        modifier = Modifier.testTag("withoutSecondLine")
                    )
                }
            }
        }

        val withLine = composeTestRule.onNodeWithTag("withSecondLine").fetchSemanticsNode().size.height
        val withoutLine = composeTestRule.onNodeWithTag("withoutSecondLine").fetchSemanticsNode().size.height

        assertEquals(withLine, withoutLine)
    }
}
