package com.mauriciotogneri.fileexplorer.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
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
 * Covers the [SecurityException] retry in [IntentUtil.openFile]. Some ROMs resolve a file to a
 * handler the app is not allowed to start — the report that motivated this code resolved a file
 * with an unknown extension (wildcard MIME type) to `com.android.apps.tag/.TagViewer`, which
 * requires `android.permission.NFC` — and the platform denies the direct launch. The app then
 * retries through the system chooser, which starts the target as the system rather than as the app.
 *
 * A [RecordingContext] overrides `startActivity` to record each attempted intent and throw
 * `SecurityException` for chosen MIME types, mirroring those ROMs without needing one. Espresso
 * Intents can't drive this: its stubs intercept the launch before resolution, so the primary
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
        private val deniedTypes: Set<String>
    ) : ContextWrapper(base) {
        val launchedActions = mutableListOf<String?>()

        override fun startActivity(intent: Intent) {
            launchedActions += intent.action
            if (intent.type in deniedTypes) {
                throw SecurityException("Permission Denial: starting ${intent.type}")
            }
            // Success: swallow so no real app opens during the test.
        }
    }

    private fun openFile(file: FileItem, deniedTypes: Set<String>): RecordingContext {
        val context = RecordingContext(instrumentation.targetContext, deniedTypes)
        instrumentation.runOnMainSync {
            IntentUtil.openFile(context, file, "test")
        }
        return context
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

        val context = openFile(file, deniedTypes = setOf("text/plain"))

        assertEquals(
            listOf(Intent.ACTION_VIEW, Intent.ACTION_CHOOSER),
            context.launchedActions
        )
    }

    @Test
    fun noHandlerForType_fallsBackToUntypedViewWithoutChooser() {
        val file = testFile("no-handler.bin", UNHANDLED_MIME_TYPE)
        assumeTrue("device handles the test MIME type", launchableHandlers(file) == 0)

        val context = openFile(file, deniedTypes = setOf(UNHANDLED_MIME_TYPE))

        // Nothing resolves the typed intent, so it is never attempted: the untyped fallback runs
        // on its own and no chooser is involved.
        assertEquals(listOf(Intent.ACTION_VIEW), context.launchedActions)
    }

    private companion object {
        const val UNHANDLED_MIME_TYPE = "application/x-fileexplorer-test"
    }
}
