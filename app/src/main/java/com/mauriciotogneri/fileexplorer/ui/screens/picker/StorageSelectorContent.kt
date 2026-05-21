package com.mauriciotogneri.fileexplorer.ui.screens.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice

@Composable
fun StorageSelectorContent(
    storages: List<StorageDevice>,
    onStorageClick: (StorageDevice) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(
            items = storages,
            key = { it.path }
        ) { storage ->
            Column {
                StoragePickerItem(
                    storage = storage,
                    onClick = { onStorageClick(storage) }
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun StoragePickerItem(
    storage: StorageDevice,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(text = storage.displayName)
        },
        supportingContent = {
            Text(
                text = stringResource(
                    R.string.storage_available,
                    storage.formattedAvailable
                )
            )
        },
        leadingContent = {
            Icon(
                imageVector = getStorageIcon(storage),
                contentDescription = null
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun getStorageIcon(storage: StorageDevice): ImageVector {
    return when {
        storage.path.contains("emulated") -> Icons.Outlined.PhoneAndroid
        storage.displayName.contains("SD", ignoreCase = true) -> Icons.Outlined.SdCard
        else -> Icons.Outlined.Storage
    }
}
