package com.mauriciotogneri.fileexplorer.integration

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
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
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.any
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeFalse
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
 * ItemInfoActivity; Home's APK permission dialog launches the install-permission Settings screen.
 *
 * Real-wiring: the real [HomeScreen] / [FolderScreen] + Espresso-Intents. `intending(anyIntent())`
 * stubs each launch so the target Activity does not actually start.
 *
 * The home folder entry points are six separate `startActivity` call sites — the location card,
 * the storage card, a recent or favorite that is now a folder, and each sheet's "Open folder" —
 * not one shared launch, so each gets its own test. Each launch is also matched on the path it
 * carries, since every one of them launches the same component: "Open folder" must open the entry's
 * parent rather than the entry, and a location card its own directory rather than another's.
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
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        assumeTrue("Device has no Download folder", downloads.isDirectory)
        renderHome()

        tapCard(string(R.string.location_downloads))

        // Matched on the path, not the component alone: every location card launches the folder
        // screen, so a card mapped to another location's directory passed. The unit tests stub every
        // location to one folder, which leaves this the only check on the mapping.
        intended(launchesFolder(downloads.absolutePath))
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

    // ==================== Home recent APK -> install-permission Settings ====================

    /**
     * Home's copy of the APK dialog's Settings wiring; `FileOpenRoutingTest` lists the five screens
     * that each wire it by hand, and the test pinning each. Driven from a recents entry on the real
     * screen: `HomeDialogsTest` renders only the shared dialog with a lambda of its own, so emptying
     * Home's `onOpenSettings` left every home test green. The package uri is part of it — without it
     * the platform opens the list of every app instead of this one's toggle.
     *
     * The entry is seeded in memory, like every home entry in this file, so the app's own recents
     * store is never written; a tap that ends at the permission dialog records no open either.
     */
    @Test
    fun homeRecentApk_settingsButton_firesManageUnknownSourcesIntent() {
        assumeTrue("Install permission check requires Android O+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        assumeFalse("Device has pre-granted unknown sources install permission", activity.packageManager.canRequestPackageInstalls())
        assertFalse("IntentUtil.canInstallApks must return false when platform permission is missing", IntentUtil.canInstallApks(activity))
        val apk = FileFixtures.createFakeApk(testDir, "app.apk")
        // The entry carries the type a recents entry records for an APK, and the tap is routed by
        // that stored type, so no platform MIME lookup decides whether the dialog appears.
        renderHome(recentFiles = listOf(recentFile(apk, mimeType = APK_MIME_TYPE)))

        tapCard(apk.name)
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
    private fun recentFile(file: File, mimeType: String = TEXT_MIME_TYPE) = RecentFile(
        path = file.absolutePath,
        name = file.name,
        mimeType = mimeType,
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
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        // The display name FakeStorageSource gives its one volume; a fixture name, not a resource.
        const val FAKE_STORAGE_NAME = "Test Storage"
    }
}
