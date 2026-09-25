package com.mauriciotogneri.fileexplorer.activities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.testutil.DocumentFixtures
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * [TextViewerActivity], [ImageViewerActivity] and [PdfViewerActivity] launched for real, which no test did before: their
 * five call sites were verified only as far as the `OpenFileResult` they branch on, so nothing
 * covered the Activity glue — the extras being read, the ViewModel being built from them, or the
 * guards around a launch that carries neither extra.
 *
 * These are the in-app fallback viewers, the last resort when no installed app handles a file, so
 * a failure here is total and user-visible.
 *
 * Covered per Activity: the file named by `EXTRA_FILE_PATH` is the one rendered; a launch without
 * that extra finishes instead of crashing or showing an empty viewer; and a launch without the
 * source extra still renders, i.e. the `DEFAULT_SOURCE` fallback applies rather than failing on a
 * missing value (the source itself is analytics-only and has no observable effect).
 *
 * Decoding is not re-asserted here — `ImageViewerScreenTest` and `TextViewerScreenTest` own the
 * screens' own behavior, including the error states. What is asserted is that the path the intent
 * carried is the path the screen ended up showing.
 */
@RunWith(AndroidJUnit4::class)
class ViewerActivityLaunchTest {

    /**
     * Empty rather than `createAndroidComposeRule`: each test launches its own Activity with the
     * intent under test. The rule only provides the waiting/matching against whatever is on screen.
     */
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(context.cacheDir, "viewer_launch_${System.currentTimeMillis()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    // ==================== TextViewerActivity ====================

    @Test
    fun textViewer_rendersTheFileFromTheIntent() {
        val file = FileFixtures.createTextFile(testDir, "notes.txt", "alpha")
        val intent = TextViewerActivity.createIntent(context, file.absolutePath, SOURCE)

        ActivityScenario.launch<TextViewerActivity>(intent).use {
            awaitText(file.name)

            // The title is File(filePath).name, so it pins the exact path the extra carried; the
            // line below it could only have come from reading that file off disk.
            composeTestRule.onNodeWithText(file.name).assertIsDisplayed()
            composeTestRule.onNodeWithText("alpha").assertIsDisplayed()
        }
    }

    @Test
    fun textViewer_withoutFilePathExtra_finishes() {
        ActivityScenario.launch<TextViewerActivity>(Intent(context, TextViewerActivity::class.java)).use { scenario ->
            assertDestroyed(scenario)
        }
    }

    @Test
    fun textViewer_withoutSourceExtra_stillRendersTheFile() {
        val file = FileFixtures.createTextFile(testDir, "notes.txt", "alpha")
        val intent = withoutSourceExtra(
            TextViewerActivity.createIntent(context, file.absolutePath, SOURCE)
        )

        ActivityScenario.launch<TextViewerActivity>(intent).use {
            awaitText("alpha")

            composeTestRule.onNodeWithText("alpha").assertIsDisplayed()
        }
    }

    // ==================== ImageViewerActivity ====================

    @Test
    fun imageViewer_rendersTheFileFromTheIntent() {
        val file = writePng("photo.png")
        val intent = ImageViewerActivity.createIntent(context, file.absolutePath, SOURCE)

        ActivityScenario.launch<ImageViewerActivity>(intent).use {
            awaitText(file.name)

            composeTestRule.onNodeWithText(file.name).assertIsDisplayed()
            // The viewer's own chrome, so the assertion above is about a screen that actually came
            // up rather than about a stray label. Existence only: the bar sits behind the system
            // navigation inset on an edge-to-edge Activity.
            composeTestRule.onNodeWithText(string(R.string.action_share)).assertExists()
            composeTestRule.onNodeWithText(string(R.string.action_delete)).assertExists()
        }
    }

    @Test
    fun imageViewer_withoutFilePathExtra_finishes() {
        ActivityScenario.launch<ImageViewerActivity>(Intent(context, ImageViewerActivity::class.java)).use { scenario ->
            assertDestroyed(scenario)
        }
    }

    @Test
    fun imageViewer_withoutSourceExtra_stillRendersTheFile() {
        val file = writePng("photo.png")
        val intent = withoutSourceExtra(
            ImageViewerActivity.createIntent(context, file.absolutePath, SOURCE)
        )

        ActivityScenario.launch<ImageViewerActivity>(intent).use {
            awaitText(file.name)

            composeTestRule.onNodeWithText(file.name).assertIsDisplayed()
        }
    }

    // ==================== PdfViewerActivity ====================

    @Test
    fun pdfViewer_rendersTheFileFromTheIntent() {
        val file = DocumentFixtures.createPdf(testDir, name = "report.pdf", pageCount = 2)
        val intent = PdfViewerActivity.createIntent(context, file.absolutePath, SOURCE)

        ActivityScenario.launch<PdfViewerActivity>(intent).use {
            awaitText(file.name)

            composeTestRule.onNodeWithText(file.name).assertIsDisplayed()
            // Only a document read off that path can say how many pages it has.
            awaitText(context.getString(R.string.pdf_viewer_page_indicator, 1, 2))
            composeTestRule.onNodeWithText(string(R.string.action_share)).assertExists()
            composeTestRule.onNodeWithText(string(R.string.action_delete)).assertExists()
        }
    }

    @Test
    fun pdfViewer_withoutFilePathExtra_finishes() {
        ActivityScenario.launch<PdfViewerActivity>(Intent(context, PdfViewerActivity::class.java)).use { scenario ->
            assertDestroyed(scenario)
        }
    }

    @Test
    fun pdfViewer_withoutSourceExtra_stillRendersTheFile() {
        val file = DocumentFixtures.createPdf(testDir, name = "report.pdf", pageCount = 2)
        val intent = withoutSourceExtra(
            PdfViewerActivity.createIntent(context, file.absolutePath, SOURCE)
        )

        ActivityScenario.launch<PdfViewerActivity>(intent).use {
            awaitText(context.getString(R.string.pdf_viewer_page_indicator, 1, 2))

            composeTestRule.onNodeWithText(file.name).assertIsDisplayed()
        }
    }

    // ==================== Helpers ====================

    /**
     * Both viewers call `finish()` from `onCreate` before `setContent`, which skips `onStart`
     * entirely, so DESTROYED is the only steady state the Activity can reach — and
     * `ActivityScenario.launch` returns once it is there. No Compose assertion is possible (or
     * wanted) on an Activity that set no content.
     */
    private fun assertDestroyed(scenario: ActivityScenario<*>) {
        assertEquals(
            "A launch without the file-path extra must finish instead of showing an empty viewer",
            Lifecycle.State.DESTROYED,
            scenario.state
        )
    }

    /**
     * [intent] with the source extra removed — the shape a launch takes if the extra is ever dropped
     * or arrives from outside `createIntent`.
     *
     * The key is found by its value rather than spelled out, because both extra keys are private to
     * the Activity's companion: hardcoding "extra_source" here would leave the test quietly
     * stripping nothing the day the key is renamed, and passing whatever it meant to remove.
     */
    private fun withoutSourceExtra(intent: Intent): Intent {
        val keys = requireNotNull(intent.extras) { "the launch intent carries no extras" }.keySet()
        val sourceKey = requireNotNull(keys.firstOrNull { intent.getStringExtra(it) == SOURCE }) {
            "no extra carries the source value, so there is nothing to strip"
        }
        return intent.apply { removeExtra(sourceKey) }
    }

    private fun writePng(name: String): File {
        val file = File(testDir, name)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun awaitText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = context.getString(id)

    private companion object {
        const val TIMEOUT_MS = 20_000L

        /** Distinct from every file name and path here, so `withoutSourceExtra` cannot mistake it. */
        const val SOURCE = "viewer_launch_test"
    }
}
