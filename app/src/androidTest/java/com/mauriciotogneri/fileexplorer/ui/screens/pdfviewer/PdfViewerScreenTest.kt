package com.mauriciotogneri.fileexplorer.ui.screens.pdfviewer

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import org.hamcrest.Matchers.allOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.source.AndroidPdfDocumentOpener
import com.mauriciotogneri.fileexplorer.data.util.PdfViewerMode
import com.mauriciotogneri.fileexplorer.testutil.DocumentFixtures
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The real [PdfViewerScreen] + [PdfViewerViewModel] over real PDFs and the platform renderer.
 *
 * Documents are built at runtime by [DocumentFixtures.createPdf]; the encrypted and linked ones are
 * the checked-in assets documented on `AndroidPdfDocumentSourceTest`. Outgoing intents are stubbed
 * with Espresso-Intents so Share never launches a real chooser.
 */
@RunWith(AndroidJUnit4::class)
class PdfViewerScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(activity.cacheDir, "test_pdf_viewer_${System.nanoTime()}").apply { mkdirs() }
        Intents.init()
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        testDir.deleteRecursively()
    }

    // ==================== Pages ====================

    @Test
    fun document_rendersItsFirstPageAndThePageIndicator() {
        render(DocumentFixtures.createPdf(testDir, pageCount = 3))

        waitForContentDescription(pageDescription(1))
        composeTestRule.onNodeWithContentDescription(pageDescription(1)).assertIsDisplayed()
        composeTestRule.onNodeWithText(indicator(1, 3)).assertIsDisplayed()
        // The description is published by the drawn page and by a page that failed to draw; the
        // error text is what tells the two apart.
        assertTrue(
            composeTestRule.onAllNodesWithText(string(R.string.pdf_viewer_page_error))
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            "A valid document must not reach the load-error state",
            composeTestRule.onAllNodesWithText(string(R.string.pdf_viewer_load_error))
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun corruptFile_showsTheLoadError() {
        render(File(testDir, "broken.pdf").apply { writeText("not a pdf at all") })

        waitForText(string(R.string.pdf_viewer_load_error))
        composeTestRule.onNodeWithText(string(R.string.pdf_viewer_load_error)).assertIsDisplayed()
    }

    /**
     * On a device with only the classic renderer, the production mode decision must land on the
     * dedicated message rather than a password prompt. Skipped on API 31+, read from the platform
     * rather than from `PdfViewerSupport`: there the decision depends on the S extension, and the
     * forced variant below covers the screen.
     */
    @Test
    fun encryptedDocument_onAClassicRendererDevice_saysPasswordsAreUnsupported() {
        assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
        render(asset("pdf_encrypted_secret.pdf"), opener = AndroidPdfDocumentOpener())

        waitForText(string(R.string.pdf_viewer_password_unsupported))
        composeTestRule.onNodeWithText(string(R.string.pdf_viewer_password_unsupported)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.uncompress_password_title)).assertDoesNotExist()
    }

    @Test
    fun encryptedDocument_inViewOnlyMode_saysPasswordsAreUnsupported() {
        render(asset("pdf_encrypted_secret.pdf"), opener = AndroidPdfDocumentOpener(PdfViewerMode.VIEW_ONLY))

        waitForText(string(R.string.pdf_viewer_password_unsupported))
        composeTestRule.onNodeWithText(string(R.string.pdf_viewer_password_unsupported)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.uncompress_password_title)).assertDoesNotExist()
    }

    // ==================== Password ====================
    // The prompt is `PdfPasswordDialog`, driven here through the screen that shows it.

    @Test
    fun encryptedDocument_asksForItsPassword_rejectsAWrongOne_andOpensWithTheRightOne() {
        assumeFullRendererAvailable()
        render(asset("pdf_encrypted_secret.pdf"), opener = AndroidPdfDocumentOpener(PdfViewerMode.FULL))

        waitForText(string(R.string.pdf_viewer_password_message))
        composeTestRule.onNodeWithText(string(R.string.uncompress_error_wrong_password)).assertDoesNotExist()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("guess")
        composeTestRule.onNode(hasText(string(R.string.action_open)) and hasClickAction()).performClick()
        waitForText(string(R.string.uncompress_error_wrong_password))

        composeTestRule.onNode(hasSetTextAction()).performTextInput("secret")
        composeTestRule.onNode(hasText(string(R.string.action_open)) and hasClickAction()).performClick()

        waitForContentDescription(pageDescription(1))
        composeTestRule.onNodeWithText(string(R.string.pdf_viewer_password_message)).assertDoesNotExist()
        composeTestRule.onNodeWithText(indicator(1, 3)).assertIsDisplayed()
    }

    @Test
    fun cancellingThePasswordPrompt_closesTheViewer() {
        assumeFullRendererAvailable()
        var finished = false
        render(
            asset("pdf_encrypted_secret.pdf"),
            opener = AndroidPdfDocumentOpener(PdfViewerMode.FULL),
            onFinish = { finished = true }
        )

        waitForText(string(R.string.pdf_viewer_password_message))
        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).performClick()

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) { finished }
        assertTrue(finished)
    }

    // ==================== Go to page ====================

    @Test
    fun goToPage_rejectsAnOutOfRangeNumberAndJumpsToAValidOne() {
        render(DocumentFixtures.createPdf(testDir, pageCount = 6))
        waitForText(indicator(1, 6))

        composeTestRule.onNodeWithText(indicator(1, 6)).performClick()
        waitForText(string(R.string.pdf_viewer_go_to_page))

        val field = composeTestRule.onNode(hasSetTextAction())
        field.performTextInput("7")
        composeTestRule.onNodeWithText(activity.getString(R.string.pdf_viewer_go_to_page_invalid, 6))
            .assertIsDisplayed()
        composeTestRule.onNode(hasText(string(R.string.pdf_viewer_go)) and hasClickAction())
            .assertIsNotEnabled()

        field.performTextReplacement("4")
        composeTestRule.onNode(hasText(string(R.string.pdf_viewer_go)) and hasClickAction()).performClick()

        waitForText(indicator(4, 6))
        composeTestRule.onNodeWithText(indicator(4, 6)).assertIsDisplayed()
    }

    // ==================== Search ====================

    @Test
    fun search_countsMatchesAcrossPages_andStepsThroughThem() {
        assumeFullRendererAvailable()
        render(
            DocumentFixtures.createPdf(testDir, pageCount = 3) { index ->
                if (index == 0) "okapi" else "zebra"
            },
            opener = AndroidPdfDocumentOpener(PdfViewerMode.FULL)
        )
        waitForContentDescription(string(R.string.pdf_viewer_search))

        composeTestRule.onNodeWithContentDescription(string(R.string.pdf_viewer_search)).performClick()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("zebra")
        composeTestRule.onNode(hasSetTextAction()).performImeAction()

        waitForText(position(1, 2))
        composeTestRule.onNodeWithText(position(1, 2)).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(string(R.string.pdf_viewer_search_next)).performClick()
        waitForText(position(2, 2))
        composeTestRule.onNodeWithContentDescription(string(R.string.pdf_viewer_search_next)).performClick()
        waitForText(position(1, 2))
        composeTestRule.onNodeWithContentDescription(string(R.string.pdf_viewer_search_previous)).performClick()
        waitForText(position(2, 2))

        composeTestRule.onNodeWithContentDescription(string(R.string.pdf_viewer_search_close)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(position(2, 2)).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(string(R.string.pdf_viewer_search)).assertIsDisplayed()
    }

    @Test
    fun search_withNoMatches_saysSo() {
        assumeFullRendererAvailable()
        render(DocumentFixtures.createPdf(testDir, pageCount = 2), opener = AndroidPdfDocumentOpener(PdfViewerMode.FULL))
        waitForContentDescription(string(R.string.pdf_viewer_search))

        composeTestRule.onNodeWithContentDescription(string(R.string.pdf_viewer_search)).performClick()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("zebra")
        composeTestRule.onNode(hasSetTextAction()).performImeAction()

        waitForText(string(R.string.pdf_viewer_search_no_matches))
        composeTestRule.onNodeWithText(string(R.string.pdf_viewer_search_no_matches)).assertIsDisplayed()
    }

    @Test
    fun viewOnly_offersNoSearch() {
        render(DocumentFixtures.createPdf(testDir, pageCount = 1), opener = AndroidPdfDocumentOpener(PdfViewerMode.VIEW_ONLY))
        waitForContentDescription(pageDescription(1))

        composeTestRule.onNodeWithContentDescription(string(R.string.pdf_viewer_search)).assertDoesNotExist()
    }

    // ==================== Links ====================

    @Test
    fun tappingAGoToLink_scrollsToItsPage() {
        assumeFullRendererAvailable()
        render(asset("pdf_links.pdf"), opener = AndroidPdfDocumentOpener(PdfViewerMode.FULL))
        waitForContentDescription(pageDescription(1))

        // "Jump to page three": /Rect [20 300 180 340] on a 300 x 400 pt page, (100, 80) from the top.
        tapPagePoint(page = 1, xPt = 100f, yPt = 80f, pageWidthPt = 300f)

        waitForText(indicator(3, 3))
        composeTestRule.onNodeWithText(indicator(3, 3)).assertIsDisplayed()
    }

    @Test
    fun tappingAWebLink_opensItOutsideTheApp() {
        assumeFullRendererAvailable()
        render(asset("pdf_links.pdf"), opener = AndroidPdfDocumentOpener(PdfViewerMode.FULL))
        waitForContentDescription(pageDescription(1))

        // "Visit website": /Rect [20 200 180 240], (100, 180) from the top.
        tapPagePoint(page = 1, xPt = 100f, yPt = 180f, pageWidthPt = 300f)

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            Intents.getIntents().any { it.action == Intent.ACTION_VIEW && it.dataString == "https://example.com" }
        }
        intended(allOf(hasAction(Intent.ACTION_VIEW), hasData("https://example.com")))
    }

    @Test
    fun viewOnly_ignoresLinks() {
        render(asset("pdf_links.pdf"), opener = AndroidPdfDocumentOpener(PdfViewerMode.VIEW_ONLY))
        waitForContentDescription(pageDescription(1))

        // One tap: a second one this soon would be read as a double tap, which zooms.
        tapPagePoint(page = 1, xPt = 100f, yPt = 180f, pageWidthPt = 300f)
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()

        assertTrue(Intents.getIntents().none { it.action == Intent.ACTION_VIEW })
        composeTestRule.onNodeWithText(indicator(1, 3)).assertIsDisplayed()
    }

    // ==================== Share / delete ====================

    @Test
    fun shareButton_firesChooserIntent() {
        render(DocumentFixtures.createPdf(testDir, pageCount = 1))

        waitForClickable(string(R.string.action_share))
        composeTestRule.onNode(hasText(string(R.string.action_share)) and hasClickAction()).performClick()
        composeTestRule.waitForIdle()

        intended(hasAction(Intent.ACTION_CHOOSER))
    }

    @Test
    fun delete_confirmThenFinishesAndRemovesFile() {
        val file = DocumentFixtures.createPdf(testDir, pageCount = 1)
        var finished = false
        render(file, onFinish = { finished = true })

        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()
        waitForText(string(R.string.dialog_cancel))
        composeTestRule
            .onAllNodes(hasText(string(R.string.dialog_delete)) and hasClickAction())
            .onLast()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) { finished }
        assertFalse(file.exists())
    }

    // ==================== Helpers ====================

    private fun render(
        file: File,
        opener: AndroidPdfDocumentOpener = AndroidPdfDocumentOpener(),
        onFinish: () -> Unit = {}
    ): PdfViewerViewModel {
        val viewModel = PdfViewerViewModel(
            filePath = file.absolutePath,
            source = "test",
            application = activity.application,
            fileRepository = FileRepository(),
            opener = opener
        )
        composeTestRule.setContent {
            FileExplorerTheme {
                PdfViewerScreen(
                    viewModel = viewModel,
                    onBackClick = {},
                    onFinish = onFinish
                )
            }
        }
        return viewModel
    }

    private fun asset(name: String): File =
        DocumentFixtures.copyAsset(InstrumentationRegistry.getInstrumentation().context, name, testDir)

    /** Taps the point ([xPt], [yPt]) points on [page], 1-based, rendered at the node's width. */
    private fun tapPagePoint(page: Int, xPt: Float, yPt: Float, pageWidthPt: Float) {
        composeTestRule.onNodeWithContentDescription(pageDescription(page)).performTouchInput {
            val pxPerPt = width / pageWidthPt
            click(Offset(xPt * pxPerPt, yPt * pxPerPt))
        }
    }

    private fun position(current: Int, total: Int) =
        activity.resources.getQuantityString(R.plurals.pdf_viewer_search_position, total, current, total)

    /** Read from the platform, never from `PdfViewerSupport`, so a wrong mode decision fails rather than skips. */
    private fun assumeFullRendererAvailable() {
        assumeTrue(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 13)
        )
    }

    private fun pageDescription(page: Int) = activity.getString(R.string.pdf_viewer_page_description, page)

    private fun indicator(page: Int, count: Int) = activity.getString(R.string.pdf_viewer_page_indicator, page, count)

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForContentDescription(description: String) {
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForClickable(text: String) {
        composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
            composeTestRule.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = activity.getString(id)

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
