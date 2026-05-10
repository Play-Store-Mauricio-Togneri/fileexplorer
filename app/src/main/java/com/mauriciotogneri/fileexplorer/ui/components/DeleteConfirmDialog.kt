package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.mauriciotogneri.fileexplorer.R

@Composable
fun DeleteConfirmDialog(
    itemCount: Int,
    itemName: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.action_delete))
        },
        text = {
            if (itemCount == 1 && itemName != null) {
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(R.string.delete_confirm_single_prefix))
                        append(" ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(itemName)
                        }
                        append(stringResource(R.string.delete_confirm_single_suffix))
                    }
                )
            } else {
                Text(
                    text = pluralStringResource(
                        R.plurals.delete_confirm_multiple,
                        itemCount,
                        itemCount
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.dialog_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
