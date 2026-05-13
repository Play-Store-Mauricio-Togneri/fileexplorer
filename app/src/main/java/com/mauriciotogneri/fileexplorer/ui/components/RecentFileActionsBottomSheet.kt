package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.theme.MenuItemTextStyle

sealed class RecentFileAction {
    data object OpenWith : RecentFileAction()
    data object OpenFolder : RecentFileAction()
    data object Share : RecentFileAction()
    data object RemoveFromRecents : RecentFileAction()
    data object Delete : RecentFileAction()
    data object Info : RecentFileAction()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentFileActionsBottomSheet(
    onAction: (RecentFileAction) -> Unit,
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
            RecentFileActionItem(
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                text = stringResource(R.string.action_open_with),
                onClick = { onAction(RecentFileAction.OpenWith) }
            )

            RecentFileActionItem(
                icon = Icons.Outlined.Folder,
                text = stringResource(R.string.action_open_folder),
                onClick = { onAction(RecentFileAction.OpenFolder) }
            )

            RecentFileActionItem(
                icon = Icons.Outlined.Share,
                text = stringResource(R.string.action_share),
                onClick = { onAction(RecentFileAction.Share) }
            )

            RecentFileActionItem(
                icon = Icons.Outlined.History,
                text = stringResource(R.string.action_remove_from_recents),
                onClick = { onAction(RecentFileAction.RemoveFromRecents) }
            )

            RecentFileActionItem(
                icon = Icons.Outlined.Delete,
                text = stringResource(R.string.action_delete),
                onClick = { onAction(RecentFileAction.Delete) }
            )

            RecentFileActionItem(
                icon = Icons.Outlined.Info,
                text = stringResource(R.string.action_info),
                onClick = { onAction(RecentFileAction.Info) }
            )
        }
    }
}

@Composable
private fun RecentFileActionItem(
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
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
