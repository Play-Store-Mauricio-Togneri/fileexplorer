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
     * Never less than [copiedBytes], because both totals are read from a filesystem other apps are
     * writing to: [totalBytes] is one pre-walk snapshot, while [copiedBytes] counts to EOF and
     * [skippedBytes] stats the file again at skip time, so a source that grew in between can carry
     * either past the snapshot. Without the floor that transfer would divide by zero or a negative
     * and show an empty bar for the rest of its run, which reads as a stall on an operation that is
     * working.
     *
     * Zero when nothing is transferable: an empty selection, or one whose every file was skipped.
     */
    val progressPercent: Float
        get() {
            val transferableBytes = (totalBytes - skippedBytes).coerceAtLeast(copiedBytes)
            return if (transferableBytes > 0) copiedBytes.toFloat() / transferableBytes else 0f
        }
}
