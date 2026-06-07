package com.mauriciotogneri.fileexplorer.integration

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.testutil.FakeStorageSource
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.testutil.Retry
import com.mauriciotogneri.fileexplorer.testutil.RetryRunner
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderScreen
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Stage 7 (Point 7): screen-level coverage of the uncompress flow, including the wrong-password
 * retry loop, which no existing test exercises end-to-end.
 *
 * Real-wiring: the real [FolderScreen] + `FolderViewModel` + `UncompressHandler` + `FileRepository`
 * extract real zips (plain + AES password-protected, via P5 helpers) into the rendered temp dir.
 * The injected `FolderViewModel` is built over `StorageRepository(FakeStorageSource(testDir))` (via
 * the P3 seam) so the extraction's allowed-roots check (`FileRepository.uncompressFile` rejects
 * targets outside storage roots) treats the temp dir as a valid root — otherwise every real file
 * operation under `cacheDir` would fail with a SecurityException.
 * After a successful extraction the handler emits `ExtractionComplete` -> the folder reloads -> the
 * extracted entry appears in the list, which is the assertion anchor.
 *
 * Retry loop: a wrong password makes `UncompressHandler` re-set `itemToUncompress` with
 * `isPasswordProtected = true`, so the password dialog reappears (plus a Toast we don't assert — a
 * system Toast is out of Compose's tree). The "wrong then correct" test proves the loop functionally.
 *
 * Out of scope (documented): the transient `UncompressProgressDialog` and the cancel path — catching
 * the in-progress state on a tiny zip is inherently racy, so it is not asserted here.
 */
@RunWith(RetryRunner::class)
class UncompressFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(activity.cacheDir, "test_uncompress_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun plainZip_extracts_withoutPasswordPrompt() {
        FileFixtures.createZip(testDir, "plain.zip", mapOf("extracted_payload.txt" to "hello"))
        renderFolder()
        tapFile("plain.zip")

        // Non-password zip -> plain UncompressDialog (no password field).
        waitForText(string(R.string.action_uncompress))
        composeTestRule.onNodeWithText(string(R.string.uncompress_password_hint)).assertDoesNotExist()

        composeTestRule.onNodeWithText(string(R.string.uncompress_extract)).performClick()

        waitForText("extracted_payload.txt", timeoutMillis = 20_000)
        composeTestRule.onNodeWithText("extracted_payload.txt").assertIsDisplayed()
    }

    @Test
    fun passwordZip_correctPassword_extractsAndShowsFiles() {
        FileFixtures.createPasswordZip(testDir, "secret.zip", PASSWORD, mapOf("secret_payload.txt" to "classified"))
        renderFolder()
        tapFile("secret.zip")

        waitForText(string(R.string.uncompress_password_title))
        typePassword(PASSWORD)
        composeTestRule.onNodeWithText(string(R.string.uncompress_extract)).performClick()

        waitForText("secret_payload.txt", timeoutMillis = 20_000)
        composeTestRule.onNodeWithText("secret_payload.txt").assertIsDisplayed()
    }

    @Test
    fun passwordZip_wrongPassword_repromptsPasswordDialog() {
        FileFixtures.createPasswordZip(testDir, "secret.zip", PASSWORD, mapOf("secret_payload.txt" to "classified"))
        renderFolder()
        tapFile("secret.zip")

        waitForText(string(R.string.uncompress_password_title))
        typePassword("wrong-password")
        composeTestRule.onNodeWithText(string(R.string.uncompress_extract)).performClick()

        // The dialog dismisses, extraction fails on the wrong password, and the password dialog
        // is shown again for another attempt. The extracted entry must NOT appear.
        waitUntilPasswordDialogShown()
        composeTestRule.onNodeWithText("secret_payload.txt").assertDoesNotExist()
    }

    @Test
    @Retry
    fun passwordZip_wrongThenCorrect_succeeds() {
        FileFixtures.createPasswordZip(testDir, "secret.zip", PASSWORD, mapOf("secret_payload.txt" to "classified"))
        renderFolder()
        tapFile("secret.zip")

        waitForText(string(R.string.uncompress_password_title))
        typePassword("wrong-password")
        composeTestRule.onNodeWithText(string(R.string.uncompress_extract)).performClick()

        // Wait for the retry prompt, then enter the correct password.
        waitUntilPasswordDialogShown()
        typePassword(PASSWORD)
        composeTestRule.onNodeWithText(string(R.string.uncompress_extract)).performClick()

        waitForText("secret_payload.txt", timeoutMillis = 20_000)
        composeTestRule.onNodeWithText("secret_payload.txt").assertIsDisplayed()
    }

    // ==================== Helpers ====================

    private fun renderFolder() {
        composeTestRule.setContent {
            val viewModel = remember { buildViewModel() }
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

    // Real VM, but with storage roots faked to the temp dir so extraction's allowed-roots check passes.
    private fun buildViewModel(): FolderViewModel {
        val app = activity.application
        return FolderViewModel(
            application = app,
            initialPath = testDir.absolutePath,
            initialTitle = null,
            fileRepository = FileRepository(),
            preferencesRepository = PreferencesRepository(DataStorePreferencesSource(app.preferencesDataStore)),
            storageRepository = StorageRepository(FakeStorageSource(testDir))
        )
    }

    private fun tapFile(name: String) {
        waitForText(name)
        composeTestRule.onNodeWithText(name).performClick()
        composeTestRule.waitForIdle()
    }

    private fun typePassword(password: String) {
        composeTestRule.onNode(hasSetTextAction()).performTextInput(password)
        composeTestRule.waitForIdle()
    }

    private fun waitUntilPasswordDialogShown() {
        composeTestRule.waitUntil(timeoutMillis = 20_000) {
            composeTestRule.onAllNodesWithText(string(R.string.uncompress_password_title))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String, timeoutMillis: Long = 5_000) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = activity.getString(id)

    private companion object {
        const val PASSWORD = "correct-horse"
    }
}
