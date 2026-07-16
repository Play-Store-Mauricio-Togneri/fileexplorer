package com.mauriciotogneri.fileexplorer.data.util

import android.system.ErrnoException
import android.system.OsConstants
import java.io.IOException

private const val MAX_CAUSE_CHAIN_DEPTH = 10

/**
 * Reports whether a failure was caused by the device running out of storage. A full disk surfaces as
 * an [IOException] whose cause is an [ErrnoException] for ENOSPC, because `IoBridge` rethrows the
 * errno failure via `ErrnoException.rethrowAsIOException`, which keeps the original as the cause.
 *
 * The errno is read from the field rather than matched in the message so that a path which happens
 * to contain the token — a file named `ENOSPC`, say — cannot be mistaken for a full disk.
 *
 * The walk is depth-bounded so that a cyclic cause chain cannot hang the caller.
 */
internal fun Throwable.isNoSpaceLeft(): Boolean =
    generateSequence(this) { it.cause }
        .take(MAX_CAUSE_CHAIN_DEPTH)
        .any { it is ErrnoException && it.errno == OsConstants.ENOSPC }
