package com.mauriciotogneri.fileexplorer.ui.screens.analyzercategory

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerFileEntry
import com.mauriciotogneri.fileexplorer.data.repository.CategoryFiles
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The disk read behind a page is bounded by the emulator, not by the test. */
private const val WAIT_TIMEOUT_MILLIS = 10_000L

/**
 * The category listing against a real temp tree, through the real [AnalyzerCategoryViewModel]: the
 * screen's whole job is to read files back a page at a time, so a fake reader would leave the part
 * under test unexercised.
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
    fun rows_doNotRespondToATap() {
        render(fileCount = 1)

        composeTestRule.onNodeWithText("file0.bin").assertHasNoClickAction()
    }

    @Test
    fun backArrow_isTheWayOut() {
        render(fileCount = 1)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.navigate_back))
            .assertHasClickAction()
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

    /** [fileCount] real files, each smaller than the one before it, biggest first. */
    private fun render(fileCount: Int, totalBytes: Long = 1_024L) {
        val entries = (0 until fileCount).map { index ->
            val size = (fileCount - index).toLong()
            val file = File(root, "file$index.bin")
            file.writeBytes(ByteArray(size.toInt()))

            AnalyzerFileEntry(path = file.path, size = size)
        }

        val viewModel = AnalyzerCategoryViewModel(
            categoryFiles = CategoryFiles(totalBytes = totalBytes, entries = entries)
        )

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

    private fun string(@StringRes id: Int): String = activity.getString(id)
}
