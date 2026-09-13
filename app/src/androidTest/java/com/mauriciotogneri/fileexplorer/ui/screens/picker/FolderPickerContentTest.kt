package com.mauriciotogneri.fileexplorer.ui.screens.picker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderPickerContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Fixture folders, so the picker renders the on-disk name and the literals below are correct in
    // kind. They were called "Documents" and "Downloads" until those collided with the
    // `location_documents` / `location_downloads` resource values: a matcher written as a literal
    // that a translation also owns is locale-dependent, and `assertDoesNotExist()` on one — which
    // `emptyFolderList_showsNothing` and `error_showsErrorMessage` both do — passes off-locale
    // whether or not the row is there. No <string> value holds these names.
    private val testFolders = listOf(
        FileItem(
            path = "/storage/emulated/0/Ledgers",
            name = "Ledgers",
            isDirectory = true,
            size = 0L,
            lastModified = 1000L,
            createdTime = 1000L,
            mimeType = "",
            childCount = 10
        ),
        FileItem(
            path = "/storage/emulated/0/Pictures",
            name = "Pictures",
            isDirectory = true,
            size = 0L,
            lastModified = 2000L,
            createdTime = 2000L,
            mimeType = "",
            childCount = 20
        ),
        FileItem(
            path = "/storage/emulated/0/Parcels",
            name = "Parcels",
            isDirectory = true,
            size = 0L,
            lastModified = 3000L,
            createdTime = 3000L,
            mimeType = "",
            childCount = 5
        )
    )

    @Test
    fun folderNames_areDisplayed() {
        composeTestRule.setContent {
            FileExplorerTheme {
                FolderPickerContent(
                    folders = testFolders,
                    isLoading = false,
                    error = null,
                    onFolderClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Ledgers").assertIsDisplayed()
        composeTestRule.onNodeWithText("Pictures").assertIsDisplayed()
        composeTestRule.onNodeWithText("Parcels").assertIsDisplayed()
    }

    @Test
    fun folderTap_triggersCallback() {
        var clickedFolder: FileItem? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                FolderPickerContent(
                    folders = testFolders,
                    isLoading = false,
                    error = null,
                    onFolderClick = { clickedFolder = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Pictures").performClick()

        assertEquals(testFolders[1], clickedFolder)
    }

    @Test
    fun emptyFolderList_showsNothing() {
        composeTestRule.setContent {
            FileExplorerTheme {
                FolderPickerContent(
                    folders = emptyList(),
                    isLoading = false,
                    error = null,
                    onFolderClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Ledgers").assertDoesNotExist()
        composeTestRule.onNodeWithText("Pictures").assertDoesNotExist()
    }

    /**
     * The spinner itself, not the absence of rows: with `folders = emptyList()` no folder name can
     * appear under any branch, so asserting one is missing passes with `isLoading = false` too —
     * and with the CircularProgressIndicator deleted.
     */
    @Test
    fun loading_showsProgressIndicator() {
        composeTestRule.setContent {
            FileExplorerTheme {
                FolderPickerContent(
                    folders = emptyList(),
                    isLoading = true,
                    error = null,
                    onFolderClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(FOLDER_PICKER_LOADING_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun notLoading_hidesProgressIndicator() {
        composeTestRule.setContent {
            FileExplorerTheme {
                FolderPickerContent(
                    folders = emptyList(),
                    isLoading = false,
                    error = null,
                    onFolderClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(FOLDER_PICKER_LOADING_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun notLoading_showsFolders() {
        composeTestRule.setContent {
            FileExplorerTheme {
                FolderPickerContent(
                    folders = testFolders,
                    isLoading = false,
                    error = null,
                    onFolderClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Ledgers").assertIsDisplayed()
    }

    @Test
    fun error_showsErrorMessage() {
        val errorMessage = "Unable to load files"

        composeTestRule.setContent {
            FileExplorerTheme {
                FolderPickerContent(
                    folders = emptyList(),
                    isLoading = false,
                    error = errorMessage,
                    onFolderClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
        composeTestRule.onNodeWithText("Ledgers").assertDoesNotExist()
    }
}
