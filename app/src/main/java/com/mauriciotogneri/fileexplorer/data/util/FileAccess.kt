package com.mauriciotogneri.fileexplorer.data.util

import android.system.ErrnoException
import android.system.OsConstants
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
 * EIO and ENODEV/ENXIO are the volume: removable storage unmounted mid-walk. Skipping those would
 * drop every remaining file and still report the operation as a success.
 *
 * The errno is read from the field rather than matched in the message for the reason
 * [isNoSpaceLeft] gives, and that is also what cannot be exercised off-device: the stubbed
 * `android.jar` cannot construct an [ErrnoException], and `errno` is a public final field rather
 * than something a mock can intercept. `FileAccessTest` covers the mapping on a device; the
 * branches that never reach an [ErrnoException] are covered on the JVM by `FileAccessCauseChainTest`
 * — and, because false is the answer that keeps a walk going, the repository's own JVM tests run
 * this function for real rather than stubbing it.
 *
 * False when the chain carries no [ErrnoException]. Two things can produce that: a platform that
 * did not attach one — `FileInputStream` throws a causeless [FileNotFoundException] only for a path
 * containing NUL, which `File.list()` cannot produce — and the JVM, where there is no such class.
 * Both mean "keep going", which is what this app did for every open failure before this function
 * existed.
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
                    it.errno == OsConstants.EIO ||
                        it.errno == OsConstants.ENODEV ||
                        it.errno == OsConstants.ENXIO
                    )
        }
