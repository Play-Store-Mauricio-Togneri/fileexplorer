package com.mauriciotogneri.fileexplorer.data.source

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Exercises [AndroidMediaChangeSource] against the device's real media provider.
 *
 * Nothing off the device can answer the only question that matters here: whether an observer
 * registered on the Files collection is actually told when a file appears in shared storage. The
 * write comes from the app under test, which drives the same cross-process provider notification
 * another app's write would. A provider notifies on the URI of the row that changed rather than on
 * the collection, so the registration has to ask for descendants — and a source that silently
 * observes nothing looks exactly like a quiet device.
 *
 * Reaching shared storage at all needs All Files Access, an app op that only exists from R, so
 * earlier releases are skipped rather than covered by a differently-shaped path.
 */
@RunWith(AndroidJUnit4::class)
class AndroidMediaChangeSourceTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        assumeTrue(
            "Reaching shared storage needs All Files Access, which only exists from R",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        )
        grantAllFilesAccess()
        assumeTrue(
            "All Files Access was not granted, so no external volume is visible",
            Environment.isExternalStorageManager()
        )
        assumeTrue(
            "Device has no mounted shared storage",
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        )
    }

    @After
    fun tearDown() {
        createdFiles.forEach { file -> runCatching { file.delete() } }
    }

    @Test
    fun changes_emitWhenAFileIsAddedToSharedStorage() = runBlocking {
        val source = AndroidMediaChangeSource(context)

        // Collected before the write, exactly as HomeViewModel's observer is: a ContentObserver
        // reports what happens after it registers, never what already did.
        val notified = async {
            withTimeout(NOTIFY_TIMEOUT_MS) { source.changes().first() }
        }
        // registerContentObserver runs inside that coroutine, so it has to reach its suspension
        // point before anything is written. delay rather than a blocking sleep, which would stop
        // runBlocking's event loop from ever starting it.
        delay(REGISTRATION_SETTLE_MS)

        writeAndScan("fe_media_change_${System.currentTimeMillis()}.txt")

        try {
            notified.await()
        } catch (e: TimeoutCancellationException) {
            throw AssertionError(
                "Writing to shared storage produced no media-change notification, so another " +
                    "app's writes would never invalidate the home screen's size cache",
                e
            )
        }
    }

    /**
     * The app's only storage permission above Q is All Files Access, which is an app op rather than
     * a runtime permission, so `GrantPermissionRule` cannot grant it.
     */
    private fun grantAllFilesAccess() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
            "appops set --uid ${context.packageName} MANAGE_EXTERNAL_STORAGE allow"
        ).close()
        repeat(ACCESS_POLL_ATTEMPTS) {
            if (Environment.isExternalStorageManager()) return
            Thread.sleep(ACCESS_POLL_INTERVAL_MS)
        }
    }

    /**
     * Writes a file and has it indexed, which is how the user's own files come to have a row — and
     * what makes the provider notify.
     */
    private fun writeAndScan(name: String) {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            name
        )
        file.parentFile?.mkdirs()
        file.writeText("content")
        createdFiles.add(file)

        val scanned = CountDownLatch(1)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("text/plain")
        ) { _, _ -> scanned.countDown() }
        scanned.await(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private companion object {
        const val ACCESS_POLL_ATTEMPTS = 20
        const val ACCESS_POLL_INTERVAL_MS = 100L
        const val SCAN_TIMEOUT_SECONDS = 10L
        const val NOTIFY_TIMEOUT_MS = 15_000L
        const val REGISTRATION_SETTLE_MS = 500L
    }
}
