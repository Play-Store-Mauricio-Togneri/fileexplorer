package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.mauriciotogneri.fileexplorer.data.model.Clipboard
import com.mauriciotogneri.fileexplorer.data.model.FileAction
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderUiState

@Composable
fun ActionBar(
    state: FolderUiState,
    clipboard: Clipboard,
    onAction: (FileAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasSelection = state.isSelectionMode

    // Only show the action bar when files are selected
    if (!hasSelection) return

    val allSelected = state.allSelected
    val singleSelected = state.selectedCount == 1
    val canPaste = clipboard.canPasteInto(state.currentPath)
    val hasFiles = state.selectedPaths.any { path ->
        state.files.find { it.path == path }?.isDirectory == false
    }

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
            ActionButton(
                icon = Icons.Default.ContentCut,
                label = stringResource(R.string.action_cut),
                onClick = { onAction(FileAction.Cut) }
            )
            ActionButton(
                icon = Icons.Default.ContentCopy,
                label = stringResource(R.string.action_copy),
                onClick = { onAction(FileAction.Copy) }
            )
            if (canPaste) {
                ActionButton(
                    icon = Icons.Default.ContentPaste,
                    label = stringResource(R.string.action_paste),
                    onClick = { onAction(FileAction.Paste) }
                )
            }
            if (!allSelected) {
                ActionButton(
                    icon = Icons.Default.SelectAll,
                    label = stringResource(R.string.action_select_all),
                    onClick = { onAction(FileAction.SelectAll) }
                )
            }
            if (singleSelected) {
                ActionButton(
                    icon = Icons.Default.Edit,
                    label = stringResource(R.string.action_rename),
                    onClick = { onAction(FileAction.Rename) }
                )
            }
            if (hasFiles) {
                ActionButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    label = stringResource(R.string.action_share),
                    onClick = { onAction(FileAction.Share) }
                )
            }
            ActionButton(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.action_delete),
                onClick = { onAction(FileAction.Delete) }
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
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
