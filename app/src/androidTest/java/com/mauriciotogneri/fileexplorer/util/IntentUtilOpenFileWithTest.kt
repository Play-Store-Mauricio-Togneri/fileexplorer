package com.mauriciotogneri.fileexplorer.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.recentFilesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStoreRecentFilesSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Covers how [IntentUtil.openFileWith] launches the chooser whatever the handler probe says.
 *
 * "Open with" asks whether any handler exists that this app may start, and reports a file with
 * none as an unopenable one — the chooser is the system's own, so it starts on an empty picker
 * without telling the app, and that dead end is invisible otherwise. The probe answers a question
 * *about* the launch and must never gate it: turning it into a guard would replace the platform's
 * picker with nothing on exactly the devices the measurement exists to find. Nothing in the
 * function's result says which way the probe went, so only a test holds that line.
 *
 * A [RecordingContext] overrides `startActivity` to record each attempted intent and swallow it,
 * so no real chooser opens during the test. Espresso Intents can't drive this: its stubs intercept
 * the launch, while what is under test is whether the launch is attempted at all.
 *
 * Documented assumptions:
 * - Both branches of the probe are covered by one pair of tests, each skipping when the device is
 *   not in the state it needs — a MIME type nothing handles, and one that something does. A device
 *   with no `text/plain` viewer runs the first only. The first is the one holding the line: the
 *   second passes whether or not the probe gates the launch, so on a device that registers a
 *   wildcard VIEW handler this file skips it and records nothing.
 * - The analytics row the probe emits is not observable from here, the same limit
 *   [IntentUtilOpenFileTest] records for `ErrorReporter`: `AnalyticsTracker` is an object calling
 *   `FirebaseAnalytics.getInstance()` directly. This covers the behaviour that must survive the
 *   emission, not the emission.
 * - A launched chooser counts as an open, so each test has `openFileWith` write a recents entry
 *   for its fixture, which `tearDown` removes: the suite shares one data directory across the
 *   orchestrator's per-test processes, so on-disk state a test persists is its own to undo.
 * - Fixtures name paths under `cacheDir` but create no files: `FileProvider.getUriForFile` only
 *   maps a path onto a `content://` URI and never touches the file system.
 * - `openFileWith` shows a Toast on its failure paths, so the call runs on the main thread.
 */
@RunWith(AndroidJUnit4::class)
class IntentUtilOpenFileWithTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private val recentFilesSource by lazy {
        DataStoreRecentFilesSource(
            instrumentation.targetContext.applicationContext.recentFilesDataStore
        )
    }

    /** Fixtures actually opened, and so written to recents — see [tearDown]. */
    private val trackedPaths = mutableListOf<String>()

    /**
     * Removes what the opens put in recents. `IntentUtil.trackRecentFile` writes from a
     * fire-and-forget coroutine, so the entry need not have landed by the time the test body
     * returns: removing straight away would run against a store not yet written and leave the
     * entry behind. Waiting for it first is what makes the cleanup deterministic; the timeout
     * bounds the case where the write never comes, which leaves the entry a later read drops
     * anyway. The source is read rather than the repository, whose reads filter out entries whose
     * file is gone — every fixture here.
     */
    @After
    fun tearDown() {
        val repository = RecentFilesRepository(recentFilesSource)

        runBlocking {
            trackedPaths.forEach { path ->
                withTimeoutOrNull(WRITE_TIMEOUT_MS) {
                    while (recentFilesSource.getRecentFiles().none { it.path == path }) {
                        delay(POLL_INTERVAL_MS)
                    }
                }
                repository.removeRecentFile(path)
            }
        }
        trackedPaths.clear()
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val launchedActions = mutableListOf<String?>()

        override fun startActivity(intent: Intent) {
            launchedActions += intent.action
            // Success: swallow so no real chooser opens during the test.
        }
    }

    private fun openFileWith(file: FileItem): Pair<RecordingContext, Boolean> {
        val context = RecordingContext(instrumentation.targetContext)
        trackedPaths += file.path
        var opened = false
        instrumentation.runOnMainSync {
            opened = IntentUtil.openFileWith(context, file, "test")
        }
        return context to opened
    }

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

    @Test
    fun noLaunchableHandler_stillLaunchesChooser() {
        val file = testFile("no-handler.bin", UNHANDLED_MIME_TYPE)
        assumeTrue("device handles the test MIME type", launchableHandlers(file) == 0)

        val (context, opened) = openFileWith(file)

        // The probe reports the file as unopenable and changes nothing: one chooser, as before.
        assertEquals(listOf(Intent.ACTION_CHOOSER), context.launchedActions)
        assertTrue("open with reported a failure the platform never signalled", opened)
    }

    @Test
    fun launchableHandler_launchesChooser() {
        val file = testFile("handled.txt", "text/plain")
        assumeTrue("device has no launchable text/plain handler", launchableHandlers(file) > 0)

        val (context, opened) = openFileWith(file)

        assertEquals(listOf(Intent.ACTION_CHOOSER), context.launchedActions)
        assertTrue("open with reported a failure the platform never signalled", opened)
    }

    private companion object {
        const val UNHANDLED_MIME_TYPE = "application/x-fileexplorer-test"
        const val WRITE_TIMEOUT_MS = 2_000L
        const val POLL_INTERVAL_MS = 25L
    }
}
