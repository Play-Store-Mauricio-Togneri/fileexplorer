package com.mauriciotogneri.fileexplorer.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.concurrent.thread

@OptIn(ExperimentalCoroutinesApi::class)
class DelayedProgressTest {

    /** Everything the dialog was told, in order; null is the dialog closing. */
    private val published = ArrayList<String?>()

    /** The handle a screen keeps its running operation in, and its Cancel button clears. */
    private var handle: Job? = null

    /**
     * Starts [block] the way a screen does, keeping its job as the handle. It runs in the test's
     * background scope, which `advanceUntilIdle` does not wait for, so the tests step time
     * explicitly.
     */
    private fun TestScope.start(block: suspend CoroutineScope.(DelayedProgress<String>) -> Unit): Job =
        backgroundScope.launchWithDelayedProgress(
            placeholder = PLACEHOLDER,
            owner = { handle },
            publish = { published += it },
            block = block
        ).also { handle = it }

    /** Moves virtual time to [millis] after now and runs everything due by then. */
    private fun TestScope.advanceTo(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    @Test
    fun `an operation that ends within the delay never shows its dialog and ends with its work`() = runTest {
        val job = start { dialog ->
            dialog.update("half way")
            delay(PROGRESS_DIALOG_DELAY_MS - 1)
            dialog.clear()
        }

        advanceTo(PROGRESS_DIALOG_DELAY_MS - 1)

        assertTrue("The timer must not keep the operation alive past its work", job.isCompleted)
        advanceTo(PROGRESS_DIALOG_DELAY_MS)
        assertEquals(emptyList<String?>(), published)
    }

    @Test
    fun `an operation still running at the delay shows the placeholder if it reported nothing`() = runTest {
        start { awaitCancellation() }

        advanceTo(PROGRESS_DIALOG_DELAY_MS - 1)
        assertEquals(emptyList<String?>(), published)

        advanceTo(1)
        assertEquals(listOf<String?>(PLACEHOLDER), published)
    }

    @Test
    fun `the dialog appears with the latest progress, then follows it`() = runTest {
        lateinit var dialog: DelayedProgress<String>
        start {
            dialog = it
            it.update("first")
            it.update("second")
            awaitCancellation()
        }

        advanceTo(PROGRESS_DIALOG_DELAY_MS)
        assertEquals(listOf<String?>("second"), published)

        dialog.update("third")
        assertEquals(listOf<String?>("second", "third"), published)
    }

    // The delay runs from the operation's start, so pre-flight work counts towards it and a report
    // arriving part-way does not push the dialog back.
    @Test
    fun `a report arriving during the delay does not restart it`() = runTest {
        start {
            delay(PROGRESS_DIALOG_DELAY_MS - 100)
            it.update("late")
            awaitCancellation()
        }

        advanceTo(PROGRESS_DIALOG_DELAY_MS)

        assertEquals(listOf<String?>("late"), published)
    }

    @Test
    fun `finishing closes the dialog it showed`() = runTest {
        val work = CompletableDeferred<Unit>()
        start {
            it.update("working")
            work.await()
            it.clear()
        }
        advanceTo(PROGRESS_DIALOG_DELAY_MS)

        work.complete(Unit)
        runCurrent()

        assertEquals(listOf("working", null), published)
    }

    @Test
    fun `a dialog the operation left up closes when the operation ends`() = runTest {
        val work = CompletableDeferred<Unit>()
        start {
            it.update("working")
            work.await()
        }
        advanceTo(PROGRESS_DIALOG_DELAY_MS)

        work.complete(Unit)
        runCurrent()

        assertEquals(listOf("working", null), published)
    }

    @Test
    fun `cancelling the operation before the delay stops its timer`() = runTest {
        val job = start { awaitCancellation() }
        advanceTo(PROGRESS_DIALOG_DELAY_MS - 1)

        job.cancel()
        runCurrent()
        assertTrue(job.isCompleted)

        advanceTo(PROGRESS_DIALOG_DELAY_MS)
        assertEquals(emptyList<String?>(), published)
    }

    // While the first operation's dialog is hidden the screen is usable, so a second one can take
    // the handle. The first must then stay out of the dialog entirely: it carries the second one's
    // only Cancel button.
    @Test
    fun `an operation that lost the handle never shows, updates or closes the newer one's dialog`() = runTest {
        val olderWork = CompletableDeferred<Unit>()
        start {
            it.update("older")
            olderWork.await()
            it.update("older, later")
            it.clear()
        }
        runCurrent()
        start {
            it.update("newer")
            awaitCancellation()
        }

        advanceTo(PROGRESS_DIALOG_DELAY_MS)
        olderWork.complete(Unit)
        runCurrent()

        assertEquals(listOf<String?>("newer"), published)
    }

    @Test
    fun `a dialog taken over after it appeared is neither updated nor closed by the older operation`() = runTest {
        val olderWork = CompletableDeferred<Unit>()
        lateinit var older: DelayedProgress<String>
        start {
            older = it
            it.update("older")
            olderWork.await()
            it.clear()
        }
        advanceTo(PROGRESS_DIALOG_DELAY_MS)
        start {
            it.update("newer")
            awaitCancellation()
        }

        older.update("older, later")
        olderWork.complete(Unit)
        advanceTo(PROGRESS_DIALOG_DELAY_MS)

        assertEquals(listOf<String?>("older", "newer"), published)
    }

    // Cancel clears the handle at once, before the operation has unwound. The operation must still
    // close the dialog it showed, and what it reports while unwinding must not reach it.
    @Test
    fun `a cancelled operation whose handle was cleared still closes its own dialog`() = runTest {
        lateinit var dialog: DelayedProgress<String>
        val job = start {
            dialog = it
            it.update("working")
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                it.clear()
                throw e
            }
        }
        advanceTo(PROGRESS_DIALOG_DELAY_MS)

        handle = null
        dialog.update("after the cancel")
        job.cancel()
        runCurrent()

        assertEquals(listOf("working", null), published)
    }

    // A screen about to be left shows the dialog early, so the press lands on it rather than on the
    // way out. The delay then has nothing left to show.
    @Test
    fun `revealing during the delay shows the latest progress at once, and the delay adds nothing`() = runTest {
        lateinit var dialog: DelayedProgress<String>
        start {
            dialog = it
            it.update("working")
            awaitCancellation()
        }
        runCurrent()

        assertTrue(dialog.reveal())
        assertEquals(listOf<String?>("working"), published)

        advanceTo(PROGRESS_DIALOG_DELAY_MS)
        assertEquals(listOf<String?>("working"), published)
    }

    @Test
    fun `revealing before anything was reported shows the placeholder`() = runTest {
        lateinit var dialog: DelayedProgress<String>
        start {
            dialog = it
            awaitCancellation()
        }
        runCurrent()

        assertTrue(dialog.reveal())
        assertEquals(listOf<String?>(PLACEHOLDER), published)
    }

    // The press can arrive after the dialog is published and before it is drawn: it must still be
    // told the dialog is up, or it would leave the screen.
    @Test
    fun `revealing a dialog already up answers that it is up without showing it again`() = runTest {
        lateinit var dialog: DelayedProgress<String>
        start {
            dialog = it
            it.update("working")
            awaitCancellation()
        }
        advanceTo(PROGRESS_DIALOG_DELAY_MS)

        assertTrue(dialog.reveal())
        assertEquals(listOf<String?>("working"), published)
    }

    @Test
    fun `an operation that finished, lost the handle or was cancelled cannot be revealed`() = runTest {
        lateinit var finished: DelayedProgress<String>
        start { finished = it }
        runCurrent()
        lateinit var older: DelayedProgress<String>
        start {
            older = it
            awaitCancellation()
        }
        runCurrent()
        lateinit var cancelled: DelayedProgress<String>
        start {
            cancelled = it
            awaitCancellation()
        }
        advanceTo(PROGRESS_DIALOG_DELAY_MS)
        // What the Cancel button does before the operation has unwound.
        handle = null

        assertFalse("A finished operation has no dialog to show", finished.reveal())
        assertFalse("A newer operation took the dialog", older.reveal())
        assertFalse("A cancelled operation no longer owns its dialog", cancelled.reveal())
        assertEquals(listOf<String?>(PLACEHOLDER), published)
    }

    // Each publish appends to a plain ArrayList, which loses entries or throws when two threads
    // write it at once: only the dialog's own lock keeps every report.
    @Test
    fun `reports from several threads each reach the shown dialog`() = runTest {
        lateinit var dialog: DelayedProgress<String>
        start {
            dialog = it
            awaitCancellation()
        }
        advanceTo(PROGRESS_DIALOG_DELAY_MS)

        val reporters = (1..4).map { reporter ->
            thread { repeat(1_000) { index -> dialog.update("$reporter-$index") } }
        }
        reporters.forEach { it.join() }

        assertEquals(1 + 4_000, published.size)
    }

    private companion object {
        const val PLACEHOLDER = "preparing"
    }
}
