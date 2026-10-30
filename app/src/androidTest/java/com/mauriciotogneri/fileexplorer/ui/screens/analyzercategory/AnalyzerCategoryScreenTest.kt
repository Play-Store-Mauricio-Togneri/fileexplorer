package com.mauriciotogneri.fileexplorer.ui.screens.analyzercategory

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import androidx.core.content.FileProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.FolderActivity
import com.mauriciotogneri.fileexplorer.activities.ItemInfoActivity
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerCategory
import com.mauriciotogneri.fileexplorer.data.model.AnalyzerFileEntry
import com.mauriciotogneri.fileexplorer.data.repository.AnalyzerResultsHolder
import com.mauriciotogneri.fileexplorer.data.repository.CategoryFiles
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.testutil.assumePlatformMapsApkExtension
import com.mauriciotogneri.fileexplorer.testutil.buttonWithText
import com.mauriciotogneri.fileexplorer.testutil.removeRecentFilesOnceWritten
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
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

    /**
     * The fixtures a test opened, by a tap or through "open with". A launched open counts as an
     * open even when the launch is stubbed, so `IntentUtil` writes the file to the app's own recents
     * store; the suite shares one data directory, so removing it again is this test's job.
     */
    private val openedPaths = mutableListOf<String>()

    @Before
    fun setUp() {
        root = File(activity.cacheDir, "analyzer-category-${System.nanoTime()}").apply { mkdirs() }
        // Four of the five row actions leave this screen. Stubbing every launch keeps the tap a tap
        // and makes the intent itself the assertion.
        Intents.init()
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        removeRecentFilesOnceWritten(openedPaths)
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

        // The scan already hands the rows over biggest first, so this pins what the screen does
        // with that order: a reversed layout or a re-sort in the list would still display all
        // three rows, and only their positions tell it apart.
        val tops = (0 until 3).map { index ->
            composeTestRule.onNodeWithText("file$index.bin").getUnclippedBoundsInRoot().top
        }
        assertEquals("Rows are not drawn biggest first: $tops", tops.sorted(), tops)
        assertEquals("Two rows share a position: $tops", 3, tops.distinct().size)
    }

    /**
     * A row carries a click action whatever its tap does — long-press selection attaches one — so
     * only the launch tells an open apart from a tap that goes nowhere. An emptied open branch
     * leaves the listing unable to open the files it names.
     */
    @Test
    fun rows_tapOpensTheFile() {
        render(fileCount = 1)
        val file = File(root, "file0.bin")
        openedPaths += file.path

        composeTestRule.onNodeWithText("file0.bin").performClick()
        composeTestRule.waitForIdle()

        // The direct launch and its fallback both carry the file's FileProvider uri, so this holds
        // whichever one the device takes.
        val expectedUri = FileProvider.getUriForFile(activity, "${activity.packageName}.provider", file)
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(expectedUri)))
    }

    // An IconButton wired to nothing still carries a click action, and then the arrow strands the
    // user on the listing: the caller's close is what it must reach.
    @Test
    fun backArrow_closesTheListing() {
        var closed = false
        render(fileCount = 1, onCloseClick = { closed = true })

        composeTestRule
            .onNodeWithContentDescription(string(R.string.navigate_back))
            .performClick()
        composeTestRule.waitForIdle()

        assertTrue("The back arrow did not close the listing", closed)
    }

    @Test
    fun rowMenu_offersOpenWithOpenFolderShareDeleteAndInfo() {
        render(fileCount = 1)

        composeTestRule
            .onNodeWithContentDescription(string(R.string.content_description_more_options))
            .performClick()

        waitForText(string(R.string.action_open_with))
        composeTestRule.onNodeWithText(string(R.string.action_open_folder)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_share)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_info)).assertIsDisplayed()

        // Everything the folder screen offers that this sheet deliberately leaves out.
        assertEquals(0, nodeCount(string(R.string.action_rename)))
        assertEquals(0, nodeCount(string(R.string.action_move_to)))
        assertEquals(0, nodeCount(string(R.string.action_copy_to)))
    }

    // Each sheet action is asserted by the effect only it produces, so a callback wired to the wrong
    // branch — Info opening the folder, "open with" wired to nothing — fails here instead of
    // shipping. `deleting_takesTheRowOffTheListAndItsBytesOffTheTotal` below covers the *selection*
    // bar's delete, which is a different call, so the sheet's own is driven here too.

    @Test
    fun rowMenu_openWith_handsTheFileToTheSystemChooser() {
        render(fileCount = 1)
        openedPaths += File(root, "file0.bin").path

        openRowMenu()
        composeTestRule.onNodeWithText(string(R.string.action_open_with)).performClick()

        // The chooser is the platform's own resolver, so no component of this app names it: the
        // action is what tells this launch apart from the two that target an activity.
        intended(hasAction(Intent.ACTION_CHOOSER))
    }

    @Test
    fun rowMenu_openFolder_opensTheFolderTheFileSitsIn() {
        render(fileCount = 1)

        openRowMenu()
        composeTestRule.onNodeWithText(string(R.string.action_open_folder)).performClick()

        // The component only: FolderActivity's path extra key is private, the same limit
        // ActivityNavigationTest records.
        intended(hasComponent(FolderActivity::class.java.name))
    }

    @Test
    fun rowMenu_share_handsTheFileToTheShareSheet() {
        render(fileCount = 1)

        openRowMenu()
        composeTestRule.onNodeWithText(string(R.string.action_share)).performClick()

        // "Open with" launches a chooser too, so the intent it wraps is what tells the two apart.
        intended(hasExtra(equalTo(Intent.EXTRA_INTENT), hasAction(Intent.ACTION_SEND)))
    }

    @Test
    fun rowMenu_info_opensTheItemInfoScreen() {
        render(fileCount = 1)

        openRowMenu()
        composeTestRule.onNodeWithText(string(R.string.action_info)).performClick()

        intended(hasComponent(ItemInfoActivity::class.java.name))
    }

    @Test
    fun rowMenu_delete_asksFirstAndThenTakesTheRowOff() {
        render(fileCount = 1)

        openRowMenu()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()

        // No dialog means the sheet's delete went somewhere else: nothing else it offers asks first.
        confirmDelete()

        composeTestRule.waitUntil(WAIT_TIMEOUT_MILLIS) {
            composeTestRule.onAllNodesWithText("file0.bin").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("file0.bin").assertDoesNotExist()
    }

    @Test
    fun longPress_selectsTheRow() {
        render(fileCount = 3)

        composeTestRule.onNodeWithText("file0.bin").performTouchInput { longClick() }

        composeTestRule.onNodeWithText(selectionCount(1)).assertIsDisplayed()
    }

    @Test
    fun selection_offersShareAndDeleteAndNothingElse() {
        render(fileCount = 3)

        composeTestRule.onNodeWithText("file0.bin").performTouchInput { longClick() }
        waitForText(string(R.string.action_delete))

        composeTestRule.onNodeWithText(string(R.string.action_share)).assertIsDisplayed()
        assertEquals(0, nodeCount(string(R.string.action_move_to)))
        assertEquals(0, nodeCount(string(R.string.action_copy_to)))
        assertEquals(0, nodeCount(string(R.string.action_compress)))
    }

    @Test
    fun selection_share_handsEverySelectedFileToTheShareSheetAndClearsTheSelection() {
        render(fileCount = 3)

        composeTestRule.onNodeWithText("file0.bin").performTouchInput { longClick() }
        waitForText(selectionCount(1))
        composeTestRule.onNodeWithText("file1.bin").performClick()
        waitForText(selectionCount(2))

        composeTestRule.onNodeWithText(string(R.string.action_share)).performClick()

        // SEND_MULTIPLE rather than SEND: sharing only the first selected file would still launch
        // a share sheet.
        intended(hasExtra(equalTo(Intent.EXTRA_INTENT), hasAction(Intent.ACTION_SEND_MULTIPLE)))
        waitForText(string(R.string.location_images))
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

    // With the back handler dropped or gated on the wrong state, system back in selection mode
    // closes the whole listing and the selection with it, instead of just leaving the selection.
    @Test
    fun selection_systemBack_clearsTheSelectionRatherThanLeaving() {
        render(fileCount = 3)
        val host = activity

        composeTestRule.onNodeWithText("file0.bin").performTouchInput { longClick() }
        waitForText(selectionCount(1))

        composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        // Checked first: a finished activity takes its UI with it, and the absence below would then
        // hold for nothing.
        assertFalse("Back in selection mode closed the listing", host.isFinishing)
        composeTestRule.onNodeWithText(selectionCount(1)).assertDoesNotExist()
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
     * Not a second copy of `FileOpenRoutingTest`'s dialog test, despite the shared name: each screen
     * wires the dialog's Settings button by hand, so each copy breaks on its own, and this pins the
     * analyzer listing's. The package uri is part of it — without it the platform opens the list of
     * every app instead of this one's toggle.
     */
    @Test
    fun apkPermissionDialog_settingsButton_firesManageUnknownSourcesIntent() {
        assumeTrue("Install permission check requires Android O+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        assumeFalse("Device has pre-granted unknown sources install permission", activity.packageManager.canRequestPackageInstalls())
        assertFalse("IntentUtil.canInstallApks must return false when platform permission is missing", IntentUtil.canInstallApks(activity))
        val apk = FileFixtures.createFakeApk(root, "app.apk")
        assumePlatformMapsApkExtension()

        render(listOf(apk), totalBytes = apk.length())
        composeTestRule.onNodeWithText("app.apk").performClick()

        waitForText(string(R.string.apk_permission_settings))
        composeTestRule.onNodeWithText(string(R.string.apk_permission_settings)).performClick()
        composeTestRule.waitForIdle()

        intended(
            allOf(
                hasAction(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
                hasData("package:${activity.packageName}")
            )
        )
    }

    /**
     * [fileCount] real files, each smaller than the one before it, biggest first — handed over the
     * way a completed scan leaves them, so the chart correction a delete makes is observable.
     */
    private fun render(fileCount: Int, totalBytes: Long = 1_024L, onCloseClick: () -> Unit = {}) {
        val files = (0 until fileCount).map { index ->
            File(root, "file$index.bin").apply { writeBytes(ByteArray(fileCount - index)) }
        }

        render(files, totalBytes, onCloseClick)
    }

    /** [files] as the category's listing, in the order given, each entered at its size on disk. */
    private fun render(files: List<File>, totalBytes: Long, onCloseClick: () -> Unit = {}) {
        val entries = files.map { file -> AnalyzerFileEntry(path = file.path, size = file.length()) }

        val categoryFiles = CategoryFiles(totalBytes = totalBytes, entries = entries)
        AnalyzerResultsHolder.store(mapOf(AnalyzerCategory.IMAGES to categoryFiles))

        // The factory reads the results out of the holder above, which is where a completed scan
        // leaves them and the only place this screen ever gets them from.
        val viewModel = AnalyzerCategoryViewModel.Factory(
            application = activity.application,
            category = AnalyzerCategory.IMAGES
        ).create(AnalyzerCategoryViewModel::class.java)

        composeTestRule.setContent {
            FileExplorerTheme {
                AnalyzerCategoryScreen(
                    category = AnalyzerCategory.IMAGES,
                    viewModel = viewModel,
                    onCloseClick = onCloseClick
                )
            }
        }

        // The first page is read off disk on Dispatchers.IO, which waitForIdle does not track, so
        // every assertion below would otherwise race the read that produces the rows.
        if (files.isNotEmpty()) {
            waitForText(files.first().name)
        } else {
            composeTestRule.waitForIdle()
        }
    }

    /** Opens the actions sheet of the only row on screen, and waits for it to be there. */
    private fun openRowMenu() {
        composeTestRule
            .onNodeWithContentDescription(string(R.string.content_description_more_options))
            .performClick()

        waitForText(string(R.string.action_open_with))
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
