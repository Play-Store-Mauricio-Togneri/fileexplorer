package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UncompressDialog(
    entryCount: Int,
    onDismiss: () -> Unit,
    onExtract: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_uncompress),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = pluralStringResource(R.plurals.uncompress_confirm, entryCount, entryCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        AnalyticsTracker.trackUncompressCancelled()
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                    TextButton(
                        onClick = {
                            AnalyticsTracker.trackUncompressConfirmed()
                            onExtract()
                        },
                        enabled = entryCount > 0,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onBackground
                        )
                    ) {
                        Text(stringResource(R.string.uncompress_extract))
                    }
                }
            }
        }
    }
}
