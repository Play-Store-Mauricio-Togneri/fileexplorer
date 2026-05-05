package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun FileInfoDialog(
    file: FileItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Thumbnail for image files
                if (!file.isDirectory && file.isImage) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(file.path))
                            .size(400)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    // Icon for non-image files and folders
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = when {
                                file.isDirectory -> Icons.Default.Folder
                                file.isPdf -> Icons.Default.PictureAsPdf
                                file.isAudio -> Icons.Default.AudioFile
                                file.isVideo -> Icons.Default.VideoFile
                                else -> Icons.AutoMirrored.Filled.InsertDriveFile
                            },
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = if (file.isDirectory) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Name
                InfoRow(
                    label = stringResource(R.string.info_name),
                    value = file.name
                )

                // Location
                InfoRow(
                    label = stringResource(R.string.info_location),
                    value = file.parentPath
                )

                // Created
                InfoRow(
                    label = stringResource(R.string.info_created),
                    value = formatDate(file.createdTime)
                )

                // Modified
                InfoRow(
                    label = stringResource(R.string.info_modified),
                    value = formatDate(file.lastModified)
                )

                // Size (files only)
                if (!file.isDirectory) {
                    InfoRow(
                        label = stringResource(R.string.info_size),
                        value = file.formattedSize
                    )
                }

                // Items count (folders only)
                if (file.isDirectory && file.childCount != null) {
                    InfoRow(
                        label = stringResource(R.string.info_size),
                        value = pluralStringResource(
                            R.plurals.item_amount,
                            file.childCount,
                            file.childCount
                        )
                    )
                }

                // Mime type (files only)
                if (!file.isDirectory && file.mimeType.isNotBlank()) {
                    InfoRow(
                        label = stringResource(R.string.info_type),
                        value = file.mimeType
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.info_close))
            }
        }
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "-"
    val date = Date(timestamp)
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return dateFormat.format(date)
}
