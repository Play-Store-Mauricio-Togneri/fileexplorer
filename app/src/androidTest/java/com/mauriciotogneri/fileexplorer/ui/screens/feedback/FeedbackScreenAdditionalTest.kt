package com.mauriciotogneri.fileexplorer.ui.screens.feedback

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Character-limit, in-flight-submit and submit-outcome behavior of the real `FeedbackScreen`.
 *
 * The submitting state used to be faked by a replica that simply took `isSubmitting` as a parameter.
 * Here a real submit is started against an [OkHttpClient] whose interceptor blocks on [releaseGate]
 * until the assertions are done, so the spinner, the disabled button and the disabled text field are
 * the ones production actually renders while a request is open.
 */
@RunWith(AndroidJUnit4::class)
class FeedbackScreenAdditionalTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val application =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    /** Held closed while a submit is in flight, so the "submitting" UI stays on screen. */
    private val releaseGate = CountDownLatch(1)

    private fun string(id: Int): String = composeTestRule.activity.getString(id)

    @After
    fun tearDown() {
        // Never leave the interceptor thread parked, even if an assertion failed.
        releaseGate.countDown()
    }

    private fun renderFeedback(
        httpClient: OkHttpClient = OkHttpClient(),
        onSubmitSuccess: () -> Unit = {},
        onBackClick: () -> Unit = {}
    ) {
        val viewModel = FeedbackViewModel(application) { httpClient }
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

    /** A client whose only request parks until [releaseGate] opens, then returns 200. */
    private fun gatedClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            releaseGate.await(30, TimeUnit.SECONDS)
            stubResponse(chain.request(), 200)
        }
        .build()

    /**
     * A client whose only request parks until [releaseGate] opens, then fails the way an offline
     * device does: no reply ever reaches the app.
     */
    private fun failingClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor {
            releaseGate.await(30, TimeUnit.SECONDS)
            throw UnknownHostException("stub: no network")
        }
        .build()

    /** A client that answers every request immediately with [code]. */
    private fun respondingClient(code: Int): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain -> stubResponse(chain.request(), code) }
        .build()

    private fun stubResponse(request: Request, code: Int): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("stub")
        .body("".toResponseBody(null))
        .build()

    private fun typeFeedback(text: String) {
        composeTestRule.onNodeWithText(string(R.string.feedback_hint)).performTextInput(text)
        composeTestRule.waitForIdle()
    }

    private fun submitButton() = composeTestRule.onNode(buttonWithText(string(R.string.feedback_submit)))

    /**
     * The submit button's spinner. Unmerged: the button merges its children, so the indicator's own
     * node is the one whose indeterminate range info is unambiguously its own.
     */
    private fun spinner() = composeTestRule.onNode(
        hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate),
        useUnmergedTree = true
    )

    private fun pressSystemBack() {
        composeTestRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    /** Opens [releaseGate] and waits until the submit button is usable again. */
    private fun finishSubmit() {
        releaseGate.countDown()
        val enabledSubmit = buttonWithText(string(R.string.feedback_submit)) and isEnabled()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodes(enabledSubmit).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
    }

    private fun counter(length: Int): String = composeTestRule.activity.getString(
        R.string.feedback_character_count_format,
        length,
        FeedbackViewModel.MAX_CHARACTERS
    )

    // ==================== Character counter ====================

    @Test
    fun feedbackScreen_characterCounter_updatesOnTyping() {
        renderFeedback()

        composeTestRule.onNodeWithText(counter(0)).assertIsDisplayed()

        typeFeedback("Hello World")

        composeTestRule.onNodeWithText(counter(11)).assertIsDisplayed()
    }

    @Test
    fun feedbackScreen_characterCounter_updatesOnClear() {
        renderFeedback()
        typeFeedback("Hello World")
        composeTestRule.onNodeWithText(counter(11)).assertIsDisplayed()

        composeTestRule.onNodeWithText("Hello World").performTextClearance()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(counter(0)).assertIsDisplayed()
    }

    @Test
    fun feedbackScreen_atCharacterLimit_showsMaxCount() {
        renderFeedback()
        typeFeedback("A".repeat(FeedbackViewModel.MAX_CHARACTERS))

        composeTestRule
            .onNodeWithText(counter(FeedbackViewModel.MAX_CHARACTERS))
            .assertIsDisplayed()
    }

    /**
     * `updateFeedbackText` drops any update that would exceed the limit, so the counter must not
     * move past the maximum no matter how much more is typed.
     */
    @Test
    fun feedbackScreen_atCharacterLimit_rejectsFurtherInput() {
        renderFeedback()
        val atLimit = "A".repeat(FeedbackViewModel.MAX_CHARACTERS)
        typeFeedback(atLimit)

        composeTestRule.onNodeWithText(atLimit).performTextInput("overflow")
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(counter(FeedbackViewModel.MAX_CHARACTERS))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(counter(FeedbackViewModel.MAX_CHARACTERS + "overflow".length))
            .assertDoesNotExist()
    }

    @Test
    fun feedbackScreen_belowCharacterLimit_acceptsInput() {
        renderFeedback()
        val nearLimit = "A".repeat(FeedbackViewModel.MAX_CHARACTERS - 5)
        typeFeedback(nearLimit)

        composeTestRule.onNodeWithText(nearLimit).performTextInput("BBBBB")
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(counter(FeedbackViewModel.MAX_CHARACTERS))
            .assertIsDisplayed()
    }

    // ==================== In-flight submit ====================

    @Test
    fun feedbackScreen_notSubmitting_buttonEnabled_whenHasContent() {
        renderFeedback()
        typeFeedback("Valid feedback")

        submitButton().assertIsEnabled()
    }

    @Test
    fun feedbackScreen_submitInProgress_disablesButton() {
        renderFeedback(gatedClient())
        typeFeedback("Valid feedback")

        submitButton().performClick()
        composeTestRule.waitForIdle()

        submitButton().assertIsNotEnabled()
    }

    @Test
    fun feedbackScreen_submitInProgress_disablesTextField() {
        renderFeedback(gatedClient())
        typeFeedback("Valid feedback")

        submitButton().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Valid feedback").assertIsNotEnabled()
    }

    /**
     * The counter is the only always-visible element inside the field's supporting text, so its
     * continued presence pins that the field itself survives the submitting state rather than being
     * swapped out.
     */
    @Test
    fun feedbackScreen_submitInProgress_keepsTypedTextVisible() {
        renderFeedback(gatedClient())
        typeFeedback("Valid feedback")

        submitButton().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Valid feedback").assertIsDisplayed()
        composeTestRule.onNodeWithText(counter("Valid feedback".length)).assertIsDisplayed()
    }

    /** Once the request finishes, the form must become usable again rather than staying locked. */
    @Test
    fun feedbackScreen_submitCompletes_reenablesForm() {
        renderFeedback(gatedClient())
        typeFeedback("Valid feedback")
        submitButton().performClick()
        composeTestRule.waitForIdle()
        submitButton().assertIsNotEnabled()

        releaseGate.countDown()

        val enabledSubmit = buttonWithText(string(R.string.feedback_submit)) and isEnabled()
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodes(enabledSubmit).fetchSemanticsNodes().isNotEmpty()
        }
        submitButton().assertIsEnabled()
        composeTestRule.onNodeWithText("Valid feedback").assertIsEnabled()
    }

    /**
     * The button shows a spinner while the request is open, and only then. The class's other
     * in-flight tests check the disabled controls, so a submit that lost its progress indicator —
     * or never removed it — passed.
     */
    @Test
    fun feedbackScreen_submitInProgress_showsSpinner() {
        renderFeedback(gatedClient())
        typeFeedback("Valid feedback")
        spinner().assertDoesNotExist()

        submitButton().performClick()
        composeTestRule.waitForIdle()
        spinner().assertIsDisplayed()

        finishSubmit()
        spinner().assertDoesNotExist()
    }

    // ==================== Submit outcome ====================

    /**
     * The feedback script can answer with an error status after it has already stored the message,
     * so any reply from the server must be reported as a successful submit.
     */
    @Test
    fun feedbackScreen_serverErrorStatus_reportsSuccess() {
        var submitSucceeded = false
        renderFeedback(respondingClient(500), onSubmitSuccess = { submitSucceeded = true })
        typeFeedback("Valid feedback")

        submitButton().performClick()

        composeTestRule.waitUntil(timeoutMillis = 10_000) { submitSucceeded }
    }

    /**
     * A request that never reaches the server is a failure: the screen stays, with the text the user
     * typed, for another try. Only a server reply was driven, so a catch that reported success —
     * closing the screen and losing the text — passed.
     */
    @Test
    fun feedbackScreen_networkFailure_keepsScreenAndText() {
        var submitSucceeded = false
        renderFeedback(failingClient(), onSubmitSuccess = { submitSucceeded = true })
        typeFeedback("Valid feedback")

        submitButton().performClick()
        composeTestRule.waitForIdle()
        submitButton().assertIsNotEnabled()

        finishSubmit()

        assertFalse("A failed submit must not report success", submitSucceeded)
        composeTestRule.onNodeWithText("Valid feedback").assertIsDisplayed()
    }

    /**
     * Pressing submit stops back from asking before it discards, and a failed submit must undo that:
     * the draft was never sent. A failure that left the flag set let back throw the text away
     * without the dialog.
     */
    @Test
    fun feedbackScreen_failedSubmit_backAsksBeforeDiscarding() {
        var closed = false
        renderFeedback(failingClient(), onBackClick = { closed = true })
        typeFeedback("Valid feedback")
        submitButton().performClick()
        composeTestRule.waitForIdle()
        finishSubmit()

        pressSystemBack()

        composeTestRule.onNodeWithText(string(R.string.feedback_discard_title)).assertIsDisplayed()
        assertFalse("Back after a failed submit must ask before closing", closed)
    }
}
