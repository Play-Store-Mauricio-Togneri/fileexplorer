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
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.data.util.FileExtensionUtil
import com.mauriciotogneri.fileexplorer.ui.theme.MenuItemTextStyle

sealed class FavoriteFileAction {
    data object OpenWith : FavoriteFileAction()
    data object Share : FavoriteFileAction()
    data object OpenFolder : FavoriteFileAction()
    data object RemoveFromFavorites : FavoriteFileAction()
    data object Delete : FavoriteFileAction()
    data object Info : FavoriteFileAction()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteFileActionsBottomSheet(
    favorite: Favorite,
    mode: String,
    onAction: (FavoriteFileAction) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val extension = remember(favorite) {
        if (favorite.isDirectory) "directory" else FileExtensionUtil.getExtension(favorite.path)
    }
    val mimeType = remember(favorite) {
        if (favorite.isDirectory) "inode/directory" else favorite.mimeType
    }
    val source = "favorite"

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
            if (!favorite.isDirectory) {
                FavoriteFileActionItem(
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    text = stringResource(R.string.action_open_with),
                    onClick = {
                        AnalyticsTracker.trackBottomSheetOpenWith(extension, mimeType, source)
                        onAction(FavoriteFileAction.OpenWith)
                    }
                )

                FavoriteFileActionItem(
                    icon = Icons.Outlined.Share,
                    text = stringResource(R.string.action_share),
                    onClick = {
                        AnalyticsTracker.trackBottomSheetShare(extension, mimeType, source)
                        onAction(FavoriteFileAction.Share)
                    }
                )
            }

            FavoriteFileActionItem(
                icon = Icons.Outlined.Folder,
                text = stringResource(R.string.action_open_folder),
                onClick = {
                    AnalyticsTracker.trackBottomSheetOpenFolder(extension, mimeType, source)
                    onAction(FavoriteFileAction.OpenFolder)
                }
            )

            FavoriteFileActionItem(
                icon = Icons.Outlined.Star,
                text = stringResource(R.string.action_remove_from_favorites),
                onClick = {
                    AnalyticsTracker.trackBottomSheetRemoveFromFavorites(extension, mimeType, source)
                    onAction(FavoriteFileAction.RemoveFromFavorites)
                }
            )

            FavoriteFileActionItem(
                icon = Icons.Outlined.Delete,
                text = stringResource(R.string.action_delete),
                onClick = {
                    AnalyticsTracker.trackBottomSheetDelete(extension, mimeType, source)
                    onAction(FavoriteFileAction.Delete)
                }
            )

            FavoriteFileActionItem(
                icon = Icons.Outlined.Info,
                text = stringResource(R.string.action_info),
                onClick = {
                    AnalyticsTracker.trackBottomSheetInfo(extension, mimeType, source)
                    onAction(FavoriteFileAction.Info)
                }
            )
        }
    }
}

@Composable
private fun FavoriteFileActionItem(
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
