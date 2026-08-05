package com.mauriciotogneri.fileexplorer.util

import android.content.ContentResolver
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Deleting the MediaStore rows of files that are already gone is cleanup, not the operation the
 * user asked for, so a provider that refuses it must not fail the caller — and must not leave the
 * rows behind either.
 */
class MediaStoreUtilTest {

    private val contentResolver = mockk<ContentResolver>()
    private val context = mockk<Context> {
        every { contentResolver } returns this@MediaStoreUtilTest.contentResolver
    }

    @Before
    fun setUp() {
        mockkObject(ErrorReporter)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        mockkStatic(MEDIA_STORE_FILES_CLASS)
        every { MediaStore.Files.getContentUri(any()) } returns mockk<Uri>()
        mockkStatic(MediaScannerConnection::class)
        every { MediaScannerConnection.scanFile(any(), any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `every path is deleted from MediaStore`() = runTest {
        every { contentResolver.delete(any(), any(), any()) } returns 1

        MediaStoreUtil.notifyDeleted(context, PATHS)

        // Exactly the reported path: a renamed directory still has descendants on disk, and the
        // provider unlinks the file backing every row it drops.
        PATHS.forEach { path ->
            verify(exactly = 1) { contentResolver.delete(any(), any(), arrayOf(path)) }
        }
        verifyNothingWasScanned()
        verifyNothingWasReported()
    }

    @Test
    fun `a deleted tree drops the rows below it in one delete per path`() = runTest {
        every { contentResolver.delete(any(), any(), any()) } returns 1

        MediaStoreUtil.notifyTreeDeleted(context, PATHS)

        // The path itself and a prefix pattern for its descendants, so deleting a directory does
        // not require the caller to enumerate the tree it just removed.
        PATHS.forEach { path ->
            verify(exactly = 1) {
                contentResolver.delete(any(), any(), arrayOf(path, "$path/*"))
            }
        }
        verifyNothingWasScanned()
        verifyNothingWasReported()
    }

    @Test
    fun `a deleted tree is matched case-sensitively`() = runTest {
        // LIKE is case-insensitive for ASCII, so it would also match a sibling directory on a
        // case-sensitive volume — whose files are still on disk for the provider to unlink.
        every { contentResolver.delete(any(), any(), any()) } returns 1

        MediaStoreUtil.notifyTreeDeleted(context, listOf("/storage/1234-5678/Foo"))

        verify(exactly = 1) {
            contentResolver.delete(any(), match { it.contains("GLOB") }, any())
        }
        verify(exactly = 0) {
            contentResolver.delete(any(), match { it.contains("LIKE") }, any())
        }
    }

    @Test
    fun `wildcards in a path are escaped so other directories are not purged`() = runTest {
        // `?` matches any single character and `[` opens a character class: unescaped, this prefix
        // would also match rows under `/storage/emulated/0/axb-dir`.
        every { contentResolver.delete(any(), any(), any()) } returns 1

        MediaStoreUtil.notifyTreeDeleted(context, listOf("/storage/emulated/0/a?b[c]-dir"))

        verify(exactly = 1) {
            contentResolver.delete(
                any(),
                any(),
                arrayOf(
                    "/storage/emulated/0/a?b[c]-dir",
                    "/storage/emulated/0/a[?]b[[]c]-dir/*"
                )
            )
        }
    }

    @Test
    fun `a literal asterisk in a path is escaped`() = runTest {
        every { contentResolver.delete(any(), any(), any()) } returns 1

        MediaStoreUtil.notifyTreeDeleted(context, listOf("/storage/emulated/0/star*dir"))

        verify(exactly = 1) {
            contentResolver.delete(
                any(),
                any(),
                arrayOf("/storage/emulated/0/star*dir", "/storage/emulated/0/star[*]dir/*")
            )
        }
    }

    @Test
    fun `a provider that refuses the delete falls back to the media scanner`() = runTest {
        // The reported failure is `IllegalArgumentException: Unknown URL content://media/external/file`
        // from providers that do not accept a delete on the Files collection at all.
        every { contentResolver.delete(any(), any(), any()) } throws
            IllegalArgumentException("Unknown URL content://media/external/file")

        MediaStoreUtil.notifyDeleted(context, PATHS)

        // Every path, not just the one that failed: the rejection is for the collection, so the
        // paths after it would have been refused the same way.
        verify(exactly = 1) {
            MediaScannerConnection.scanFile(context, PATHS.toTypedArray(), null, null)
        }
        verify(exactly = 1) { ErrorReporter.warning(any(), "notify_media_store_deleted", any()) }
    }

    @Test
    fun `a broken reporter does not take the media scanner with it`() = runTest {
        // ErrorReporter.report calls FirebaseCrashlytics.getInstance() unguarded, so it throws when
        // Firebase never initialised. Scanning is the actual recovery and has to run regardless.
        every { contentResolver.delete(any(), any(), any()) } throws IllegalArgumentException("Unknown URL")
        every { ErrorReporter.warning(any(), any(), any()) } throws IllegalStateException("no reporter")

        MediaStoreUtil.notifyDeleted(context, PATHS)

        verify(exactly = 1) {
            MediaScannerConnection.scanFile(context, PATHS.toTypedArray(), null, null)
        }
    }

    @Test
    fun `a broken scanner does not suppress the report`() = runTest {
        every { contentResolver.delete(any(), any(), any()) } throws IllegalArgumentException("Unknown URL")
        every { MediaScannerConnection.scanFile(any(), any(), any(), any()) } throws
            IllegalStateException("no scanner")

        MediaStoreUtil.notifyDeleted(context, PATHS)

        verify(exactly = 1) { ErrorReporter.warning(any(), "notify_media_store_deleted", any()) }
    }

    @Test
    fun `a recovery that fails too is still absorbed`() = runTest {
        // Callers treat the cleanup as unable to fail; a scanner or a reporter that is itself
        // broken must not turn into the escape the fallback exists to prevent.
        every { contentResolver.delete(any(), any(), any()) } throws IllegalArgumentException("Unknown URL")
        every { ErrorReporter.warning(any(), any(), any()) } throws IllegalStateException("no reporter")
        every { MediaScannerConnection.scanFile(any(), any(), any(), any()) } throws
            IllegalStateException("no scanner")

        MediaStoreUtil.notifyDeleted(context, PATHS)
    }

    @Test
    fun `nothing is deleted or scanned for an empty list`() = runTest {
        MediaStoreUtil.notifyDeleted(context, emptyList())

        verify(exactly = 0) { contentResolver.delete(any(), any(), any()) }
        verifyNothingWasScanned()
        verifyNothingWasReported()
    }

    private fun verifyNothingWasScanned() {
        verify(exactly = 0) { MediaScannerConnection.scanFile(any(), any(), any(), any()) }
    }

    private fun verifyNothingWasReported() {
        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
    }

    private companion object {
        const val MEDIA_STORE_FILES_CLASS = "android.provider.MediaStore\$Files"
        val PATHS = listOf("/storage/emulated/0/Pictures/one.jpg", "/storage/emulated/0/two.mp4")
    }
}
