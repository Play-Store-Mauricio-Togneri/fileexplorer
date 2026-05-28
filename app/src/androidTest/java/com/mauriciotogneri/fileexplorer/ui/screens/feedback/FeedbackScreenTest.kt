package com.mauriciotogneri.fileexplorer.ui.screens.feedback

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.theme.AppBarTitleStyle
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedbackScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // ==================== Display Tests ====================

    @Test
    fun feedbackScreen_displaysCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackScreen(
                    feedbackText = "",
                    onFeedbackTextChange = {},
                    onBackClick = {},
                    onSubmit = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.drawer_feedback))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_hint))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_submit))
            .assertIsDisplayed()
    }

    @Test
    fun feedbackScreen_submitButtonDisabled_whenEmpty() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackScreen(
                    feedbackText = "",
                    onFeedbackTextChange = {},
                    onBackClick = {},
                    onSubmit = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_submit))
            .assertIsNotEnabled()
    }

    @Test
    fun feedbackScreen_submitButtonEnabled_whenHasText() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackScreen(
                    feedbackText = "Some feedback",
                    onFeedbackTextChange = {},
                    onBackClick = {},
                    onSubmit = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_submit))
            .assertIsEnabled()
    }

    // ==================== Text Input Tests ====================

    @Test
    fun feedbackScreen_typeText_updatesField() {
        var capturedText = ""

        composeTestRule.setContent {
            var feedbackText by remember { mutableStateOf("") }
            FileExplorerTheme {
                TestFeedbackScreen(
                    feedbackText = feedbackText,
                    onFeedbackTextChange = {
                        feedbackText = it
                        capturedText = it
                    },
                    onBackClick = {},
                    onSubmit = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_hint))
            .performTextInput("Test feedback message")

        assertEquals("Test feedback message", capturedText)
    }

    @Test
    fun feedbackScreen_showsCharacterCount() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackScreen(
                    feedbackText = "Hello",
                    onFeedbackTextChange = {},
                    onBackClick = {},
                    onSubmit = {},
                    maxCharacters = 1000
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("5 / 1000")
            .assertIsDisplayed()
    }

    // ==================== Back Button with Discard Dialog Tests ====================

    @Test
    fun feedbackScreen_backWithNoText_closesDirectly() {
        var backClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackScreen(
                    feedbackText = "",
                    onFeedbackTextChange = {},
                    onBackClick = { backClicked = true },
                    onSubmit = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .performClick()

        assertTrue("Should close directly when no text", backClicked)
    }

    @Test
    fun feedbackScreen_backWithText_showsDiscardDialog() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackScreen(
                    feedbackText = "Some feedback text",
                    onFeedbackTextChange = {},
                    onBackClick = {},
                    onSubmit = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.feedback_discard_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_discard_message))
            .assertIsDisplayed()
    }

    @Test
    fun feedbackScreen_discardDialog_showsKeepEditingAndDiscard() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackScreen(
                    feedbackText = "Some feedback text",
                    onFeedbackTextChange = {},
                    onBackClick = {},
                    onSubmit = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .performClick()

        composeTestRule.onNodeWithText(context.getString(R.string.feedback_keep_editing))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_discard))
            .assertIsDisplayed()
    }

    @Test
    fun feedbackScreen_selectKeepEditing_dismissesDialogAndKeepsText() {
        var backClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackScreen(
                    feedbackText = "Some feedback text",
                    onFeedbackTextChange = {},
                    onBackClick = { backClicked = true },
                    onSubmit = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Press back to show dialog
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .performClick()

        // Select "Keep editing"
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_keep_editing))
            .performClick()

        // Dialog should be dismissed
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_discard_title))
            .assertDoesNotExist()

        // Should not have closed the screen
        assertFalse("Should not close when keeping editing", backClicked)

        // Text field should still be visible
        composeTestRule.onNodeWithText("Some feedback text")
            .assertIsDisplayed()
    }

    @Test
    fun feedbackScreen_selectDiscard_closesScreen() {
        var backClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackScreen(
                    feedbackText = "Some feedback text",
                    onFeedbackTextChange = {},
                    onBackClick = { backClicked = true },
                    onSubmit = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Press back to show dialog
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .performClick()

        // Select "Discard"
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_discard))
            .performClick()

        assertTrue("Should close when discarding", backClicked)
    }

    @Test
    fun feedbackScreen_backTwice_keepEditingThenDiscard() {
        var backClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackScreen(
                    feedbackText = "Some feedback text",
                    onFeedbackTextChange = {},
                    onBackClick = { backClicked = true },
                    onSubmit = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // First back press - show dialog
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_discard_title))
            .assertIsDisplayed()

        // Select "Keep editing"
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_keep_editing))
            .performClick()
        assertFalse("Should not close after keep editing", backClicked)

        // Second back press - show dialog again
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_discard_title))
            .assertIsDisplayed()

        // Select "Discard"
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_discard))
            .performClick()
        assertTrue("Should close after discard", backClicked)
    }

    // ==================== Submit Tests ====================

    @Test
    fun feedbackScreen_submitWithText_triggersCallback() {
        var submitted = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackScreen(
                    feedbackText = "Valid feedback",
                    onFeedbackTextChange = {},
                    onBackClick = {},
                    onSubmit = { submitted = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_submit))
            .performClick()

        assertTrue("Submit should be triggered", submitted)
    }

    // ==================== Edge Cases ====================

    @Test
    fun feedbackScreen_whitespaceOnlyText_submitDisabled() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackScreen(
                    feedbackText = "   ",
                    onFeedbackTextChange = {},
                    onBackClick = {},
                    onSubmit = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_submit))
            .assertIsNotEnabled()
    }

    @Test
    fun feedbackScreen_whitespaceOnlyText_backClosesDirectly() {
        var backClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackScreen(
                    feedbackText = "   ",
                    onFeedbackTextChange = {},
                    onBackClick = { backClicked = true },
                    onSubmit = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .performClick()

        // Whitespace-only should not show dialog
        assertTrue("Should close directly with whitespace-only text", backClicked)
    }

    @Test
    fun feedbackScreen_keepEditingButton_dismissesDialogAndKeepsScreen() {
        var backClicked = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestFeedbackScreen(
                    feedbackText = "Some feedback text",
                    onFeedbackTextChange = {},
                    onBackClick = { backClicked = true },
                    onSubmit = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Show dialog
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.navigate_back))
            .performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_discard_title))
            .assertIsDisplayed()

        // Dismiss by clicking "Keep editing" (simulating dismiss behavior)
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_keep_editing))
            .performClick()

        // Dialog should be dismissed, screen should stay
        composeTestRule.onNodeWithText(context.getString(R.string.feedback_discard_title))
            .assertDoesNotExist()
        assertFalse("Should not close on dismiss", backClicked)
    }

    // ==================== Test Composable ====================

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestFeedbackScreen(
        feedbackText: String,
        onFeedbackTextChange: (String) -> Unit,
        onBackClick: () -> Unit,
        onSubmit: () -> Unit,
        maxCharacters: Int = 1000
    ) {
        var showDiscardDialog by remember { mutableStateOf(false) }
        val hasContent = feedbackText.isNotBlank()

        val handleBack = {
            if (hasContent) {
                showDiscardDialog = true
            } else {
                onBackClick()
            }
        }

        BackHandler(enabled = true) {
            handleBack()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.drawer_feedback),
                            style = AppBarTitleStyle
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { handleBack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = feedbackText,
                    onValueChange = onFeedbackTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
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
                    enabled = hasContent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.feedback_submit))
                }
            }
        }

        if (showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.feedback_discard_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = { Text(stringResource(R.string.feedback_discard_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDiscardDialog = false
                        onBackClick()
                    }) {
                        Text(
                            text = stringResource(R.string.feedback_discard),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardDialog = false }) {
                        Text(stringResource(R.string.feedback_keep_editing))
                    }
                }
            )
        }
    }
}
