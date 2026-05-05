package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem

sealed class FileAction {
    data object Select : FileAction()
    data object Share : FileAction()
    data object OpenWith : FileAction()
    data object Compress : FileAction()
    data object MoveTo : FileAction()
    data object CopyTo : FileAction()
    data object Rename : FileAction()
    data object Delete : FileAction()
    data object Info : FileAction()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionsBottomSheet(
    file: FileItem,
    onAction: (FileAction) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            FileActionItem(
                icon = Icons.Default.CheckBox,
                text = stringResource(R.string.action_select),
                onClick = { onAction(FileAction.Select) }
            )

            if (!file.isDirectory) {
                FileActionItem(
                    icon = Icons.Default.Share,
                    text = stringResource(R.string.action_share),
                    onClick = { onAction(FileAction.Share) }
                )

                FileActionItem(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    text = stringResource(R.string.action_open_with),
                    onClick = { onAction(FileAction.OpenWith) }
                )
            }

            FileActionItem(
                icon = Icons.Default.Compress,
                text = stringResource(R.string.action_compress),
                onClick = { onAction(FileAction.Compress) }
            )

            FileActionItem(
                icon = Icons.AutoMirrored.Filled.DriveFileMove,
                text = stringResource(R.string.action_move_to),
                onClick = { onAction(FileAction.MoveTo) }
            )

            FileActionItem(
                icon = Icons.Default.ContentCopy,
                text = stringResource(R.string.action_copy_to),
                onClick = { onAction(FileAction.CopyTo) }
            )

            FileActionItem(
                icon = Icons.Default.Edit,
                text = stringResource(R.string.action_rename),
                onClick = { onAction(FileAction.Rename) }
            )

            FileActionItem(
                icon = Icons.Default.Delete,
                text = stringResource(R.string.action_delete),
                onClick = { onAction(FileAction.Delete) }
            )

            FileActionItem(
                icon = Icons.Default.Info,
                text = stringResource(R.string.action_info),
                onClick = { onAction(FileAction.Info) }
            )
        }
    }
}

@Composable
private fun FileActionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(text = text) },
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
