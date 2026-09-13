package com.mauriciotogneri.fileexplorer.ui.screens.analyzercategory

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerFileEntry
import com.mauriciotogneri.fileexplorer.data.repository.AnalyzerResultsHolder
import com.mauriciotogneri.fileexplorer.data.repository.CategoryFiles
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter
import com.mauriciotogneri.fileexplorer.testutil.buttonWithText
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The disk read behind a page is bounded by the emulator, not by the test. */
private const val WAIT_TIMEOUT_MILLIS = 10_000L

/**
 * The category listing against a real temp tree, through the real [AnalyzerCategoryViewModel]: the
 * screen's whole job is to read files back a page at a time and act on what it finds, so a fake
 * reader would leave the part under test unexercised.
 */
@RunWith(AndroidJUnit4::class)
class AnalyzerCategoryScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private lateinit var root: File

    @Before
    fun setUp() {
        root = File(activity.cacheDir, "analyzer-category-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        AnalyzerResultsHolder.clear()
        root.deleteRecursively()
    }

    @Test
    fun header_namesTheCategoryAndItsShareOfTheVolume() {
        render(fileCount = 3, totalBytes = 4_096L)

        composeTestRule.onNodeWithText(string(R.string.location_images)).assertIsDisplayed()
        composeTestRule.onNodeWithText(FileSizeFormatter.format(4_096L)).assertIsDisplayed()
    }

    @Test
    fun list_showsTheBiggestFileFirst() {
        render(fileCount = 3)

        composeTestRule.onNodeWithText("file0.bin").assertIsDisplayed()
    }

    @Test
    fun rows_respondToATap() {
        render(fileCount = 1)

        composeTestRule.onNodeWithText("file0.bin").assertHasClickAction()
    }

    @Test
    fun backArrow_isTheWayOut() {
        render(fileCount = 1)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.navigate_back))
            .assertHasClickAction()
    }

    @Test
    fun rows_carryAMenu() {
        render(fileCount = 1)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.content_description_more_options))
            .assertHasClickAction()
    }

    @Test
    fun rowMenu_offersOpenWithOpenFolderDeleteAndInfo() {
        render(fileCount = 1)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.content_description_more_options))
            .performClick()

        waitForText(string(R.string.action_open_with))
        composeTestRule.onNodeWithText(string(R.string.action_open_folder)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_info)).assertIsDisplayed()

        // Everything the folder screen offers that this sheet deliberately leaves out.
        assertEquals(0, nodeCount(string(R.string.action_share)))
        assertEquals(0, nodeCount(string(R.string.action_rename)))
        assertEquals(0, nodeCount(string(R.string.action_move_to)))
        assertEquals(0, nodeCount(string(R.string.action_copy_to)))
    }

    @Test
    fun longPress_selectsTheRow() {
        render(fileCount = 3)

        composeTestRule.onNodeWithText("file0.bin").performTouchInput { longClick() }

        composeTestRule.onNodeWithText(selectionCount(1)).assertIsDisplayed()
    }

    @Test
    fun selection_offersDeleteAndNothingElse() {
        render(fileCount = 3)

        composeTestRule.onNodeWithText("file0.bin").performTouchInput { longClick() }
        waitForText(string(R.string.action_delete))

        assertEquals(0, nodeCount(string(R.string.action_share)))
        assertEquals(0, nodeCount(string(R.string.action_move_to)))
        assertEquals(0, nodeCount(string(R.string.action_copy_to)))
        assertEquals(0, nodeCount(string(R.string.action_compress)))
    }

    @Test
    fun selection_hidesTheRowMenus() {
        render(fileCount = 3)

        composeTestRule.onNodeWithText("file0.bin").performTouchInput { longClick() }
        waitForText(selectionCount(1))

        assertEquals(
            0,
            composeTestRule
                .onAllNodesWithContentDescription(string(R.string.content_description_more_options))
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun selectAll_isOfferedInTheToolbar() {
        render(fileCount = 1)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.action_select_all))
            .assertHasClickAction()
    }

    @Test
    fun selectAll_picksEveryLoadedRow() {
        render(fileCount = 3)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.action_select_all))
            .performClick()

        composeTestRule.onNodeWithText(selectionCount(3)).assertIsDisplayed()
    }

    @Test
    fun clearingTheSelection_bringsBackTheCategoryHeader() {
        render(fileCount = 3)

        composeTestRule.onNodeWithText("file0.bin").performTouchInput { longClick() }
        waitForText(selectionCount(1))

        composeTestRule
            .onNodeWithContentDescription(string(R.string.content_description_clear_selection))
            .performClick()

        composeTestRule.onNodeWithText(string(R.string.location_images)).assertIsDisplayed()
    }

    @Test
    fun deleting_takesTheRowOffTheListAndItsBytesOffTheTotal() {
        // Sizes 3, 2 and 1 against a total that is their sum, so the header is checked against
        // arithmetic rather than against a figure the screen could have left untouched.
        render(fileCount = 3, totalBytes = 6L)

        composeTestRule.onNodeWithText("file0.bin").performTouchInput { longClick() }
        waitForText(string(R.string.action_delete))
        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()

        confirmDelete()

        composeTestRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeTestRule.onAllNodesWithText("file0.bin").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("file1.bin").assertIsDisplayed()
        composeTestRule.onNodeWithText(FileSizeFormatter.format(3L)).assertIsDisplayed()
    }

    @Test
    fun deleting_correctsTheChartTheListingWasOpenedFrom() {
        render(fileCount = 3, totalBytes = 6L)

        composeTestRule.onNodeWithText("file0.bin").performTouchInput { longClick() }
        waitForText(string(R.string.action_delete))
        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()

        confirmDelete()

        composeTestRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            AnalyzerResultsHolder.filesFor(AnalyzerCategory.IMAGES)?.totalBytes == 3L
        }
        assertEquals(2, AnalyzerResultsHolder.filesFor(AnalyzerCategory.IMAGES)?.entries?.size)
    }

    @Test
    fun list_beyondOnePage_loadsTheNextOnReachingTheBottom() {
        render(fileCount = AnalyzerCategoryViewModel.PAGE_SIZE + 20)

        // The loader is the last item of the first page, so PAGE_SIZE is the highest index that
        // exists yet — scrolling past it would be out of bounds rather than a paging trigger.
        composeTestRule.onNode(hasScrollAction())
            .performScrollToIndex(AnalyzerCategoryViewModel.PAGE_SIZE)

        // Composing the loader asks for the next page, which then takes the loader's own slot.
        waitForText("file${AnalyzerCategoryViewModel.PAGE_SIZE}.bin")

        composeTestRule.onNode(hasScrollAction())
            .performScrollToIndex(AnalyzerCategoryViewModel.PAGE_SIZE + 10)

        composeTestRule.onNodeWithText("file110.bin").assertIsDisplayed()
    }

    @Test
    fun emptyCategory_saysSoRatherThanShowingABlankList() {
        render(fileCount = 0)

        composeTestRule.onNodeWithText(string(R.string.analyzer_category_empty)).assertIsDisplayed()
    }

    /**
     * [fileCount] real files, each smaller than the one before it, biggest first — handed over the
     * way a completed scan leaves them, so the chart correction a delete makes is observable.
     */
    private fun render(fileCount: Int, totalBytes: Long = 1_024L) {
        val entries = (0 until fileCount).map { index ->
            val size = (fileCount - index).toLong()
            val file = File(root, "file$index.bin")
            file.writeBytes(ByteArray(size.toInt()))

            AnalyzerFileEntry(path = file.path, size = size)
        }

        val categoryFiles = CategoryFiles(totalBytes = totalBytes, entries = entries)
        AnalyzerResultsHolder.store(mapOf(AnalyzerCategory.IMAGES to categoryFiles))

        val viewModel = AnalyzerCategoryViewModel.Factory(
            application = activity.application,
            category = AnalyzerCategory.IMAGES,
            categoryFiles = categoryFiles
        ).create(AnalyzerCategoryViewModel::class.java)

        composeTestRule.setContent {
            FileExplorerTheme {
                AnalyzerCategoryScreen(
                    category = AnalyzerCategory.IMAGES,
                    viewModel = viewModel,
                    onCloseClick = {}
                )
            }
        }

        // The first page is read off disk on Dispatchers.IO, which waitForIdle does not track, so
        // every assertion below would otherwise race the read that produces the rows.
        if (fileCount > 0) {
            waitForText("file0.bin")
        } else {
            composeTestRule.waitForIdle()
        }
    }

    /** Waits for [text] to be composed, rather than for a frame that may not carry it yet. */
    private fun waitForText(text: String) {
        composeTestRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * `action_delete`, `delete_confirm_title` and `dialog_delete` all read "Delete", so the bar
     * button, the dialog title and its confirm button cannot be told apart by text. The confirm
     * button is the one carrying the Material button role.
     */
    private fun confirmDelete() {
        composeTestRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeTestRule.onAllNodes(buttonWithText(string(R.string.dialog_delete)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(buttonWithText(string(R.string.dialog_delete))).performClick()
    }

    private fun nodeCount(text: String): Int =
        composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun selectionCount(count: Int): String =
        activity.resources.getQuantityString(R.plurals.selection_count, count, count)

    private fun string(@StringRes id: Int): String = activity.getString(id)
}
