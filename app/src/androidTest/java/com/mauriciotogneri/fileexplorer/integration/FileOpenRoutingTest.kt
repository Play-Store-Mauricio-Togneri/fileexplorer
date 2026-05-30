package com.mauriciotogneri.fileexplorer.integration

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasFlag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderScreen
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Stage 2 (Point 2): verifies the real tap-routing in [FolderScreen]
 * (`IntentUtil.openFile` -> `OpenFileResult` branches): regular file -> `ACTION_VIEW`,
 * zip -> uncompress dialog, password zip -> password dialog, apk -> install-permission dialog.
 *
 * Real-wiring approach: the real [FolderScreen] + its real `FolderViewModel`/`FileRepository`
 * list a temp dir populated with real fixtures (P5). Espresso-Intents stubs every outgoing intent
 * (`intending(anyIntent())`) so taps never launch an external app.
 *
 * Environment assumptions (documented per plan):
 * - The FileProvider is configured with `root-path "/"`, so files under `cacheDir` produce valid
 *   `content://` URIs — the `ACTION_VIEW` branch fires rather than swallowing an URI exception.
 * - The apk branch depends on `canRequestPackageInstalls()` being false (normal on a fresh
 *   emulator). The apk cases self-skip via `assumeFalse(IntentUtil.canInstallApks(...))` if the
 *   permission happens to be pre-granted, and via `assumeTrue(isApk)` if the platform mime db does
 *   not map the `apk` extension.
 */
@RunWith(AndroidJUnit4::class)
class FileOpenRoutingTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(activity.cacheDir, "test_open_routing_${System.currentTimeMillis()}").apply { mkdirs() }
        Intents.init()
        // Stub every outgoing intent so taps don't launch real apps / settings.
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        testDir.deleteRecursively()
    }

    // ==================== Regular files ====================

    @Test
    fun tapRegularFile_firesViewIntent() {
        FileFixtures.createTextFile(testDir, "notes.txt", "hello world")
        renderFolder()
        tapFile("notes.txt")

        intended(allOf(hasAction(Intent.ACTION_VIEW), hasFlag(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
    }

    @Test
    fun tapImageFile_firesViewIntent_withImageData() {
        val image = FileFixtures.createTextFile(testDir, "photo.jpg", "not-a-real-image")
        renderFolder()
        tapFile("photo.jpg")

        // Both the primary and the fallback ACTION_VIEW paths set data = the FileProvider uri,
        // so asserting the exact uri is robust regardless of which path the device takes.
        val expectedUri = FileProvider.getUriForFile(activity, "${activity.packageName}.provider", image)
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(expectedUri)))
    }

    // ==================== Zip files ====================

    @Test
    fun tapZipFile_showsUncompressDialog() {
        FileFixtures.createZip(testDir, "archive.zip", mapOf("a.txt" to "1", "b.txt" to "2"))
        renderFolder()
        tapFile("archive.zip")

        // Non-password zip -> UncompressDialog (its title + extract button are unique to it).
        waitForText(string(R.string.action_uncompress))
        composeTestRule.onNodeWithText(string(R.string.action_uncompress)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.uncompress_extract)).assertIsDisplayed()
    }

    @Test
    fun tapPasswordZip_showsPasswordDialog() {
        FileFixtures.createPasswordZip(testDir, "secret.zip", "pw123", mapOf("s.txt" to "x"))
        renderFolder()
        tapFile("secret.zip")

        // Password-protected zip -> PasswordUncompressDialog (distinct title + password field).
        waitForText(string(R.string.uncompress_password_title))
        composeTestRule.onNodeWithText(string(R.string.uncompress_password_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.uncompress_password_hint)).assertExists()
    }

    // ==================== Apk files ====================

    @Test
    fun tapApkFile_noInstallPermission_showsApkPermissionDialog() {
        assumeFalse(IntentUtil.canInstallApks(activity))
        val apk = FileFixtures.createFakeApk(testDir, "app.apk")
        assumeTrue(MimeTypeUtil.isApk(MimeTypeUtil.getMimeType(apk)))

        renderFolder()
        tapFile("app.apk")

        waitForText(string(R.string.apk_permission_title))
        composeTestRule.onNodeWithText(string(R.string.apk_permission_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.apk_permission_message)).assertIsDisplayed()
    }

    @Test
    fun apkPermissionDialog_settingsButton_firesManageUnknownSourcesIntent() {
        assumeFalse(IntentUtil.canInstallApks(activity))
        val apk = FileFixtures.createFakeApk(testDir, "app.apk")
        assumeTrue(MimeTypeUtil.isApk(MimeTypeUtil.getMimeType(apk)))

        renderFolder()
        tapFile("app.apk")

        waitForText(string(R.string.apk_permission_settings))
        composeTestRule.onNodeWithText(string(R.string.apk_permission_settings)).performClick()
        composeTestRule.waitForIdle()

        intended(hasAction(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
    }

    // ==================== Folders ====================

    @Test
    fun tapFolder_navigates_doesNotFireViewIntent() {
        FileFixtures.createFolder(testDir, "MyFolder")
        var navigatedPath: String? = null
        renderFolder(onNavigateToFolder = { navigatedPath = it })
        tapFile("MyFolder")

        assertEquals(File(testDir, "MyFolder").absolutePath, navigatedPath)
        // Tapping a folder navigates in-app and starts no activity at all.
        Intents.assertNoUnverifiedIntents()
    }

    // ==================== Helpers ====================

    private fun renderFolder(onNavigateToFolder: (String) -> Unit = {}) {
        composeTestRule.setContent {
            FileExplorerTheme {
                FolderScreen(
                    path = testDir.absolutePath,
                    onNavigateToFolder = onNavigateToFolder,
                    onNavigateBack = {}
                )
            }
        }
        // The list loads asynchronously; callers wait for their target file via tapFile().
    }

    private fun tapFile(name: String) {
        waitForText(name)
        composeTestRule.onNodeWithText(name).performClick()
        composeTestRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = activity.getString(id)
}
