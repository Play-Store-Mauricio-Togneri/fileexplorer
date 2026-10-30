package com.mauriciotogneri.fileexplorer.ui.screens.search

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.favoriteFilesDataStore
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStoreFavoriteFilesSource
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.testutil.FakeStorageSource
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.testutil.assumePlatformMapsApkExtension
import com.mauriciotogneri.fileexplorer.ui.components.SearchFileAction
import com.mauriciotogneri.fileexplorer.ui.components.SearchFileActionsBottomSheet
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import org.hamcrest.Matchers.allOf
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

/**
 * Search chrome (field, clear, back) driven through the real [SearchScreen], plus the real
 * [SearchFileActionsBottomSheet].
 *
 * The previous version declared `TestSearchScreen` and `TestSearchFileActionsBottomSheet` copies.
 * The sheet copy listed five actions; production has six — it grew an add/remove-favorite item that
 * the copy never had, so `searchScreen_bottomSheet_displaysFileActions` enumerated the sheet's
 * contents and passed while missing an entire item.
 */
@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val application = context.applicationContext as Application

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(context.cacheDir, "test_search_screen_${System.currentTimeMillis()}")
            .apply { mkdirs() }
        Intents.init()
        // Stub every outgoing intent so a tap never launches a real app or the Settings screen.
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        testDir.deleteRecursively()
    }

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    private fun renderSearch(onBackClick: () -> Unit = {}) {
        val viewModel = SearchViewModel(
            application = application,
            fileRepository = FileRepository(),
            storageRepository = StorageRepository(FakeStorageSource(testDir)),
            preferencesRepository = PreferencesRepository(
                DataStorePreferencesSource(application.preferencesDataStore)
            ),
            favoritesRepository = FavoritesRepository(
                DataStoreFavoriteFilesSource(application.favoriteFilesDataStore)
            )
        )
        composeTestRule.setContent {
            FileExplorerTheme {
                SearchScreen(onBackClick = onBackClick, viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun typeQuery(query: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextInput(query)
        composeTestRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private val testFile = FileItem(
        path = "/storage/emulated/0/Documents/test.txt",
        name = "test.txt",
        isDirectory = false,
        size = 1024L,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = "text/plain",
        childCount = null
    )

    private val testFolder = FileItem(
        path = "/storage/emulated/0/Documents/TestFolder",
        name = "TestFolder",
        isDirectory = true,
        size = 0L,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = "",
        childCount = 5
    )

    // ==================== Chrome ====================

    @Test
    fun searchScreen_displaysSearchField() {
        renderSearch()

        composeTestRule.onNodeWithText(string(R.string.search_placeholder)).assertIsDisplayed()
    }

    @Test
    fun searchScreen_displaysBackButton() {
        renderSearch()

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).assertIsDisplayed()
    }

    @Test
    fun searchScreen_backClick_triggersCallback() {
        var backClicked = false
        renderSearch(onBackClick = { backClicked = true })

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()

        assertTrue("Back should be triggered", backClicked)
    }

    @Test
    fun searchScreen_emptyQuery_hidesClearButton() {
        renderSearch()

        composeTestRule.onNodeWithContentDescription(string(R.string.search_clear)).assertDoesNotExist()
    }

    @Test
    fun searchScreen_withQuery_showsClearButton() {
        renderSearch()
        typeQuery("report")

        composeTestRule.onNodeWithContentDescription(string(R.string.search_clear)).assertIsDisplayed()
    }

    @Test
    fun searchScreen_typeText_updatesField() {
        renderSearch()
        typeQuery("document")

        composeTestRule.onNodeWithText("document").assertIsDisplayed()
    }

    // ==================== Results ====================

    @Test
    fun searchScreen_withResults_displaysMatchingFiles() {
        FileFixtures.createTextFile(testDir, "report_alpha.txt", "a")
        FileFixtures.createTextFile(testDir, "report_beta.txt", "b")
        FileFixtures.createTextFile(testDir, "unrelated.txt", "c")
        renderSearch()

        typeQuery("report")

        waitForText("report_alpha.txt")
        waitForText("report_beta.txt")
        composeTestRule.onNodeWithText("report_alpha.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("report_beta.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText("unrelated.txt").assertDoesNotExist()
    }

    @Test
    fun searchScreen_clearButton_resetsResults() {
        FileFixtures.createTextFile(testDir, "report_alpha.txt", "a")
        renderSearch()
        typeQuery("report")
        waitForText("report_alpha.txt")

        composeTestRule.onNodeWithContentDescription(string(R.string.search_clear)).performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithText("report_alpha.txt").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("report_alpha.txt").assertDoesNotExist()
    }

    // ==================== APK permission dialog ====================

    /**
     * Not a second copy of `FileOpenRoutingTest`'s dialog test, despite the shared name: each screen
     * wires the dialog's Settings button by hand, so each copy breaks on its own, and this pins the
     * search screen's. The package uri is part of it — without it the platform opens the list of
     * every app instead of this one's toggle.
     */
    @Test
    fun apkPermissionDialog_settingsButton_firesManageUnknownSourcesIntent() {
        val activity = composeTestRule.activity
        assumeTrue("Install permission check requires Android O+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        assumeFalse("Device has pre-granted unknown sources install permission", activity.packageManager.canRequestPackageInstalls())
        assertFalse("IntentUtil.canInstallApks must return false when platform permission is missing", IntentUtil.canInstallApks(activity))
        FileFixtures.createFakeApk(testDir, "app.apk")
        assumePlatformMapsApkExtension()

        renderSearch()
        typeQuery("app")
        waitForText("app.apk")
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

    // ==================== File actions bottom sheet ====================

    private fun renderSheet(
        file: FileItem,
        isFavorite: Boolean = false,
        onAction: (SearchFileAction) -> Unit = {}
    ) {
        composeTestRule.setContent {
            FileExplorerTheme {
                SearchFileActionsBottomSheet(
                    file = file,
                    mode = "test",
                    isFavorite = isFavorite,
                    onAction = onAction,
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun bottomSheet_forFile_displaysEveryAction() {
        renderSheet(testFile)

        composeTestRule.onNodeWithText(string(R.string.action_open_with)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_open_folder)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_share)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_add_to_favorites)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_info)).assertIsDisplayed()
    }

    @Test
    fun bottomSheet_forFolder_hidesFileOnlyActions() {
        renderSheet(testFolder)

        composeTestRule.onNodeWithText(string(R.string.action_open_with)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.action_open_folder)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.action_share)).assertDoesNotExist()

        // A folder can still be favorited, deleted and inspected.
        composeTestRule.onNodeWithText(string(R.string.action_add_to_favorites)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_info)).assertIsDisplayed()
    }

    @Test
    fun bottomSheet_openWithClick_triggersAction() {
        var action: SearchFileAction? = null
        renderSheet(testFile, onAction = { action = it })

        composeTestRule.onNodeWithText(string(R.string.action_open_with)).performClick()

        assertEquals(SearchFileAction.OpenWith, action)
    }

    @Test
    fun bottomSheet_openFolderClick_triggersAction() {
        var action: SearchFileAction? = null
        renderSheet(testFile, onAction = { action = it })

        composeTestRule.onNodeWithText(string(R.string.action_open_folder)).performClick()

        assertEquals(SearchFileAction.OpenFolder, action)
    }

    @Test
    fun bottomSheet_shareClick_triggersAction() {
        var action: SearchFileAction? = null
        renderSheet(testFile, onAction = { action = it })

        composeTestRule.onNodeWithText(string(R.string.action_share)).performClick()

        assertEquals(SearchFileAction.Share, action)
    }

    @Test
    fun bottomSheet_deleteClick_triggersAction() {
        var action: SearchFileAction? = null
        renderSheet(testFile, onAction = { action = it })

        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()

        assertEquals(SearchFileAction.Delete, action)
    }

    @Test
    fun bottomSheet_infoClick_triggersAction() {
        var action: SearchFileAction? = null
        renderSheet(testFile, onAction = { action = it })

        composeTestRule.onNodeWithText(string(R.string.action_info)).performClick()

        assertEquals(SearchFileAction.Info, action)
    }

    // ==================== Favorites action (absent from the old replica) ====================

    @Test
    fun bottomSheet_notFavorite_offersAddToFavorites() {
        renderSheet(testFile, isFavorite = false)

        composeTestRule.onNodeWithText(string(R.string.action_add_to_favorites)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_remove_from_favorites)).assertDoesNotExist()
    }

    @Test
    fun bottomSheet_isFavorite_offersRemoveFromFavorites() {
        renderSheet(testFile, isFavorite = true)

        composeTestRule.onNodeWithText(string(R.string.action_remove_from_favorites)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_add_to_favorites)).assertDoesNotExist()
    }

    @Test
    fun bottomSheet_addToFavoritesClick_triggersAction() {
        var action: SearchFileAction? = null
        renderSheet(testFile, isFavorite = false, onAction = { action = it })

        composeTestRule.onNodeWithText(string(R.string.action_add_to_favorites)).performClick()

        assertEquals(SearchFileAction.AddToFavorites, action)
    }

    @Test
    fun bottomSheet_removeFromFavoritesClick_triggersAction() {
        var action: SearchFileAction? = null
        renderSheet(testFile, isFavorite = true, onAction = { action = it })

        composeTestRule.onNodeWithText(string(R.string.action_remove_from_favorites)).performClick()

        assertEquals(SearchFileAction.RemoveFromFavorites, action)
    }

    @Test
    fun bottomSheet_folderIsFavorite_offersRemoveFromFavorites() {
        renderSheet(testFolder, isFavorite = true)

        composeTestRule.onNodeWithText(string(R.string.action_remove_from_favorites)).assertIsDisplayed()
    }
}
