package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
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
import com.mauriciotogneri.fileexplorer.ui.theme.MenuItemTextStyle

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
        sheetState = sheetState,
        dragHandle = { FullWidthDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            FileActionItem(
                icon = Icons.Outlined.CheckBox,
                text = stringResource(R.string.action_select),
                onClick = { onAction(FileAction.Select) }
            )

            if (!file.isDirectory) {
                FileActionItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share),
                    onClick = { onAction(FileAction.Share) }
                )

                FileActionItem(
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    text = stringResource(R.string.action_open_with),
                    onClick = { onAction(FileAction.OpenWith) }
                )
            }

            FileActionItem(
                icon = Icons.Outlined.Compress,
                text = stringResource(R.string.action_compress),
                onClick = { onAction(FileAction.Compress) }
            )

            FileActionItem(
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                text = stringResource(R.string.action_move_to),
                onClick = { onAction(FileAction.MoveTo) }
            )

            FileActionItem(
                icon = Icons.Outlined.ContentCopy,
                text = stringResource(R.string.action_copy_to),
                onClick = { onAction(FileAction.CopyTo) }
            )

            FileActionItem(
                icon = Icons.Outlined.Edit,
                text = stringResource(R.string.action_rename),
                onClick = { onAction(FileAction.Rename) }
            )

            FileActionItem(
                icon = Icons.Outlined.Delete,
                text = stringResource(R.string.action_delete),
                onClick = { onAction(FileAction.Delete) }
            )

            FileActionItem(
                icon = Icons.Outlined.Info,
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
        text = { Text(text = text, style = MenuItemTextStyle) },
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
