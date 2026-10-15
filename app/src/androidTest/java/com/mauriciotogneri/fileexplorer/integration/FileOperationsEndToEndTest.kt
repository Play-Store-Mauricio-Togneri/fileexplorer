package com.mauriciotogneri.fileexplorer.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// device-required: each case here reaches a platform call the unit suite can only stand in for.
// Deletes, and the source half of a move, go through FileRepository's default removeFile, which
// calls android.system.Os.remove — FileRepositoryTest and FileRepositoryOperationsTest replace it
// with a java.io stand-in. The extraction runs uncompressFile's free-space pre-flight against a
// real StatFs, which the JVM tests mock. Copy, compress and extract scenarios that need neither
// live in FileRepositoryOperationsTest.
@RunWith(AndroidJUnit4::class)
class FileOperationsEndToEndTest {

    private lateinit var testDir: File
    private lateinit var sourceDir: File
    private lateinit var targetDir: File
    private lateinit var fileRepository: FileRepository
    private lateinit var allowedRoots: List<String>

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.cacheDir, "test_e2e_${System.currentTimeMillis()}")
        testDir.mkdirs()

        sourceDir = File(testDir, "source")
        sourceDir.mkdirs()

        targetDir = File(testDir, "target")
        targetDir.mkdirs()

        fileRepository = FileRepository()
        allowedRoots = listOf(testDir.absolutePath)
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    // region Move Tests

    @Test
    fun moveMixedFilesAndFolders_removesSource() = runBlocking {
        val file = createTestFile(sourceDir, "move_file.txt", "file content")
        val folder = File(sourceDir, "MoveFolder")
        folder.mkdirs()
        createTestFile(folder, "inside.txt", "folder content")

        val items = listOf(
            FileItem.from(file),
            FileItem.from(folder)
        )

        fileRepository.copyFiles(
            sources = items,
            targetDir = targetDir.absolutePath,
            deleteAfter = true,
            allowedRoots = allowedRoots
        ).toList()

        assertTrue("File should exist in target", File(targetDir, "move_file.txt").exists())
        assertTrue("Folder should exist in target", File(targetDir, "MoveFolder").exists())
        assertFalse("Source file should be deleted", file.exists())
        assertFalse("Source folder should be deleted", folder.exists())
    }

    // endregion

    // region Uncompress Tests

    @Test
    fun uncompressZip_extractsAllFiles() = runBlocking {
        val zipPath = createTestZip(
            sourceDir,
            "test_archive.zip",
            mapOf(
                "file1.txt" to "content 1",
                "file2.txt" to "content 2",
                "folder/nested.txt" to "nested content"
            )
        )

        val progressList = fileRepository.uncompressFile(
            zipPath = zipPath,
            targetDir = targetDir.absolutePath,
            password = null,
            allowedRoots = allowedRoots
        ).toList()

        val lastProgress = progressList.last()
        assertTrue("Should be complete", lastProgress.isComplete)

        assertTrue("file1.txt should be extracted", File(targetDir, "file1.txt").exists())
        assertTrue("file2.txt should be extracted", File(targetDir, "file2.txt").exists())
        assertTrue("folder should be extracted", File(targetDir, "folder").exists())
        assertTrue("nested.txt should be extracted", File(targetDir, "folder/nested.txt").exists())

        assertEquals("content 1", File(targetDir, "file1.txt").readText())
        assertEquals("nested content", File(targetDir, "folder/nested.txt").readText())
    }

    // endregion

    // region Delete With Progress Tests

    @Test
    fun deleteMultipleFiles_showsProgress() = runBlocking {
        val files = (1..15).map { i ->
            createTestFile(sourceDir, "file$i.txt", "content $i")
        }
        val items = files.map { FileItem.from(it) }

        val progressList = fileRepository.deleteWithProgress(items).toList()

        assertTrue("Should have progress updates", progressList.size > 1)

        val lastProgress = progressList.last()
        assertTrue("Should be complete", lastProgress.isComplete)
        assertEquals("Total files should be 15", 15, lastProgress.totalFiles)
        assertEquals("All files should be deleted", 15, lastProgress.deletedFiles)
        assertEquals("No failures expected", 0, lastProgress.failedFiles)

        assertTrue(
            "Should report file names during deletion",
            progressList.dropLast(1).any { it.currentFile.startsWith("file") }
        )

        files.forEach { file ->
            assertFalse("File should be deleted: ${file.name}", file.exists())
        }
    }

    /**
     * The crash this covers was an `OutOfMemoryError` deleting a large tree: the caller enumerated
     * every path into a list it held for the whole operation, so the cost grew with the tree
     * rather than staying flat. A test cannot assert on heap usage, so this asserts what the fix
     * makes possible instead — a tree far past any progress threshold walks, counts and deletes to
     * completion, with totals that stay consistent across the whole run.
     */
    @Test
    fun deleteLargeTree_completesWithConsistentTotals() = runBlocking {
        val root = File(sourceDir, "LargeTree")
        val expectedFiles = DIRECTORY_COUNT * FILES_PER_DIRECTORY
        repeat(DIRECTORY_COUNT) { dirIndex ->
            val branch = File(root, "branch_$dirIndex/leaf")
            branch.mkdirs()
            repeat(FILES_PER_DIRECTORY) { fileIndex ->
                createTestFile(branch, "file_$fileIndex.txt", "content")
            }
        }
        val rootItem = FileItem.from(root)
        // The root, each branch, and each branch's leaf.
        val expectedDirectories = 1 + DIRECTORY_COUNT * 2

        // Counting the tree must not depend on holding it: this is the walk that ran out of heap.
        assertEquals(
            "Node count should cover every file and directory",
            expectedFiles + expectedDirectories,
            fileRepository.totalNodeCount(listOf(rootItem))
        )

        val progressList = fileRepository.deleteWithProgress(listOf(rootItem)).toList()
        val lastProgress = progressList.last()

        assertTrue("Should be complete", lastProgress.isComplete)
        assertEquals("Total should be every leaf file", expectedFiles, lastProgress.totalFiles)
        assertEquals("Every file should be deleted", expectedFiles, lastProgress.deletedFiles)
        assertEquals("No failures expected", 0, lastProgress.failedFiles)
        assertFalse("Structural delete should succeed", lastProgress.structuralDeleteFailed)
        // The numerator never overtakes the denominator, so the dialog cannot show >100%.
        assertTrue(
            "Deleted count must never exceed the total",
            progressList.all { it.deletedFiles <= it.totalFiles }
        )
        assertFalse("Tree should be gone", root.exists())
    }

    @Test
    fun deleteFolderWithContents_deletesRecursively() = runBlocking {
        val folder = File(sourceDir, "DeleteMe")
        folder.mkdirs()
        createTestFile(folder, "inside1.txt", "content")
        createTestFile(folder, "inside2.txt", "content")
        val subFolder = File(folder, "SubFolder")
        subFolder.mkdirs()
        createTestFile(subFolder, "nested.txt", "content")

        val folderItem = FileItem.from(folder)

        val progressList = fileRepository.deleteWithProgress(listOf(folderItem)).toList()

        val lastProgress = progressList.last()
        assertTrue("Should be complete", lastProgress.isComplete)
        assertFalse("Folder should be deleted", folder.exists())
    }

    // endregion

    // region Helper Methods

    private fun createTestFile(dir: File, name: String, content: String): File {
        val file = File(dir, name)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    private fun createTestZip(dir: File, name: String, entries: Map<String, String>): String {
        val zipFile = File(dir, name)
        val tempDir = File(dir, "temp_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            entries.forEach { (entryName, content) ->
                val file = File(tempDir, entryName)
                file.parentFile?.mkdirs()
                file.writeText(content)
            }

            ZipFile(zipFile).use { zip ->
                val params = ZipParameters().apply {
                    compressionMethod = CompressionMethod.DEFLATE
                    compressionLevel = CompressionLevel.NORMAL
                }
                tempDir.listFiles()?.forEach { file ->
                    if (file.isDirectory) {
                        zip.addFolder(file, params)
                    } else {
                        zip.addFile(file, params)
                    }
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }

        return zipFile.absolutePath
    }

    // endregion

    companion object {
        // Wide rather than deep: enough nodes to be well past any progress threshold and to make a
        // per-path list measurable, while staying shallow, since the recursive walks would hit the
        // stack long before they hit the heap.
        private const val DIRECTORY_COUNT = 20
        private const val FILES_PER_DIRECTORY = 100
    }
}
