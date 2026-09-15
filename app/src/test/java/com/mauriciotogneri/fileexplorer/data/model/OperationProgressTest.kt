package com.mauriciotogneri.fileexplorer.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class OperationProgressTest {

    // Exact binary fractions throughout, so this only guards against a future case whose expected
    // value is not one.
    private val DELTA = 0.001f

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
        assertEquals(0.25f, progress(copiedBytes = 250, totalBytes = 1000).progressPercent, DELTA)
    }

    @Test
    fun `progressPercent reaches full when every byte that could be copied was copied`() {
        // The skipped bytes are in totalBytes — the tally walks the same listing the transfer does
        // and charges for files it later cannot open — and copiedBytes can never reach them. Left
        // in the denominator the bar stalls at 0.6 and the dialog closes there, telling the user a
        // transfer that did everything it could was cut off.
        val completed = progress(copiedBytes = 600, totalBytes = 1000, skippedBytes = 400)
        assertEquals(1f, completed.progressPercent, DELTA)
    }

    @Test
    fun `progressPercent measures against the transferable bytes while the transfer runs`() {
        assertEquals(
            0.5f,
            progress(copiedBytes = 300, totalBytes = 1000, skippedBytes = 400).progressPercent,
            DELTA
        )
    }

    @Test
    fun `progressPercent is zero when every file in the selection was skipped`() {
        // Nothing is transferable, so there is no fraction to show — and the division that would
        // produce one is by zero.
        assertEquals(
            0f,
            progress(copiedBytes = 0, totalBytes = 1000, skippedBytes = 1000).progressPercent,
            DELTA
        )
    }

    @Test
    fun `progressPercent is full when the skipped bytes swallow the whole total`() {
        // Both totals are read from a filesystem other apps write to: totalBytes is one pre-walk
        // snapshot, while the skip stats the file again and can charge more than the snapshot did.
        // Left unfloored the denominator goes to zero or below, and a transfer that is copying
        // normally shows an empty bar for the rest of its run — which reads as a stall and invites
        // the user to cancel something that is working.
        val outrun = progress(copiedBytes = 300, totalBytes = 1000, skippedBytes = 1000)
        assertEquals(1f, outrun.progressPercent, DELTA)
    }

    @Test
    fun `progressPercent never exceeds full when more was copied than the total tallied`() {
        // The other half of the same race: the copy loop reads to EOF, not to the length the tally
        // charged, so a source that grew can carry copiedBytes past the denominator on its own.
        val grew = progress(copiedBytes = 1200, totalBytes = 1000)
        assertEquals(1f, grew.progressPercent, DELTA)
    }

    @Test
    fun `progressPercent is zero for an empty selection`() {
        assertEquals(0f, progress(copiedBytes = 0, totalBytes = 0).progressPercent, DELTA)
    }
}
