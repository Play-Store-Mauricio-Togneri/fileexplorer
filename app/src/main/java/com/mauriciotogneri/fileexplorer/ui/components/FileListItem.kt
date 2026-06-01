package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.ui.theme.extendedColorScheme
import com.mauriciotogneri.fileexplorer.data.util.AppImageLoader
import com.mauriciotogneri.fileexplorer.ui.util.getFileIcon
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    isSelected: Boolean,
    isRestricted: Boolean = false,
    modifier: Modifier = Modifier,
    showMenu: Boolean = true
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.extendedColorScheme.selectionBackground
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectableFileIcon(file = file, isSelected = isSelected, isRestricted = isRestricted)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = when {
                        !file.isDirectory -> file.formattedSize
                        file.childCount != null -> pluralStringResource(
                            R.plurals.item_amount,
                            file.childCount,
                            file.childCount
                        )
                        // Reached only when childCount is null; a known count takes precedence.
                        isRestricted -> stringResource(R.string.folder_restricted)
                        // Count not yet loaded: keep the line blank but reserve its height
                        // via minLines so the row doesn't shift when the count arrives.
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 1
                )
            }

            if (showMenu) {
                Box(modifier = Modifier.size(48.dp)) {
                    if (!isSelected) {
                        IconButton(onClick = onMenuClick) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.content_description_more_options),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectableFileIcon(
    file: FileItem,
    isSelected: Boolean,
    isRestricted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val iconSize = 40.dp

    when {
        isSelected -> {
            Box(
                modifier = modifier
                    .size(iconSize)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.content_description_selected),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        isRestricted -> {
            // Folder icon with a small lock badge in the bottom-end corner. The badge
            // sits on a surface-colored circle so it reads clearly over the folder, and is
            // decorative (the "Restricted" subtitle already announces the state).
            Box(modifier = modifier.size(iconSize)) {
                FileIcon(file = file)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        else -> {
            FileIcon(file = file, modifier = modifier)
        }
    }
}

@Composable
private fun FileIcon(
    file: FileItem,
    modifier: Modifier = Modifier
) {
    val iconSize = 40.dp
    val context = LocalContext.current

    when {
        file.isDirectory -> {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = stringResource(R.string.content_description_folder),
                modifier = modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        file.hasThumbnailSupport -> {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(file.path))
                    .size(120)
                    .crossfade(true)
                    .build(),
                imageLoader = AppImageLoader.get(context),
                contentDescription = file.name,
                modifier = modifier.size(iconSize),
                success = {
                    SubcomposeAsyncImageContent(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                },
                error = {
                    Icon(
                        imageVector = getFileIcon(file),
                        contentDescription = file.name,
                        modifier = Modifier.size(iconSize),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
        }

        else -> {
            Icon(
                imageVector = getFileIcon(file),
                contentDescription = file.name,
                modifier = modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

