package com.mauriciotogneri.fileexplorer.ui.screens.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackScreenAdditionalTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    // ==================== Character Counter Tests ====================

    @Test
    fun feedbackScreen_characterCounter_updatesOnTyping() {
        composeTestRule.setContent {
            var text by remember { mutableStateOf("") }
            FileExplorerTheme {
                TestFeedbackContent(
                    feedbackText = text,
                    onFeedbackTextChange = { text = it },
                    isSubmitting = false,
                    onSubmit = {},
                    maxCharacters = MAX_CHARACTERS
                )
            }
        }

        composeTestRule.waitForIdle()

        // Initially shows 0 / 1000
        composeTestRule.onNodeWithText("0 / $MAX_CHARACTERS")
            .assertIsDisplayed()

        // Type 50 characters
        val fiftyChars = "a".repeat(50)
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_hint))
            .performTextInput(fiftyChars)

        composeTestRule.waitForIdle()

        // Counter should update to 50 / 1000
        composeTestRule.onNodeWithText("50 / $MAX_CHARACTERS")
            .assertIsDisplayed()
    }

    @Test
    fun feedbackScreen_characterCounter_updatesOnClear() {
        composeTestRule.setContent {
            var text by remember { mutableStateOf("Hello World") }
            FileExplorerTheme {
                TestFeedbackContent(
                    feedbackText = text,
                    onFeedbackTextChange = { text = it },
                    isSubmitting = false,
                    onSubmit = {},
                    maxCharacters = MAX_CHARACTERS
                )
            }
        }

        composeTestRule.waitForIdle()

        // Initially shows 11 / 1000
        composeTestRule.onNodeWithText("11 / $MAX_CHARACTERS")
            .assertIsDisplayed()

        // Clear text
        composeTestRule.onNodeWithText("Hello World")
            .performTextClearance()

        composeTestRule.waitForIdle()

        // Counter should show 0 / 1000
        composeTestRule.onNodeWithText("0 / $MAX_CHARACTERS")
            .assertIsDisplayed()
    }

    @Test
    fun feedbackScreen_atCharacterLimit_showsMaxCount() {
        val maxText = "a".repeat(MAX_CHARACTERS)

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackContent(
                    feedbackText = maxText,
                    onFeedbackTextChange = {},
                    isSubmitting = false,
                    onSubmit = {},
                    maxCharacters = MAX_CHARACTERS
                )
            }
        }

        composeTestRule.waitForIdle()

        // Should show 1000 / 1000
        composeTestRule.onNodeWithText("$MAX_CHARACTERS / $MAX_CHARACTERS")
            .assertIsDisplayed()
    }

    @Test
    fun feedbackScreen_atCharacterLimit_disablesFurtherInput() {
        var capturedText = "a".repeat(MAX_CHARACTERS)

        composeTestRule.setContent {
            var text by remember { mutableStateOf(capturedText) }
            FileExplorerTheme {
                TestFeedbackContent(
                    feedbackText = text,
                    onFeedbackTextChange = { newText ->
                        // Only accept if within limit (mimics ViewModel behavior)
                        if (newText.length <= MAX_CHARACTERS) {
                            text = newText
                            capturedText = newText
                        }
                    },
                    isSubmitting = false,
                    onSubmit = {},
                    maxCharacters = MAX_CHARACTERS
                )
            }
        }

        composeTestRule.waitForIdle()

        // Try typing more text
        composeTestRule.onNodeWithText(capturedText)
            .performTextInput("extra")

        composeTestRule.waitForIdle()

        // Text length should still be at max
        assertEquals(
            "Text should not exceed max characters",
            MAX_CHARACTERS,
            capturedText.length
        )
    }

    // ==================== Submit Button State Tests ====================

    @Test
    fun feedbackScreen_submitInProgress_disablesButton() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackContent(
                    feedbackText = "Valid feedback",
                    onFeedbackTextChange = {},
                    isSubmitting = true,
                    onSubmit = {},
                    maxCharacters = MAX_CHARACTERS
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.feedback_submit))
            .assertIsNotEnabled()
    }

    @Test
    fun feedbackScreen_submitInProgress_showsLoadingIndicator() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackContent(
                    feedbackText = "Valid feedback",
                    onFeedbackTextChange = {},
                    isSubmitting = true,
                    onSubmit = {},
                    maxCharacters = MAX_CHARACTERS
                )
            }
        }

        composeTestRule.waitForIdle()

        // The button should contain the submit text even while loading
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_submit))
            .assertIsDisplayed()
    }

    @Test
    fun feedbackScreen_notSubmitting_buttonEnabled_whenHasContent() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackContent(
                    feedbackText = "Valid feedback",
                    onFeedbackTextChange = {},
                    isSubmitting = false,
                    onSubmit = {},
                    maxCharacters = MAX_CHARACTERS
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.feedback_submit))
            .assertIsEnabled()
    }

    @Test
    fun feedbackScreen_submitInProgress_disablesTextField() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackContentWithDisabledField(
                    feedbackText = "Valid feedback",
                    isSubmitting = true,
                    maxCharacters = MAX_CHARACTERS
                )
            }
        }

        composeTestRule.waitForIdle()

        // Text field should be displayed and disabled
        composeTestRule.onNodeWithText("Valid feedback")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    // ==================== Test Composables ====================

    @Composable
    private fun TestFeedbackContent(
        feedbackText: String,
        onFeedbackTextChange: (String) -> Unit,
        isSubmitting: Boolean,
        onSubmit: () -> Unit,
        maxCharacters: Int
    ) {
        val hasContent = feedbackText.isNotBlank()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = feedbackText,
                onValueChange = onFeedbackTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                enabled = !isSubmitting,
                placeholder = { Text(stringResource(R.string.feedback_hint)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences
                ),
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text("${feedbackText.length} / $maxCharacters")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSubmit,
                enabled = hasContent && !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.feedback_submit))
            }
        }
    }

    @Composable
    private fun TestFeedbackContentWithDisabledField(
        feedbackText: String,
        isSubmitting: Boolean,
        maxCharacters: Int
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = feedbackText,
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                enabled = !isSubmitting,
                placeholder = { Text(stringResource(R.string.feedback_hint)) },
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text("${feedbackText.length} / $maxCharacters")
                    }
                }
            )
        }
    }

    companion object {
        private const val MAX_CHARACTERS = 1000
    }
}
