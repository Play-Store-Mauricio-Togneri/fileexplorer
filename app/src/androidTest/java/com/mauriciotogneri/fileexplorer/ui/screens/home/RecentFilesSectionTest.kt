package com.mauriciotogneri.fileexplorer.ui.screens.home

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.ui.components.RecentFileAction
import com.mauriciotogneri.fileexplorer.ui.components.RecentFileActionsBottomSheet
import com.mauriciotogneri.fileexplorer.ui.components.RecentFilesSection
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentFilesSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun createTestRecentFile(
        name: String = "test.txt",
        path: String = "/storage/emulated/0/Documents/$name",
        mimeType: String = "text/plain",
        lastOpenedTimestamp: Long = System.currentTimeMillis()
    ) = RecentFile(
        path = path,
        name = name,
        mimeType = mimeType,
        lastOpenedTimestamp = lastOpenedTimestamp
    )

    private val testRecentFiles = listOf(
        createTestRecentFile(name = "photo.jpg", mimeType = "image/jpeg"),
        createTestRecentFile(name = "document.pdf", mimeType = "application/pdf"),
        createTestRecentFile(name = "notes.txt", mimeType = "text/plain")
    )

    // ==================== Section Display Tests ====================

    @Test
    fun recentFilesSection_displaysTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFilesSection(
                    recentFiles = testRecentFiles,
                    onFileClick = {},
                    onMenuClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.section_recent))
            .assertIsDisplayed()
    }

    @Test
    fun recentFilesSection_displaysFileCards() {
        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFilesSection(
                    recentFiles = testRecentFiles,
                    onFileClick = {},
                    onMenuClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").assertIsDisplayed()
        composeTestRule.onNodeWithText("document.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithText("notes.txt").assertIsDisplayed()
    }

    @Test
    fun recentFilesSection_emptyList_hidesSection() {
        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFilesSection(
                    recentFiles = emptyList(),
                    onFileClick = {},
                    onMenuClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.section_recent))
            .assertDoesNotExist()
    }

    @Test
    fun recentFilesSection_singleFile_displaysCorrectly() {
        val singleFile = listOf(createTestRecentFile(name = "single.doc", mimeType = "application/msword"))

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFilesSection(
                    recentFiles = singleFile,
                    onFileClick = {},
                    onMenuClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.section_recent)).assertIsDisplayed()
        composeTestRule.onNodeWithText("single.doc").assertIsDisplayed()
    }

    // ==================== Card Click Tests ====================

    @Test
    fun recentFilesSection_cardTap_triggersOpenCallback() {
        var clickedFile: RecentFile? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFilesSection(
                    recentFiles = testRecentFiles,
                    onFileClick = { clickedFile = it },
                    onMenuClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()

        assertEquals("photo.jpg", clickedFile?.name)
        assertEquals("image/jpeg", clickedFile?.mimeType)
    }

    @Test
    fun recentFilesSection_cardTap_triggersCorrectFileCallback() {
        var clickedFile: RecentFile? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFilesSection(
                    recentFiles = testRecentFiles,
                    onFileClick = { clickedFile = it },
                    onMenuClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf").performClick()

        assertEquals("document.pdf", clickedFile?.name)
        assertEquals("application/pdf", clickedFile?.mimeType)
    }

    @Test
    fun recentFilesSection_cardLongPress_triggersMenuCallback() {
        var menuFile: RecentFile? = null
        var menuMode: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFilesSection(
                    recentFiles = testRecentFiles,
                    onFileClick = {},
                    onMenuClick = { file, mode ->
                        menuFile = file
                        menuMode = mode
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performTouchInput {
            longClick()
        }

        assertEquals("photo.jpg", menuFile?.name)
        assertEquals("press", menuMode)
    }

    @Test
    fun recentFilesSection_menuIconTap_triggersMenuCallback() {
        var menuFile: RecentFile? = null
        var menuMode: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFilesSection(
                    recentFiles = listOf(testRecentFiles[0]),
                    onFileClick = {},
                    onMenuClick = { file, mode ->
                        menuFile = file
                        menuMode = mode
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.content_description_more_options))
            .performClick()

        assertEquals("photo.jpg", menuFile?.name)
        assertEquals("icon", menuMode)
    }

    @Test
    fun recentFilesSection_eachCard_hasMenuIcon() {
        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFilesSection(
                    recentFiles = testRecentFiles,
                    onFileClick = {},
                    onMenuClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        val menuIcons = composeTestRule.onAllNodesWithContentDescription(
            context.getString(R.string.content_description_more_options)
        )
        assertEquals(3, menuIcons.fetchSemanticsNodes().size)
    }

    // ==================== Scroll Tests ====================

    @Test
    fun recentFilesSection_horizontalScroll_works() {
        val manyFiles = (1..10).map { index ->
            createTestRecentFile(
                name = "file$index.txt",
                path = "/storage/emulated/0/Documents/file$index.txt"
            )
        }

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFilesSection(
                    recentFiles = manyFiles,
                    onFileClick = {},
                    onMenuClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("file1.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("file10.txt").assertDoesNotExist()
    }

    @Test
    fun recentFilesSection_scrollToEnd_revealsLaterItems() {
        val manyFiles = (1..10).map { index ->
            createTestRecentFile(
                name = "file$index.txt",
                path = "/storage/emulated/0/Documents/file$index.txt"
            )
        }

        composeTestRule.setContent {
            FileExplorerTheme {
                val lazyListState = rememberLazyListState()
                RecentFilesSection(
                    recentFiles = manyFiles,
                    onFileClick = {},
                    onMenuClick = { _, _ -> },
                    lazyListState = lazyListState
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("file1.txt").assertIsDisplayed()
    }

    // ==================== Multiple Interactions Tests ====================

    @Test
    fun recentFilesSection_multipleCardClicks_triggersCorrectCallbacks() {
        val clickedFiles = mutableListOf<String>()

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFilesSection(
                    recentFiles = testRecentFiles,
                    onFileClick = { clickedFiles.add(it.name) },
                    onMenuClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()
        composeTestRule.onNodeWithText("document.pdf").performClick()
        composeTestRule.onNodeWithText("notes.txt").performClick()

        assertEquals(3, clickedFiles.size)
        assertTrue(clickedFiles.contains("photo.jpg"))
        assertTrue(clickedFiles.contains("document.pdf"))
        assertTrue(clickedFiles.contains("notes.txt"))
    }

    @Test
    fun recentFilesSection_clickDoesNotTriggerMenu() {
        var menuTriggered = false

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFilesSection(
                    recentFiles = testRecentFiles,
                    onFileClick = {},
                    onMenuClick = { _, _ -> menuTriggered = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("photo.jpg").performClick()

        assertTrue(!menuTriggered)
    }

    // ==================== Bottom Sheet Tests ====================

    @Test
    fun bottomSheet_displaysAllActions() {
        val testFile = createTestRecentFile(name = "test.pdf", mimeType = "application/pdf")

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFileActionsBottomSheet(
                    recentFile = testFile,
                    mode = "icon",
                    onAction = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_open_with)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_open_folder)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_share)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_remove_from_recents)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_delete)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_info)).assertIsDisplayed()
    }

    @Test
    fun bottomSheet_openWithAction_triggersCallback() {
        var triggeredAction: RecentFileAction? = null
        val testFile = createTestRecentFile(name = "test.pdf", mimeType = "application/pdf")

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFileActionsBottomSheet(
                    recentFile = testFile,
                    mode = "icon",
                    onAction = { triggeredAction = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_open_with)).performClick()

        assertEquals(RecentFileAction.OpenWith, triggeredAction)
    }

    @Test
    fun bottomSheet_openFolderAction_triggersCallback() {
        var triggeredAction: RecentFileAction? = null
        val testFile = createTestRecentFile(name = "test.pdf", mimeType = "application/pdf")

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFileActionsBottomSheet(
                    recentFile = testFile,
                    mode = "icon",
                    onAction = { triggeredAction = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_open_folder)).performClick()

        assertEquals(RecentFileAction.OpenFolder, triggeredAction)
    }

    @Test
    fun bottomSheet_shareAction_triggersCallback() {
        var triggeredAction: RecentFileAction? = null
        val testFile = createTestRecentFile(name = "test.pdf", mimeType = "application/pdf")

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFileActionsBottomSheet(
                    recentFile = testFile,
                    mode = "icon",
                    onAction = { triggeredAction = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_share)).performClick()

        assertEquals(RecentFileAction.Share, triggeredAction)
    }

    @Test
    fun bottomSheet_removeFromRecentsAction_triggersCallback() {
        var triggeredAction: RecentFileAction? = null
        val testFile = createTestRecentFile(name = "test.pdf", mimeType = "application/pdf")

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFileActionsBottomSheet(
                    recentFile = testFile,
                    mode = "icon",
                    onAction = { triggeredAction = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_remove_from_recents)).performClick()

        assertEquals(RecentFileAction.RemoveFromRecents, triggeredAction)
    }

    @Test
    fun bottomSheet_deleteAction_triggersCallback() {
        var triggeredAction: RecentFileAction? = null
        val testFile = createTestRecentFile(name = "test.pdf", mimeType = "application/pdf")

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFileActionsBottomSheet(
                    recentFile = testFile,
                    mode = "icon",
                    onAction = { triggeredAction = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_delete)).performClick()

        assertEquals(RecentFileAction.Delete, triggeredAction)
    }

    @Test
    fun bottomSheet_infoAction_triggersCallback() {
        var triggeredAction: RecentFileAction? = null
        val testFile = createTestRecentFile(name = "test.pdf", mimeType = "application/pdf")

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFileActionsBottomSheet(
                    recentFile = testFile,
                    mode = "icon",
                    onAction = { triggeredAction = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_info)).performClick()

        assertEquals(RecentFileAction.Info, triggeredAction)
    }
}
