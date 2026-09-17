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

    /**
     * Nothing was there. Something else took the path off before this call reached it, on a path
     * that still resolves — which is what separates this from [Unresolvable].
     *
     * Callers scan such a path rather than reporting it deleted. For a directory that costs
     * something and it is a deliberate trade: a scan drops the directory's own row but not its
     * descendants', where the prefix delete dropped all of them, so a folder removed from under
     * the user leaves gallery rows behind until the next media scan. Those are cosmetic and
     * self-healing; the alternative — prefix-deleting a path this app did not empty — unlinks
     * whatever occupies it now, and that is not recoverable.
     */
    data object AlreadyAbsent : RemoveOutcome

    /**
     * The path stopped resolving: an ancestor of it is gone, so the node cannot be reached to say
     * what became of it.
     *
     * Apart from [AlreadyAbsent] because the two are not equally trustworthy, and folded into it
     * for the user because to them they are the same thing. Nothing is at the path either way, so
     * the request is met and the caller reports it done — but where an already-absent path was
     * observed empty, this one was never reached, and the file may be sitting under the name
     * another app just renamed its folder to. No errno tells the two apart — the ancestor answers
     * ENOENT whether it was deleted or renamed — which is why this state carries neither an errno
     * nor a message: there is nothing true to say beyond that the path is gone.
     *
     * What it costs is MediaStore's prefix row delete, which a walk may only aim at a subtree it
     * watched itself empty. A root holding one of these is scanned instead of prefix-deleted, even
     * where the walk did unlink something under it — see [RemoveOutcome] for what that scan gives
     * up, and why it is the side to err on.
     */
    data object Unresolvable : RemoveOutcome

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
 * Which ENOENT it is, though, is the parent's to say. `remove(3)` answers it as readily for an
 * ancestor component that stopped resolving as for a missing file, and there the data may be
 * untouched — sitting under the folder another app renamed while this walk was inside it. So the
 * parent is stat'd before the answer is trusted, and the three cases it separates get three
 * answers:
 *
 *  - the parent stats clean — the path was reached and held nothing: [RemoveOutcome.AlreadyAbsent].
 *  - the parent answers ENOENT — the ancestor is gone, and whether the file went with it or was
 *    renamed out from under the walk is not a question any errno here answers:
 *    [RemoveOutcome.Unresolvable], which meets the user's request without licensing the prefix
 *    delete. Both readings are ordinary — this app's own folder delete produces the first every
 *    time a stale listing is acted on afterwards — so neither an error nor a clean success over
 *    the whole subtree would be honest.
 *  - the parent answers anything else — it is still there, or the volume under it is not, and
 *    either way the file was not deleted: [RemoveOutcome.Failed] with the parent's errno, which is
 *    the one that names the cause. A torn-down FUSE mount landing here earns the user
 *    [DeleteFailure.STORAGE_UNAVAILABLE] rather than a generic failure, though it reaches this
 *    branch only by racing: a mount already gone makes `remove(3)` itself answer ENOTCONN, which
 *    never gets this far.
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
            when (val parentErrno = pathResolutionErrno(file)) {
                null -> RemoveOutcome.AlreadyAbsent
                OsConstants.ENOENT -> RemoveOutcome.Unresolvable
                else -> RemoveOutcome.Failed(parentErrno)
            }
        } else {
            RemoveOutcome.Failed(e.errno)
        }
    }

/**
 * What stat-ing [file]'s parent answered: null where it stats clean, and otherwise the errno,
 * whatever it was — the caller reads which errno rather than only whether there was one. Null too
 * where there is no parent to ask, which leaves it with the answer it gave before this check
 * existed.
 *
 * One `stat(2)`, paid only on the ENOENT branch: a node that was really there never reaches this,
 * and a tree something else emptied pays it once per node to keep the walk honest about which of
 * the two ENOENTs it met.
 *
 * `stat` rather than `lstat`, because the question is the one `remove(3)`'s own path resolution
 * asked: a parent that is a symlink to a directory that has gone is an unreachable path, not a
 * present one, and only the following form says so.
 */
private fun pathResolutionErrno(file: File): Int? {
    val parent = file.parent ?: return null

    return try {
        Os.stat(parent)
        null
    } catch (e: ErrnoException) {
        e.errno
    }
}
