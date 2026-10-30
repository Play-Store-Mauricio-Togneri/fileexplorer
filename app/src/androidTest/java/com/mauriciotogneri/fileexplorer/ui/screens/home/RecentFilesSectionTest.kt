package com.mauriciotogneri.fileexplorer.ui.screens.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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

    // The second card, not the first: a section that passed `recentFiles.first()` to every card's
    // long press would still open the right sheet for the first one.
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
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            longClick()
        }

        assertEquals(testRecentFiles[1], menuFile)
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
        // onNode, not onAllNodes[0]: one recent file is rendered, so exactly one overflow must exist.
        // Indexing would have picked the first of a duplicated set rather than failing on it.
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.content_description_more_options))
            .performClick()

        assertEquals("photo.jpg", menuFile?.name)
        assertEquals("icon", menuMode)
    }

    /**
     * Each card's overflow must report *that* card's file. The test above renders one card and the
     * count below checks no identity, so `onIconClick = { onMenuClick(recentFiles.first(), "icon") }`
     * — every card opening the first file's sheet — was green everywhere.
     *
     * The overflow is addressed through the card that owns it (`hasAnyAncestor(hasText(name))`)
     * rather than by index into `onAllNodes`, which would pick a duplicate rather than fail on it.
     */
    @Test
    fun recentFilesSection_eachCardsMenu_reportsItsOwnFile() {
        val received = mutableListOf<RecentFile>()

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFilesSection(
                    recentFiles = testRecentFiles,
                    onFileClick = {},
                    onMenuClick = { file, _ -> received += file }
                )
            }
        }

        composeTestRule.waitForIdle()
        testRecentFiles.forEach { file ->
            composeTestRule
                .onNode(
                    hasContentDescription(context.getString(R.string.content_description_more_options)) and
                        hasAnyAncestor(hasText(file.name))
                )
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()
        }

        assertEquals(testRecentFiles, received)
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

    private fun manyRecentFiles() = (1..10).map { index ->
        createTestRecentFile(
            name = "file$index.txt",
            path = "/storage/emulated/0/Documents/file$index.txt"
        )
    }

    /**
     * A swipe rather than a programmatic scroll: `userScrollEnabled = false` on the LazyRow would
     * freeze the strip for users while `scrollToItem()` kept working, so only a gesture catches it.
     */
    @Test
    fun recentFilesSection_horizontalScroll_works() {
        val manyFiles = manyRecentFiles()
        lateinit var lazyListState: LazyListState

        composeTestRule.setContent {
            FileExplorerTheme {
                lazyListState = rememberLazyListState()
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

        composeTestRule.onNodeWithText("file1.txt").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        assertTrue(
            "Swiping must move the recent-files strip",
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0
        )
    }

    /**
     * Scrolls through the `ScrollToIndex` semantics action rather than calling
     * `lazyListState.scrollToItem` inside `runOnIdle`. That call suspends on
     * `waitForFirstLayout()`, which only resumes from a main-looper callback — and `runOnIdle`
     * has already blocked the main thread, so the test would hang rather than fail. The action
     * is also absent when `userScrollEnabled = false`, so this fails loudly on that regression
     * too. Same pattern as [BreadcrumbsTest].
     */
    @Test
    fun recentFilesSection_scrollToEnd_revealsLaterItems() {
        val manyFiles = manyRecentFiles()

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
        // The last card starts off-screen; reaching it is what this test is named for.
        composeTestRule.onNodeWithText("file10.txt").assertDoesNotExist()

        composeTestRule.onNode(hasScrollAction()).performScrollToIndex(manyFiles.lastIndex)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("file10.txt").assertIsDisplayed()
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
                    isFavorite = false,
                    isDirectory = false,
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
        composeTestRule.onNodeWithText(context.getString(R.string.action_add_to_favorites)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_delete)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_info)).assertIsDisplayed()
    }

    // The favourite row flips on isFavorite. A sheet that ignored it would offer "Add to favorites"
    // for a file that already is one, and the user could never remove it from here.
    @Test
    fun bottomSheet_favorite_offersRemoveFromFavorites() {
        val testFile = createTestRecentFile(name = "test.pdf", mimeType = "application/pdf")

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFileActionsBottomSheet(
                    recentFile = testFile,
                    mode = "icon",
                    isFavorite = true,
                    isDirectory = false,
                    onAction = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_remove_from_favorites)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_add_to_favorites)).assertDoesNotExist()
    }

    // The row's label and its action come from two separate `isFavorite` branches, so they can
    // disagree: this and the remove test below each pin one label to the action it names.
    @Test
    fun bottomSheet_addToFavoritesAction_triggersCallback() {
        var triggeredAction: RecentFileAction? = null
        val testFile = createTestRecentFile(name = "test.pdf", mimeType = "application/pdf")

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFileActionsBottomSheet(
                    recentFile = testFile,
                    mode = "icon",
                    isFavorite = false,
                    isDirectory = false,
                    onAction = { triggeredAction = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_add_to_favorites)).performClick()

        assertEquals(RecentFileAction.AddToFavorites, triggeredAction)
    }

    @Test
    fun bottomSheet_removeFromFavoritesAction_triggersCallback() {
        var triggeredAction: RecentFileAction? = null
        val testFile = createTestRecentFile(name = "test.pdf", mimeType = "application/pdf")

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFileActionsBottomSheet(
                    recentFile = testFile,
                    mode = "icon",
                    isFavorite = true,
                    isDirectory = false,
                    onAction = { triggeredAction = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_remove_from_favorites)).performClick()

        assertEquals(RecentFileAction.RemoveFromFavorites, triggeredAction)
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
                    isFavorite = false,
                    isDirectory = false,
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
                    isFavorite = false,
                    isDirectory = false,
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
                    isFavorite = false,
                    isDirectory = false,
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
                    isFavorite = false,
                    isDirectory = false,
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
                    isFavorite = false,
                    isDirectory = false,
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
                    isFavorite = false,
                    isDirectory = false,
                    onAction = { triggeredAction = it },
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_info)).performClick()

        assertEquals(RecentFileAction.Info, triggeredAction)
    }

    // A recents entry is a file by contract, but the store re-validates a stored path with exists()
    // alone, which a directory satisfies. The card's tap handler navigates into such a path, so the
    // sheet must not keep offering the two actions that hand it to another app as a file.
    @Test
    fun bottomSheet_directory_hidesOpenWithAndShare() {
        val testFile = createTestRecentFile(name = "notes.md", mimeType = "text/markdown")

        composeTestRule.setContent {
            FileExplorerTheme {
                RecentFileActionsBottomSheet(
                    recentFile = testFile,
                    mode = "icon",
                    isFavorite = false,
                    isDirectory = true,
                    onAction = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.action_open_with)).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.action_share)).assertDoesNotExist()

        // The parent is a folder either way, and the rest of the sheet stays usable.
        composeTestRule.onNodeWithText(context.getString(R.string.action_open_folder)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_remove_from_recents)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_delete)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.action_info)).assertIsDisplayed()
    }
}
