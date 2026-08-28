package com.mauriciotogneri.fileexplorer.integration

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.FolderActivity
import com.mauriciotogneri.fileexplorer.activities.MainActivity
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.testutil.Retry
import com.mauriciotogneri.fileexplorer.testutil.RetryRunner
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Covers the startup-screen setting end to end: what [MainActivity] actually does on a cold start
 * once a folder has been chosen. The setting's UI is covered by `SettingsScreenTest`; this class
 * covers the behaviour that UI configures, which nothing else touched.
 *
 * Three things here are load-bearing:
 *
 * 1. The folder is opened **over** the home screen rather than instead of it, so one back press
 *    returns home. That is the whole shape of the feature.
 * 2. It is opened only for a **fresh** Activity. `MainActivity` guards on `savedInstanceState`, and
 *    if that guard ever breaks, every recreation — a rotation, a theme change, a resize — reopens
 *    the folder on top of the home screen the user was trying to reach, and the screen behind it
 *    becomes unreachable for as long as the setting is on.
 * 3. A folder that has since been deleted or unmounted falls back to home instead of opening a
 *    screen that cannot list anything.
 * 4. The home screen is not drawn *ahead* of the folder. Resolution is asynchronous, so without the
 *    hold `MainActivity` keeps while it runs, home composes first and is covered a frame later —
 *    the flash the hold exists to remove. Guarded only weakly; see that test's own note.
 *
 * **Real preferences.** These tests write the app's own `user_preferences` store, because that is
 * what `MainActivity` reads before it composes anything — there is no seam to inject. `tearDown`
 * puts the setting back to [StartupScreen.HOME], which is also the state a fresh install is in.
 *
 * **What is not asserted.** The missing-folder case asserts that no folder is opened, not that the
 * Toast appears: a Toast renders in its own window, and matching it is unreliable across the API
 * range this app supports. The Toast's string is covered by the locale-parity check instead.
 */
@RunWith(RetryRunner::class)
class StartupScreenTest {

    /**
     * Empty rather than `createAndroidComposeRule<MainActivity>()`: that rule launches the Activity
     * as the rule starts, which is before `@Before` can choose the startup folder the launch is
     * supposed to read.
     */
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    private val preferencesRepository =
        PreferencesRepository(DataStorePreferencesSource(context.preferencesDataStore))

    private lateinit var externalRoot: File
    private val createdFolders = mutableListOf<File>()
    private var scenario: ActivityScenario<MainActivity>? = null

    /** User-facing assertions go through resources so they hold in every supported locale. */
    private fun string(@StringRes id: Int): String = context.getString(id)

    @Before
    fun setUp() {
        assumeTrue(
            "Opening a folder on shared storage needs All Files Access, which only exists from R",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        )
        grantAllFilesAccess()
        assumeTrue(
            "All Files Access was not granted, so MainActivity shows the permission wall instead",
            Environment.isExternalStorageManager()
        )
        assumeTrue(
            "Device has no mounted shared storage to put a startup folder on",
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        )

        externalRoot = Environment.getExternalStorageDirectory()
        Intents.init()
    }

    @After
    fun tearDown() {
        scenario?.close()
        Intents.release()
        // Restore the shipped default, so a failure here cannot leave the rest of the suite — or
        // the device the suite ran on — starting in a folder this test made.
        runBlocking { preferencesRepository.setStartupScreen(StartupScreen.HOME, null) }
        createdFolders.forEach { runCatching { it.deleteRecursively() } }
    }

    // ==================== Opening the configured folder ====================

    @Test
    fun startupFolder_opensThatFolderWithItsStorageRoot() {
        val folder = createFolder("Reports")
        chooseStartupFolder(folder)
        stubActivityLaunches()

        launchMainActivity()

        val launch = awaitFolderActivityLaunch()
        assertEquals(folder.absolutePath, launch.getStringExtra(EXTRA_PATH))
        assertEquals(folder.name, launch.getStringExtra(EXTRA_TITLE))
        // The storage root travels with the folder so the breadcrumb trail can be trimmed to it;
        // without it an SD-card path renders as its raw segments and can be navigated above.
        assertEquals(externalRoot.absolutePath, launch.getStringExtra(EXTRA_ROOT_PATH))
        assertNotNull(
            "The storage root's display name is what the first breadcrumb shows",
            launch.getStringExtra(EXTRA_ROOT_DISPLAY_NAME)
        )
    }

    @Test
    fun startupHome_opensNoFolder() {
        runBlocking { preferencesRepository.setStartupScreen(StartupScreen.HOME, null) }
        stubActivityLaunches()

        launchMainActivity()
        awaitHomeScreen()

        assertTrue(
            "The default startup screen must open nothing on top of home",
            folderActivityLaunches().isEmpty()
        )
    }

    // ==================== The hold while resolving ====================

    /**
     * The gate that keeps the navigation graph out of composition while the folder is being
     * resolved.
     *
     * **Weak by construction, and kept on those terms.** `ActivityScenario.launch` returns only once
     * the main looper has gone idle after `onCreate`, and on a warm volume the resolution has landed
     * by then — so the loop below usually exits on its first check having asserted nothing. It bites
     * only when resolution outlasts that idle, which nothing here arranges. Its value is that it
     * costs a few milliseconds and occasionally catches a gate that stopped holding; its limit is
     * that a green run is not evidence the gate works.
     *
     * What it will not do is accuse an intact gate. Home is sampled *before* the launch is read, so
     * a launch that the sample itself let through breaks the loop rather than being reported as a
     * flash — see [homeScreenIsShowing], which advances the main thread as a side effect of looking.
     *
     * Making it deterministic needs a seam that lets a test stall resolution: a companion-level
     * resolver override on [MainActivity], installed before launch. That is a production affordance
     * existing purely for a test, which this codebase has so far done without.
     */
    @Test
    fun homeScreen_isNotDrawnBeforeTheStartupFolderIsLaunched() {
        val folder = createFolder("Reports")
        chooseStartupFolder(folder)
        stubActivityLaunches()

        launchMainActivity()

        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (true) {
            val homeShowing = homeScreenIsShowing()

            if (folderActivityLaunches().isNotEmpty()) break

            assertFalse(
                "The home screen must not be composed before the startup folder is launched",
                homeShowing
            )
            assertTrue(
                "The startup folder was never launched",
                System.currentTimeMillis() < deadline
            )
        }
    }

    // ==================== The recreation guard ====================

    /**
     * The failure this prevents is not a wrong pixel: if the folder reopens on every recreation, the
     * user rotates the device to get back to the home screen and lands in the folder again, with no
     * way out but turning the setting off — which lives on a screen they can no longer reach.
     */
    @Test
    fun startupFolder_isNotReopenedWhenTheActivityIsRecreated() {
        val folder = createFolder("Reports")
        chooseStartupFolder(folder)
        stubActivityLaunches()

        val activityScenario = launchMainActivity()
        awaitFolderActivityLaunch()

        activityScenario.recreate()
        composeTestRule.waitForIdle()

        // Settle rather than sample once: a second launch would be posted from the recreated
        // Activity's onCreate, so asserting immediately could pass before it arrived.
        repeat(SETTLE_POLLS) {
            assertEquals(
                "A recreated MainActivity must not reopen the startup folder",
                1,
                folderActivityLaunches().size
            )
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    // ==================== A folder that is no longer there ====================

    @Test
    fun startupFolder_thatNoLongerExists_fallsBackToHome() {
        // Chosen, then never created: the same state the store is in after the user deletes the
        // folder from another app, or takes the SD card out.
        val folder = File(externalRoot, "fe_startup_missing_${System.currentTimeMillis()}")
        chooseStartupFolder(folder)
        stubActivityLaunches()

        launchMainActivity()
        awaitHomeScreen()

        assertTrue(
            "A startup folder that cannot be opened must leave the user on the home screen",
            folderActivityLaunches().isEmpty()
        )
    }

    @Test
    fun startupFolder_thatIsAFile_fallsBackToHome() {
        val file = File(externalRoot, "fe_startup_file_${System.currentTimeMillis()}.txt").apply {
            writeText("not a folder")
        }
        createdFolders.add(file)
        chooseStartupFolder(file)
        stubActivityLaunches()

        launchMainActivity()
        awaitHomeScreen()

        assertTrue(
            "A path that is a file, not a folder, must leave the user on the home screen",
            folderActivityLaunches().isEmpty()
        )
    }

    // ==================== Back returns home ====================

    /**
     * The one assertion that pins the shape of the feature: the folder sits on top of the home
     * screen, so a single back press reaches home rather than leaving the app.
     *
     * Deliberately does not stub the launch — the real [FolderActivity] has to start for there to be
     * a back stack to pop. Two real Activity transitions under full-suite emulator load is exactly
     * what [Retry] exists for.
     */
    @Retry
    @Test
    fun backFromTheStartupFolder_returnsToHome() {
        val folder = createFolder("Reports")
        chooseStartupFolder(folder)

        launchMainActivity()
        awaitFolderActivityLaunch()
        awaitText(folder.name)

        Espresso.pressBack()

        awaitHomeScreen()
    }

    // ==================== Helpers ====================

    private fun createFolder(name: String): File =
        File(externalRoot, "fe_startup_${System.currentTimeMillis()}_$name")
            .apply { mkdirs() }
            .also { createdFolders.add(it) }

    private fun chooseStartupFolder(folder: File) {
        runBlocking {
            preferencesRepository.setStartupScreen(StartupScreen.FOLDER, folder.absolutePath)
        }
    }

    /**
     * Swallows the folder launch so [FolderActivity] never actually starts, keeping [MainActivity]
     * resumed and the assertions about *what was launched* independent of what the launched screen
     * then does. Omitted by the back-navigation test, which needs the real thing.
     *
     * Matches [FolderActivity] specifically rather than `anyIntent()`: a stub is consulted before
     * the launch happens, and `ActivityScenario.launch` starts [MainActivity] through the same
     * instrumentation hook. A catch-all therefore answers that launch with this canned result too,
     * so [MainActivity] never starts and `startActivitySync` waits on it forever — a hang with no
     * timeout that takes the whole suite with it.
     */
    private fun stubActivityLaunches() {
        intending(hasComponent(FolderActivity::class.java.name))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    private fun launchMainActivity(): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also { scenario = it }

    private fun folderActivityLaunches(): List<Intent> = Intents.getIntents()
        .filter { it.component?.className == FolderActivity::class.java.name }

    private fun awaitFolderActivityLaunch(): Intent {
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            folderActivityLaunches().isNotEmpty()
        }
        val launches = folderActivityLaunches()
        assertEquals("The startup folder must be opened exactly once", 1, launches.size)
        return launches.first()
    }

    private fun awaitHomeScreen() = awaitText(string(R.string.search_placeholder))

    /**
     * Whether home is on screen. **Not an instantaneous sample:** `fetchSemanticsNodes` waits for a
     * compose root, drives Espresso to idle, and waits for the next frame, so the main thread
     * advances during the call. Callers must therefore read anything they are racing against
     * *after* this, not before.
     *
     * `atLeastOneRootRequired = false` returns an empty list instead of throwing while no root
     * exists yet — the blank hold's own first frames. Catching the throwing default would have been
     * the alternative, and a catch here would also swallow the exceptions Compose stashes from
     * composition and rethrows through this same call.
     */
    private fun homeScreenIsShowing(): Boolean =
        composeTestRule.onAllNodesWithText(string(R.string.search_placeholder))
            .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * The app's only storage permission above Q is All Files Access, which is an app op rather than
     * a runtime permission, so `GrantPermissionRule` cannot grant it. Without it `MainActivity`
     * starts on the permission wall and never reaches the startup-folder decision at all.
     */
    private fun grantAllFilesAccess() {
        instrumentation.uiAutomation.executeShellCommand(
            "appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow"
        ).close()
        // The shell command runs in another process; give the op a moment to land.
        repeat(20) {
            if (Environment.isExternalStorageManager()) return
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
        const val POLL_INTERVAL_MS = 100L
        const val SETTLE_POLLS = 10

        // FolderActivity's extra keys are private to its companion. These literals mirror them
        // deliberately: they are the wire contract of the launch intent, so a rename that this test
        // catches is a rename that would have broken every other caller too.
        const val EXTRA_PATH = "extra_path"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ROOT_PATH = "extra_root_path"
        const val EXTRA_ROOT_DISPLAY_NAME = "extra_root_display_name"
    }
}
