package com.mauriciotogneri.fileexplorer.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Deleting the MediaStore rows of files that are already gone is cleanup, not the operation the
 * user asked for, so a provider that refuses it must not fail the caller — and must not leave the
 * rows behind either.
 */
class MediaStoreUtilTest {

    private val contentResolver = mockk<ContentResolver>()
    private val deletedChunks = mutableListOf<List<Long>>()
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
    fun `a deleted tree drops the rows below it without enumerating them`() = runTest {
        stubRows(rowsPerPath = 3)

        MediaStoreUtil.notifyTreeDeleted(context, PATHS)

        // The path itself and a prefix pattern for its descendants, so deleting a directory does
        // not require the caller to enumerate the tree it just removed.
        // Two rounds per path, not "at least one": the productive round plus the empty one that
        // ends the loop. A count is what keeps a loop that re-queries for ever from passing.
        PATHS.forEach { path ->
            verify(exactly = 2) {
                contentResolver.query(any(), any(), any(), arrayOf(path, "$path/*"), any())
            }
        }
        // Every row the provider reported under the two trees, and by id: the rows are deleted in
        // chunks, so the prefix selection never reaches a delete.
        assertEquals(listOf(listOf(1L, 2L, 3L), listOf(4L, 5L, 6L)), deletedChunks)
        verifyNothingWasScanned()
        verifyNothingWasReported()
    }

    @Test
    fun `a tree with more rows than one delete may match is deleted in chunks`() = runTest {
        // The reported crash: one delete for the whole prefix makes the provider iterate a cursor
        // over every matching row while deleting rows from inside that iteration, and past the
        // first CursorWindow the refill asks a shrunken table for a row that is no longer there.
        stubRows(rowsPerPath = (2 * MAX_ROWS_PER_DELETE) + 1)

        MediaStoreUtil.notifyTreeDeleted(context, listOf(PATHS.first()))

        assertEquals(
            listOf(MAX_ROWS_PER_DELETE, MAX_ROWS_PER_DELETE, 1),
            deletedChunks.map { chunk -> chunk.size }
        )
        // Chunked, but still the whole tree: a row left behind is a gallery entry for a file that
        // is gone until the next full scan.
        assertEquals(
            (1L..((2 * MAX_ROWS_PER_DELETE) + 1)).toList(),
            deletedChunks.flatten()
        )
        verifyNothingWasScanned()
        verifyNothingWasReported()
    }

    @Test
    fun `a tree whose rows the provider keeps is handed to the scanner`() = runTest {
        // A delete that removes nothing would otherwise have the next round query the same ids for
        // ever. The scanner runs as the provider itself, so it can still reconcile them.
        stubRows(rowsPerPath = 3, removesRows = false, reportsDeletions = false)

        MediaStoreUtil.notifyTreeDeleted(context, listOf("/storage/emulated/0/DCIM/Trip"))

        // Once, then the repeated ids end it: the rows are stuck, and asking again cannot help.
        verify(exactly = 1) { contentResolver.delete(any(), any(), any()) }
        verify(exactly = 1) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf("/storage/emulated/0/DCIM/Trip", "/storage/emulated/0/DCIM"),
                null,
                null
            )
        }
        verify(exactly = 1) { ErrorReporter.warning(any(), "notify_media_store_deleted", any()) }
    }

    @Test
    fun `a provider that keeps rows while reporting them deleted is still bounded`() = runTest {
        // The count is not what the loop trusts: a provider that reports a chunk gone while
        // keeping it would otherwise be asked for the same ids until the process died.
        stubRows(rowsPerPath = 3, removesRows = false)

        MediaStoreUtil.notifyTreeDeleted(context, listOf("/storage/emulated/0/DCIM/Trip"))

        verify(exactly = 1) { contentResolver.delete(any(), any(), any()) }
        verify(exactly = 1) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf("/storage/emulated/0/DCIM/Trip", "/storage/emulated/0/DCIM"),
                null,
                null
            )
        }
        verify(exactly = 1) { ErrorReporter.warning(any(), "notify_media_store_deleted", any()) }
    }

    @Test
    fun `rows that go before the delete lands are not reported as kept`() = runTest {
        // A media scan — this app starts them itself — can drop the rows between the query and the
        // delete, and the provider then counts nothing deleted. Nothing is left behind, so this
        // must not spend a directory walk and a crash report on a tree that is already clean.
        stubRows(rowsPerPath = 3, reportsDeletions = false)

        MediaStoreUtil.notifyTreeDeleted(context, listOf("/storage/emulated/0/DCIM/Trip"))

        verify(exactly = 1) { contentResolver.delete(any(), any(), any()) }
        verifyNothingWasScanned()
        verifyNothingWasReported()
    }

    @Test
    fun `a tree the provider holds no rows for is not deleted`() = runTest {
        stubRows(rowsPerPath = 0)

        MediaStoreUtil.notifyTreeDeleted(context, PATHS)

        verify(exactly = 0) { contentResolver.delete(any(), any(), any()) }
        verifyNothingWasScanned()
        verifyNothingWasReported()
    }

    @Test
    fun `a provider that returns no cursor is handed to the scanner`() = runTest {
        // How a query reports the provider it could not reach — a delete on the same collection
        // throws `Unknown URL` instead. Read as an empty tree it would make the broken provider
        // this fallback exists for the one case that recovers nothing.
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null

        MediaStoreUtil.notifyTreeDeleted(context, listOf("/storage/emulated/0/DCIM/Trip"))

        verify(exactly = 0) { contentResolver.delete(any(), any(), any()) }
        verify(exactly = 1) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf("/storage/emulated/0/DCIM/Trip", "/storage/emulated/0/DCIM"),
                null,
                null
            )
        }
        verify(exactly = 1) { ErrorReporter.warning(any(), "notify_media_store_deleted", any()) }
    }

    @Test
    fun `a CursorWindow failure from the provider falls back to the scanner`() = runTest {
        // The reported failure, raised in the provider process: `IllegalStateException: Couldn't
        // read row 3629, col 0 from CursorWindow`.
        stubRows(rowsPerPath = 3)
        every { contentResolver.delete(any(), any(), any()) } throws
            IllegalStateException("Couldn't read row 3629, col 0 from CursorWindow")

        MediaStoreUtil.notifyTreeDeleted(context, listOf("/storage/emulated/0/DCIM/Trip"))

        verify(exactly = 1) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf("/storage/emulated/0/DCIM/Trip", "/storage/emulated/0/DCIM"),
                null,
                null
            )
        }
        verify(exactly = 1) { ErrorReporter.warning(any(), "notify_media_store_deleted", any()) }
    }

    @Test
    fun `a deleted tree is matched case-sensitively`() = runTest {
        // LIKE is case-insensitive for ASCII, so it would also match a sibling directory on a
        // case-sensitive volume — whose files are still on disk for the provider to unlink.
        stubRows(rowsPerPath = 1)

        MediaStoreUtil.notifyTreeDeleted(context, listOf("/storage/1234-5678/Foo"))

        verify(exactly = 2) {
            contentResolver.query(any(), any(), match { it.contains("GLOB") }, any(), any())
        }
        verify(exactly = 0) {
            contentResolver.query(any(), any(), match { it.contains("LIKE") }, any(), any())
        }
    }

    @Test
    fun `wildcards in a path are escaped so other directories are not purged`() = runTest {
        // `?` matches any single character and `[` opens a character class: unescaped, this prefix
        // would also match rows under `/storage/emulated/0/axb-dir`.
        stubRows(rowsPerPath = 1)

        MediaStoreUtil.notifyTreeDeleted(context, listOf("/storage/emulated/0/a?b[c]-dir"))

        verify(exactly = 2) {
            contentResolver.query(
                any(),
                any(),
                any(),
                arrayOf(
                    "/storage/emulated/0/a?b[c]-dir",
                    "/storage/emulated/0/a[?]b[[]c]-dir/*"
                ),
                any()
            )
        }
    }

    @Test
    fun `a literal asterisk in a path is escaped`() = runTest {
        stubRows(rowsPerPath = 1)

        MediaStoreUtil.notifyTreeDeleted(context, listOf("/storage/emulated/0/star*dir"))

        verify(exactly = 2) {
            contentResolver.query(
                any(),
                any(),
                any(),
                arrayOf("/storage/emulated/0/star*dir", "/storage/emulated/0/star[*]dir/*"),
                any()
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
    fun `a refused tree delete scans the parents so descendant rows go too`() = runTest {
        // Scanning a path that no longer exists only drops that path's own row, so handing the
        // scanner the deleted roots would leave every row beneath them behind until the next full
        // scan. Their parents are still on disk and a scanned directory is walked, which reaches
        // the whole missing subtree without this having to enumerate it.
        stubRows(rowsPerPath = 1)
        every { contentResolver.delete(any(), any(), any()) } throws
            IllegalArgumentException("Unknown URL content://media/external/file")

        MediaStoreUtil.notifyTreeDeleted(context, listOf("/storage/emulated/0/DCIM/Trip"))

        verify(exactly = 1) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf("/storage/emulated/0/DCIM/Trip", "/storage/emulated/0/DCIM"),
                null,
                null
            )
        }
    }

    @Test
    fun `a refused tree delete does not scan one parent twice`() = runTest {
        // A multi-selection is usually siblings, so the same parent would otherwise be walked once
        // per selected item — the expensive half of this recovery, repeated for no gain.
        stubRows(rowsPerPath = 1)
        every { contentResolver.delete(any(), any(), any()) } throws IllegalArgumentException("Unknown URL")

        MediaStoreUtil.notifyTreeDeleted(
            context,
            listOf("/storage/emulated/0/DCIM/Trip", "/storage/emulated/0/DCIM/Camera")
        )

        verify(exactly = 1) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(
                    "/storage/emulated/0/DCIM/Trip",
                    "/storage/emulated/0/DCIM/Camera",
                    "/storage/emulated/0/DCIM"
                ),
                null,
                null
            )
        }
    }

    @Test
    fun `a refused delete of exact paths still scans only those paths`() = runTest {
        // notifyDeleted reports files, not trees: nothing was removed below them, so walking their
        // parents would be a directory scan bought for nothing.
        every { contentResolver.delete(any(), any(), any()) } throws IllegalArgumentException("Unknown URL")

        MediaStoreUtil.notifyDeleted(context, PATHS)

        verify(exactly = 1) {
            MediaScannerConnection.scanFile(context, PATHS.toTypedArray(), null, null)
        }
    }

    @Test
    fun `a broken reporter does not take the media scanner with it`() = runTest {
        // ErrorReporter absorbs its own failures, but the recovery must not depend on that:
        // scanning is what actually drops the rows and has to run whatever the reporter does.
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
    fun `a scanner that refuses the files does not fail the caller`() = runTest {
        // scanFiles runs while an extraction is still writing, and the flow it is collected from
        // rolls the extraction back on any exception — so a broken scanner must not reach it.
        every { MediaScannerConnection.scanFile(any(), any(), any(), any()) } throws
            IllegalStateException("no scanner")

        MediaStoreUtil.scanFiles(context, PATHS)
    }

    @Test
    fun `nothing is deleted or scanned for an empty list`() = runTest {
        MediaStoreUtil.notifyDeleted(context, emptyList())

        verify(exactly = 0) { contentResolver.delete(any(), any(), any()) }
        verifyNothingWasScanned()
        verifyNothingWasReported()
    }

    /**
     * Stands in for the provider's rows under each deleted tree: a query hands back the ids that
     * are still there for the path it was given, and a delete drops the ids its selection names.
     *
     * [removesRows] and [reportsDeletions] are separate because the provider can part them: a
     * concurrent media scan takes the rows while the delete counts none, and a provider can as
     * easily report a chunk gone and keep it. Both are what the loop must stay bounded through.
     */
    private fun stubRows(
        rowsPerPath: Int,
        removesRows: Boolean = true,
        reportsDeletions: Boolean = true
    ) {
        val remaining = mutableMapOf<String, MutableList<Long>>()
        var nextId = 1L
        every { contentResolver.query(any(), any(), any(), any(), any()) } answers {
            val path = arg<Array<String>>(3).first()
            cursorOver(remaining.getOrPut(path) { MutableList(rowsPerPath) { nextId++ } })
        }
        every { contentResolver.delete(any(), any(), any()) } answers {
            val ids = deletedIdsIn(secondArg())
            deletedChunks += ids
            if (removesRows) {
                remaining.values.forEach { rows -> rows.removeAll(ids) }
            }
            if (reportsDeletions) ids.size else 0
        }
    }

    /**
     * The ids a delete names, read only from a selection of the shape the production code is
     * required to build. Scraping any digits out of it would let a delete against the wrong column
     * — or no `IN` at all — record the ids the assertions expect while the real provider dropped
     * nothing, which is the one mistake these tests exist to catch.
     */
    private fun deletedIdsIn(selection: String): List<Long> {
        val ids = DELETE_BY_ID.matchEntire(selection)?.groupValues?.get(1)
            ?: throw AssertionError("A tree's rows are deleted by id, not by `$selection`")

        return ids.split(",").map { it.toLong() }
    }

    private fun cursorOver(ids: List<Long>): Cursor {
        var position = -1
        return mockk {
            every { moveToNext() } answers { ++position < ids.size }
            every { getLong(0) } answers { ids[position] }
            every { close() } just Runs
        }
    }

    private fun verifyNothingWasScanned() {
        verify(exactly = 0) { MediaScannerConnection.scanFile(any(), any(), any(), any()) }
    }

    private fun verifyNothingWasReported() {
        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
    }

    private companion object {
        const val MEDIA_STORE_FILES_CLASS = "android.provider.MediaStore\$Files"

        /** Mirrors `MediaStoreUtil.MAX_ROWS_PER_DELETE`, which is private to it. */
        const val MAX_ROWS_PER_DELETE = 500
        val PATHS = listOf("/storage/emulated/0/Pictures/one.jpg", "/storage/emulated/0/two.mp4")
        val DELETE_BY_ID = Regex("_id IN \\((\\d+(?:,\\d+)*)\\)")
    }
}
