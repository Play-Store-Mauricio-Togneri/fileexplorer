package com.mauriciotogneri.fileexplorer.integration

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.SortManager
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderScreen
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Stage 5 (Point 5): verifies that selecting each sort mode actually REORDERS the folder list
 * (existing tests only confirmed the sheet shows options and a callback fires).
 *
 * Real-wiring: the real [FolderScreen] + real `FolderViewModel`/`FileRepository`/`SortManager` list
 * a temp dir of fixtures with distinct name / size / mtime, so every ordering is unambiguous. Sort
 * mode is changed through the real UI (overflow menu -> "Sort by" -> the sheet option). Row order is
 * read from each name node's vertical position, so no production `testTag` is required.
 *
 * `SortManager` is a process-global singleton (and `FolderViewModel` persists the mode to DataStore);
 * it is reset to NAME_ASC in `@Before`/`@After` to keep tests order-independent.
 *
 * Note: the "selected mode indicator" case from the plan is intentionally omitted — selection is
 * conveyed only via text color (not exposed in semantics), so it cannot be asserted robustly.
 */
@RunWith(AndroidJUnit4::class)
class FolderSortingTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private lateinit var testDir: File

    @Before
    fun setUp() {
        SortManager.setSortMode(SortMode.NAME_ASC)
        testDir = File(activity.cacheDir, "test_sorting_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        SortManager.setSortMode(SortMode.NAME_ASC)
        testDir.deleteRecursively()
    }

    // ==================== Name ====================

    @Test
    fun sortNameAsc_ordersAlphabetically() {
        SortManager.setSortMode(SortMode.NAME_DESC) // start reversed so selecting NAME_ASC visibly reorders
        createNameSizeDateFixtures()
        renderFolder()
        assertVerticalOrder("c.txt", "b.txt", "a.txt")

        selectSortMode(R.string.sort_name_asc)
        assertVerticalOrder("a.txt", "b.txt", "c.txt")
    }

    @Test
    fun sortNameDesc_ordersReverseAlphabetically() {
        createNameSizeDateFixtures()
        renderFolder()
        assertVerticalOrder("a.txt", "b.txt", "c.txt")

        selectSortMode(R.string.sort_name_desc)
        assertVerticalOrder("c.txt", "b.txt", "a.txt")
    }

    // ==================== Size ====================

    @Test
    fun sortSizeAsc_ordersBySizeAscending() {
        createNameSizeDateFixtures() // sizes: a=1, c=2000, b=3000
        renderFolder()
        assertVerticalOrder("a.txt", "b.txt", "c.txt")

        selectSortMode(R.string.sort_size_asc)
        assertVerticalOrder("a.txt", "c.txt", "b.txt")
    }

    @Test
    fun sortSizeDesc_ordersBySizeDescending() {
        createNameSizeDateFixtures()
        renderFolder()
        assertVerticalOrder("a.txt", "b.txt", "c.txt")

        selectSortMode(R.string.sort_size_desc)
        assertVerticalOrder("b.txt", "c.txt", "a.txt")
    }

    // ==================== Date (lastModified) ====================

    @Test
    fun sortDateAsc_ordersByModifiedAscending() {
        val (a, b, c) = createNameSizeDateFixtures() // mtimes: b oldest, c middle, a newest
        assumeTrue(modifiedTimesAreOrdered(b, c, a))
        renderFolder()

        selectSortMode(R.string.sort_date_asc)
        assertVerticalOrder("b.txt", "c.txt", "a.txt")
    }

    @Test
    fun sortDateDesc_ordersByModifiedDescending() {
        val (a, b, c) = createNameSizeDateFixtures()
        assumeTrue(modifiedTimesAreOrdered(b, c, a))
        renderFolder()

        selectSortMode(R.string.sort_date_desc)
        assertVerticalOrder("a.txt", "c.txt", "b.txt")
    }

    // ==================== Folders-vs-files contract ====================

    @Test
    fun foldersListedBeforeFiles_regardlessOfName() {
        FileFixtures.createFolder(testDir, "zzz_folder")
        FileFixtures.createTextFile(testDir, "a.txt", "a")
        renderFolder()

        // NAME_ASC default: the folder sorts first despite its 'z' name, ahead of the file.
        assertVerticalOrder("zzz_folder", "a.txt")
    }

    /**
     * Changing the sort must put the user at the top of the new order, not follow the row they were
     * looking at.
     *
     * `LazyColumn` is keyed by path, so it anchors the viewport to the *item* that was on top rather
     * than to its index. That is what holds the user's place across a refresh, and it is why a sort
     * change used to jump: re-sorting does not move the viewport, it moves the row the viewport is
     * pinned to, so switching to Z-A while sitting at the top of an A-Z order followed `file_00` all
     * the way to the end and left the user at the bottom of the list.
     *
     * Forty rows so the two ends cannot be on screen together — with the anchor left in place the
     * new first row is not composed at all, so this fails as "no node found" rather than passing on
     * a list that happens to fit. `file_00` is asserted absent as well, because "the new top row is
     * visible" alone would also hold on a viewport that merely drifted near it.
     */
    @Test
    fun changingSortMode_returnsTheListToTheTopOfTheNewOrder() {
        repeat(ROWS_SPANNING_MORE_THAN_ONE_SCREEN) { index ->
            FileFixtures.createTextFile(testDir, "file_%02d.txt".format(index))
        }
        renderFolder()
        // Precondition: A-Z puts file_00 on top, which is the row the viewport anchors to.
        awaitText("file_00.txt")
        composeTestRule.onNodeWithText("file_00.txt").assertIsDisplayed()

        selectSortMode(R.string.sort_name_desc)

        val topOfNewOrder = "file_%02d.txt".format(ROWS_SPANNING_MORE_THAN_ONE_SCREEN - 1)
        awaitText(topOfNewOrder)
        composeTestRule.onNodeWithText(topOfNewOrder).assertIsDisplayed()
        composeTestRule.onNodeWithText("file_00.txt").assertDoesNotExist()
    }

    // ==================== Helpers ====================

    /**
     * a.txt: size 1, newest mtime. b.txt: size 3000, oldest mtime. c.txt: size 2000, middle mtime.
     * Distinct across name, size, and mtime so each sort mode produces a unique order.
     */
    private fun createNameSizeDateFixtures(): Triple<File, File, File> {
        val now = System.currentTimeMillis()
        val a = FileFixtures.createTextFile(testDir, "a.txt", "a")
        val b = FileFixtures.createTextFile(testDir, "b.txt", "b".repeat(3000))
        val c = FileFixtures.createTextFile(testDir, "c.txt", "c".repeat(2000))
        b.setLastModified(now - 300_000)
        c.setLastModified(now - 200_000)
        a.setLastModified(now - 100_000)
        return Triple(a, b, c)
    }

    private fun modifiedTimesAreOrdered(oldest: File, middle: File, newest: File): Boolean =
        oldest.lastModified() < middle.lastModified() && middle.lastModified() < newest.lastModified()

    private fun renderFolder() {
        composeTestRule.setContent {
            FileExplorerTheme {
                FolderScreen(
                    path = testDir.absolutePath,
                    onNavigateToFolder = {},
                    onNavigateBack = {}
                )
            }
        }
    }

    private fun selectSortMode(@StringRes optionRes: Int) {
        openOverflowMenu()
        composeTestRule.onNodeWithText(string(R.string.menu_sort_by)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(optionRes)).performClick()
        composeTestRule.waitForIdle()
    }

    /**
     * The toolbar overflow shares its "more options" content description with every row's menu
     * button. The toolbar action lives in the top app bar, so it is the topmost match.
     */
    private fun openOverflowMenu() {
        val cd = string(R.string.content_description_more_options)
        val nodes = composeTestRule.onAllNodesWithContentDescription(cd)
        val tops = nodes.fetchSemanticsNodes().map { it.boundsInRoot.top }
        val topIndex = tops.indices.minByOrNull { tops[it] }
            ?: error("No overflow menu node found")
        nodes[topIndex].performClick()
        composeTestRule.waitForIdle()
    }

    private fun assertVerticalOrder(vararg names: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { isTopToBottom(names) }.getOrDefault(false)
        }
        assertTrue(
            "Expected top-to-bottom order ${names.toList()} but tops were ${names.map { topOf(it) }}",
            isTopToBottom(names)
        )
    }

    private fun isTopToBottom(names: Array<out String>): Boolean =
        names.map { topOf(it) }.zipWithNext().all { (upper, lower) -> upper < lower }

    private fun topOf(name: String): Float =
        composeTestRule.onNodeWithText(name).fetchSemanticsNode().boundsInRoot.top

    /**
     * The listing runs on `Dispatchers.IO`, which Compose's idling sync does not cover, so
     * `waitForIdle()` can return while `state.files` is still the previous order. Every other
     * assertion in this file waits through [assertVerticalOrder]; these need the same.
     */
    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = activity.getString(id)

    private companion object {
        const val ROWS_SPANNING_MORE_THAN_ONE_SCREEN = 40
    }
}
