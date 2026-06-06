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

        fun getDisplayName(path: String, sdCardIndex: Int, sdCardCount: Int): String {
            return when {
                path.contains("emulated/0") -> "Internal Storage"
                path.contains("emulated") -> "Internal Storage ${path.substringAfterLast("emulated/")}"
                sdCardCount > 1 -> "SD Card ${sdCardIndex + 1}"
                else -> "SD Card"
            }
        }
    }
}
