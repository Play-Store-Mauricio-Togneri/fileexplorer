package com.mauriciotogneri.fileexplorer.data.util

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileNotFoundException

private const val MAX_CAUSE_CHAIN_DEPTH = 10

/**
 * Reports whether a failure to open a file means the storage underneath it has gone away, as
 * opposed to something being wrong with that one file.
 *
 * Every failure of `open(2)` reaches Java as the same [FileNotFoundException]: `IoBridge.open`
 * catches [ErrnoException] unqualified and rethrows it as one, keeping the original as the cause.
 * The type therefore says nothing about what went wrong, and a walk that steps over what it cannot
 * open has to read the errno to find the failures it must not step over.
 *
 * Phrased as the small closed set rather than its complement, and answering false for everything
 * else, because the two directions are not equally safe. The per-file failures are open-ended and
 * some are not obvious: besides the denial and the vanished source this exists for, libcore itself
 * synthesises EISDIR by `fstat`-ing the descriptor it just opened, and a listing can still hand a
 * walk ENOTDIR, ELOOP or ENAMETOOLONG. Skipping one file that should have failed the operation
 * costs that file; failing the operation on one file that should have been skipped costs the user
 * the whole copy, move or archive — which is the bug this whole rule was written to fix, and it
 * would come back the moment a device answered with an errno nobody listed.
 *
 * Both storage roots this app walks are served by a FUSE daemon, and a daemon that dies answers
 * every request afterwards with ENOTCONN, or ECONNABORTED for the one already in flight — those,
 * not EIO, are what a torn-down volume actually produces. EIO and ENODEV/ENXIO are in the set as
 * the classical answers for failing or absent block storage. ENODEV and ENXIO are near-inert here
 * — `open(2)` raises them for device special files, which a FAT volume has none of — and are kept
 * as insurance rather than removed.
 *
 * EIO is the one entry that is not purely the volume's: ext4 raises it for a single unreadable
 * inode and FUSE for a single name whose attributes the daemon returned malformed, so one bad file
 * on a healthy volume stops the operation. Kept anyway, because the alternative is not noticing a
 * volume that has failed rather than vanished, and a stopped operation is recoverable where a
 * silently incomplete one is not.
 *
 * Skipping any of these would drop every remaining file and still report the operation as a
 * success.
 *
 * The errno is read from the field rather than matched in the message for the reason
 * [isNoSpaceLeft] gives, and that is also what cannot be exercised off-device: the stubbed
 * `android.jar` cannot construct an [ErrnoException], and `errno` is a public final field rather
 * than something a mock can intercept. `FileAccessTest` covers the mapping on a device; the
 * branches that never reach an [ErrnoException] are covered on the JVM by `FileAccessCauseChainTest`
 * — and, because false is the answer that keeps a walk going, the repository's own JVM tests run
 * this function for real rather than stubbing it.
 *
 * False when the chain carries no [ErrnoException], which means "keep going" — what this app did
 * for every open failure before this function existed, so the answer degrades to the old behaviour
 * rather than to a new one. The JVM is one such platform, and `File.isInvalid()` (a path containing
 * NUL, which `File.list()` cannot produce) is one such path. Whether every API level from
 * `minSdk` up routes `FileInputStream` through `IoBridge.open`, which is what attaches the cause,
 * is unverified: it is settled on a device, not by reading, and where it does not hold this
 * function is inert and the walk skips as it always did.
 *
 * A limit of the whole approach, not of this set: a volume that goes away during *enumeration*
 * raises nothing at all. `File.list()` answers null, `forEachChild` returns, and the walk reports a
 * clean success over a subtree it never saw. No errno reaches this function on that path.
 *
 * Not to be confused with [isUnreadableFile], which asks a related question by type rather than by
 * errno and deliberately does not separate a denied file from a failing volume. That one decides
 * whether a failure is worth reporting; this one decides whether a walk stops.
 *
 * The walk is depth-bounded so that a cyclic cause chain cannot hang the caller.
 */
internal fun Throwable.isStorageUnavailable(): Boolean =
    generateSequence(this) { it.cause }
        .take(MAX_CAUSE_CHAIN_DEPTH)
        .any {
            it is ErrnoException &&
                (
                    it.errno == OsConstants.ENOTCONN ||
                        it.errno == OsConstants.ECONNABORTED ||
                        it.errno == OsConstants.EIO ||
                        it.errno == OsConstants.ENODEV ||
                        it.errno == OsConstants.ENXIO
                    )
        }

/**
 * The errno behind a failure, or null when the chain carries no [ErrnoException] — the JVM, and any
 * failure not raised by a syscall.
 *
 * Reported alongside a skip so that [isStorageUnavailable]'s set can be checked against what
 * devices actually produce. It is an int with no file in it, which is what makes it reportable at
 * all: the walk that reads it is looking at the user's own files, and every message on that failure
 * is built from the path.
 */
internal fun Throwable.errnoOrNull(): Int? =
    generateSequence(this) { it.cause }
        .take(MAX_CAUSE_CHAIN_DEPTH)
        .filterIsInstance<ErrnoException>()
        .firstOrNull()
        ?.errno

/**
 * What taking a file off its path did.
 *
 * Three states rather than a boolean or a nullable errno, because the two ways a delete can end
 * well are not interchangeable to every caller. [Removed] says this call unlinked something;
 * [AlreadyAbsent] says the path held nothing to begin with. Both satisfy the user's request — the
 * path holds nothing either way — and a caller that only has to answer the user may treat them
 * alike. A caller that then tells another system the file is gone may not: MediaStore's row
 * deletion is a prefix match and a media provider unlinks the file behind a row it drops, so
 * reporting an already-absent path would delete whatever occupies it now.
 */
sealed interface RemoveOutcome {
    /** This call unlinked the file, or removed the directory. */
    data object Removed : RemoveOutcome

    /** Nothing was there. Something else took the path off before this call reached it. */
    data object AlreadyAbsent : RemoveOutcome

    /** [errno] as the syscall reported it, or [ERRNO_UNKNOWN] where the caller has none to give. */
    data class Failed(val errno: Int) : RemoveOutcome
}

/**
 * Removes [file].
 *
 * `File.delete()` is this call with the [ErrnoException] swallowed — libcore hands `remove(3)` the
 * path and returns false for every failure alike — so this is that method with the one thing it
 * discards kept. `remove(3)` unlinks a file and `rmdir`s a directory, which is why a single call
 * covers both and why substituting it changes nothing about what gets deleted.
 *
 * ENOENT answers [RemoveOutcome.AlreadyAbsent] rather than a failure: a delete is asked for a path
 * that holds nothing afterwards, and a path that held nothing already satisfies that. Reporting it
 * as a failure is what put an error message in front of a user whose file another app had removed
 * first — the stale search result, the stale recents entry. It is kept apart from
 * [RemoveOutcome.Removed] rather than folded into it because only the latter licenses telling
 * MediaStore the path is gone; see [RemoveOutcome].
 *
 * Not reachable from JVM unit tests: [Os] comes from the stubbed `android.jar` and throws. That is
 * what [com.mauriciotogneri.fileexplorer.data.repository.FileRepository]'s `removeFile` parameter
 * exists for, and `FileAccessTest` covers this function on a device.
 */
internal fun removePath(file: File): RemoveOutcome =
    try {
        Os.remove(file.path)
        RemoveOutcome.Removed
    } catch (e: ErrnoException) {
        if (e.errno == OsConstants.ENOENT) {
            RemoveOutcome.AlreadyAbsent
        } else {
            RemoveOutcome.Failed(e.errno)
        }
    }
