package com.mauriciotogneri.fileexplorer.data.util

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The half of [isStorageUnavailable] that runs off device. Its errno comparisons need an
 * [android.system.ErrnoException] the stubbed `android.jar` cannot construct, and `FileAccessTest`
 * covers those — but the walk that looks for one reaches its answer without ever constructing one,
 * so the default and the depth bound belong in the per-change loop rather than behind an emulator.
 *
 * What these pin is the answer that keeps a walk going. A body that answered true unconditionally
 * would fail here and take every skip test in `FileRepositoryTest` with it.
 */
class FileAccessCauseChainTest {

    @Test
    fun `a failure carrying no errno is not a storage failure`() {
        // What every JVM failure looks like, and what a platform that attached no cause would give:
        // the walk finds nothing to match and the caller steps over that one file.
        assertFalse(FileNotFoundException("/tmp/gone.txt (No such file or directory)").isStorageUnavailable())
    }

    @Test
    fun `a wrapped failure carrying no errno is not a storage failure`() {
        val open = FileNotFoundException("/tmp/gone.txt (No such file or directory)")

        assertFalse(IOException("Failed to copy file", open).isStorageUnavailable())
    }

    @Test
    fun `a cyclic cause chain terminates`() {
        // The depth bound exists for this: without it the walk never ends and the transfer hangs.
        val first = IOException("first")
        val second = IOException("second", first)
        first.initCause(second)

        assertFalse(first.isStorageUnavailable())
    }
}
