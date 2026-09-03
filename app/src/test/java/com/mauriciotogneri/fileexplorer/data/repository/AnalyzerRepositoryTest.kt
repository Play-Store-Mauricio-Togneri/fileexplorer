package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.SearchFileType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The extensions used here are the ones a JVM unit test can classify. `MimeTypeMap` is an unmocked
 * Android stub that throws, so [com.mauriciotogneri.fileexplorer.data.util.MimeTypeUtil.getMimeType]
 * only resolves what `URLConnection.guessContentTypeFromName` knows — `.jpg`, `.mpg`, `.wav`,
 * `.pdf` — and answers the wildcard for anything else, which is what puts `.bin` in OTHER.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyzerRepositoryTest {

    private lateinit var root: File

    // Zero interval, so that every file produces an emission and a test can watch progress arrive
    // rather than only the final tally. The clock is a counter because the real one is an android.os
    // stub that throws here, and the volume answers because StatFs is the same.
    private var clock = 0L
    private var volumeAnswers = true

    private val repository = AnalyzerRepository(
        emitIntervalMillis = 0L,
        elapsedMillis = { clock++ },
        storageAnswers = { volumeAnswers }
    )

    @Before
    fun setUp() {
        root = Files.createTempDirectory("analyzer").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `sums file sizes into their categories`() = runTest {
        write("photo.jpg", 100)
        write("clip.mpg", 200)
        write("song.wav", 300)
        write("notes.pdf", 400)
        write("blob.bin", 500)

        val result = repository.analyze(root.absolutePath).toList().last()

        assertEquals(100L, result.sizesByType[SearchFileType.IMAGES])
        assertEquals(200L, result.sizesByType[SearchFileType.VIDEOS])
        assertEquals(300L, result.sizesByType[SearchFileType.AUDIO])
        assertEquals(400L, result.sizesByType[SearchFileType.DOCUMENTS])
        assertEquals(500L, result.sizesByType[SearchFileType.OTHER])
        assertEquals(1500L, result.scannedBytes)
        assertEquals(5, result.fileCount)
    }

    @Test
    fun `every category has an entry even when nothing matched it`() = runTest {
        write("blob.bin", 10)

        val result = repository.analyze(root.absolutePath).toList().last()

        assertEquals(SearchFileType.entries.toSet(), result.sizesByType.keys)
        assertEquals(0L, result.sizesByType[SearchFileType.IMAGES])
    }

    @Test
    fun `walks nested directories to the bottom`() = runTest {
        write("a/b/c/deep.jpg", 64)
        write("a/shallow.jpg", 32)

        val result = repository.analyze(root.absolutePath).toList().last()

        assertEquals(96L, result.scannedBytes)
        assertEquals(2, result.fileCount)
    }

    @Test
    fun `counts hidden files and the contents of hidden directories`() = runTest {
        write(".hidden.jpg", 10)
        write(".hiddenDir/inside.jpg", 20)

        val result = repository.analyze(root.absolutePath).toList().last()

        assertEquals(30L, result.scannedBytes)
        assertEquals(2, result.fileCount)
    }

    @Test
    fun `does not follow symlinks`() = runTest {
        write("real/photo.jpg", 128)
        Files.createSymbolicLink(
            File(root, "link").toPath(),
            File(root, "real").toPath()
        )

        val result = repository.analyze(root.absolutePath).toList().last()

        assertEquals(128L, result.scannedBytes)
        assertEquals(1, result.fileCount)
    }

    @Test
    fun `steps over a directory it cannot list`() = runTest {
        write("readable.jpg", 50)
        val closed = File(root, "closed").apply { mkdirs() }
        File(closed, "unseen.jpg").writeBytes(ByteArray(999))
        closed.setReadable(false)

        try {
            // A build running as root ignores the permission bit entirely, and the case this test
            // exists for cannot be set up at all. Skipped rather than asserted the other way, which
            // would leave it passing while proving nothing.
            assumeTrue(closed.list() == null)

            val result = repository.analyze(root.absolutePath).toList().last()

            // The unreadable subtree is simply absent; the scan still completes over the rest.
            assertTrue(result.isComplete)
            assertEquals(50L, result.scannedBytes)
        } finally {
            // Restored so that the temp tree can be removed again.
            closed.setReadable(true)
        }
    }

    @Test
    fun `reports the folder being scanned and ends on the root`() = runTest {
        write("sub/photo.jpg", 8)

        val emissions = repository.analyze(root.absolutePath).toList()

        assertTrue(emissions.any { it.currentFolder == File(root, "sub").absolutePath })
        assertEquals(root.absolutePath, emissions.last().currentFolder)
    }

    @Test
    fun `only the last emission is complete`() = runTest {
        repeat(5) { index -> write("file$index.jpg", 10) }

        val emissions = repository.analyze(root.absolutePath).toList()

        assertTrue(emissions.last().isComplete)
        assertTrue(emissions.dropLast(1).none { it.isComplete })
    }

    @Test
    fun `completes with an empty tally on an empty volume`() = runTest {
        val result = repository.analyze(root.absolutePath).toList().last()

        assertEquals(0L, result.scannedBytes)
        assertEquals(0, result.fileCount)
        assertTrue(result.isComplete)
    }

    @Test
    fun `completes without failing when the root does not exist`() = runTest {
        val result = repository.analyze(File(root, "gone").absolutePath).toList().last()

        assertEquals(0L, result.scannedBytes)
        assertTrue(result.isComplete)
    }

    /**
     * The case a null listing cannot be told apart from on its own: an ejected card answers exactly
     * like `Android/data` does. Completing here would chart the whole volume as system space.
     */
    @Test
    fun `fails instead of completing when the volume stops answering after losing a directory`() = runTest {
        write("readable.jpg", 40)
        volumeAnswers = false

        assertThrows(StorageUnavailableException::class.java) {
            runBlocking { repository.analyze(File(root, "gone").absolutePath).toList() }
        }
    }

    @Test
    fun `completes when a directory was lost but the volume still answers`() = runTest {
        write("readable.jpg", 40)
        volumeAnswers = true

        val result = repository.analyze(File(root, "gone").absolutePath).toList().last()

        assertTrue(result.isComplete)
    }

    private fun write(relativePath: String, bytes: Int) {
        val file = File(root, relativePath)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(bytes))
    }
}
