package com.mauriciotogneri.fileexplorer.ui.screens.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.data.model.FileItem

/**
 * Tags the loading spinner so a test can assert the loading branch rendered. Without it the only
 * observable difference between loading and an empty folder is the absence of rows, which an empty
 * list produces too.
 */
const val FOLDER_PICKER_LOADING_TEST_TAG = "folder_picker_loading"

@Composable
fun FolderPickerContent(
    folders: List<FileItem>,
    isLoading: Boolean,
    error: String?,
    onFolderClick: (FileItem) -> Unit
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag(FOLDER_PICKER_LOADING_TEST_TAG))
            }
        }

        error != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        else -> {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    items = folders,
                    key = { it.path }
                ) { folder ->
                    Column {
                        FolderPickerItem(
                            folder = folder,
                            onClick = { onFolderClick(folder) }
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderPickerItem(
    folder: FileItem,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = folder.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
