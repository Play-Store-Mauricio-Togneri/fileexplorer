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
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.util.AppImageLoader
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    file: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    showMenu: Boolean = true
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
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
            SelectableFileIcon(file = file, isSelected = isSelected)

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
                    text = if (file.isDirectory) {
                        pluralStringResource(
                            R.plurals.item_amount,
                            file.childCount ?: 0,
                            file.childCount ?: 0
                        )
                    } else {
                        file.formattedSize
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showMenu) {
                Box(modifier = Modifier.size(48.dp)) {
                    if (!isSelected) {
                        IconButton(onClick = onMenuClick) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = null,
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
    modifier: Modifier = Modifier
) {
    val iconSize = 40.dp

    if (isSelected) {
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
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    } else {
        FileIcon(file = file, modifier = modifier)
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
                contentDescription = null,
                modifier = modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        file.hasThumbnailSupport -> {
            val fallbackIcon = rememberVectorPainter(getFileIcon(file))
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(file.path))
                    .size(120)
                    .crossfade(true)
                    .build(),
                imageLoader = AppImageLoader.get(context),
                contentDescription = null,
                modifier = modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
                error = fallbackIcon
            )
        }

        else -> {
            Icon(
                imageVector = getFileIcon(file),
                contentDescription = null,
                modifier = modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getFileIcon(file: FileItem): ImageVector {
    return when {
        file.isImage || file.isSvg -> Icons.Outlined.Image
        file.isPdf -> Icons.Outlined.PictureAsPdf
        file.isAudio -> Icons.Outlined.AudioFile
        file.isVideo -> Icons.Outlined.VideoFile
        file.isApk -> Icons.Outlined.Android
        file.isEpub -> Icons.Outlined.Book
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

