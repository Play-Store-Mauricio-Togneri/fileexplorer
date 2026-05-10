package com.mauriciotogneri.fileexplorer.ui.screens.iteminfo

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
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
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun ItemInfoScreen(
    viewModel: ItemInfoViewModel,
    onCloseClick: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val openUnableMessage = stringResource(R.string.open_unable)

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ItemInfoUiEvent.OpenFile -> {
                    val opened = IntentUtil.openFile(context, event.file)
                    if (!opened) {
                        Toast.makeText(context, openUnableMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.error -> {
                    Text(
                        text = stringResource(R.string.info_error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    state.file?.let { file ->
                        ItemInfoContent(
                            file = file,
                            onOpenFile = { viewModel.onOpenFile() }
                        )
                    }
                }
            }

            IconButton(
                onClick = onCloseClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.info_close),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ItemInfoContent(
    file: FileItem,
    onOpenFile: () -> Unit
) {
    val openLabel = stringResource(R.string.action_open)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(top = 48.dp)
    ) {
        if (!file.isDirectory && file.isImage) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(file.path))
                    .size(400)
                    .crossfade(true)
                    .build(),
                contentDescription = openLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = openLabel, onClick = onOpenFile),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = !file.isDirectory,
                        onClickLabel = openLabel,
                        onClick = onOpenFile
                    ),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when {
                        file.isDirectory -> Icons.Outlined.Folder
                        file.isPdf -> Icons.Outlined.PictureAsPdf
                        file.isAudio -> Icons.Outlined.AudioFile
                        file.isVideo -> Icons.Outlined.VideoFile
                        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                    },
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = if (file.isDirectory) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        InfoRow(
            label = stringResource(R.string.info_name),
            value = file.name
        )

        InfoRow(
            label = stringResource(R.string.info_location),
            value = file.parentPath
        )

        InfoRow(
            label = stringResource(R.string.info_created),
            value = formatDate(file.createdTime)
        )

        InfoRow(
            label = stringResource(R.string.info_modified),
            value = formatDate(file.lastModified)
        )

        if (!file.isDirectory) {
            InfoRow(
                label = stringResource(R.string.info_size),
                value = file.formattedSize
            )
        }

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

        if (!file.isDirectory && file.mimeType.isNotBlank()) {
            InfoRow(
                label = stringResource(R.string.info_type),
                value = file.mimeType
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
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
