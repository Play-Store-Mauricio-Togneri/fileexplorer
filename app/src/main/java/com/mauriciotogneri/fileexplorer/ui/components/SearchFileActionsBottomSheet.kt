package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
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

sealed class SearchFileAction {
    data object OpenWith : SearchFileAction()
    data object OpenFolder : SearchFileAction()
    data object Share : SearchFileAction()
    data object AddToFavorites : SearchFileAction()
    data object RemoveFromFavorites : SearchFileAction()
    data object Delete : SearchFileAction()
    data object Info : SearchFileAction()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFileActionsBottomSheet(
    file: FileItem,
    mode: String,
    isFavorite: Boolean,
    onAction: (SearchFileAction) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val extension = remember(file) { if (file.isDirectory) "directory" else FileExtensionUtil.getExtension(file.path) }
    val mimeType = remember(file) { if (file.isDirectory) "inode/directory" else file.mimeType }
    val source = "search"

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
            if (!file.isDirectory) {
                SearchFileActionItem(
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    text = stringResource(R.string.action_open_with),
                    onClick = {
                        AnalyticsTracker.trackBottomSheetOpenWith(extension, mimeType, source)
                        onAction(SearchFileAction.OpenWith)
                    }
                )

                SearchFileActionItem(
                    icon = Icons.Outlined.Folder,
                    text = stringResource(R.string.action_open_folder),
                    onClick = {
                        AnalyticsTracker.trackBottomSheetOpenFolder(extension, mimeType, source)
                        onAction(SearchFileAction.OpenFolder)
                    }
                )

                SearchFileActionItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share),
                    onClick = {
                        AnalyticsTracker.trackBottomSheetShare(extension, mimeType, source)
                        onAction(SearchFileAction.Share)
                    }
                )
            }

            SearchFileActionItem(
                icon = if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                text = stringResource(
                    if (isFavorite) R.string.action_remove_from_favorites
                    else R.string.action_add_to_favorites
                ),
                onClick = {
                    if (isFavorite) {
                        AnalyticsTracker.trackBottomSheetRemoveFromFavorites(extension, mimeType, source)
                        onAction(SearchFileAction.RemoveFromFavorites)
                    } else {
                        AnalyticsTracker.trackBottomSheetAddToFavorites(extension, mimeType, source)
                        onAction(SearchFileAction.AddToFavorites)
                    }
                }
            )

            SearchFileActionItem(
                icon = Icons.Outlined.Delete,
                text = stringResource(R.string.action_delete),
                onClick = {
                    AnalyticsTracker.trackBottomSheetDelete(extension, mimeType, source)
                    onAction(SearchFileAction.Delete)
                }
            )

            SearchFileActionItem(
                icon = Icons.Outlined.Info,
                text = stringResource(R.string.action_info),
                onClick = {
                    AnalyticsTracker.trackBottomSheetInfo(extension, mimeType, source)
                    onAction(SearchFileAction.Info)
                }
            )
        }
    }
}

@Composable
private fun SearchFileActionItem(
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
