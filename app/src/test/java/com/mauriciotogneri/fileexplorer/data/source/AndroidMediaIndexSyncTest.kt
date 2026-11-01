package com.mauriciotogneri.fileexplorer.data.source

import android.content.ContentResolver
import android.database.Cursor
import android.media.MediaScannerConnection
import android.mtp.MtpConstants
import android.net.Uri
import android.provider.MediaStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mauriciotogneri.fileexplorer.data.source.AndroidMediaIndexSync.IndexedRow
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException

/**
 * Each sync runs on a child of `backgroundScope` that stands for one app process: the worker waits
 * for its next entry for ever, which would keep a test's own scope from finishing, and cancelling
 * the child is the process being killed. `runCurrent` drives it, since `advanceUntilIdle` stops
 * once only background work is left.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidMediaIndexSyncTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val root: File get() = temporaryFolder.root

    private val store = FakeInMemoryDataStore()

    /** Every scanner call, oldest first. */
    private val scanned = mutableListOf<List<String>>()

    /** When set, every scanner call waits for it, which holds the worker inside an entry. */
    private var scanGate: CompletableDeferred<Unit>? = null

    /** The paths of the index rows at and under each root, as the provider would report them. */
    private val indexRows = mutableMapOf<String, List<String>>()

    /** The paths in [indexRows] the provider marks as folders. */
    private val indexedFolders = mutableSetOf<String>()
    private val lookedUp = mutableListOf<String>()

    /** Pages [indexRows] the way the provider lookup does, numbering each root's rows from 1. */
    private val fakeIndexedRows: (String, Long, Int) -> List<IndexedRow> = { indexedRoot, afterId, limit ->
        lookedUp += indexedRoot
        indexRows[indexedRoot].orEmpty()
            .mapIndexed { index, path -> IndexedRow(index + 1L, path, isFolder = path in indexedFolders) }
            .filter { row -> row.id > afterId }
            .take(limit)
    }

    private var mounted: (String) -> Boolean = { true }
    private val mountChecks = mutableListOf<String>()

    @Before
    fun setUp() {
        mockkObject(ErrorReporter)
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { ErrorReporter.error(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun TestScope.launchSync(
        sdkInt: Int = 29,
        dataStore: DataStore<Preferences> = store,
        process: CoroutineScope = newProcess(),
        indexedRows: (String, Long, Int) -> List<IndexedRow> = fakeIndexedRows
    ): AndroidMediaIndexSync = AndroidMediaIndexSync(
        sdkInt = sdkInt,
        dataStore = dataStore,
        scope = process,
        scan = { paths ->
            scanned.add(paths)
            scanGate?.await()
        },
        indexedRows = indexedRows,
        isMounted = { path ->
            mountChecks += path
            mounted(path)
        }
    ).also { it.start() }

    private fun TestScope.newProcess(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))

    private fun file(relativePath: String): File = File(root, relativePath).apply {
        parentFile?.mkdirs()
        writeText("content")
    }

    private suspend fun savedQueue(): String? = store.data.first()[KEY_QUEUE]

    // ---------- Android version ---------- \\

    @Test
    fun `on Android 11 and later an operation queues nothing and touches nothing`() = runTest {
        val photo = file("DCIM/photo.jpg")
        val sync = launchSync(sdkInt = 30)

        sync.afterOperation(created = listOf(photo.path), removed = listOf("${root.path}/old.jpg"))
        runCurrent()

        assertEquals(emptyList<List<String>>(), scanned)
        assertEquals(emptyList<String>(), lookedUp)
        assertEquals(emptyList<String>(), mountChecks)
        assertNull(savedQueue())
    }

    @Test
    fun `on Android 11 and later start clears a queue saved before the OS upgrade`() = runTest {
        val card = file("card/photo.jpg")
        mounted = { false }
        val beforeUpgrade = newProcess()
        launchSync(sdkInt = 29, process = beforeUpgrade)
            .afterOperation(created = listOf(card.path), removed = emptyList())
        runCurrent()
        assertNotNull(savedQueue())
        beforeUpgrade.cancel()

        mounted = { true }
        launchSync(sdkInt = 30)
        runCurrent()

        assertNull(savedQueue())
        assertEquals(emptyList<List<String>>(), scanned)
    }

    @Test
    fun `on Android 7 to 10 an operation stays saved until its sync has finished`() = runTest {
        val photo = file("DCIM/photo.jpg")
        val gate = CompletableDeferred<Unit>().also { scanGate = it }
        val sync = launchSync(sdkInt = 24)

        sync.afterOperation(created = listOf(photo.path), removed = emptyList())
        runCurrent()

        assertEquals(listOf(listOf(photo.path)), scanned)
        assertNotNull(savedQueue())

        gate.complete(Unit)
        runCurrent()

        assertNull(savedQueue())
    }

    @Test
    fun `an operation that touched nothing queues nothing`() = runTest {
        val sync = launchSync()
        runCurrent()
        val writesAtStart = store.writeCount

        sync.afterOperation(created = emptyList(), removed = emptyList())
        runCurrent()

        assertEquals(writesAtStart, store.writeCount)
        assertEquals(emptyList<String>(), mountChecks)
    }

    // ---------- Created roots ---------- \\

    @Test
    fun `created roots have every file under them scanned and links skipped`() = runTest {
        val photo = file("Trip/photo.jpg")
        val video = file("Trip/day 2/video.mp4")
        val raw = file("Trip/day 2/raw/shot.dng")
        val elsewhere = file("Elsewhere/private.jpg")
        Files.createSymbolicLink(File(root, "Trip/linked folder").toPath(), elsewhere.parentFile!!.toPath())
        Files.createSymbolicLink(File(root, "Trip/linked.jpg").toPath(), elsewhere.toPath())
        val archive = file("Trip.zip")
        val sync = launchSync()

        sync.afterOperation(
            created = listOf(File(root, "Trip").path, archive.path, File(root, "never written").path),
            removed = emptyList()
        )
        runCurrent()

        // Files only, each once: neither a folder nor anything reached through a link.
        val paths = scanned.flatten()
        assertEquals(setOf(photo.path, video.path, raw.path, archive.path), paths.toSet())
        assertEquals(4, paths.size)
    }

    @Test
    fun `created files are scanned 500 paths per scanner call`() = runTest {
        val files = (1..1001).map { file("Camera/IMG_$it.jpg") }
        val sync = launchSync()

        sync.afterOperation(created = listOf(File(root, "Camera").path), removed = emptyList())
        runCurrent()

        assertEquals(listOf(500, 500, 1), scanned.map { it.size })
        assertEquals(files.map { it.path }.toSet(), scanned.flatten().toSet())
    }

    // ---------- Removed roots ---------- \\

    @Test
    fun `a removed root scans only the index rows whose file is gone`() = runTest {
        val trip = File(root, "Trip").path
        // Written at an old path after the operation: its row now describes a file that is there.
        val back = file("Trip/back.jpg")
        indexRows[trip] = listOf(trip, "$trip/gone.jpg", "$trip/day 2/gone.mp4", back.path)
        val sync = launchSync()

        sync.afterOperation(created = emptyList(), removed = listOf(trip))
        runCurrent()

        assertEquals(listOf(trip), lookedUp)
        assertEquals(listOf(listOf("$trip/gone.jpg", "$trip/day 2/gone.mp4")), scanned)
    }

    @Test
    fun `on Android 10 gone folder rows are never scanned, so a folder whose name starts with theirs keeps its rows`() = runTest {
        // Android 10's scanner, handed a path that is gone, drops every row whose path starts with
        // it, with no folder boundary: scanning the gone `Trip` would drop the rows of `Trip 2`,
        // indexed a moment earlier by the same rename.
        val trip = File(root, "Trip").path
        val renamed = file("Trip 2/photo.jpg")
        indexRows[trip] = listOf(trip, "$trip/photo.jpg", "$trip/day 2", "$trip/day 2/clip.mp4")
        indexedFolders += listOf(trip, "$trip/day 2")
        val sync = launchSync(sdkInt = 29)

        sync.afterOperation(created = listOf(renamed.parentFile!!.path), removed = listOf(trip))
        runCurrent()

        assertEquals(listOf(listOf(renamed.path, "$trip/photo.jpg", "$trip/day 2/clip.mp4")), scanned)
    }

    @Test
    fun `on Android 7 to 9 gone folder rows are scanned too, whose scanner drops only the exact path`() = runTest {
        val trip = File(root, "Trip").path
        indexRows[trip] = listOf(trip, "$trip/photo.jpg", "$trip/day 2", "$trip/day 2/clip.mp4")
        indexedFolders += listOf(trip, "$trip/day 2")
        val sync = launchSync(sdkInt = 28)

        sync.afterOperation(created = emptyList(), removed = listOf(trip))
        runCurrent()

        assertEquals(listOf(indexRows.getValue(trip)), scanned)
    }

    @Test
    fun `a removed root reaches the provider through a query and never a delete`() = runTest {
        // The provider can unlink the file behind a row it removes, so the sync only ever scans.
        val contentResolver = mockk<ContentResolver>()
        stubFilesUri()
        val trip = File(root, "Trip").path
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns
            cursorOver(listOf(trip, "$trip/gone.jpg"))
        every { contentResolver.delete(any(), any(), any()) } returns 0
        every { contentResolver.update(any(), any(), any(), any()) } returns 0
        val sync = launchSync(
            indexedRows = { indexedRoot, afterId, limit ->
                AndroidMediaIndex.rowsAtAndUnder(contentResolver, indexedRoot, afterId, limit)
            }
        )

        sync.afterOperation(created = emptyList(), removed = listOf(trip))
        runCurrent()

        assertEquals(listOf(listOf(trip, "$trip/gone.jpg")), scanned)
        verify(exactly = 0) { contentResolver.delete(any(), any(), any()) }
        verify(exactly = 0) { contentResolver.update(any(), any(), any(), any()) }
        verify(exactly = 0) { ErrorReporter.warning(any(), any(), any()) }
    }

    @Test
    fun `a removed root with more rows than a page is read a page at a time, each after the last`() = runTest {
        val trip = File(root, "Trip").path
        indexRows[trip] = List(1_200) { index -> "$trip/gone-$index.jpg" }
        val pagesAfter = mutableListOf<Long>()
        val sync = launchSync(
            indexedRows = { indexedRoot, afterId, limit ->
                pagesAfter += afterId
                fakeIndexedRows(indexedRoot, afterId, limit)
            }
        )

        sync.afterOperation(created = emptyList(), removed = listOf(trip))
        runCurrent()

        assertEquals(listOf(0L, 500L, 1_000L), pagesAfter)
        assertEquals(listOf(500, 500, 200), scanned.map { it.size })
        assertEquals(indexRows.getValue(trip), scanned.flatten())
    }

    // ---------- Saved queue ---------- \\

    @Test
    fun `a queue the process died in the middle of resumes on the next launch`() = runTest {
        // Names a hand-rolled format could trip on: line breaks, tabs and common separators.
        val created = file("Photos/a, b; c\n[d]|e:f.jpg")
        val removed = File(root, "Old\tAlbum").path
        indexRows[removed] = listOf("$removed/0", "$removed/x\ny.jpg")
        scanGate = CompletableDeferred()
        val firstLaunch = newProcess()
        val sync = launchSync(process = firstLaunch)

        sync.afterOperation(created = listOf(created.path), removed = emptyList())
        sync.afterOperation(created = emptyList(), removed = listOf(removed))
        runCurrent()
        // Killed while the first entry was still being scanned.
        firstLaunch.cancel()
        scanned.clear()
        scanGate = null

        launchSync()
        runCurrent()

        assertEquals(listOf(listOf(created.path), listOf("$removed/0", "$removed/x\ny.jpg")), scanned)
        assertNull(savedQueue())
    }

    @Test
    fun `a saved queue that does not parse reads as empty and is reported without its contents`() = runTest {
        val corrupt = FakeInMemoryDataStore(
            preferencesOf(KEY_QUEUE to "/storage/emulated/0/Documents/tax-return.pdf")
        )
        val reported = slot<Throwable>()
        every { ErrorReporter.error(capture(reported), any(), any()) } just Runs
        val photo = file("DCIM/photo.jpg")
        val sync = launchSync(dataStore = corrupt)
        runCurrent()

        assertEquals(emptyList<List<String>>(), scanned)
        assertFalse(reported.captured.message.orEmpty().contains("tax-return"))
        assertNull(reported.captured.cause)

        sync.afterOperation(created = listOf(photo.path), removed = emptyList())
        runCurrent()

        assertEquals(listOf(listOf(photo.path)), scanned)
    }

    @Test
    fun `a store that can be neither read nor written still syncs this launch's operations`() = runTest {
        val photo = file("DCIM/photo.jpg")
        val sync = launchSync(dataStore = FakeThrowingDataStore())

        sync.afterOperation(created = listOf(photo.path), removed = emptyList())
        runCurrent()

        assertEquals(listOf(listOf(photo.path)), scanned)
    }

    // ---------- Order and failures ---------- \\

    @Test
    fun `entries run one at a time in the order they were queued`() = runTest {
        val first = file("first.jpg")
        val second = file("second.jpg")
        val third = file("third.jpg")
        val gate = CompletableDeferred<Unit>().also { scanGate = it }
        val sync = launchSync()

        listOf(first, second, third).forEach { file ->
            sync.afterOperation(created = listOf(file.path), removed = emptyList())
        }
        runCurrent()

        assertEquals(listOf(listOf(first.path)), scanned)

        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf(listOf(first.path), listOf(second.path), listOf(third.path)), scanned)
    }

    @Test
    fun `a failing entry is reported without its paths and dropped, and the next one still runs`() = runTest {
        val photo = file("photo.jpg")
        val reported = slot<Throwable>()
        every { ErrorReporter.warning(capture(reported), any(), any()) } just Runs
        val sync = launchSync(indexedRows = { failedRoot, _, _ -> error("Could not query $failedRoot") })

        sync.afterOperation(created = emptyList(), removed = listOf("/storage/emulated/0/Private/diary"))
        sync.afterOperation(created = listOf(photo.path), removed = emptyList())
        runCurrent()

        assertEquals(listOf(listOf(photo.path)), scanned)
        verify(exactly = 1) { ErrorReporter.warning(any(), any(), any()) }
        assertFalse(reported.captured.message.orEmpty().contains("diary"))
        assertNull(reported.captured.cause)
        assertNull(savedQueue())
    }

    @Test
    fun `an entry whose sync runs out of memory is reported and dropped, and the next one still runs`() = runTest {
        // An Error, not an Exception: let through, it would end the worker and leave the entry at
        // the head of the saved queue, failing first on every later launch.
        val photo = file("photo.jpg")
        val sync = launchSync(indexedRows = { _, _, _ -> throw OutOfMemoryError("Failed to allocate") })

        sync.afterOperation(created = emptyList(), removed = listOf("/storage/emulated/0/DCIM/Camera"))
        sync.afterOperation(created = listOf(photo.path), removed = emptyList())
        runCurrent()

        assertEquals(listOf(listOf(photo.path)), scanned)
        verify(exactly = 1) { ErrorReporter.warning(any(), any(), any()) }
        assertNull(savedQueue())
    }

    // ---------- Unmounted storage ---------- \\

    @Test
    fun `an entry reaching unmounted storage syncs its other roots now and keeps only those for later`() = runTest {
        // A move off a card that was removed before its entry ran: the copies on internal storage
        // are there to index, whatever became of the card.
        val card = file("card/photo.jpg")
        val copy = file("internal/photo.jpg")
        val goneFromCard = File(root, "card/Album").path
        indexRows[goneFromCard] = listOf("$goneFromCard/old.jpg")
        mounted = { path -> !path.startsWith(File(root, "card").path) }
        val firstLaunch = newProcess()
        val sync = launchSync(process = firstLaunch)

        sync.afterOperation(created = listOf(copy.path, card.path), removed = listOf(goneFromCard))
        runCurrent()

        assertEquals(listOf(listOf(copy.path)), scanned)
        assertEquals(emptyList<String>(), lookedUp)
        assertNotNull(savedQueue())

        firstLaunch.cancel()
        scanned.clear()
        mounted = { true }
        launchSync()
        runCurrent()

        assertEquals(listOf(listOf(card.path, "$goneFromCard/old.jpg")), scanned)
        assertNull(savedQueue())
    }

    @Test
    fun `an entry on unmounted storage waits for a later launch while later entries run`() = runTest {
        val card = file("card/photo.jpg")
        val first = file("internal/first.jpg")
        val second = file("internal/second.jpg")
        mounted = { path -> !path.startsWith(File(root, "card").path) }
        val firstLaunch = newProcess()
        val sync = launchSync(process = firstLaunch)

        sync.afterOperation(created = listOf(card.path), removed = emptyList())
        sync.afterOperation(created = listOf(first.path), removed = emptyList())
        runCurrent()
        sync.afterOperation(created = listOf(second.path), removed = emptyList())
        runCurrent()

        assertEquals(listOf(listOf(first.path), listOf(second.path)), scanned)
        // Skipped for the rest of the launch: checked once, not again whenever the worker wakes.
        assertEquals(1, mountChecks.count { it == card.path })

        firstLaunch.cancel()
        scanned.clear()
        mounted = { true }
        launchSync()
        runCurrent()

        assertEquals(listOf(listOf(card.path)), scanned)
        assertNull(savedQueue())
    }

    @Test
    fun `an entry whose storage is unmounted on three launches is dropped`() = runTest {
        val card = file("card/photo.jpg")
        mounted = { false }
        val launches = List(3) { newProcess() }

        launchSync(process = launches[0]).afterOperation(created = listOf(card.path), removed = emptyList())
        runCurrent()
        launches[0].cancel()
        launchSync(process = launches[1])
        runCurrent()
        launches[1].cancel()

        assertNotNull(savedQueue())

        launchSync(process = launches[2])
        runCurrent()
        launches[2].cancel()

        assertNull(savedQueue())
        assertEquals(3, mountChecks.count { it == card.path })

        mounted = { true }
        launchSync()
        runCurrent()

        assertEquals(emptyList<List<String>>(), scanned)
    }

    // ---------- Android pieces ---------- \\

    @Test
    fun `the index lookup escapes GLOB wildcards so a root matches only its own rows`() {
        // `?` matches any one character, `[` opens a class and `*` matches anything: unescaped,
        // this prefix would also match rows under `/storage/emulated/0/axbc-dir`.
        val contentResolver = mockk<ContentResolver>()
        val uri = stubFilesUri()
        val wildRoot = "/storage/emulated/0/a?b[c]*-dir"
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns
            cursorOver(listOf(wildRoot, "$wildRoot/photo.jpg"))

        val rows = AndroidMediaIndex.rowsAtAndUnder(contentResolver, wildRoot, afterId = 0L, limit = 500)

        assertEquals(
            listOf(IndexedRow(1L, wildRoot, isFolder = false), IndexedRow(2L, "$wildRoot/photo.jpg", isFolder = false)),
            rows
        )
        verify(exactly = 1) {
            contentResolver.query(
                uri,
                arrayOf("_id", "_data", "format"),
                "(_data=? OR _data GLOB ?) AND _id>?",
                arrayOf(wildRoot, "/storage/emulated/0/a[?]b[[]c][*]-dir/*", "0"),
                "_id ASC"
            )
        }
    }

    @Test
    fun `the index lookup passes LIKE wildcards and backslashes through, which GLOB reads literally`() {
        val contentResolver = mockk<ContentResolver>()
        val uri = stubFilesUri()
        val likeRoot = "/storage/emulated/0/100%_off\\deals"
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursorOver(emptyList())

        AndroidMediaIndex.rowsAtAndUnder(contentResolver, likeRoot, afterId = 0L, limit = 500)

        verify(exactly = 1) {
            contentResolver.query(uri, any(), any(), arrayOf(likeRoot, "$likeRoot/*", "0"), any())
        }
    }

    @Test
    fun `an index lookup reads no further than its page and starts after the given id`() {
        val contentResolver = mockk<ContentResolver>()
        val uri = stubFilesUri()
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns
            cursorOver(listOf("/a.jpg", "/b.jpg", "/c.jpg"))

        val rows = AndroidMediaIndex.rowsAtAndUnder(contentResolver, "/Trip", afterId = 41L, limit = 2)

        assertEquals(listOf(IndexedRow(1L, "/a.jpg", isFolder = false), IndexedRow(2L, "/b.jpg", isFolder = false)), rows)
        verify(exactly = 1) {
            contentResolver.query(uri, any(), any(), arrayOf("/Trip", "/Trip/*", "41"), any())
        }
    }

    @Test
    fun `an index lookup marks as folders the rows whose format is the MTP folder code, and no other`() {
        val contentResolver = mockk<ContentResolver>()
        stubFilesUri()
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns cursorOver(
            listOf("/Trip", "/Trip/photo.jpg", "/Trip/notes"),
            formats = listOf(MtpConstants.FORMAT_ASSOCIATION, MtpConstants.FORMAT_EXIF_JPEG, null)
        )

        val rows = AndroidMediaIndex.rowsAtAndUnder(contentResolver, "/Trip", afterId = 0L, limit = 500)

        assertEquals(listOf(true, false, false), rows.map { it.isFolder })
    }

    @Test
    fun `an index lookup with no cursor fails rather than reading as nothing indexed`() {
        val contentResolver = mockk<ContentResolver>()
        stubFilesUri()
        every { contentResolver.query(any(), any(), any(), any(), any()) } returns null

        assertThrows(IllegalStateException::class.java) {
            AndroidMediaIndex.rowsAtAndUnder(contentResolver, "/storage/emulated/0/Trip", afterId = 0L, limit = 500)
        }
    }

    @Test
    fun `a scan returns only once the scanner has called back for every path`() = runTest {
        val listener = slot<MediaScannerConnection.OnScanCompletedListener>()
        mockkStatic(MediaScannerConnection::class)
        every { MediaScannerConnection.scanFile(any(), any(), any(), capture(listener)) } just Runs

        val scan = launch { AndroidMediaIndex.scan(mockk(), listOf("/a.jpg", "/b.jpg")) }
        runCurrent()
        listener.captured.onScanCompleted("/a.jpg", null)
        runCurrent()

        assertTrue(scan.isActive)

        listener.captured.onScanCompleted("/b.jpg", null)
        runCurrent()

        assertTrue(scan.isCompleted)
    }

    @Test
    fun `a scanner that never calls back fails as an ordinary error once the limit passes`() = runTest {
        mockkStatic(MediaScannerConnection::class)
        every { MediaScannerConnection.scanFile(any(), any(), any(), any()) } just Runs

        val failure = runCatching { AndroidMediaIndex.scan(mockk(), listOf("/a.jpg")) }.exceptionOrNull()

        // Not the timeout's own cancellation, which the worker would rethrow and stop on. Ruled out
        // directly, since a cancellation is an IllegalStateException too.
        assertNotNull(failure)
        assertFalse(failure is CancellationException)
        assertFalse(failure?.message.orEmpty().contains("/a.jpg"))
    }

    private fun stubFilesUri(): Uri {
        val uri = mockk<Uri>()
        mockkStatic(MEDIA_STORE_FILES_CLASS)
        every { MediaStore.Files.getContentUri("external") } returns uri
        return uri
    }

    /** A cursor over rows numbered from 1, in the provider's id, path and format columns. */
    private fun cursorOver(paths: List<String>, formats: List<Int?> = paths.map { null }): Cursor {
        var position = -1
        return mockk {
            every { moveToNext() } answers { ++position < paths.size }
            every { getLong(0) } answers { position + 1L }
            every { getString(1) } answers { paths[position] }
            every { isNull(2) } answers { formats[position] == null }
            every { getInt(2) } answers { checkNotNull(formats[position]) }
            every { close() } just Runs
        }
    }

    private companion object {
        const val MEDIA_STORE_FILES_CLASS = "android.provider.MediaStore\$Files"
        val KEY_QUEUE = stringPreferencesKey("queue")
    }
}
