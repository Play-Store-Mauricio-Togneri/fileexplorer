package com.mauriciotogneri.fileexplorer.data.util

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * A stand-in to report — or to attach as a cause — in place of [this], carrying what a triager
 * needs from a failure raised over the user's own file, and dropping what identifies the file.
 *
 * Almost everything this app fails on is built from the file it failed on:
 * [java.io.FileNotFoundException] and `SQLiteException` interpolate the absolute path,
 * `FileProvider.getUriForFile` interpolates `getCanonicalPath()`, `ZipException` interpolates the
 * entry name, and a metadata or XML parse failure can quote the file's contents.
 * `ErrorReporter.report` hands the throwable to `recordException`, which transmits the message
 * *and every cause below it* — so a catch clause that reports what it caught publishes a path. A
 * file name is personal data, this app can read every file on the device, and no catch clause can
 * see whether the throwable reaching it was built from one.
 *
 * Applied wherever the reported failure was raised over a user's file — every metadata extractor
 * and thumbnail fetcher, the viewers, the compress and delete paths, the four `getFileUri` catches
 * in `IntentUtil`, and the favorites and recent-files reads, whose hand-rolled JSON blob is a list
 * of the user's paths and whose `JSONException` quotes the whole input it failed on. Applied too
 * to `ItemInfoScreen.openGeoUri`, which is a `startActivity` catch but not one of the cases below:
 * its `Intent` is always the same `ACTION_VIEW` on a `geo:` URI, so the dump adds nothing a
 * triager needs and carries the photo's coordinates, which `Uri.toSafeString()` does not redact.
 *
 * Knowingly still raw, and not an oversight: the `startActivity` catches in `IntentUtil`, where
 * `ActivityNotFoundException` carries the whole `Intent` and so the file's content URI — the
 * `Intent` dump is what makes a missing-handler report actionable, so that trade is filed as
 * follow-up rather than decided here. The Coil thumbnail cache is excluded on its merits: its keys
 * are hashed before they reach the store, so its failures name the app's own cache directory.
 * `DataStoreSafeAccess` is excluded for the same reason — it reports failures *of* the store, not
 * of the blob inside it.
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
 * A cancellation keeps its type, because that is the one distinction anything downstream still
 * reads: [ErrorReporter.report] drops a [CancellationException] before it reaches
 * `recordException`. Collapsing it into the [IOException] carrier would defeat that filter, and a
 * cancelled read is the user leaving the screen rather than a failure — every catch clause that
 * reports what it caught would file a non-fatal per back-press. Scrubbed all the same, so the
 * carrier is never the object the caller was handed.
 *
 * Not for a failure the app raises itself with a message it controls — those already name an
 * operation and never a file, and reporting them directly keeps their type for grouping.
 */
internal fun Throwable.scrubbed(): Throwable =
    if (this is CancellationException) {
        CancellationException(javaClass.name).apply { stackTrace = this@scrubbed.stackTrace }
    } else {
        IOException(javaClass.name).apply { stackTrace = this@scrubbed.stackTrace }
    }
