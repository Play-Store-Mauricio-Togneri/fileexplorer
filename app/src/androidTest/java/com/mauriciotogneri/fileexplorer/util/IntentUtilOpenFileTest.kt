package com.mauriciotogneri.fileexplorer.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.util.AndroidRuntimeException
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Covers how [IntentUtil.openFile] reacts when the platform refuses a launch it had resolved.
 *
 * A denial ([SecurityException]) is retried through the system chooser: some ROMs resolve a file to
 * a handler the app is not allowed to start — the report that motivated this code resolved a file
 * with an unknown extension (wildcard MIME type) to `com.android.apps.tag/.TagViewer`, which
 * requires `android.permission.NFC` — and the chooser starts the target as the system rather than
 * as the app. A cancelled launch ([AndroidRuntimeException]) gets no retry: nothing about going
 * through the chooser would change the outcome, so the file falls through to the in-app viewer.
 *
 * A [RecordingContext] overrides `startActivity` to record each attempted intent and to inject
 * either fault for chosen MIME types, mirroring those ROMs without needing one. Espresso Intents
 * can't drive this: its stubs intercept the launch before resolution, so the primary
 * `startActivity` never throws.
 *
 * Documented assumptions:
 * - The retry only happens when the device has a handler the app may actually launch, so the
 *   chooser test is skipped on devices without a `text/plain` viewer. The opposite branch — the
 *   denied component being the only handler, where the chooser would come up empty and the caller
 *   must fall through to the in-app viewer instead — needs a permission-guarded handler installed,
 *   so it is verified manually rather than here.
 * - `openFile` reports recent files and analytics, and shows a Toast when nothing can open the
 *   file, so the call runs on the main thread (Toast requires a Looper).
 */
@RunWith(AndroidJUnit4::class)
class IntentUtilOpenFileTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private class RecordingContext(
        base: Context,
        private val deniedTypes: Set<String?>,
        private val cancelledTypes: Set<String?>
    ) : ContextWrapper(base) {
        val launchedActions = mutableListOf<String?>()

        override fun startActivity(intent: Intent) {
            launchedActions += intent.action
            if (intent.type in deniedTypes) {
                throw SecurityException("Permission Denial: starting ${intent.type}")
            }
            if (intent.type in cancelledTypes) {
                throw AndroidRuntimeException("Activity could not be started for $intent")
            }
            // Success: swallow so no real app opens during the test.
        }
    }

    private fun openFile(
        file: FileItem,
        deniedTypes: Set<String?> = emptySet(),
        cancelledTypes: Set<String?> = emptySet()
    ): Pair<RecordingContext, OpenFileResult> {
        val context = RecordingContext(instrumentation.targetContext, deniedTypes, cancelledTypes)
        lateinit var result: OpenFileResult
        instrumentation.runOnMainSync {
            result = IntentUtil.openFile(context, file, "test")
        }
        return context to result
    }

    private fun testFile(name: String, mimeType: String): FileItem {
        val file = File(instrumentation.targetContext.cacheDir, name)
        file.writeText("test")

        return FileItem(
            path = file.absolutePath,
            name = name,
            isDirectory = false,
            size = file.length(),
            lastModified = file.lastModified(),
            createdTime = file.lastModified(),
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
    fun launchDeniedWithOtherHandlers_retriesThroughChooser() {
        val file = testFile("chooser-retry.txt", "text/plain")
        assumeTrue("device has no launchable text/plain handler", launchableHandlers(file) > 0)

        val (context, _) = openFile(file, deniedTypes = setOf("text/plain"))

        assertEquals(
            listOf(Intent.ACTION_VIEW, Intent.ACTION_CHOOSER),
            context.launchedActions
        )
    }

    @Test
    fun noHandlerForType_fallsBackToUntypedViewWithoutChooser() {
        val file = testFile("no-handler.bin", UNHANDLED_MIME_TYPE)
        assumeTrue("device handles the test MIME type", launchableHandlers(file) == 0)

        val (context, _) = openFile(file, deniedTypes = setOf(UNHANDLED_MIME_TYPE))

        // Nothing resolves the typed intent, so it is never attempted: the untyped fallback runs
        // on its own and no chooser is involved.
        assertEquals(listOf(Intent.ACTION_VIEW), context.launchedActions)
    }

    /**
     * The platform resolving a handler for the untyped fallback and then cancelling the launch —
     * `START_CANCELED`, reported as a bare [AndroidRuntimeException]. It is a dead end like an
     * unresolved intent, so no chooser is attempted and the file falls through to the in-app
     * viewer.
     *
     * This pins the fall-through contract, not the catch clause that carries it: the clause exists
     * to keep the cancelled launch out of Crashlytics, and whether it reported is not observable
     * from here ([com.mauriciotogneri.fileexplorer.data.util.ErrorReporter] is an object calling
     * `FirebaseCrashlytics.getInstance()` directly).
     */
    @Test
    fun untypedFallbackCancelled_fallsThroughToTextViewer() {
        val file = testFile("cancelled.txt", UNHANDLED_MIME_TYPE)
        assumeTrue("device handles the test MIME type", launchableHandlers(file) == 0)

        val (context, result) = openFile(file, cancelledTypes = setOf(null))

        assertEquals(listOf(Intent.ACTION_VIEW), context.launchedActions)
        assertEquals(OpenFileResult.RequiresTextViewer(file), result)
    }

    private companion object {
        const val UNHANDLED_MIME_TYPE = "application/x-fileexplorer-test"
    }
}
