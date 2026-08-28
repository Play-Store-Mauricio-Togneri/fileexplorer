package com.mauriciotogneri.fileexplorer.util

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Covers how [IntentUtil.shareFiles] reacts to a selection too large for a Binder transaction.
 *
 * Every shared URI travels inside the chooser intent, so a big enough selection overflows the 1 MB
 * buffer the process gets for its in-flight transactions and the platform kills the launch with a
 * `TransactionTooLargeException` — the report that motivated the guard carried 1,549,852 bytes.
 * `shareFiles` measures the chooser first and refuses instead, so the user gets a message asking
 * for a smaller selection rather than a dead end.
 *
 * A [RecordingContext] overrides `startActivity` to record each attempted intent and to swallow it,
 * so no real app opens during the test. Espresso Intents can't drive this: its stubs intercept the
 * launch, while what is under test is whether the launch is attempted at all.
 *
 * Documented assumptions:
 * - Fixtures name paths under `cacheDir` but create no files: `FileProvider.getUriForFile` only
 *   maps a path onto a `content://` URI and never touches the file system.
 * - `shareFiles` shows a Toast on the refusal path, so the call runs on the main thread.
 */
@RunWith(AndroidJUnit4::class)
class IntentUtilShareTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val launchedActions = mutableListOf<String?>()

        override fun startActivity(intent: Intent) {
            launchedActions += intent.action
            // Success: swallow so no real app opens during the test.
        }
    }

    private fun shareFiles(files: List<FileItem>): Pair<RecordingContext, Boolean> {
        val context = RecordingContext(instrumentation.targetContext)
        var launched = false
        instrumentation.runOnMainSync {
            launched = IntentUtil.shareFiles(context, files)
        }
        return context to launched
    }

    private fun testFile(name: String): FileItem {
        val file = File(instrumentation.targetContext.cacheDir, name)

        return FileItem(
            path = file.absolutePath,
            name = name,
            isDirectory = false,
            size = 0L,
            lastModified = 0L,
            createdTime = 0L,
            mimeType = "text/plain"
        )
    }

    @Test
    fun multipleFiles_launchesChooser() {
        val (context, launched) = shareFiles(
            listOf(testFile("share-a.txt"), testFile("share-b.txt"))
        )

        assertTrue("share was refused", launched)
        assertEquals(listOf(Intent.ACTION_CHOOSER), context.launchedActions)
    }

    /**
     * The selection is sized past the guard's budget through the file names, so the assertion does
     * not depend on where `cacheDir` sits on the device.
     */
    @Test
    fun selectionOverTransactionBudget_isRefusedWithoutLaunching() {
        val padding = "x".repeat(LONG_NAME_LENGTH)
        val files = (1..OVERSIZED_SELECTION).map { testFile("share-$it-$padding.txt") }

        val (context, launched) = shareFiles(files)

        assertFalse("share was launched", launched)
        assertEquals(emptyList<String?>(), context.launchedActions)
    }

    private companion object {
        /** 2,000 URIs, each a padded name on top of the provider path: over 1 MB of payload. */
        const val OVERSIZED_SELECTION = 2_000
        const val LONG_NAME_LENGTH = 180
    }
}
