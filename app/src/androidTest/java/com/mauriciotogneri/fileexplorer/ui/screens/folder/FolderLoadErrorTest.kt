package com.mauriciotogneri.fileexplorer.ui.screens.folder

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.mauriciotogneri.fileexplorer.testutil.ThrowingFileRepository
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Stage 11 (Point 12) — folder-screen load errors. `FileRepository.listFiles` returns an empty list
 * (not a throw) for missing paths, so the error branch can only be reached with a repository that
 * throws. A [ThrowingFileRepository] (subclass; `FileRepository` was made `open` as a test seam) is
 * wired into a real [FolderViewModel] via the P3 injectable-viewModel seam. The empty-state case
 * uses the real repository to confirm empty != error.
 *
 * Out of scope (documented): "SD card removed mid-session" / "permission revoked mid-session"
 * require device-state manipulation not reliably available in instrumentation.
 */
@RunWith(AndroidJUnit4::class)
class FolderLoadErrorTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(activity.cacheDir, "test_loaderror_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun listFilesThrows_showsErrorState() {
        render(buildViewModel(ThrowingFileRepository()))

        waitForText(string(R.string.error_load_files))
        composeTestRule.onNodeWithText(string(R.string.error_load_files)).assertIsDisplayed()
    }

    @Test
    fun errorState_thenRefresh_recovers() {
        val repo = ThrowingFileRepository()
        val viewModel = buildViewModel(repo)
        render(viewModel)
        waitForText(string(R.string.error_load_files))

        // Repository recovers; a refresh clears the error and shows files.
        repo.filesOnSuccess = listOf(FileItem.from(FileFixtures.createTextFile(testDir, "recovered.txt", "ok")))
        repo.shouldThrow = false
        composeTestRule.runOnUiThread { viewModel.refresh() }

        waitForText("recovered.txt")
        composeTestRule.onNodeWithText("recovered.txt").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.error_load_files)).assertDoesNotExist()
    }

    @Test
    fun emptyDirectory_showsEmptyState_notError() {
        // Real repository over an empty temp dir -> empty state, never the error state.
        render(buildViewModel(FileRepository()))

        waitForText(string(R.string.list_empty))
        composeTestRule.onNodeWithText(string(R.string.list_empty)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.error_load_files)).assertDoesNotExist()
    }

    // ==================== Helpers ====================

    private fun buildViewModel(fileRepository: FileRepository): FolderViewModel {
        val app = activity.application
        return FolderViewModel(
            application = app,
            initialPath = testDir.absolutePath,
            initialTitle = null,
            fileRepository = fileRepository,
            preferencesRepository = PreferencesRepository(DataStorePreferencesSource(app.preferencesDataStore)),
            storageRepository = StorageRepository(FakeStorageSource(testDir)),
            favoritesRepository = FavoritesRepository(DataStoreFavoriteFilesSource(app.favoriteFilesDataStore))
        )
    }

    private fun render(viewModel: FolderViewModel) {
        composeTestRule.setContent {
            FileExplorerTheme {
                FolderScreen(
                    path = testDir.absolutePath,
                    onNavigateToFolder = {},
                    onNavigateBack = {},
                    viewModel = viewModel
                )
            }
        }
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = activity.getString(id)
}
