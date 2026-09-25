package com.mauriciotogneri.fileexplorer.ui.screens.iteminfo

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.util.AndroidRuntimeException
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.ImageViewerActivity
import com.mauriciotogneri.fileexplorer.activities.PdfViewerActivity
import com.mauriciotogneri.fileexplorer.activities.TextViewerActivity
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil
import com.mauriciotogneri.fileexplorer.testutil.DocumentFixtures
import com.mauriciotogneri.fileexplorer.testutil.FakeStorageSource
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.testutil.hasClickLabel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext

/**
 * The real [ItemInfoScreen] *wrapper* — everything around the [ItemInfoContent] that
 * `ItemInfoScreenTest` and `ItemInfoMetadataTest` already cover thoroughly: the loading spinner, the
 * error text, and the `LaunchedEffect` that turns a `ItemInfoUiEvent.OpenFile` into an actual launch.
 *
 * None of it had a test, so tapping the file icon in Info could have done nothing at all and every
 * existing ItemInfo test would still have passed: they drive `ItemInfoContent` directly and stop at
 * its `onOpenFile` callback.
 *
 * Three mechanisms are used to drive and observe the routing:
 *
 * - Espresso-Intents for the ordinary open, the APK permission dialog's Settings button and for
 *   asserting that the lifecycle retry launches *nothing*.
 * - A [ViewerRoutingContext] provided as `LocalContext` for the two in-app viewer fallbacks. The
 *   screen takes the context it starts activities with from the composition, so substituting it is
 *   the only way to refuse every external launch — which is exactly the condition
 *   `IntentUtil.openFile` needs to reach `RequiresTextViewer` / `RequiresImageViewer` — and it
 *   records the viewer launch that follows. Espresso cannot do this half: its stubs make every
 *   launch succeed, so nothing ever falls back.
 * - A [TestLifecycleOwner] provided as `LocalLifecycleOwner` for the `repeatOnLifecycle(RESUMED)`
 *   APK-install retry.
 *
 * **What is not asserted.** The `ItemInfoUiEvent.ShowToast` arm of the same collector. Text toasts
 * are rendered by the system on API 30+ rather than in the app's window, so no Compose or Espresso
 * matcher can see one on the emulators this suite runs on — the same limit `StartupScreenTest`
 * documents. The collector itself is covered by every `OpenFile` case below; what a toast arm needs
 * to become assertable is a seam over the toast call (an injected `showMessage: (Int) -> Unit`), not
 * a better matcher.
 */
@RunWith(AndroidJUnit4::class)
class ItemInfoScreenEventsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(activity.cacheDir, "test_iteminfo_events_${System.currentTimeMillis()}").apply { mkdirs() }
        Intents.init()
        // Stub every outgoing intent so a tap never launches a real app or the Settings screen.
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        testDir.deleteRecursively()
    }

    // ==================== Loading / error states ====================

    @Test
    fun whileLoading_showsSpinnerAndNoFileRows() {
        val file = FileFixtures.createTextFile(testDir, "notes.txt", "hello")
        render(viewModelFor(file, ioDispatcher = NeverRuns))
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
        // The content is mutually exclusive with the spinner: no row may be on screen yet.
        composeTestRule.onNodeWithText(string(R.string.info_name)).assertDoesNotExist()
    }

    @Test
    fun missingFile_showsErrorMessage() {
        render(viewModelFor(File(testDir, "gone.txt")))

        waitForText(string(R.string.info_error))
        composeTestRule.onNodeWithText(string(R.string.info_error)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.info_name)).assertDoesNotExist()
    }

    // ==================== OpenFile -> IntentUtil.openFile ====================

    @Test
    fun tapFile_opensThatFile() {
        val file = FileFixtures.createTextFile(testDir, "notes.txt", "hello world")
        render(viewModelFor(file))
        tapOpen()

        // Both the typed launch and the untyped fallback carry the FileProvider uri as their data,
        // so asserting it holds whichever path the device takes — and it pins the intent to *this*
        // file rather than merely to "some VIEW intent happened".
        val expectedUri = FileProvider.getUriForFile(activity, "${activity.packageName}.provider", file)
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData(expectedUri)))
    }

    @Test
    fun tapZip_showsUncompressDialog() {
        val zip = FileFixtures.createZip(testDir, "archive.zip", mapOf("a.txt" to "1", "b.txt" to "2"))
        render(viewModelFor(zip))
        tapOpen()

        waitForText(string(R.string.action_uncompress))
        composeTestRule.onNodeWithText(string(R.string.uncompress_extract)).assertIsDisplayed()
    }

    @Test
    fun tapPasswordProtectedZip_showsPasswordDialog() {
        val zip = FileFixtures.createPasswordZip(testDir, "secret.zip", "pw123", mapOf("s.txt" to "x"))
        render(viewModelFor(zip))
        tapOpen()

        waitForText(string(R.string.uncompress_password_title))
        composeTestRule.onNodeWithText(string(R.string.uncompress_password_hint)).assertExists()
    }

    @Test
    fun tapApk_withoutInstallPermission_showsApkPermissionDialog() {
        assumeTrue("Install permission check requires Android O+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        assumeFalse("Device has pre-granted unknown sources install permission", activity.packageManager.canRequestPackageInstalls())
        assertFalse("IntentUtil.canInstallApks must return false when platform permission is missing", IntentUtil.canInstallApks(activity))
        val apk = FileFixtures.createFakeApk(testDir, "app.apk")
        assumeTrue(MimeTypeUtil.isApk(MimeTypeUtil.getMimeType(apk)))
        render(viewModelFor(apk))
        tapOpen()

        waitForText(string(R.string.apk_permission_title))
        composeTestRule.onNodeWithText(string(R.string.apk_permission_message)).assertIsDisplayed()
    }

    @Test
    fun apkPermissionDialog_settingsButton_firesManageUnknownSourcesIntent() {
        // Reaching this dialog at all means the install permission is missing, which on API 26+ is
        // the only way `canInstallApks` returns false — so the settings intent is the O+ one.
        assumeTrue("Install permission check requires Android O+", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        assumeFalse("Device has pre-granted unknown sources install permission", activity.packageManager.canRequestPackageInstalls())
        assertFalse("IntentUtil.canInstallApks must return false when platform permission is missing", IntentUtil.canInstallApks(activity))
        val apk = FileFixtures.createFakeApk(testDir, "app.apk")
        assumeTrue(MimeTypeUtil.isApk(MimeTypeUtil.getMimeType(apk)))
        render(viewModelFor(apk))
        tapOpen()

        waitForText(string(R.string.apk_permission_settings))
        composeTestRule.onNodeWithText(string(R.string.apk_permission_settings)).performClick()
        composeTestRule.waitForIdle()

        intended(hasAction(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
    }

    /**
     * The `repeatOnLifecycle(RESUMED)` retry, driven in both directions through the screen's
     * [ItemInfoScreen]'s `canInstallApks` probe without modifying device-wide state:
     * 1. Without permission: re-entering RESUMED leaves the pending install alone.
     * 2. With permission: re-entering RESUMED clears the dialog and triggers the APK installation.
     */
    @Test
    fun pendingApkInstall_resumedWithoutPermission_keepsDialogAndLaunchesNothing() {
        val apk = FileFixtures.createFakeApk(testDir, "app.apk")
        val viewModel = viewModelFor(apk)
        val owner = TestLifecycleOwner()
        composeTestRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        render(viewModel, lifecycleOwner = owner, canInstallApks = { false })
        waitForText(string(R.string.info_name))

        val loaded = checkNotNull(viewModel.state.value.file) { "file info did not load" }
        composeTestRule.runOnUiThread { viewModel.setPendingApkInstall(loaded) }
        waitForText(string(R.string.apk_permission_title))

        composeTestRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
        composeTestRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.apk_permission_title)).assertIsDisplayed()
        Intents.assertNoUnverifiedIntents()
    }

    @Test
    fun pendingApkInstall_resumedWithPermission_clearsDialogAndLaunchesInstall() {
        val apk = FileFixtures.createFakeApk(testDir, "app.apk")
        val viewModel = viewModelFor(apk)
        val owner = TestLifecycleOwner()
        composeTestRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        render(viewModel, lifecycleOwner = owner, canInstallApks = { true })
        waitForText(string(R.string.info_name))

        val loaded = checkNotNull(viewModel.state.value.file) { "file info did not load" }
        composeTestRule.runOnUiThread { viewModel.setPendingApkInstall(loaded) }
        waitForText(string(R.string.apk_permission_title))

        composeTestRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
        composeTestRule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.apk_permission_title)).assertDoesNotExist()
        assertNull(viewModel.state.value.pendingApkInstall)
        intended(hasAction(Intent.ACTION_VIEW))
    }

    // ==================== OpenFile -> in-app viewer fallbacks ====================

    @Test
    fun tapTextFile_whenNothingCanOpenIt_launchesTextViewerActivity() {
        val file = FileFixtures.createTextFile(testDir, "notes.txt", "hello world")
        val refusing = ViewerRoutingContext(activity)
        render(viewModelFor(file), context = refusing)
        tapOpen()

        assertEquals(
            "Info must fall back to the in-app text viewer when no installed app can open the file",
            TextViewerActivity::class.java.name,
            awaitLaunch(refusing)
        )
    }

    @Test
    fun tapImage_whenNothingCanOpenIt_launchesImageViewerActivity() {
        val file = writePng("photo.png")
        val refusing = ViewerRoutingContext(activity)
        render(viewModelFor(file), context = refusing)
        tapOpen()

        assertEquals(
            "Info must fall back to the in-app image viewer when no installed app can open the file",
            ImageViewerActivity::class.java.name,
            awaitLaunch(refusing)
        )
    }

    @Test
    fun tapPdf_whenNothingCanOpenIt_launchesPdfViewerActivity() {
        val file = DocumentFixtures.createPdf(testDir, name = "report.pdf", pageCount = 1)
        val refusing = ViewerRoutingContext(activity)
        render(viewModelFor(file), context = refusing)
        tapOpen()

        assertEquals(
            "Info must fall back to the in-app PDF viewer when no installed app can open the file",
            PdfViewerActivity::class.java.name,
            awaitLaunch(refusing)
        )
    }

    // ==================== Helpers ====================

    private fun viewModelFor(
        file: File,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): ItemInfoViewModel = ItemInfoViewModel(
        filePath = file.absolutePath,
        application = activity.application,
        fileRepository = FileRepository(),
        // The uncompress handler asks the storage list for its allowed extraction roots, and the
        // fixtures live under cacheDir rather than on a real volume.
        storageRepository = StorageRepository(FakeStorageSource(testDir)),
        ioDispatcher = ioDispatcher
    )

    private fun render(
        viewModel: ItemInfoViewModel,
        context: Context = activity,
        lifecycleOwner: LifecycleOwner = activity,
        canInstallApks: (Context) -> Boolean = IntentUtil::canInstallApks
    ) {
        composeTestRule.setContent {
            FileExplorerTheme {
                // The screen reads both of these from the composition rather than taking them as
                // parameters, so providing them is the only seam available. Both default to the host
                // Activity, which is what ItemInfoActivity supplies in production.
                CompositionLocalProvider(
                    LocalContext provides context,
                    LocalLifecycleOwner provides lifecycleOwner
                ) {
                    ItemInfoScreen(
                        viewModel = viewModel,
                        onCloseClick = {},
                        canInstallApks = canInstallApks
                    )
                }
            }
        }
    }

    /** Taps the preview/icon, whose click action is the one labelled "open". */
    private fun tapOpen() {
        val openLabel = string(R.string.action_open)
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodes(hasClickLabel(openLabel)).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(hasClickLabel(openLabel)).performClick()
        composeTestRule.waitForIdle()
    }

    /** The class name of the first activity [context] was asked to start, waiting for the hop. */
    private fun awaitLaunch(context: ViewerRoutingContext): String? {
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            context.startedComponents.isNotEmpty()
        }
        return context.startedComponents.firstOrNull()?.className
    }

    private fun writePng(name: String): File {
        val file = File(testDir, name)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = activity.getString(id)

    /**
     * Refuses every implicit launch and records the explicit ones.
     *
     * Each launch `IntentUtil.openFile` attempts is implicit, and [AndroidRuntimeException] is the
     * refusal it treats as a dead end rather than retrying (see `IntentUtil.startActivityOrChooser`),
     * so refusing them all is what puts the file on the in-app viewer path. The viewer launch itself
     * is explicit — `TextViewerActivity.createIntent` names its component — so it is recorded and
     * allowed to pass.
     */
    private class ViewerRoutingContext(base: Context) : ContextWrapper(base) {
        /** Written on the main thread, read from the test thread while it polls. */
        val startedComponents = CopyOnWriteArrayList<ComponentName>()

        override fun startActivity(intent: Intent) {
            val component = intent.component ?: throw AndroidRuntimeException("no handler for $intent")
            startedComponents += component
        }
    }

    /** A lifecycle this test owns and drives, standing in for the host Activity's. */
    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle get() = registry
    }

    /**
     * Drops whatever it is handed, so a load started on it never runs.
     *
     * The real load settles in milliseconds — no other test can catch the screen mid-load, which is
     * why the spinner had no coverage at all.
     */
    private object NeverRuns : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
