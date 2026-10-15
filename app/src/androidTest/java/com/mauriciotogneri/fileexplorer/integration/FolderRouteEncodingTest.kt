package com.mauriciotogneri.fileexplorer.integration

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.activities.FolderNavHost
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.ui.navigation.Routes
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Regression tests for folder route argument encoding/decoding.
 *
 * Navigation Compose URL-decodes route arguments exactly once (via Uri.decode).
 * [Routes.folder] encodes values with Uri.encode() to match, and destinations read
 * the already-decoded values directly. A previous implementation decoded a second
 * time with URLDecoder, which crashed with
 * `IllegalArgumentException: URLDecoder: Illegal hex characters in escape (%) pattern`
 * whenever a file/folder name contained a literal '%' (e.g. "%#@"), and silently
 * corrupted names containing spaces or '+'.
 *
 * These tests drive the real [FolderNavHost] that FolderActivity hosts, over real folders carrying
 * those names, and assert on what the folder screen renders: a marker file inside the folder only
 * lists when the path arrived intact, and the title and root breadcrumb show the query arguments.
 * The previous version declared its own NavHost with a copy of the route's arguments, so a second
 * decode put back in the real destination left it green.
 */
@RunWith(AndroidJUnit4::class)
class FolderRouteEncodingTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(composeTestRule.activity.cacheDir, "test_route_encoding_${System.currentTimeMillis()}")
            .apply { mkdirs() }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    private fun folderWithMarker(name: String): File {
        val folder = FileFixtures.createFolder(testDir, name)
        FileFixtures.createTextFile(folder, MARKER)
        return folder
    }

    private fun render(
        path: String,
        title: String? = null,
        rootPath: String? = null,
        rootDisplayName: String? = null
    ) {
        composeTestRule.setContent {
            FileExplorerTheme {
                FolderNavHost(
                    path = path,
                    title = title,
                    rootPath = rootPath,
                    rootDisplayName = rootDisplayName,
                    onFinish = {}
                )
            }
        }
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(10_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertFolderOpensAtStart(name: String) {
        val folder = folderWithMarker(name)

        render(folder.absolutePath)

        waitForText(MARKER)
        composeTestRule.onNodeWithText(MARKER).assertIsDisplayed()
    }

    @Test
    fun folderPath_withPercentLiteral_roundTripsWithoutCrashing() {
        // Exactly reproduces the Crashlytics crash input ("%#@").
        assertFolderOpensAtStart("100%#@")
    }

    @Test
    fun folderPath_withSpace_roundTripsAsSpaceNotPlus() {
        assertFolderOpensAtStart("My Folder")
    }

    @Test
    fun folderPath_withPlusLiteral_roundTripsAsPlusNotSpace() {
        assertFolderOpensAtStart("a+b")
    }

    @Test
    fun folderPath_withReservedUriCharacters_roundTrips() {
        assertFolderOpensAtStart("a#b&c=d?e")
    }

    @Test
    fun openingChildFolder_withPercentLiteral_navigatesIntoIt() {
        // The start destination and a folder opened from the list build their routes separately;
        // this covers the second, which pushes through navController.navigate.
        folderWithMarker("100%#@")

        render(testDir.absolutePath)
        waitForText("100%#@")
        composeTestRule.onNodeWithText("100%#@").performClick()

        waitForText(MARKER)
        composeTestRule.onNodeWithText(MARKER).assertIsDisplayed()
    }

    @Test
    fun folderQueryArgs_withPercentLiteral_roundTripWithoutCrashing() {
        val root = FileFixtures.createFolder(testDir, "r%t")
        val folder = folderWithMarker("r%t/Music")
        val title = "50% off"
        val rootDisplayName = "Root %#@"

        render(folder.absolutePath, title, root.absolutePath, rootDisplayName)

        waitForText(MARKER)
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(rootDisplayName).assertIsDisplayed()
    }

    private companion object {
        const val MARKER = "marker.txt"
    }
}
