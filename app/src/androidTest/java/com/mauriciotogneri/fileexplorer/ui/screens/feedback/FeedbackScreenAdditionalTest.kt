package com.mauriciotogneri.fileexplorer.ui.screens.feedback

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Character-limit and in-flight-submit behavior of the real `FeedbackScreen`.
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

    private fun renderFeedback(httpClient: OkHttpClient = OkHttpClient()) {
        composeTestRule.setContent {
            FileExplorerTheme {
                FeedbackScreen(
                    onBackClick = {},
                    onSubmitSuccess = {},
                    viewModel = FeedbackViewModel(application, httpClient)
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    /** A client whose only request parks until [releaseGate] opens, then returns 200. */
    private fun gatedClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            releaseGate.await(30, TimeUnit.SECONDS)
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody(null))
                .build()
        }
        .build()

    private fun typeFeedback(text: String) {
        composeTestRule.onNodeWithText(string(R.string.feedback_hint)).performTextInput(text)
        composeTestRule.waitForIdle()
    }

    private fun submitButton() = composeTestRule.onNode(buttonWithText(string(R.string.feedback_submit)))

    private fun counter(length: Int) = "$length / ${FeedbackViewModel.MAX_CHARACTERS}"

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
}
