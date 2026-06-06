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

        fun getLabel(path: String, index: Int, count: Int): StorageLabel {
            val numbered = count > 1
            return when {
                isSdCard(path) -> if (numbered) StorageLabel.SdCardNumbered(index + 1) else StorageLabel.SdCard
                numbered -> StorageLabel.InternalNumbered(index + 1)
                else -> StorageLabel.Internal
            }
        }
    }
}

sealed interface StorageLabel {
    data object Internal : StorageLabel
    data class InternalNumbered(val number: Int) : StorageLabel
    data object SdCard : StorageLabel
    data class SdCardNumbered(val number: Int) : StorageLabel
}
