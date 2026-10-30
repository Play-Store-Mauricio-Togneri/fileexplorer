package com.mauriciotogneri.fileexplorer.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.ReviewPromptRecord
import com.mauriciotogneri.fileexplorer.data.repository.reviewPromptDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStoreReviewPromptSource
import com.mauriciotogneri.fileexplorer.testutil.removeRecentFilesOnceWritten
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Covers the rating-ask hooks in [IntentUtil]: a file open that launched something counts once
 * towards [ReviewPrompt]'s ask, and one that reached a dead end counts nothing.
 *
 * The hooks change nothing the functions return, so these tests read the effect where it lands:
 * the action count in the app's own review store, written by the real [ReviewPrompt] the app under
 * test initialises on start-up. Each test starts from an empty record, and [tearDown] puts back the
 * one stored before it: the suite shares one data directory across the orchestrator's per-test
 * processes, so on-disk state a test persists is its own to undo.
 *
 * A [RecordingContext] swallows every launch, as in [IntentUtilOpenFileWithTest], so an app, the
 * chooser or the installer counts as launched without anything opening.
 *
 * Documented assumptions:
 * - The count is written from a fire-and-forget coroutine; [actionCountOnceWritten] says how long
 *   these tests wait for it, and how an open counted twice is caught.
 * - A failure only sets an in-memory flag that `ReviewPrompt` does not expose, so the failure test
 *   asserts that nothing was counted, not that the ask was blocked.
 * - The "Open with" cases follow [IntentUtilOpenFileWithTest]: the have-a-handler one **requires** a
 *   `text/plain` viewer and fails without one, and the no-handler one skips on a device that
 *   registers a wildcard VIEW handler. Which query answer counts is pinned for every case by the
 *   unit test `IntentUtilOpenWithReviewPromptTest`; these check that the real answer reaches it.
 * - A counted open also writes a recents entry, which [tearDown] removes.
 * - Fixtures name paths under `cacheDir` but create no files: `FileProvider.getUriForFile` only
 *   maps a path onto a `content://` URI and never touches the file system.
 * - The calls show a Toast on their failure paths, so they run on the main thread.
 */
@RunWith(AndroidJUnit4::class)
class IntentUtilReviewPromptTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val appContext get() = instrumentation.targetContext.applicationContext

    private val reviewPromptSource by lazy {
        DataStoreReviewPromptSource(appContext.reviewPromptDataStore)
    }

    /** The record stored before the test, which [tearDown] puts back. */
    private var storedRecord: ReviewPromptRecord? = null

    /** Fixtures actually opened, and so written to recents — see [tearDown]. */
    private val trackedPaths = mutableListOf<String>()

    @Before
    fun setUp() {
        runBlocking {
            storedRecord = reviewPromptSource.getRecord()
            assertNotNull("the review store could not be read", storedRecord)
            assertNotNull(
                "the review store could not be reset",
                reviewPromptSource.updateRecord { ReviewPromptRecord() }
            )
        }
    }

    /**
     * Puts back the review record, then removes what the opens put in recents. The recents entry is
     * written from a fire-and-forget coroutine too, so [removeRecentFilesOnceWritten] waits for it
     * before removing it.
     */
    @After
    fun tearDown() {
        runBlocking {
            storedRecord?.let { record -> reviewPromptSource.updateRecord { record } }
        }
        removeRecentFilesOnceWritten(trackedPaths)
        trackedPaths.clear()
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var launches = 0

        override fun startActivity(intent: Intent) {
            launches++
            // Success: swallow so no real app, chooser or installer opens during the test.
        }
    }

    /**
     * Runs [call] on [file] against a [RecordingContext], on the main thread. A launch is an open,
     * which also puts [file] in recents for [tearDown] to remove.
     */
    private fun open(file: FileItem, call: (Context, FileItem, String) -> Any): RecordingContext {
        val context = RecordingContext(instrumentation.targetContext)
        instrumentation.runOnMainSync { call(context, file, "test") }
        if (context.launches > 0) trackedPaths += file.path
        return context
    }

    /**
     * The stored action count once the call under test has had time to write it. The write comes
     * from a fire-and-forget coroutine, so this waits up to [WRITE_TIMEOUT_MS] for the count to
     * move off zero, then [SETTLE_MS] more, so that a second write — one open counted twice — lands
     * before the count is read. A count that never moves is read after the whole wait, which is how
     * a test that expects no write tells "none" from "not yet".
     */
    private fun actionCountOnceWritten(): Int? = runBlocking {
        withTimeoutOrNull(WRITE_TIMEOUT_MS) {
            while (actionCount() == 0) {
                delay(POLL_INTERVAL_MS)
            }
        }
        delay(SETTLE_MS)
        actionCount()
    }

    private suspend fun actionCount(): Int? = reviewPromptSource.getRecord()?.actionCount

    private fun testFile(name: String, mimeType: String): FileItem {
        val file = File(instrumentation.targetContext.cacheDir, name)

        return FileItem(
            path = file.absolutePath,
            name = name,
            isDirectory = false,
            size = 0L,
            lastModified = 0L,
            createdTime = 0L,
            mimeType = mimeType
        )
    }

    /** Handlers of a VIEW intent for [file] that this app holds the permission to start. */
    private fun launchableHandlers(file: FileItem): Int {
        val context = instrumentation.targetContext
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            File(file.path)
        )
        val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, file.mimeType) }

        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .count { candidate ->
                val permission = candidate.activityInfo?.permission
                permission == null ||
                    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            }
    }

    /** Whichever launch `openFile` settles on, direct or fallback, the context lets it succeed. */
    @Test
    fun openFile_launched_countsOneAction() {
        open(testFile("counted.txt", "text/plain"), IntentUtil::openFile)

        assertEquals("an open that launched an app must count once", 1, actionCountOnceWritten())
    }

    @Test
    fun openFileWith_launchableHandler_countsOneAction() {
        val file = testFile("counted-with.txt", "text/plain")
        // Asserted, not assumed: a skip on an image without a text/plain viewer would report green
        // over the only case in which an "Open with" counts. A failure here names the device.
        assertTrue(
            "This device has no launchable text/plain VIEW handler, so the handler query cannot " +
                "find one. Run on an image that ships one.",
            launchableHandlers(file) > 0
        )

        open(file, IntentUtil::openFileWith)

        assertEquals("an open with an app to offer must count once", 1, actionCountOnceWritten())
    }

    @Test
    fun openFileWith_noLaunchableHandler_countsNothing() {
        val file = testFile("not-counted.bin", UNHANDLED_MIME_TYPE)
        assumeTrue("device handles the test MIME type", launchableHandlers(file) == 0)

        open(file, IntentUtil::openFileWith)

        // The chooser launched, but on an empty picker: a dead end, not a success.
        assertEquals("an open with nothing to offer must not count", 0, actionCountOnceWritten())
    }

    @Test
    fun installApk_launched_countsOneAction() {
        open(testFile("counted.apk", APK_MIME_TYPE), IntentUtil::installApk)

        assertEquals(
            "an install that launched the installer must count once",
            1,
            actionCountOnceWritten()
        )
    }

    /**
     * An APK whose path cannot become a content URI never reaches the installer. The provider's
     * `root-path` covers every path below `/` but not `/` itself, so the root is a path
     * `FileProvider.getUriForFile` refuses on any device.
     */
    @Test
    fun installApk_pathWithoutContentUri_countsNothing() {
        val file = testFile("unreachable.apk", APK_MIME_TYPE).copy(path = "/")

        val context = open(file, IntentUtil::installApk)

        assertEquals("the URI should have failed before any launch", 0, context.launches)
        // The failure also blocks the ask, through a flag held only in memory; the count is what
        // can be read.
        assertEquals("a failed install must not count", 0, actionCountOnceWritten())
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val UNHANDLED_MIME_TYPE = "application/x-fileexplorer-test"
        const val WRITE_TIMEOUT_MS = 2_000L
        const val SETTLE_MS = 500L
        const val POLL_INTERVAL_MS = 25L
    }
}
