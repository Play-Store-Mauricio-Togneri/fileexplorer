package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
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

sealed class AnalyzerFileAction {
    data object OpenWith : AnalyzerFileAction()
    data object OpenFolder : AnalyzerFileAction()
    data object Delete : AnalyzerFileAction()
    data object Info : AnalyzerFileAction()
}

/**
 * What the storage analyzer's category listing offers for one of its rows.
 *
 * Shorter than the folder screen's sheet by design: the listing exists to show what is taking up
 * room, so it carries the four actions that answer "what is this, and do I still want it" and
 * leaves managing the file to the folder it sits in — which [AnalyzerFileAction.OpenFolder] is the
 * way to.
 *
 * Every row is a file. The scan classifies files into categories and never lists a directory, so
 * unlike the sheets on screens that mix the two, nothing here is conditional on what was tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzerFileActionsBottomSheet(
    file: FileItem,
    mode: String,
    onAction: (AnalyzerFileAction) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Only the extension is worth remembering; the sibling sheets wrap the MIME type too because
    // they pick it against file.isDirectory, and no row here is a directory.
    val extension = remember(file) { FileExtensionUtil.getExtension(file.path) }
    val mimeType = file.mimeType
    val source = "analyzer_category"

    LaunchedEffect(Unit) {
        AnalyticsTracker.trackBottomSheetOpened(extension, mimeType, source, mode)
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
            AnalyzerFileActionItem(
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                text = stringResource(R.string.action_open_with),
                onClick = {
                    AnalyticsTracker.trackBottomSheetOpenWith(extension, mimeType, source)
                    onAction(AnalyzerFileAction.OpenWith)
                }
            )

            AnalyzerFileActionItem(
                icon = Icons.Outlined.Folder,
                text = stringResource(R.string.action_open_folder),
                onClick = {
                    AnalyticsTracker.trackBottomSheetOpenFolder(extension, mimeType, source)
                    onAction(AnalyzerFileAction.OpenFolder)
                }
            )

            AnalyzerFileActionItem(
                icon = Icons.Outlined.Delete,
                text = stringResource(R.string.action_delete),
                onClick = {
                    AnalyticsTracker.trackBottomSheetDelete(extension, mimeType, source)
                    onAction(AnalyzerFileAction.Delete)
                }
            )

            AnalyzerFileActionItem(
                icon = Icons.Outlined.Info,
                text = stringResource(R.string.action_info),
                onClick = {
                    AnalyticsTracker.trackBottomSheetInfo(extension, mimeType, source)
                    onAction(AnalyzerFileAction.Info)
                }
            )
        }
    }
}

@Composable
private fun AnalyzerFileActionItem(
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
