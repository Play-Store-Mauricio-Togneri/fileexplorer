package com.mauriciotogneri.fileexplorer.ui.screens.feedback

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.FeedbackScreen
import com.mauriciotogneri.fileexplorer.activities.FeedbackViewModel
import com.mauriciotogneri.fileexplorer.testutil.buttonWithText
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real `FeedbackScreen` from `FeedbackActivity`, exposed as an `internal` test seam,
 * driven by a real [FeedbackViewModel].
 *
 * This file previously asserted against a private replica that re-implemented the discard-dialog
 * state machine, so the production `handleBack` logic — which only prompts when there is unsent text
 * and submit has not been pressed — was never actually run.
 *
 * The submit *network* path is deliberately out of scope here; [FeedbackScreenAdditionalTest] covers
 * the in-flight UI by holding a request open.
 */
@RunWith(AndroidJUnit4::class)
class FeedbackScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    private lateinit var viewModel: FeedbackViewModel

    private fun renderFeedback(
        onBackClick: () -> Unit = {},
        onSubmitSuccess: () -> Unit = {}
    ) {
        viewModel = FeedbackViewModel(application)
        composeTestRule.setContent {
            FileExplorerTheme {
                FeedbackScreen(
                    onBackClick = onBackClick,
                    onSubmitSuccess = onSubmitSuccess,
                    viewModel = viewModel
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun typeFeedback(text: String) {
        composeTestRule.onNodeWithText(string(R.string.feedback_hint)).performTextInput(text)
        composeTestRule.waitForIdle()
    }

    private fun pressSystemBack() {
        composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    // ==================== Display ====================

    @Test
    fun feedbackScreen_displaysCorrectly() {
        renderFeedback()

        composeTestRule.onNodeWithText(string(R.string.drawer_feedback)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.feedback_hint)).assertIsDisplayed()
        composeTestRule.onNode(buttonWithText(string(R.string.feedback_submit))).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).assertIsDisplayed()
    }

    @Test
    fun feedbackScreen_showsCharacterCount() {
        renderFeedback()

        composeTestRule.onNodeWithText("0 / ${FeedbackViewModel.MAX_CHARACTERS}").assertIsDisplayed()
    }

    // ==================== Submit button enablement ====================

    @Test
    fun feedbackScreen_submitButtonDisabled_whenEmpty() {
        renderFeedback()

        composeTestRule.onNode(buttonWithText(string(R.string.feedback_submit))).assertIsNotEnabled()
    }

    @Test
    fun feedbackScreen_submitButtonEnabled_whenHasText() {
        renderFeedback()
        typeFeedback("This app needs a dark mode toggle")

        composeTestRule.onNode(buttonWithText(string(R.string.feedback_submit))).assertIsEnabled()
    }

    @Test
    fun feedbackScreen_whitespaceOnlyText_submitDisabled() {
        renderFeedback()
        typeFeedback("     ")

        composeTestRule.onNode(buttonWithText(string(R.string.feedback_submit))).assertIsNotEnabled()
    }

    @Test
    fun feedbackScreen_typeText_updatesField() {
        renderFeedback()
        typeFeedback("hello there")

        composeTestRule.onNodeWithText("hello there").assertIsDisplayed()
        composeTestRule.onNodeWithText("11 / ${FeedbackViewModel.MAX_CHARACTERS}").assertIsDisplayed()
    }

    // ==================== Discard dialog ====================

    @Test
    fun feedbackScreen_backWithNoText_closesDirectly() {
        var closed = false
        renderFeedback(onBackClick = { closed = true })

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()
        composeTestRule.waitForIdle()

        assertTrue("Back with no text should close immediately", closed)
        composeTestRule.onNodeWithText(string(R.string.feedback_discard_title)).assertDoesNotExist()
    }

    /**
     * Blank-but-non-empty text is not worth prompting about: `hasContent` uses `isNotBlank`.
     */
    @Test
    fun feedbackScreen_whitespaceOnlyText_backClosesDirectly() {
        var closed = false
        renderFeedback(onBackClick = { closed = true })
        typeFeedback("   ")

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()
        composeTestRule.waitForIdle()

        assertTrue("Back with only whitespace should close immediately", closed)
    }

    @Test
    fun feedbackScreen_backWithText_showsDiscardDialog() {
        var closed = false
        renderFeedback(onBackClick = { closed = true })
        typeFeedback("unsent feedback")

        composeTestRule.onNodeWithContentDescription(string(R.string.navigate_back)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.feedback_discard_title)).assertIsDisplayed()
        assertFalse("The screen must not close while the prompt is up", closed)
    }

    @Test
    fun feedbackScreen_systemBackWithText_showsDiscardDialog() {
        renderFeedback()
        typeFeedback("unsent feedback")

        pressSystemBack()

        composeTestRule.onNodeWithText(string(R.string.feedback_discard_title)).assertIsDisplayed()
    }

    @Test
    fun feedbackScreen_discardDialog_showsKeepEditingAndDiscard() {
        renderFeedback()
        typeFeedback("unsent feedback")
        pressSystemBack()

        composeTestRule.onNodeWithText(string(R.string.feedback_discard_message)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.feedback_discard)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.feedback_keep_editing)).assertIsDisplayed()
    }

    @Test
    fun feedbackScreen_selectKeepEditing_dismissesDialogAndKeepsText() {
        var closed = false
        renderFeedback(onBackClick = { closed = true })
        typeFeedback("draft worth keeping")
        pressSystemBack()

        composeTestRule.onNodeWithText(string(R.string.feedback_keep_editing)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.feedback_discard_title)).assertDoesNotExist()
        composeTestRule.onNodeWithText("draft worth keeping").assertIsDisplayed()
        assertFalse("Keep editing must not close the screen", closed)
    }

    @Test
    fun feedbackScreen_selectDiscard_closesScreen() {
        var closed = false
        renderFeedback(onBackClick = { closed = true })
        typeFeedback("throwaway draft")
        pressSystemBack()

        composeTestRule.onNodeWithText(string(R.string.feedback_discard)).performClick()
        composeTestRule.waitForIdle()

        assertTrue("Discard should close the screen", closed)
    }

    @Test
    fun feedbackScreen_backTwice_keepEditingThenDiscard() {
        var closed = false
        renderFeedback(onBackClick = { closed = true })
        typeFeedback("draft")

        pressSystemBack()
        composeTestRule.onNodeWithText(string(R.string.feedback_keep_editing)).performClick()
        composeTestRule.waitForIdle()
        assertFalse("Still open after keep editing", closed)

        // The prompt must arm again — a one-shot flag here would silently discard the draft.
        pressSystemBack()
        composeTestRule.onNodeWithText(string(R.string.feedback_discard_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.feedback_discard)).performClick()
        composeTestRule.waitForIdle()

        assertTrue("Discard on the second prompt should close", closed)
    }

    @Test
    fun feedbackScreen_clearingText_stopsPromptingOnBack() {
        var closed = false
        renderFeedback(onBackClick = { closed = true })
        typeFeedback("typed then removed")

        composeTestRule.onNodeWithText("typed then removed").performTextClearance()
        composeTestRule.waitForIdle()
        pressSystemBack()

        assertTrue("With the draft cleared, back should close directly", closed)
        composeTestRule.onNodeWithText(string(R.string.feedback_discard_title)).assertDoesNotExist()
    }
}
