package com.mauriciotogneri.fileexplorer.ui.screens.textviewer

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
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

/**
 * Verifies the real [TextViewerScreen] + [TextViewerViewModel] over real temp files: content
 * rendering, empty/error states, the truncation banner, and the Share / Delete actions
 * (including the Delete -> confirm dialog -> finish wiring). Outgoing intents are stubbed with
 * Espresso-Intents so Share never launches a real chooser.
 */
@RunWith(AndroidJUnit4::class)
class TextViewerScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val activity get() = composeTestRule.activity

    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = File(activity.cacheDir, "test_text_viewer_${System.currentTimeMillis()}").apply { mkdirs() }
        Intents.init()
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
        testDir.deleteRecursively()
    }

    @Test
    fun displaysFileContentByLine() {
        val file = File(testDir, "notes.txt").apply { writeText("alpha\nbeta\ngamma") }
        renderViewer(file)

        waitForText("alpha")
        composeTestRule.onNodeWithText("beta").assertIsDisplayed()
        composeTestRule.onNodeWithText("gamma").assertIsDisplayed()
    }

    @Test
    fun emptyFile_showsEmptyState() {
        val file = File(testDir, "empty.txt").apply { writeText("") }
        renderViewer(file)

        waitForText(string(R.string.text_viewer_empty))
        composeTestRule.onNodeWithText(string(R.string.text_viewer_empty)).assertIsDisplayed()
    }

    @Test
    fun unreadableFile_showsErrorState() {
        val file = File(testDir, "does_not_exist.txt")
        renderViewer(file)

        waitForText(string(R.string.text_viewer_read_error))
        composeTestRule.onNodeWithText(string(R.string.text_viewer_read_error)).assertIsDisplayed()
    }

    @Test
    fun largeFile_showsTruncationBanner() {
        val file = File(testDir, "big.txt")
        val line = "x".repeat(99) + "\n"
        val builder = StringBuilder()
        while (builder.length <= TextViewerViewModel.MAX_BYTES) {
            builder.append(line)
        }
        file.writeText(builder.toString())
        renderViewer(file)

        waitForText(string(R.string.text_viewer_truncated))
        composeTestRule.onNodeWithText(string(R.string.text_viewer_truncated)).assertIsDisplayed()
    }

    @Test
    fun actionBar_showsShareAndDelete() {
        val file = File(testDir, "notes.txt").apply { writeText("hello") }
        renderViewer(file)

        waitForText("hello")
        composeTestRule.onNodeWithText(string(R.string.action_share)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.action_delete)).assertIsDisplayed()
    }

    @Test
    fun shareButton_firesChooserIntent() {
        val file = File(testDir, "notes.txt").apply { writeText("hello") }
        renderViewer(file)

        waitForText("hello")
        composeTestRule.onNodeWithText(string(R.string.action_share)).performClick()
        composeTestRule.waitForIdle()

        intended(hasAction(Intent.ACTION_CHOOSER))
    }

    @Test
    fun delete_confirmThenFinishesAndRemovesFile() {
        val file = File(testDir, "notes.txt").apply { writeText("bye") }
        var finished = false
        renderViewer(file, onFinish = { finished = true })

        waitForText("bye")
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
        val viewModel = TextViewerViewModel(
            filePath = file.absolutePath,
            source = "test",
            application = activity.application,
            fileRepository = FileRepository()
        )
        composeTestRule.setContent {
            FileExplorerTheme {
                TextViewerScreen(
                    viewModel = viewModel,
                    onBackClick = {},
                    onFinish = onFinish
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
