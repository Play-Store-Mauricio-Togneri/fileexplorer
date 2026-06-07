package com.mauriciotogneri.fileexplorer.ui.screens.imageviewer

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Verifies the real [ImageViewerScreen] + [ImageViewerViewModel] over real temp files: the error
 * state for undecodable input, that a valid image avoids it, and the Share / Delete actions
 * (including the Delete -> confirm dialog -> finish wiring). Outgoing intents are stubbed with
 * Espresso-Intents so Share never launches a real chooser.
 */
@RunWith(AndroidJUnit4::class)
class ImageViewerScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(activity.cacheDir, "test_image_viewer_${System.currentTimeMillis()}").apply { mkdirs() }
        Intents.init()
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        testDir.deleteRecursively()
    }

    @Test
    fun undecodableFile_showsErrorState() {
        // A .png name that holds non-image bytes: passes the allowlist but fails to decode.
        val file = File(testDir, "broken.png").apply { writeText("not really an image") }
        renderViewer(file)

        waitForText(string(R.string.image_viewer_load_error))
        composeTestRule.onNodeWithText(string(R.string.image_viewer_load_error)).assertIsDisplayed()
    }

    @Test
    fun validImage_doesNotShowErrorState() {
        val file = writePng("photo.png")
        renderViewer(file)

        composeTestRule.waitForIdle()
        // The error message only renders on a decode failure, which a valid PNG never triggers.
        assertTrue(
            composeTestRule.onAllNodesWithText(string(R.string.image_viewer_load_error))
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun actionBar_showsShareAndDelete() {
        val file = writePng("photo.png")
        renderViewer(file)

        composeTestRule.onNodeWithText(string(R.string.action_share)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).assertIsDisplayed()
    }

    @Test
    fun shareButton_firesChooserIntent() {
        val file = writePng("photo.png")
        renderViewer(file)

        // Share is enabled only once the file metadata has loaded.
        waitForClickable(string(R.string.action_share))
        composeTestRule.onNode(hasText(string(R.string.action_share)) and hasClickAction()).performClick()
        composeTestRule.waitForIdle()

        intended(hasAction(Intent.ACTION_CHOOSER))
    }

    @Test
    fun delete_confirmThenFinishesAndRemovesFile() {
        val file = writePng("photo.png")
        var finished = false
        renderViewer(file, onFinish = { finished = true })

        // Pre-dialog, the bottom-bar Delete is the only "Delete" on screen.
        composeTestRule.onNodeWithText(string(R.string.action_delete)).performClick()

        // Confirm dialog open (its Cancel label is unique).
        waitForText(string(R.string.dialog_cancel))
        composeTestRule.onNodeWithText(string(R.string.dialog_cancel)).assertIsDisplayed()

        // Two clickable "Delete" nodes now exist (bottom bar + dialog confirm); the dialog's is last.
        composeTestRule
            .onAllNodes(hasText(string(R.string.dialog_delete)) and hasClickAction())
            .onLast()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { !file.exists() }
        composeTestRule.waitForIdle()
        assertTrue(finished)
        assertFalse(file.exists())
    }

    // ==================== Helpers ====================

    private fun renderViewer(file: File, onFinish: () -> Unit = {}) {
        val viewModel = ImageViewerViewModel(
            filePath = file.absolutePath,
            source = "test",
            application = activity.application,
            fileRepository = FileRepository()
        )
        composeTestRule.setContent {
            FileExplorerTheme {
                ImageViewerScreen(
                    viewModel = viewModel,
                    onBackClick = {},
                    onFinish = onFinish
                )
            }
        }
    }

    private fun writePng(name: String): File {
        val file = File(testDir, name)
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForClickable(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(@StringRes id: Int): String = activity.getString(id)
}
