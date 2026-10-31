package com.mauriciotogneri.fileexplorer.integration

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
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
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.testutil.DocumentFixtures
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.testutil.assumePlatformMapsApkExtension
import com.mauriciotogneri.fileexplorer.testutil.removeRecentFilesOnceWritten
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderScreen
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
 * Files with no extension are typed from their first bytes, so they route the same way.
 *
 * Real-wiring approach: the real [FolderScreen] + its real `FolderViewModel`/`FileRepository`
 * list a temp dir populated with real fixtures (P5). Espresso-Intents stubs every outgoing intent
 * (`intending(anyIntent())`) so taps never launch an external app.
 *
 * Environment assumptions (documented per plan):
 * - The FileProvider is configured with `root-path "/"`, so files under `cacheDir` produce valid
 *   `content://` URIs — the `ACTION_VIEW` branch fires rather than swallowing an URI exception.
 * - The apk branch depends on `canRequestPackageInstalls()` being false (normal on a fresh
 *   emulator). The apk cases self-skip if the platform install permission happens to be pre-granted,
 *   assert `IntentUtil.canInstallApks(...)` agrees, and skip if the platform's own MIME map does not
 *   map the `apk` extension (read from `MimeTypeMap`, never from production's detector).
 */
@RunWith(AndroidJUnit4::class)
class FileOpenRoutingTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private lateinit var testDir: File

    /**
     * The fixtures a test opened. A launched open counts as an open even when the launch is stubbed,
     * so `IntentUtil` writes the file to the app's own recents store; the suite shares one data
     * directory, so removing it again is this test's job.
     */
    private val openedPaths = mutableListOf<String>()

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
        removeRecentFilesOnceWritten(openedPaths)
        testDir.deleteRecursively()
    }

    // ==================== Regular files ====================

    /**
     * Also pins that a file tap stays a file tap: a tap handler that stopped branching on
     * `isDirectory` would hand the file to the folder navigation as well as, or instead of, opening it.
     */
    @Test
    fun tapRegularFile_firesViewIntent() {
        val notes = FileFixtures.createTextFile(testDir, "notes.txt", "hello world")
        openedPaths += notes.absolutePath
        var navigatedPath: String? = null
        renderFolder(onNavigateToFolder = { navigatedPath = it })
        tapFile("notes.txt")

        intended(allOf(hasAction(Intent.ACTION_VIEW), hasFlag(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
        assertNull("Tapping a file must not navigate into a folder", navigatedPath)
    }

    @Test
    fun tapImageFile_firesViewIntent_withImageData() {
        val image = FileFixtures.createTextFile(testDir, "photo.jpg", "not-a-real-image")
        openedPaths += image.absolutePath
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
        assumeTrue("Install permission check requires Android O+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        assumeFalse("Device has pre-granted unknown sources install permission", activity.packageManager.canRequestPackageInstalls())
        assertFalse("IntentUtil.canInstallApks must return false when platform permission is missing", IntentUtil.canInstallApks(activity))
        val apk = FileFixtures.createFakeApk(testDir, "app.apk")
        assumePlatformMapsApkExtension()

        renderFolder()
        tapFile("app.apk")

        waitForText(string(R.string.apk_permission_title))
        composeTestRule.onNodeWithText(string(R.string.apk_permission_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.apk_permission_message)).assertIsDisplayed()
    }

    /**
     * Not a second copy of the other screens' dialog tests, despite the shared name.
     *
     * `ApkPermissionDialog` is one shared component, but `onOpenSettings` is not — it is a
     * hand-copied one-liner on each of the five screens that open an APK, and each one can be
     * broken on its own. Each screen's is pinned by its own test:
     *
     * - the folder screen, the route most users reach an APK by: this test;
     * - Item Info: `ItemInfoScreenEventsTest.apkPermissionDialog_settingsButton_firesManageUnknownSourcesIntent`;
     * - Home, from a recents entry: `ActivityNavigationTest.homeRecentApk_settingsButton_firesManageUnknownSourcesIntent`;
     * - Search: `SearchScreenTest.apkPermissionDialog_settingsButton_firesManageUnknownSourcesIntent`;
     * - an analyzer category's listing:
     *   `AnalyzerCategoryScreenTest.apkPermissionDialog_settingsButton_firesManageUnknownSourcesIntent`.
     *
     * Empty the folder screen's `onOpenSettings` and only this test goes red. The package uri is
     * part of what is pinned: without it the platform opens the list of every app instead of this
     * one's toggle.
     *
     * The dialog's own button — that tapping it calls whatever it was handed — belongs to the
     * dialog and is not retested here.
     */
    @Test
    fun apkPermissionDialog_settingsButton_firesManageUnknownSourcesIntent() {
        assumeTrue("Install permission check requires Android O+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        assumeFalse("Device has pre-granted unknown sources install permission", activity.packageManager.canRequestPackageInstalls())
        assertFalse("IntentUtil.canInstallApks must return false when platform permission is missing", IntentUtil.canInstallApks(activity))
        val apk = FileFixtures.createFakeApk(testDir, "app.apk")
        assumePlatformMapsApkExtension()

        renderFolder()
        tapFile("app.apk")

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

    // ==================== Files with no extension ====================

    @Test
    fun tapExtensionlessZip_showsUncompressDialog() {
        FileFixtures.createZip(testDir, "archive", mapOf("a.txt" to "1", "b.txt" to "2"))
        renderFolder()
        tapFile("archive")

        waitForText(string(R.string.action_uncompress))
        composeTestRule.onNodeWithText(string(R.string.uncompress_extract)).assertIsDisplayed()
    }

    @Test
    fun tapExtensionlessJpeg_neverOpensAsAnyType() {
        val photo = File(testDir, "photo")
        photo.outputStream().use { output ->
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, output)
        }

        assertOpensAs(photo, "image/jpeg")
    }

    @Test
    fun tapExtensionlessPdf_neverOpensAsAnyType() {
        assertOpensAs(DocumentFixtures.createPdf(testDir, name = "report"), "application/pdf")
    }

    @Test
    fun tapExtensionlessVideo_neverOpensAsAnyType() {
        val clip = DocumentFixtures.copyAsset(
            InstrumentationRegistry.getInstrumentation().context,
            "sample_video.mp4",
            testDir,
            "clip"
        )

        assertOpensAs(clip, "video/mp4")
    }

    @Test
    fun tapExtensionlessText_neverOpensAsAnyType() {
        assertOpensAs(FileFixtures.createTextFile(testDir, "notes", "hello world\n"), "text/plain")
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

    /**
     * Taps [file] and asserts every `ACTION_VIEW` for it carried [type], or no type at all: the
     * untyped fallback, taken when nothing on the device handles the typed intent. Which of the two
     * goes out depends on the device's apps; "any type", the type before contents were read, never
     * does. The open finishes after its contents are read off the main thread, so the intent is
     * waited for rather than expected once the tap is idle.
     */
    private fun assertOpensAs(file: File, type: String) {
        openedPaths += file.absolutePath
        val uri: Uri = FileProvider.getUriForFile(activity, "${activity.packageName}.provider", file)
        renderFolder()
        tapFile(file.name)

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            Intents.getIntents().any { it.data == uri }
        }
        val views = Intents.getIntents().filter { it.action == Intent.ACTION_VIEW && it.data == uri }
        assertTrue(views.map { it.type }.toString(), views.all { it.type == type || it.type == null })
    }

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
