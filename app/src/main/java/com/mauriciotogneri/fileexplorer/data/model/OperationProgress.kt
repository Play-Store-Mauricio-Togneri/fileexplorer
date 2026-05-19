package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class OperationProgress(
    val mode: OperationMode,
    val currentFile: String,
    val copiedBytes: Long,
    val totalBytes: Long,
    val isCancelling: Boolean = false
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) copiedBytes.toFloat() / totalBytes else 0f
}
