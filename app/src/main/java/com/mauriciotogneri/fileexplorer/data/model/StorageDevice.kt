package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter

@Immutable
data class StorageDevice(
    val path: String,
    val displayName: String,
    val totalBytes: Long,
    val availableBytes: Long
) {
    val formattedTotal: String get() = FileSizeFormatter.format(totalBytes)
    val formattedAvailable: String get() = FileSizeFormatter.format(availableBytes)
    val analyticsType: String get() = if (path.contains("emulated")) "internal" else "sd_card"

    companion object {
        fun isSdCard(path: String): Boolean = !path.contains("emulated")

        fun getLabel(path: String, sdCardIndex: Int, sdCardCount: Int): StorageLabel {
            return when {
                path.contains("emulated/0") -> StorageLabel.Internal
                path.contains("emulated") -> StorageLabel.InternalNumbered(path.substringAfterLast("emulated/"))
                sdCardCount > 1 -> StorageLabel.SdCardNumbered(sdCardIndex + 1)
                else -> StorageLabel.SdCard
            }
        }
    }
}

sealed interface StorageLabel {
    data object Internal : StorageLabel
    data class InternalNumbered(val suffix: String) : StorageLabel
    data object SdCard : StorageLabel
    data class SdCardNumbered(val number: Int) : StorageLabel
}
