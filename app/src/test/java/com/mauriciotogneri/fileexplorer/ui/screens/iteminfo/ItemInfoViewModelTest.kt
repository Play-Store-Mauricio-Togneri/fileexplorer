package com.mauriciotogneri.fileexplorer.ui.screens.iteminfo

import android.app.Application
import app.cash.turbine.test
import com.mauriciotogneri.fileexplorer.data.model.ApkMetadata
import com.mauriciotogneri.fileexplorer.data.model.AudioMetadata
import com.mauriciotogneri.fileexplorer.data.model.CsvMetadata
import com.mauriciotogneri.fileexplorer.data.model.EpubMetadata
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.ICalendarMetadata
import com.mauriciotogneri.fileexplorer.data.model.ImageMetadata
import com.mauriciotogneri.fileexplorer.data.model.OfficeMetadata
import com.mauriciotogneri.fileexplorer.data.model.PdfMetadata
import com.mauriciotogneri.fileexplorer.data.model.SqliteMetadata
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.StorageType
import com.mauriciotogneri.fileexplorer.data.model.VCardMetadata
import com.mauriciotogneri.fileexplorer.data.model.VideoMetadata
import com.mauriciotogneri.fileexplorer.data.model.ZipMetadata
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.UncompressProgress
import com.mauriciotogneri.fileexplorer.data.repository.ZipInfo
import com.mauriciotogneri.fileexplorer.data.util.ApkMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.AudioMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.CsvMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.EpubMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.ICalendarMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.ImageMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil
import com.mauriciotogneri.fileexplorer.data.util.OfficeMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.PdfMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.SqliteMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.VCardMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.VideoMetadataExtractor
import com.mauriciotogneri.fileexplorer.data.util.ZipMetadataExtractor
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import com.mauriciotogneri.fileexplorer.util.MediaStoreUtil
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ItemInfoViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var fileRepository: FileRepository
    private lateinit var storageRepository: StorageRepository
    private lateinit var tempDir: File

    private val testStorage = StorageDevice(
        path = "/storage/emulated/0",
        displayName = "Internal Storage",
        totalBytes = 64_000_000_000L,
        availableBytes = 32_000_000_000L,
        type = StorageType.INTERNAL
    )

    // One sentinel per metadata extractor, returned by its stub. A field holding exactly its
    // sentinel means that extractor's gate opened, which a real extraction cannot show: most of
    // them return null on a file that is not their type, so an open gate looks the same as a
    // closed one.
    private val imageSentinel = mockk<ImageMetadata>()
    private val audioSentinel = mockk<AudioMetadata>()
    private val videoSentinel = mockk<VideoMetadata>()
    private val pdfSentinel = mockk<PdfMetadata>()
    private val apkSentinel = mockk<ApkMetadata>()
    private val zipSentinel = mockk<ZipMetadata>()
    private val officeSentinel = mockk<OfficeMetadata>()
    private val epubSentinel = mockk<EpubMetadata>()
    private val sqliteSentinel = mockk<SqliteMetadata>()
    private val vcardSentinel = mockk<VCardMetadata>()
    private val icalendarSentinel = mockk<ICalendarMetadata>()
    private val csvSentinel = mockk<CsvMetadata>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        tempDir = File(System.getProperty("java.io.tmpdir"), "iteminfo_test_${System.nanoTime()}")
        tempDir.mkdirs()

        application = mockk(relaxed = true)
        fileRepository = mockk(relaxed = true)
        storageRepository = mockk()

        coEvery { storageRepository.getStorages() } returns listOf(testStorage)

        mockkObject(ErrorReporter)
        mockkObject(MediaStoreUtil)
        mockkObject(IntentUtil)
        every { ErrorReporter.error(any(), any()) } just Runs
        every { ErrorReporter.error(any(), any(), any()) } just Runs
        every { ErrorReporter.warning(any(), any(), any()) } just Runs
        every { MediaStoreUtil.scanFiles(any(), any()) } just Runs
        every { IntentUtil.trackRecentFile(any(), any()) } just Runs

        // Only getMimeType is stubbed, and only by the tests that need it: MimeTypeMap is a stub on
        // the JVM, so what a file's name maps to there is not what it maps to on a device.
        mockkObject(MimeTypeUtil)
        mockkObject(*EXTRACTORS)
        every { ImageMetadataExtractor.extract(any()) } returns imageSentinel
        every { AudioMetadataExtractor.extract(any()) } returns audioSentinel
        every { VideoMetadataExtractor.extract(any()) } returns videoSentinel
        every { PdfMetadataExtractor.extract(any()) } returns pdfSentinel
        every { ApkMetadataExtractor.extract(any(), any()) } returns apkSentinel
        every { ZipMetadataExtractor.extract(any()) } returns zipSentinel
        every { OfficeMetadataExtractor.extract(any()) } returns officeSentinel
        every { EpubMetadataExtractor.extract(any()) } returns epubSentinel
        every { SqliteMetadataExtractor.extract(any()) } returns sqliteSentinel
        every { VCardMetadataExtractor.extract(any()) } returns vcardSentinel
        every { ICalendarMetadataExtractor.extract(any()) } returns icalendarSentinel
        every { CsvMetadataExtractor.extract(any()) } returns csvSentinel
    }

    @After
    fun tearDown() {
        advanceAndWait()
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
        unmockkObject(ErrorReporter)
        unmockkObject(MediaStoreUtil)
        unmockkObject(IntentUtil)
        unmockkObject(MimeTypeUtil)
        unmockkObject(*EXTRACTORS)
    }

    /**
     * Runs every coroutine to completion, background work included.
     *
     * This used to be `advanceUntilIdle()` around a `Thread.sleep(100)`, because the metadata load
     * ran on a hardcoded `Dispatchers.IO` that `setMain` cannot intercept — so the scheduler had no
     * idea it existed. That made every test in this file a race: slower than 100 ms under CI load
     * and it failed, faster and passing was never evidence the coroutine had actually run. The
     * ViewModel now takes [ItemInfoViewModel.ioDispatcher], so [testDispatcher] owns that work too
     * and idle means idle.
     */
    private fun advanceAndWait() {
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun createViewModel(filePath: String): ItemInfoViewModel {
        return ItemInfoViewModel(
            filePath = filePath,
            application = application,
            fileRepository = fileRepository,
            storageRepository = storageRepository,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `loads file info for existing file`() = runTest {
        val testFile = File(tempDir, "test_${System.nanoTime()}.txt")
        testFile.writeText("Hello World")

        val viewModel = createViewModel(testFile.absolutePath)
        advanceAndWait()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse(state.error)
        assertNotNull(state.file)
        assertEquals(testFile.name, state.file?.name)
        assertFalse(state.file?.isDirectory ?: true)
    }

    @Test
    fun `loads folder info for existing folder`() = runTest {
        val testFolder = File(tempDir, "TestFolder_${System.nanoTime()}")
        testFolder.mkdirs()
        File(testFolder, "file.txt").writeText("Content")

        val viewModel = createViewModel(testFolder.absolutePath)
        advanceAndWait()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertFalse(state.error)
        assertNotNull(state.file)
        assertTrue(state.file?.isDirectory ?: false)
    }


    @Test
    fun `sets error state for non-existing file`() = runTest {
        val viewModel = createViewModel("/non/existing/path_${System.nanoTime()}.txt")
        advanceAndWait()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertTrue(state.error)
        assertNull(state.file)
    }

    @Test
    fun `onOpenFile emits event for file`() = runTest {
        val testFile = File(tempDir, "open_test_${System.nanoTime()}.txt")
        testFile.writeText("Hello World")

        val viewModel = createViewModel(testFile.absolutePath)
        advanceAndWait()

        viewModel.events.test {
            viewModel.onOpenFile()
            advanceAndWait()

            val event = awaitItem()
            assertTrue(event is ItemInfoUiEvent.OpenFile)
            assertEquals(testFile.name, (event as ItemInfoUiEvent.OpenFile).file.name)
        }
    }

    @Test
    fun `onOpenFile does nothing for folder`() = runTest {
        val testFolder = File(tempDir, "folder_open_test_${System.nanoTime()}")
        testFolder.mkdirs()

        val viewModel = createViewModel(testFolder.absolutePath)
        advanceAndWait()

        viewModel.events.test {
            viewModel.onOpenFile()
            advanceAndWait()

            expectNoEvents()
        }
    }

    @Test
    fun `onOpenFile does nothing when file is null`() = runTest {
        val viewModel = createViewModel("/non/existing/path_${System.nanoTime()}.txt")
        advanceAndWait()

        viewModel.events.test {
            viewModel.onOpenFile()
            advanceAndWait()

            expectNoEvents()
        }
    }

    /**
     * Every extractor returns its sentinel here, so a gate left open fails the test. Against the
     * real extractors it could not: all but the CSV one return null on a text file anyway, so only
     * that gate was ever checked.
     */
    @Test
    fun `file metadata is null for plain text file`() = runTest {
        every { MimeTypeUtil.getMimeType(any()) } returns "text/plain"
        val testFile = File(tempDir, "plain_${System.nanoTime()}.txt")
        testFile.writeText("Plain text content")

        val viewModel = createViewModel(testFile.absolutePath)
        advanceAndWait()

        metadataFields(viewModel.state.value).forEach { (field, value, _) ->
            assertNull("A plain text file must not reach the $field extractor", value)
        }
    }

    /**
     * Guards the type gates in front of the twelve extractors: a gate swapped with another, widened
     * to a related type (an APK or an EPUB is a ZIP too) or dropped would put a section on the
     * screen for the wrong file, or leave the right one empty. Each row reaches its own extractor
     * and no other.
     */
    @Test
    fun `each file type reaches its own extractor and no other`() = runTest {
        val routes = listOf(
            Triple("photo.jpg", "image/jpeg", "image"),
            Triple("song.mp3", "audio/mpeg", "audio"),
            Triple("clip.mp4", "video/mp4", "video"),
            Triple("paper.pdf", "application/pdf", "pdf"),
            Triple("app.apk", "application/vnd.android.package-archive", "apk"),
            Triple("archive.zip", "application/zip", "zip"),
            Triple("report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "office"),
            Triple("book.epub", "application/epub+zip", "epub"),
            // A MIME type no gate knows, so these four are routed by their extension alone.
            Triple("contacts.db", "*/*", "sqlite"),
            Triple("card.vcf", "*/*", "vcard"),
            Triple("events.ics", "*/*", "icalendar"),
            Triple("table.csv", "*/*", "csv")
        )

        routes.forEach { (fileName, mimeType, expected) ->
            every { MimeTypeUtil.getMimeType(any()) } returns mimeType
            val testFile = File(tempDir, fileName).apply { writeText("x") }

            val viewModel = createViewModel(testFile.absolutePath)
            advanceAndWait()

            metadataFields(viewModel.state.value).forEach { (field, value, sentinel) ->
                if (field == expected) {
                    assertSame("$fileName must reach the $field extractor", sentinel, value)
                } else {
                    assertNull("$fileName must not reach the $field extractor", value)
                }
            }
        }
    }

    /** Each metadata field of [state] by name, with the sentinel its extractor's stub returns. */
    private fun metadataFields(state: ItemInfoUiState): List<Triple<String, Any?, Any>> = listOf(
        Triple("image", state.imageMetadata, imageSentinel),
        Triple("audio", state.audioMetadata, audioSentinel),
        Triple("video", state.videoMetadata, videoSentinel),
        Triple("pdf", state.pdfMetadata, pdfSentinel),
        Triple("apk", state.apkMetadata, apkSentinel),
        Triple("zip", state.zipMetadata, zipSentinel),
        Triple("office", state.officeMetadata, officeSentinel),
        Triple("epub", state.epubMetadata, epubSentinel),
        Triple("sqlite", state.sqliteMetadata, sqliteSentinel),
        Triple("vcard", state.vcardMetadata, vcardSentinel),
        Triple("icalendar", state.icalendarMetadata, icalendarSentinel),
        Triple("csv", state.csvMetadata, csvSentinel)
    )

    @Test
    fun `folder size is null for files`() = runTest {
        val testFile = File(tempDir, "size_null_test_${System.nanoTime()}.txt")
        testFile.writeText("Content")

        val viewModel = createViewModel(testFile.absolutePath)
        advanceAndWait()

        assertNull(viewModel.state.value.folderSize)
    }

    // Both uncompress entry points below are thin delegations to UncompressHandler. Driving them
    // from a fresh view model only reads back ItemInfoUiState defaults, which stays green even if
    // the delegation is severed — leaving this screen unable to cancel or dismiss an extraction.

    @Test
    fun `cancelUncompression stops a running extraction and clears progress`() = runTest {
        val zipFile = File(tempDir, "archive_${System.nanoTime()}.zip")
        zipFile.writeText("Content")
        val zip = FileItem.from(zipFile)

        coEvery { fileRepository.getZipInfo(zip.path) } returns ZipInfo(entryCount = 3, isEncrypted = false)

        var extractionStopped = false
        coEvery { fileRepository.uncompressFile(any(), any(), any(), any(), any()) } returns flow {
            emit(
                UncompressProgress(
                    currentFile = "file1.txt",
                    extractedFiles = 1,
                    totalFiles = 3,
                    extractedBytes = 100L,
                    totalBytes = 300L,
                    isComplete = false,
                    extractedPaths = emptyList()
                )
            )
            // Hold the extraction open so the cancel lands mid-flight.
            try {
                awaitCancellation()
            } finally {
                extractionStopped = true
            }
        }

        val viewModel = createViewModel(zipFile.absolutePath)
        advanceAndWait()

        viewModel.showUncompressDialog(zip)
        advanceAndWait()
        viewModel.confirmUncompress()
        advanceAndWait()

        assertEquals("file1.txt", viewModel.state.value.uncompressProgress?.currentFile)

        viewModel.cancelUncompression()
        advanceAndWait()

        assertTrue("Cancel must stop the running extraction", extractionStopped)
        assertNull(viewModel.state.value.uncompressProgress)
    }

    @Test
    fun `dismissUncompressDialog clears the open dialog`() = runTest {
        val zipFile = File(tempDir, "archive_${System.nanoTime()}.zip")
        zipFile.writeText("Content")
        val zip = FileItem.from(zipFile)

        coEvery { fileRepository.getZipInfo(zip.path) } returns ZipInfo(entryCount = 7, isEncrypted = false)

        val viewModel = createViewModel(zipFile.absolutePath)
        advanceAndWait()

        viewModel.showUncompressDialog(zip)
        advanceAndWait()
        assertNotNull("The dialog must be open first", viewModel.state.value.itemToUncompress)
        assertEquals(7, viewModel.state.value.uncompressEntryCount)

        viewModel.dismissUncompressDialog()
        // The dialog state reaches the screen state through the handler's state collector.
        advanceAndWait()

        assertNull(viewModel.state.value.itemToUncompress)
        assertEquals(0, viewModel.state.value.uncompressEntryCount)
    }

    // This screen has no folder of its own, so the extraction has to target the folder that holds
    // the file it was opened on; a target left unset is "", and every extraction from here fails.
    @Test
    fun `an archive extracts into the folder that holds it`() = runTest {
        val folder = File(tempDir, "archives").apply { mkdirs() }
        val zipFile = File(folder, "archive.zip").apply { writeText("Content") }
        val zip = FileItem.from(zipFile)

        coEvery { fileRepository.getZipInfo(zip.path) } returns ZipInfo(entryCount = 1, isEncrypted = false)
        coEvery { fileRepository.uncompressFile(any(), any(), any(), any(), any()) } returns emptyFlow()

        val viewModel = createViewModel(zipFile.absolutePath)
        advanceAndWait()

        viewModel.showUncompressDialog(zip)
        advanceAndWait()
        viewModel.confirmUncompress()
        advanceAndWait()

        coVerify(exactly = 1) {
            fileRepository.uncompressFile(zip.path, folder.absolutePath, null, any(), any())
        }
    }

    // ==================== Storage the file is on ====================

    private fun storageAt(path: String, displayName: String, type: StorageType = StorageType.SD_CARD) =
        StorageDevice(
            path = path,
            displayName = displayName,
            totalBytes = 1_000L,
            availableBytes = 500L,
            type = type
        )

    @Test
    fun `resolves the storage the file lives on`() = runTest {
        val volume = storageAt(tempDir.absolutePath, "SD card")
        coEvery { storageRepository.getStorages() } returns listOf(testStorage, volume)
        val folder = File(tempDir, "Music").apply { mkdirs() }
        val testFile = File(folder, "song.mp3").apply { writeText("x") }

        val viewModel = createViewModel(testFile.absolutePath)
        advanceAndWait()

        assertEquals(volume, viewModel.state.value.storage)
    }

    @Test
    fun `a volume mounted inside another one wins over the volume containing it`() = runTest {
        val outer = storageAt(tempDir.absolutePath, "Outer")
        val innerFolder = File(tempDir, "mnt").apply { mkdirs() }
        val inner = storageAt(innerFolder.absolutePath, "Inner")
        coEvery { storageRepository.getStorages() } returns listOf(outer, inner)
        val testFile = File(innerFolder, "notes.txt").apply { writeText("x") }

        val viewModel = createViewModel(testFile.absolutePath)
        advanceAndWait()

        assertEquals(inner, viewModel.state.value.storage)
    }

    @Test
    fun `a storage whose path is only a name prefix of the folder is not its storage`() = runTest {
        val volume = storageAt(File(tempDir, "card").absolutePath, "SD card")
        coEvery { storageRepository.getStorages() } returns listOf(volume)
        val folder = File(tempDir, "cards").apply { mkdirs() }
        val testFile = File(folder, "notes.txt").apply { writeText("x") }

        val viewModel = createViewModel(testFile.absolutePath)
        advanceAndWait()

        assertNull(viewModel.state.value.storage)
    }

    @Test
    fun `a failed storage lookup still shows the file`() = runTest {
        coEvery { storageRepository.getStorages() } throws IllegalStateException("volumes unavailable")
        val testFile = File(tempDir, "notes.txt").apply { writeText("x") }

        val viewModel = createViewModel(testFile.absolutePath)
        advanceAndWait()

        val state = viewModel.state.value
        assertFalse(state.error)
        assertEquals(testFile.name, state.file?.name)
        assertNull(state.storage)
    }

    private companion object {
        val EXTRACTORS = arrayOf(
            ImageMetadataExtractor,
            AudioMetadataExtractor,
            VideoMetadataExtractor,
            PdfMetadataExtractor,
            ApkMetadataExtractor,
            ZipMetadataExtractor,
            OfficeMetadataExtractor,
            EpubMetadataExtractor,
            SqliteMetadataExtractor,
            VCardMetadataExtractor,
            ICalendarMetadataExtractor,
            CsvMetadataExtractor
        )
    }
}
