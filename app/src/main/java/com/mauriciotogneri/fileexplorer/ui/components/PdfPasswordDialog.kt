package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R

/**
 * Asks for the password of an encrypted PDF. [wrongPassword] shows that the last attempt failed.
 * The field starts empty on every showing — the viewer hides the dialog while it tries a password
 * and shows a fresh one if the password was wrong — so a rejected password is never offered again.
 * Cancelling, by button, back or tapping outside, is [onCancel]: the document cannot be shown
 * without it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPasswordDialog(
    wrongPassword: Boolean,
    onCancel: () -> Unit,
    onOpen: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val isValid = password.isNotEmpty()
    val submit = {
        if (isValid) {
            val submitted = password
            password = ""
            onOpen(submitted)
        }
    }

    BasicAlertDialog(onDismissRequest = onCancel) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.uncompress_password_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = stringResource(R.string.pdf_viewer_password_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.uncompress_password_hint)) },
                    singleLine = true,
                    isError = wrongPassword,
                    supportingText = if (wrongPassword) {
                        { Text(stringResource(R.string.uncompress_error_wrong_password)) }
                    } else {
                        null
                    },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = stringResource(
                                    if (passwordVisible) {
                                        R.string.content_description_hide_password
                                    } else {
                                        R.string.content_description_show_password
                                    }
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                    TextButton(
                        onClick = submit,
                        enabled = isValid,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onBackground
                        )
                    ) {
                        Text(stringResource(R.string.action_open))
                    }
                }
            }
        }
    }
}
