package com.mauriciotogneri.fileexplorer.data.util

import java.io.IOException

/**
 * A stand-in to report — or to attach as a cause — in place of [this], carrying what a triager
 * needs from a failure raised over the user's own file, and dropping what identifies the file.
 *
 * Almost everything this app fails on is built from the file it failed on:
 * [java.io.FileNotFoundException] and `SQLiteException` interpolate the absolute path,
 * `FileProvider.getUriForFile` interpolates `getCanonicalPath()`, an [android.content.ActivityNotFoundException]
 * interpolates the whole `Intent`, and a metadata or XML parse failure can quote the file's
 * contents. `ErrorReporter.report` hands the throwable to `recordException`, which transmits the
 * message *and every cause below it* — so a catch clause that reports what it caught publishes a
 * path. A file name is personal data, this app can read every file on the device, and no catch
 * clause can see whether the throwable reaching it was built from one.
 *
 * What survives: the failing type, as the message — rebuilding an arbitrary subclass with a new
 * message is not possible, so a triager reads the type there instead — and the original stack
 * trace, so the report still points at the frame that threw rather than at the catch clause. What
 * does not: the message, and the whole cause chain, since the stand-in has no cause of its own.
 *
 * [IOException] is the carrier rather than a claim about the failure; the class it replaces is
 * named in the message. It is the carrier because the cause position ([FileRepository]'s transfer
 * wrappers) is typed that way, and one helper across both positions is what keeps a single
 * scrubbing idiom in the codebase.
 *
 * Not for a failure the app raises itself with a message it controls — those already name an
 * operation and never a file, and reporting them directly keeps their type for grouping.
 */
internal fun Throwable.scrubbed(): IOException =
    IOException(javaClass.name).apply { stackTrace = this@scrubbed.stackTrace }
