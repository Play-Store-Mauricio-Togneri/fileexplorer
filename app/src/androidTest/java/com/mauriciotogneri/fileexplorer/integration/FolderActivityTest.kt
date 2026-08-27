package com.mauriciotogneri.fileexplorer.integration

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.FolderActivity
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Back-stack behavior of the standalone [FolderActivity]: the launch folder, and the breadcrumb
 * taps that pop back to it or open a folder above it.
 *
 * At the launch folder the internal NavHost has nothing to pop and the screen is not in selection
 * mode, so the Activity registers no enabled `OnBackPressedCallback`: a system back is not
 * intercepted and propagates to the system, which returns to whatever launched the Activity.
 *
 * We assert that "does not intercept back" contract rather than the Activity actually finishing,
 * because the finish is platform behavior [ActivityScenario] cannot reproduce: it launches
 * FolderActivity as the task root, and on API 33+ the platform back fallback does not finish a
 * task-root Activity (predictive back). In production FolderActivity is always started on top of
 * the app's task (see its callers in HomeScreen/SearchScreen), so it is never the task root and the
 * same back press does finish it.
 *
 * The breadcrumb tests below cover the two ways the NavHost resolves a tapped ancestor, which need
 * the real [FolderActivity] because they turn on what its NavController holds. The ordinary trail
 * itself — which crumbs are shown, and which path each reports — is covered against a caller-owned
 * stack by `NavigationIntegrationTest` and `BreadcrumbsIntegrationTest`.
 */
@RunWith(AndroidJUnit4::class)
class FolderActivityTest {

    // An empty Compose rule launches nothing itself; it lets the test wait for the Activity's
    // NavHost to finish its first composition (via waitUntil below) before inspecting back state.
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(context.cacheDir, "folderact_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun launchFolder_doesNotInterceptSystemBack() {
        val intent = FolderActivity.createIntent(context, testDir.absolutePath)
        ActivityScenario.launch<FolderActivity>(intent).use { scenario ->
            // Wait until FolderScreen is actually on screen (its back button exists) so the NavHost
            // has composed and registered its back callbacks. Only then is hasEnabledCallbacks()
            // meaningful: a false result then means the callbacks are registered-but-disabled, not
            // merely not-yet-created.
            val backDescription = context.getString(R.string.navigate_back)
            composeTestRule.waitUntil(timeoutMillis = 20_000) {
                composeTestRule.onAllNodesWithContentDescription(backDescription)
                    .fetchSemanticsNodes().isNotEmpty()
            }

            // At the launch folder nothing should consume system back: the NavController has nothing
            // to pop and the selection-mode BackHandler is disabled, so the press propagates to the
            // system. Read on the main thread; onActivity blocks until the block completes.
            val interceptsBack = AtomicBoolean(true)
            scenario.onActivity { activity ->
                interceptsBack.set(activity.onBackPressedDispatcher.hasEnabledCallbacks())
            }

            assertFalse(
                "System back at the launch folder must not be intercepted by the app",
                interceptsBack.get()
            )
        }
    }

    /**
     * The breadcrumb trail is trimmed to the root the Activity was launched with, which the startup
     * folder setting sets to the storage the folder lives on — above the launch folder. Those
     * ancestors are therefore rendered without ever having been pushed on the NavHost's back stack,
     * and tapping one must open that folder rather than pop entries that do not exist: popping past
     * the launch entry empties the stack, and the NavHost goes on composing the entry it just
     * popped, held at CREATED, so the screen stops collecting its state.
     */
    @Test
    fun ancestorBreadcrumbAboveLaunchFolder_opensThatFolder() {
        val rootMarker = FileFixtures.createTextFile(testDir, "marker_root.txt")
        val folder = FileFixtures.createFolder(testDir, "One")
        val folderMarker = FileFixtures.createTextFile(folder, "marker_one.txt")

        val intent = FolderActivity.createIntent(
            context = context,
            path = folder.absolutePath,
            title = TITLE,
            rootPath = testDir.absolutePath,
            rootDisplayName = ROOT_NAME
        )
        ActivityScenario.launch<FolderActivity>(intent).use { scenario ->
            awaitText(folderMarker.name)

            composeTestRule.onNodeWithText(ROOT_NAME).performClick()

            // The root's own content proves the tap navigated and the screen is still collecting
            // state; over-popping instead leaves the folder frozen on its last content.
            awaitText(rootMarker.name)

            // The launch folder is still on the stack, so back returns to it instead of leaving.
            assertTrue(
                "Back must return to the launch folder rather than propagate to the system",
                interceptsBack(scenario)
            )
        }
    }

    /**
     * The other resolution: an ancestor that *is* on the back stack must be popped back to, not
     * pushed on top of. Everything launched from the home screen or a search hit passes its own
     * path as the root, so this is the ordinary breadcrumb tap — and a silent degradation from
     * popping to pushing would leave back walking the user deeper instead of out.
     *
     * Asserted through the launch folder's own crumb, where the two outcomes are distinguishable:
     * popping leaves the single launch entry and no enabled back callback, while pushing would
     * leave four entries and an enabled one.
     */
    @Test
    fun ancestorBreadcrumbOnBackStack_popsBackToIt() {
        val rootMarker = FileFixtures.createTextFile(testDir, "marker_root.txt")
        val first = FileFixtures.createFolder(testDir, "One")
        val second = FileFixtures.createFolder(first, "Two")
        val secondMarker = FileFixtures.createTextFile(second, "marker_two.txt")

        val intent = FolderActivity.createIntent(
            context = context,
            path = testDir.absolutePath,
            title = TITLE,
            rootPath = testDir.absolutePath,
            rootDisplayName = ROOT_NAME
        )
        ActivityScenario.launch<FolderActivity>(intent).use { scenario ->
            awaitText(first.name)
            composeTestRule.onNodeWithText(first.name).performClick()

            awaitText(second.name)
            composeTestRule.onNodeWithText(second.name).performClick()

            awaitText(secondMarker.name)

            // Without this the assertion below could pass on a descent that never happened.
            assertTrue(
                "The descent must have pushed entries for the pop to be meaningful",
                interceptsBack(scenario)
            )

            composeTestRule.onNodeWithText(ROOT_NAME).performClick()
            awaitText(rootMarker.name)

            assertFalse(
                "Tapping the launch folder's crumb must pop back to it, not push a copy on top",
                interceptsBack(scenario)
            )
        }
    }

    /** Waits until [text] is on screen; each navigation settles by waiting for its content. */
    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Whether the Activity consumes system back, read on the main thread: [ActivityScenario]'s
     * `onActivity` blocks until its block completes. True exactly when the NavHost holds more than
     * the launch entry, since the selection-mode BackHandler is disabled throughout these tests.
     */
    private fun interceptsBack(scenario: ActivityScenario<FolderActivity>): Boolean {
        composeTestRule.waitForIdle()
        val intercepts = AtomicBoolean(false)
        scenario.onActivity { activity ->
            intercepts.set(activity.onBackPressedDispatcher.hasEnabledCallbacks())
        }
        return intercepts.get()
    }

    private companion object {
        // Distinct from every folder and file name below, so the matchers stay unambiguous: the top
        // bar renders the title, and the first crumb the root's display name.
        const val TITLE = "FolderTitle"
        const val ROOT_NAME = "TestRoot"
    }
}
