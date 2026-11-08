package com.mauriciotogneri.fileexplorer.testutil

import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry

/** `ErrorReporter`'s own log tag, which is private to it, hence the copy. */
private const val REPORTER_TAG = "ErrorReporter"
private const val PROBE_TAG = "NonFatalReportProbe"
private const val WINDOW_OPENED = "opened:"
private const val WINDOW_CLOSED = "closed:"
private const val DUMP_ATTEMPTS = 20
private const val DUMP_INTERVAL_MS = 100L

/**
 * Every non-fatal `ErrorReporter` filed while [block] ran, named by [case] in the failure it raises
 * when that cannot be determined.
 *
 * Crashlytics has nothing to read a report back from and mockk is not on the androidTest
 * classpath, so the `Log.e` a debug build makes in `ErrorReporter.report` is the only place a
 * report is observable from a device test. The window is bounded by markers written to the same
 * buffer from the same process, so logd orders them against anything the code under test logged,
 * and the dump is retried until the closing marker appears: a dump read before it lands could miss
 * a report that was filed and pass vacuously.
 * `ThumbnailFetcherRobustnessTest.aFiledNonFatal_isVisibleToTheseTests` is what proves the channel
 * carries a report at all.
 */
fun nonFatalsFiledWhile(case: String, block: () -> Unit): List<String> {
    val nonce = System.nanoTime().toString()
    Log.e(PROBE_TAG, "$WINDOW_OPENED$nonce")
    block()
    Log.e(PROBE_TAG, "$WINDOW_CLOSED$nonce")

    repeat(DUMP_ATTEMPTS) {
        val lines = logcat().split("\n")
        val opened = lines.indexOfFirst { it.contains("$WINDOW_OPENED$nonce") }
        val closed = lines.indexOfFirst { it.contains("$WINDOW_CLOSED$nonce") }
        if (opened >= 0 && closed > opened) {
            return lines.subList(opened + 1, closed).filter { it.contains(REPORTER_TAG) }
        }
        Thread.sleep(DUMP_INTERVAL_MS)
    }

    throw AssertionError(
        "logcat never showed the markers bounding $case, so whether a non-fatal was filed " +
            "cannot be read — and reading nothing must not count as reporting nothing"
    )
}

/**
 * The main log buffer, dumped through the shell — which, unlike this process, holds READ_LOGS —
 * and silenced down to the two tags involved by `-s`. The command is tokenised on whitespace
 * rather than run by a shell, so it can carry no quoting, no glob and no pipe.
 */
private fun logcat(): String {
    val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand("logcat -d -b main -s $REPORTER_TAG:E $PROBE_TAG:E")

    return ParcelFileDescriptor.AutoCloseInputStream(descriptor).use {
        it.readBytes().decodeToString()
    }
}
