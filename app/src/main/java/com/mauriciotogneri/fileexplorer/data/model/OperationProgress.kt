package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class OperationProgress(
    val mode: OperationMode,
    val currentFile: String,
    val copiedBytes: Long,
    val totalBytes: Long,
    val skippedBytes: Long = 0,
    val isCancelling: Boolean = false
) {
    /**
     * Measured against the part of the selection the transfer can actually write:
     * [totalBytes] counts every file the listing named, including the ones that turned out to be
     * unopenable, while [copiedBytes] only ever counts bytes that were written. Dividing by the
     * unadjusted total would leave the bar short of full on a transfer that skipped something and
     * then close the dialog there, saying it was cut off — the partial-success toast that follows
     * is what tells the user what was left behind.
     *
     * Zero when nothing is transferable, which is the selection whose every file was skipped.
     */
    val progressPercent: Float
        get() {
            val transferableBytes = totalBytes - skippedBytes
            return if (transferableBytes > 0) copiedBytes.toFloat() / transferableBytes else 0f
        }
}
