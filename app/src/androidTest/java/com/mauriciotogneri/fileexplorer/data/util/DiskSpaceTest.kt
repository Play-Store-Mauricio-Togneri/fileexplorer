package com.mauriciotogneri.fileexplorer.data.util

import android.system.ErrnoException
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Exercises [isNoSpaceLeft] against real [ErrnoException] and [OsConstants] values. These cases
 * cannot run as JVM unit tests: the stubbed `android.jar` cannot construct an [ErrnoException], and
 * `errno` is a public final field, so it cannot be mocked either.
 */
@RunWith(AndroidJUnit4::class)
class DiskSpaceTest {

    @Test
    fun isNoSpaceLeft_diskFullFailure_returnsTrue() {
        // The shape Crashlytics reported: ZipOutputStream.write hits a full disk, and IoBridge
        // rethrows the errno failure as an IOException that keeps the ErrnoException as its cause.
        val errno = ErrnoException("write", OsConstants.ENOSPC)

        assertTrue(IOException(errno.message, errno).isNoSpaceLeft())
    }

    @Test
    fun isNoSpaceLeft_diskFullFailureNestedDeeper_returnsTrue() {
        val errno = ErrnoException("write", OsConstants.ENOSPC)
        val failure = IOException("Compression failed", IOException(errno.message, errno))

        assertTrue(failure.isNoSpaceLeft())
    }

    @Test
    fun isNoSpaceLeft_dataStoreWriteFailure_returnsTrue() {
        // The shape DataStore reports on a full device: it cannot create the `.tmp` file it writes
        // before the atomic rename, so the errno surfaces as a FileNotFoundException that DataStore
        // then wraps again, leaving the ErrnoException two levels down.
        val errno = ErrnoException("open", OsConstants.ENOSPC)
        val open = FileNotFoundException("recent_files.preferences_pb.tmp (No space left on device)")
        open.initCause(errno)
        val failure = IOException("Inoperable file: freeSpace[0]", open)

        assertTrue(failure.isNoSpaceLeft())
    }

    @Test
    fun isNoSpaceLeft_otherErrno_returnsFalse() {
        // A missing or unreadable source must keep reporting as itself, not as a full disk.
        val errno = ErrnoException("open", OsConstants.ENOENT)

        assertFalse(IOException(errno.message, errno).isNoSpaceLeft())
    }

    @Test
    fun isNoSpaceLeft_pathNamedAfterTheErrno_returnsFalse() {
        // The errno is read from the field rather than matched in the message precisely so that a
        // path containing the token cannot be mistaken for a full disk.
        val errno = ErrnoException("open", OsConstants.ENOENT)
        val failure = IOException("/storage/emulated/0/ENOSPC (No space left on device)", errno)

        assertFalse(failure.isNoSpaceLeft())
    }

    @Test
    fun isNoSpaceLeft_failureWithoutErrno_returnsFalse() {
        assertFalse(IOException("Compression failed").isNoSpaceLeft())
    }

    @Test
    fun isNoSpaceLeft_cyclicCauseChain_terminates() {
        val first = IOException("first")
        val second = IOException("second", first)
        first.initCause(second)

        assertFalse(first.isNoSpaceLeft())
    }
}
