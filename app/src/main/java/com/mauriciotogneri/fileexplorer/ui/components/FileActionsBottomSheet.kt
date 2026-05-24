package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.FileExtensionUtil
import com.mauriciotogneri.fileexplorer.ui.theme.MenuItemTextStyle

sealed class FileAction {
    data object Select : FileAction()
    data object Share : FileAction()
    data object OpenWith : FileAction()
    data object Compress : FileAction()
    data object Uncompress : FileAction()
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

    val extension = remember(file) { if (file.isDirectory) "directory" else FileExtensionUtil.getExtension(file.path) }
    val mimeType = remember(file) { if (file.isDirectory) "inode/directory" else file.mimeType }
    val source = "folder"

    LaunchedEffect(Unit) {
        AnalyticsTracker.trackBottomSheetOpened(extension, mimeType, source)
    }

    ModalBottomSheet(
        onDismissRequest = {
            AnalyticsTracker.trackBottomSheetDismissed(extension, mimeType, source)
            onDismiss()
        },
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
                onClick = {
                    AnalyticsTracker.trackBottomSheetSelect(extension, mimeType, source)
                    onAction(FileAction.Select)
                }
            )

            if (!file.isDirectory) {
                FileActionItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share),
                    onClick = {
                        AnalyticsTracker.trackBottomSheetShare(extension, mimeType, source)
                        onAction(FileAction.Share)
                    }
                )

                FileActionItem(
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    text = stringResource(R.string.action_open_with),
                    onClick = {
                        AnalyticsTracker.trackBottomSheetOpenWith(extension, mimeType, source)
                        onAction(FileAction.OpenWith)
                    }
                )
            }

            FileActionItem(
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                text = stringResource(R.string.action_move_to),
                onClick = {
                    AnalyticsTracker.trackBottomSheetMoveTo(extension, mimeType, source)
                    onAction(FileAction.MoveTo)
                }
            )

            FileActionItem(
                icon = Icons.Outlined.ContentCopy,
                text = stringResource(R.string.action_copy_to),
                onClick = {
                    AnalyticsTracker.trackBottomSheetCopyTo(extension, mimeType, source)
                    onAction(FileAction.CopyTo)
                }
            )

            FileActionItem(
                icon = Icons.Outlined.Edit,
                text = stringResource(R.string.action_rename),
                onClick = {
                    AnalyticsTracker.trackBottomSheetRename(extension, mimeType, source)
                    onAction(FileAction.Rename)
                }
            )

            if (file.isZip) {
                FileActionItem(
                    icon = Icons.Outlined.FolderZip,
                    text = stringResource(R.string.action_uncompress),
                    onClick = {
                        AnalyticsTracker.trackBottomSheetUncompress(extension, mimeType, source)
                        onAction(FileAction.Uncompress)
                    }
                )
            } else {
                FileActionItem(
                    icon = Icons.Outlined.Compress,
                    text = stringResource(R.string.action_compress),
                    onClick = {
                        AnalyticsTracker.trackBottomSheetCompress(extension, mimeType, source)
                        onAction(FileAction.Compress)
                    }
                )
            }

            FileActionItem(
                icon = Icons.Outlined.Delete,
                text = stringResource(R.string.action_delete),
                onClick = {
                    AnalyticsTracker.trackBottomSheetDelete(extension, mimeType, source)
                    onAction(FileAction.Delete)
                }
            )

            FileActionItem(
                icon = Icons.Outlined.Info,
                text = stringResource(R.string.action_info),
                onClick = {
                    AnalyticsTracker.trackBottomSheetInfo(extension, mimeType, source)
                    onAction(FileAction.Info)
                }
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
    DropdownMenuItem(
        text = { Text(text = text, style = MenuItemTextStyle) },
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
