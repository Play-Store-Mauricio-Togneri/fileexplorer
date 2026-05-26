package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileAction
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderUiState

@Composable
fun ActionBar(
    state: FolderUiState,
    onAction: (FileAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasSelection = state.isSelectionMode

    // Only show the action bar when files are selected
    if (!hasSelection) return

    val singleSelected = state.selectedCount == 1
    val singleSelectedFile = state.singleSelectedFile
    val singleSelectedIsZip = singleSelectedFile?.isZip == true
    val allFilesSelected = state.allSelectedAreFiles

    BottomAppBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (allFilesSelected) {
                ActionButton(
                    icon = Icons.Outlined.Share,
                    label = stringResource(R.string.action_share),
                    onClick = {
                        AnalyticsTracker.trackFolderBottomBarShare()
                        onAction(FileAction.Share)
                    }
                )
            }
            ActionButton(
                icon = Icons.AutoMirrored.Outlined.DriveFileMove,
                label = stringResource(R.string.action_move_to),
                onClick = {
                    AnalyticsTracker.trackFolderBottomBarMoveTo()
                    onAction(FileAction.MoveTo)
                }
            )
            ActionButton(
                icon = Icons.Outlined.ContentCopy,
                label = stringResource(R.string.action_copy_to),
                onClick = {
                    AnalyticsTracker.trackFolderBottomBarCopyTo()
                    onAction(FileAction.CopyTo)
                }
            )
            if (singleSelected) {
                ActionButton(
                    icon = Icons.Outlined.Edit,
                    label = stringResource(R.string.action_rename),
                    onClick = {
                        AnalyticsTracker.trackFolderBottomBarRename()
                        onAction(FileAction.Rename)
                    }
                )
            }
            if (singleSelectedIsZip) {
                ActionButton(
                    icon = Icons.Outlined.FolderZip,
                    label = stringResource(R.string.action_uncompress),
                    onClick = {
                        AnalyticsTracker.trackFolderBottomBarUncompress()
                        onAction(FileAction.Uncompress)
                    }
                )
            } else {
                ActionButton(
                    icon = Icons.Outlined.Compress,
                    label = stringResource(R.string.action_compress),
                    onClick = {
                        AnalyticsTracker.trackFolderBottomBarCompress()
                        onAction(FileAction.Compress)
                    }
                )
            }
            ActionButton(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.action_delete),
                onClick = {
                    AnalyticsTracker.trackFolderBottomBarDelete()
                    onAction(FileAction.Delete)
                }
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
