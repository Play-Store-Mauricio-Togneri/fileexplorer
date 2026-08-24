package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class ErrorScrubbingTest {

    @Test
    fun `scrubbed drops the message that names the file`() {
        // The shape a platform read failure actually takes: the absolute path is the message.
        val e = FileNotFoundException("/storage/emulated/0/Documents/tax-return-2025.pdf (No such file or directory)")

        assertFalse(e.scrubbed().message.orEmpty().contains("tax-return-2025"))
        assertFalse(e.scrubbed().message.orEmpty().contains("/storage"))
    }

    @Test
    fun `scrubbed keeps the failing type as the message`() {
        // What a triager needs from the message once the message itself is gone. Asserted with a
        // type that is not the stand-in's own class, so a helper that hardcoded its own name would
        // fail here.
        val e = IllegalArgumentException("Failed to find configured root that contains /storage/emulated/0/a.pdf")

        assertEquals(IllegalArgumentException::class.java.name, e.scrubbed().message)
    }

    @Test
    fun `scrubbed drops the whole cause chain`() {
        // recordException transmits every cause, so a scrub that stopped at the outermost message
        // would publish the path one hop down.
        val root = FileNotFoundException("/storage/emulated/0/Pictures/holiday.jpg (No such file)")
        val e = IOException("wrapped", IllegalStateException("also wrapped", root))

        val chain = generateSequence<Throwable>(e.scrubbed()) { it.cause }.toList()

        assertEquals(1, chain.size)
        assertNull(e.scrubbed().cause)
        assertFalse(chain.single().message.orEmpty().contains("holiday"))
    }

    @Test
    fun `scrubbed keeps the frame that threw`() {
        // Without the copy every scrubbed report would group by the catch clause that built the
        // stand-in instead of by the call that actually failed.
        val e = IOException("boom")
        val thrownAt = e.stackTrace

        assertArrayEquals(thrownAt, e.scrubbed().stackTrace)
        assertTrue(e.scrubbed().stackTrace.isNotEmpty())
    }

    @Test
    fun `scrubbed leaves a cancellation filterable`() {
        // ErrorReporter.report drops a CancellationException before recordException, and that
        // filter reads the type. Collapsing one into the IOException carrier would file a non-fatal
        // every time a user backs out of a screen mid-read — the scrub has to keep this one
        // distinction to stay invisible to that guard.
        val cancelled: Throwable = CancellationException("/storage/emulated/0/Documents/notes.txt")

        assertTrue(cancelled.scrubbed() is CancellationException)
        assertFalse(cancelled.scrubbed().message.orEmpty().contains("notes.txt"))
    }

    @Test
    fun `scrubbed still uses the IO carrier for everything else`() {
        // Keeps the cancellation branch above from widening: anything that is not a cancellation
        // must stay on the carrier the cause position in FileRepository is typed for.
        assertTrue(IllegalStateException("boom").scrubbed() is IOException)
        assertFalse(IllegalStateException("boom").scrubbed() is CancellationException)
    }

    @Test
    fun `scrubbed survives a throwable carrying neither message nor stack trace`() {
        // writableStackTrace = false yields a zero-length trace; a null message must not become
        // the literal "null" in a report.
        val e = object : Exception(null, null, false, false) {}

        assertEquals(e.javaClass.name, e.scrubbed().message)
        assertEquals(0, e.scrubbed().stackTrace.size)
    }
}
