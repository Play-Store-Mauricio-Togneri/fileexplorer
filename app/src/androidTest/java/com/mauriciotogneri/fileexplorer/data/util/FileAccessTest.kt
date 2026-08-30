package com.mauriciotogneri.fileexplorer.data.util

import android.system.ErrnoException
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Exercises [isStorageUnavailable] against real [ErrnoException] and [OsConstants] values. These
 * cases cannot run as JVM unit tests, for the reason [DiskSpaceTest] gives about its own subject:
 * the stubbed `android.jar` cannot construct an [ErrnoException], and `errno` is a public final
 * field, so it cannot be mocked either. `FileAccessCauseChainTest` covers the walk itself off
 * device, and `FileRepositoryTest` covers what each answer makes a walk do.
 */
@RunWith(AndroidJUnit4::class)
class FileAccessTest {

    @Test
    fun isStorageUnavailable_volumeIoError_returnsTrue() {
        // Removable storage unmounted mid-walk, which must fail the whole operation rather than be
        // counted as one skipped file.
        val errno = ErrnoException("open", OsConstants.EIO)
        val failure = FileNotFoundException("/storage/2CEF-0918/photos/holiday.jpg")
        failure.initCause(errno)

        assertTrue(failure.isStorageUnavailable())
    }

    @Test
    fun isStorageUnavailable_fuseDaemonGone_returnsTrue() {
        // What a torn-down volume actually produces on this app's storage roots, both of which are
        // served by a FUSE daemon: every request after the daemon dies answers ENOTCONN.
        val errno = ErrnoException("open", OsConstants.ENOTCONN)
        val failure = FileNotFoundException("/storage/2CEF-0918/photos/holiday.jpg")
        failure.initCause(errno)

        assertTrue(failure.isStorageUnavailable())
    }

    @Test
    fun isStorageUnavailable_fuseRequestAborted_returnsTrue() {
        // The request that was already in flight when the connection was aborted gets this one
        // rather than ENOTCONN.
        val errno = ErrnoException("open", OsConstants.ECONNABORTED)
        val failure = FileNotFoundException("/storage/2CEF-0918/photos/holiday.jpg")
        failure.initCause(errno)

        assertTrue(failure.isStorageUnavailable())
    }

    @Test
    fun isStorageUnavailable_volumeGone_returnsTrue() {
        val errno = ErrnoException("open", OsConstants.ENODEV)
        val failure = FileNotFoundException("/storage/2CEF-0918/photos/holiday.jpg")
        failure.initCause(errno)

        assertTrue(failure.isStorageUnavailable())
    }

    @Test
    fun isStorageUnavailable_volumeFailureNestedDeeper_returnsTrue() {
        // The walk is depth-bounded, so a failure the caller has already wrapped must still be
        // recognised rather than only the one it was handed directly.
        val errno = ErrnoException("open", OsConstants.EIO)
        val open = FileNotFoundException("/storage/2CEF-0918/photos/holiday.jpg")
        open.initCause(errno)

        assertTrue(IOException("Failed to copy file", open).isStorageUnavailable())
    }

    @Test
    fun isStorageUnavailable_deniedOpen_returnsFalse() {
        // The case the whole rule exists for: `Android/data` on a removable volume is listed and
        // then denied, and that one file is stepped over rather than failing the operation.
        val errno = ErrnoException("open", OsConstants.EACCES)
        val failure = FileNotFoundException("/storage/2CEF-0918/Android/data/.nomedia")
        failure.initCause(errno)

        assertFalse(failure.isStorageUnavailable())
    }

    @Test
    fun isStorageUnavailable_missingFile_returnsFalse() {
        // A source deleted between the listing and the walk, which wants the same handling.
        val errno = ErrnoException("open", OsConstants.ENOENT)
        val failure = FileNotFoundException("/storage/emulated/0/gone.txt")
        failure.initCause(errno)

        assertFalse(failure.isStorageUnavailable())
    }

    @Test
    fun isStorageUnavailable_perFileErrno_returnsFalse() {
        // libcore synthesises EISDIR itself, by fstat-ing the descriptor it just opened. It reaches
        // a walk when an entry the listing named as a file is a directory by the time it is opened,
        // and it is that entry's problem rather than the volume's.
        val errno = ErrnoException("open", OsConstants.EISDIR)
        val failure = FileNotFoundException("/storage/emulated/0/replaced")
        failure.initCause(errno)

        assertFalse(failure.isStorageUnavailable())
    }

    @Test
    fun isStorageUnavailable_realDeniedOpen_returnsFalse() {
        // The one case that asserts what the platform actually produces rather than a value this
        // test chose: every other case here builds its own ErrnoException, so none of them would
        // notice if a real denied open stopped carrying one.
        val file = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "denied_open_probe.txt"
        )
        try {
            file.writeText("secret")
            file.setReadable(false, false)
            // Root ignores the permission bits; the assertion below would then be vacuous.
            org.junit.Assume.assumeTrue(!file.canRead())

            val thrown = runCatching { file.inputStream() }.exceptionOrNull()

            assertTrue(thrown is FileNotFoundException)
            assertFalse(thrown!!.isStorageUnavailable())
        } finally {
            file.setReadable(true, true)
            file.delete()
        }
    }

    @Test
    fun isStorageUnavailable_pathNamedAfterTheErrno_returnsFalse() {
        // The errno is read from the field rather than matched in the message precisely so that a
        // path containing the token cannot be mistaken for a failing volume.
        val errno = ErrnoException("open", OsConstants.EACCES)
        val failure = IOException("/storage/emulated/0/EIO (I/O error)", errno)

        assertFalse(failure.isStorageUnavailable())
    }
}
