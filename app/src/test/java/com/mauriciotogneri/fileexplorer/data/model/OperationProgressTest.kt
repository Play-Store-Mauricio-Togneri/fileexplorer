package com.mauriciotogneri.fileexplorer.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OperationProgressTest {

    private fun progress(
        copiedBytes: Long,
        totalBytes: Long,
        skippedBytes: Long = 0
    ) = OperationProgress(
        mode = OperationMode.COPY,
        currentFile = "file.txt",
        copiedBytes = copiedBytes,
        totalBytes = totalBytes,
        skippedBytes = skippedBytes
    )

    @Test
    fun `progressPercent is the copied fraction of the whole selection when nothing is skipped`() {
        assertEquals(0.25f, progress(copiedBytes = 250, totalBytes = 1000).progressPercent, 0f)
    }

    @Test
    fun `progressPercent reaches full when every byte that could be copied was copied`() {
        // The skipped bytes are in totalBytes — the tally walks the same listing the transfer does
        // and charges for files it later cannot open — and copiedBytes can never reach them. Left
        // in the denominator the bar stalls at 0.6 and the dialog closes there, telling the user a
        // transfer that did everything it could was cut off.
        val completed = progress(copiedBytes = 600, totalBytes = 1000, skippedBytes = 400)
        assertEquals(1f, completed.progressPercent, 0f)
    }

    @Test
    fun `progressPercent measures against the transferable bytes while the transfer runs`() {
        assertEquals(
            0.5f,
            progress(copiedBytes = 300, totalBytes = 1000, skippedBytes = 400).progressPercent,
            0f
        )
    }

    @Test
    fun `progressPercent is zero when every file in the selection was skipped`() {
        // Nothing is transferable, so there is no fraction to show — and the division that would
        // produce one is by zero.
        assertEquals(0f, progress(copiedBytes = 0, totalBytes = 1000, skippedBytes = 1000).progressPercent, 0f)
    }

    @Test
    fun `progressPercent is zero for an empty selection`() {
        assertEquals(0f, progress(copiedBytes = 0, totalBytes = 0).progressPercent, 0f)
    }
}
