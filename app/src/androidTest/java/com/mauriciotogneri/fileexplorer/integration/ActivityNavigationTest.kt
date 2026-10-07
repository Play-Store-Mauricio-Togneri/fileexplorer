package com.mauriciotogneri.fileexplorer.integration

import android.app.Activity
import android.app.Instrumentation
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.AboutActivity
import com.mauriciotogneri.fileexplorer.activities.AnalyzerActivity
import com.mauriciotogneri.fileexplorer.activities.FolderActivity
import com.mauriciotogneri.fileexplorer.activities.ItemInfoActivity
import com.mauriciotogneri.fileexplorer.activities.SettingsActivity
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.testutil.FakeStorageSource
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.testutil.FolderScreenRobot
import com.mauriciotogneri.fileexplorer.testutil.InMemoryPreferencesSource
import com.mauriciotogneri.fileexplorer.testutil.NoExternalChanges
import com.mauriciotogneri.fileexplorer.testutil.SeededFavorites
import com.mauriciotogneri.fileexplorer.testutil.SeededRecentFiles
import com.mauriciotogneri.fileexplorer.testutil.WarmLocationSizes
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderScreen
import com.mauriciotogneri.fileexplorer.ui.screens.home.HomeScreen
import com.mauriciotogneri.fileexplorer.ui.screens.home.HomeViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.any
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Stage 12 (Point 13): verifies UI actions actually launch the right Activity (existing tests only
 * checked the click callbacks). Home drawer items launch Settings/Analyzer/About; every home entry
 * point into a folder launches FolderActivity; the folder file Info action launches
 * ItemInfoActivity.
 *
 * Real-wiring: the real [HomeScreen] / [FolderScreen] + Espresso-Intents. `intending(anyIntent())`
 * stubs each launch so the target Activity does not actually start.
 *
 * The home folder entry points are six separate `startActivity` call sites — the location card,
 * the storage card, a recent or favorite that is now a folder, and each sheet's "Open folder" —
 * not one shared launch, so each gets its own test. Where the expected folder is known exactly, the
 * launch is also matched on the path it carries: "Open folder" must open the entry's parent, and
 * opening the entry itself would launch the same component.
 *
 * The home ViewModel is the real one over seeded sources, as in `NavigationDrawerTest`: a fake
 * storage volume and warm location sizes remove the storage scan the screen used to wait up to 20
 * seconds behind, which is what the storage card's `@Retry` was absorbing. Preferences are held in
 * memory, because the drawer taps below dismiss badges and the app's own store would keep those
 * dismissals for every later test.
 */
@RunWith(AndroidJUnit4::class)
class ActivityNavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(activity.cacheDir, "test_actnav_${System.currentTimeMillis()}").apply { mkdirs() }
        Intents.init()
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        testDir.deleteRecursively()
    }

    // ==================== Home drawer -> Activity ====================

    @Test
    fun drawerSettings_launchesSettingsActivity() {
        renderHome()
        openDrawerAndTap(R.string.drawer_settings)
        intended(hasComponent(SettingsActivity::class.java.name))
    }

    @Test
    fun drawerAnalyzer_launchesAnalyzerActivity() {
        renderHome()
        openDrawerAndTap(R.string.drawer_analyzer)
        intended(hasComponent(AnalyzerActivity::class.java.name))
    }

    @Test
    fun drawerAbout_launchesAboutActivity() {
        renderHome()
        openDrawerAndTap(R.string.drawer_about)
        intended(hasComponent(AboutActivity::class.java.name))
    }

    // ==================== Home folder entry points -> FolderActivity ====================

    @Test
    fun storageCard_launchesFolderActivity() {
        renderHome()

        tapCard(FAKE_STORAGE_NAME)

        intended(launchesFolder(testDir.absolutePath))
    }

    @Test
    fun locationCard_launchesFolderActivity() {
        // The card is listed only when the device has the folder; read from the platform, so a
        // production change that stops listing it still fails here.
        assumeTrue(
            "Device has no Download folder",
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).isDirectory
        )
        renderHome()

        tapCard(string(R.string.location_downloads))

        intended(hasComponent(FolderActivity::class.java.name))
    }

    @Test
    fun recentEntryThatIsNowAFolder_launchesFolderActivity() {
        val folder = FileFixtures.createFolder(testDir, "recent_folder")
        renderHome(recentFiles = listOf(recentFile(folder)))

        tapCard(folder.name)

        intended(launchesFolder(folder.absolutePath))
    }

    @Test
    fun favoriteFolder_launchesFolderActivity() {
        val folder = FileFixtures.createFolder(testDir, "favorite_folder")
        renderHome(favorites = listOf(favorite(folder)))

        tapCard(folder.name)

        intended(launchesFolder(folder.absolutePath))
    }

    @Test
    fun recentSheetOpenFolder_launchesFolderActivityOnTheParent() {
        val parent = FileFixtures.createFolder(testDir, "recent_parent")
        val file = FileFixtures.createTextFile(parent, "recent_file.txt")
        renderHome(recentFiles = listOf(recentFile(file)))

        openEntrySheetAndTap(file.name, R.string.action_open_folder)

        intended(launchesFolder(parent.absolutePath))
    }

    @Test
    fun favoriteSheetOpenFolder_launchesFolderActivityOnTheParent() {
        val parent = FileFixtures.createFolder(testDir, "favorite_parent")
        val file = FileFixtures.createTextFile(parent, "favorite_file.txt")
        renderHome(favorites = listOf(favorite(file)))

        openEntrySheetAndTap(file.name, R.string.action_open_folder)

        intended(launchesFolder(parent.absolutePath))
    }

    // ==================== Folder file Info -> ItemInfoActivity ====================

    /**
     * Info is the last row of a sheet that opens partially expanded and animates in. This used to
     * tap it as soon as the sheet's first rows existed — mid-animation, and on a short screen below
     * the sheet's visible part — so the tap could land on the scrim and dismiss the sheet instead,
     * which `@Retry` absorbed. It now waits for the sheet to settle and scrolls the row into view.
     */
    @Test
    fun folderFileInfo_launchesItemInfoActivity() {
        FileFixtures.createTextFile(testDir, "doc.txt", "x")
        val robot = FolderScreenRobot(composeTestRule, testDir).render()
        robot.openRowActions("doc.txt")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.action_info))
            .performScrollTo()
            .performClick()

        intended(hasComponent(ItemInfoActivity::class.java.name))
    }

    // ==================== Helpers ====================

    private fun renderHome(
        recentFiles: List<RecentFile> = emptyList(),
        favorites: List<Favorite> = emptyList()
    ) {
        composeTestRule.setContent {
            val viewModel = remember { buildHomeViewModel(recentFiles, favorites) }
            FileExplorerTheme {
                HomeScreen(viewModel = viewModel)
            }
        }
        // Home finished loading once its search bar is present.
        waitForText(string(R.string.search_placeholder))
    }

    private fun buildHomeViewModel(
        recentFiles: List<RecentFile>,
        favorites: List<Favorite>
    ): HomeViewModel {
        val preferencesRepository = PreferencesRepository(InMemoryPreferencesSource())

        return HomeViewModel(
            application = activity.application,
            recentFilesRepository = RecentFilesRepository(SeededRecentFiles(recentFiles)),
            favoritesRepository = FavoritesRepository(SeededFavorites(favorites)),
            locationsRepository = LocationsRepository(WarmLocationSizes, preferencesRepository),
            storageRepository = StorageRepository(FakeStorageSource(testDir)),
            preferencesRepository = preferencesRepository,
            fileRepository = FileRepository(),
            mediaChangeSource = NoExternalChanges,
            storageVolumeChangeSource = NoExternalChanges
        )
    }

    // Real files: both repositories re-stat every stored entry and drop the ones whose path is gone.
    private fun recentFile(file: File) = RecentFile(
        path = file.absolutePath,
        name = file.name,
        mimeType = TEXT_MIME_TYPE,
        lastOpenedTimestamp = System.currentTimeMillis()
    )

    private fun favorite(file: File) = Favorite(
        path = file.absolutePath,
        name = file.name,
        isDirectory = file.isDirectory,
        mimeType = if (file.isDirectory) "" else TEXT_MIME_TYPE,
        favoritedTimestamp = System.currentTimeMillis()
    )

    private fun launchesFolder(path: String) = allOf(
        hasComponent(FolderActivity::class.java.name),
        // The extra's key is private to FolderActivity; the path is what tells the folders apart.
        hasExtra(any(String::class.java), equalTo(path))
    )

    private fun tapCard(text: String) {
        waitForText(text)
        composeTestRule.onNodeWithText(text)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun openEntrySheetAndTap(entryName: String, @StringRes actionRes: Int) {
        waitForText(entryName)
        composeTestRule.onNodeWithText(entryName)
            .performScrollTo()
            .performTouchInput { longClick() }
        waitForText(string(actionRes))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(actionRes))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun openDrawerAndTap(@StringRes labelRes: Int) {
        composeTestRule.onNodeWithContentDescription(string(R.string.menu_open)).performClick()
        waitForText(string(labelRes))
        composeTestRule.onNodeWithText(string(labelRes)).performClick()
        composeTestRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = activity.getString(id)

    private companion object {
        // A ceiling on composition and the seeded sources' first read, not on a directory walk.
        const val TIMEOUT_MS = 10_000L
        const val TEXT_MIME_TYPE = "text/plain"

        // The display name FakeStorageSource gives its one volume; a fixture name, not a resource.
        const val FAKE_STORAGE_NAME = "Test Storage"
    }
}
