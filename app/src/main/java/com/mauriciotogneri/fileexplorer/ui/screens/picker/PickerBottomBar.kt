package com.mauriciotogneri.fileexplorer.ui.screens.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.OperationMode

@Composable
fun PickerBottomBar(
    mode: OperationMode?,
    isValidDestination: Boolean,
    validationError: String?,
    onNewFolder: () -> Unit,
    onConfirm: () -> Unit
) {
    // Choosing a folder to open on startup lists read-only folders too, where creating one would
    // fail; and the job is to point at a folder that already exists, not to build one.
    val canCreateFolder = mode != null

    val confirmLabel = when (mode) {
        OperationMode.MOVE -> stringResource(R.string.picker_confirm_move)
        OperationMode.COPY -> stringResource(R.string.picker_confirm_copy)
        null -> stringResource(R.string.picker_confirm_select)
    }
    val confirmIcon = when (mode) {
        OperationMode.MOVE -> Icons.AutoMirrored.Outlined.DriveFileMove
        OperationMode.COPY -> Icons.Outlined.ContentCopy
        null -> Icons.Outlined.Check
    }

    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (canCreateFolder) Arrangement.SpaceBetween else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canCreateFolder) {
                    OutlinedButton(onClick = onNewFolder) {
                        Icon(
                            imageVector = Icons.Outlined.CreateNewFolder,
                            contentDescription = stringResource(R.string.picker_new_folder)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.picker_new_folder))
                    }
                }

                Button(
                    onClick = onConfirm,
                    enabled = isValidDestination
                ) {
                    Icon(
                        imageVector = confirmIcon,
                        contentDescription = confirmLabel
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = confirmLabel)
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
