package com.mauriciotogneri.fileexplorer.integration

import android.content.Context
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.FolderActivity
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Back behavior of the standalone [FolderActivity] at the launch folder.
 *
 * There the internal NavHost has nothing to pop and the screen is not in selection mode, so the
 * Activity registers no enabled `OnBackPressedCallback`: a system back is not intercepted and
 * propagates to the system, which returns to whatever launched the Activity.
 *
 * We assert that "does not intercept back" contract rather than the Activity actually finishing,
 * because the finish is platform behavior [ActivityScenario] cannot reproduce: it launches
 * FolderActivity as the task root, and on API 33+ the platform back fallback does not finish a
 * task-root Activity (predictive back). In production FolderActivity is always started on top of
 * the app's task (see its callers in HomeScreen/SearchScreen), so it is never the task root and the
 * same back press does finish it.
 *
 * Deeper folder-to-folder navigation pops within the NavHost instead and is exercised by the folder
 * navigation tests.
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
}
