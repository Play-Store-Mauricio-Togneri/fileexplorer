package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.ui.graphics.vector.ImageVector
import com.mauriciotogneri.fileexplorer.data.model.StorageType

/** The icon that identifies a volume's kind, shared by every list that draws a storage device. */
fun storageIcon(type: StorageType): ImageVector = when (type) {
    StorageType.INTERNAL -> Icons.Outlined.PhoneAndroid
    StorageType.SD_CARD -> Icons.Outlined.SdCard
}
