package com.mauriciotogneri.fileexplorer.data.model

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ClipboardTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "clipboard_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `isEmpty returns true for empty clipboard`() {
        val clipboard = Clipboard()
        assertTrue(clipboard.isEmpty())
    }

    @Test
    fun `isEmpty returns false when items exist`() {
        val clipboard = Clipboard(
            items = listOf(createFileItem()),
            mode = ClipboardMode.COPY
        )
        assertFalse(clipboard.isEmpty())
    }

    @Test
    fun `canPasteInto returns false for empty clipboard`() {
        val clipboard = Clipboard()
        assertFalse(clipboard.canPasteInto("/storage/emulated/0/target"))
    }

    @Test
    fun `canPasteInto returns false when pasting into source parent`() {
        val clipboard = Clipboard(
            items = listOf(createFileItem(path = "/storage/emulated/0/source/file.txt")),
            mode = ClipboardMode.COPY,
            sourceParent = "/storage/emulated/0/source"
        )
        assertFalse(clipboard.canPasteInto("/storage/emulated/0/source"))
    }

    @Test
    fun `canPasteInto returns false when pasting into subdirectory of selected item`() {
        val clipboard = Clipboard(
            items = listOf(createFileItem(path = "/storage/emulated/0/folder")),
            mode = ClipboardMode.COPY,
            sourceParent = "/storage/emulated/0"
        )
        assertFalse(clipboard.canPasteInto("/storage/emulated/0/folder/subfolder"))
    }

    @Test
    fun `canPasteInto returns false when pasting into exact path of selected item`() {
        val clipboard = Clipboard(
            items = listOf(createFileItem(path = "/storage/emulated/0/folder")),
            mode = ClipboardMode.COPY,
            sourceParent = "/storage/emulated/0"
        )
        assertFalse(clipboard.canPasteInto("/storage/emulated/0/folder"))
    }

    @Test
    fun `canPasteInto returns true for valid different target`() {
        val sourceFile = File(tempDir, "source/file.txt")
        sourceFile.parentFile?.mkdirs()
        sourceFile.createNewFile()

        val clipboard = Clipboard(
            items = listOf(createFileItem(path = sourceFile.absolutePath)),
            mode = ClipboardMode.COPY,
            sourceParent = sourceFile.parent
        )
        assertTrue(clipboard.canPasteInto(File(tempDir, "target").absolutePath))
    }

    @Test
    fun `canPasteInto handles path prefix edge case correctly`() {
        // "folder2" should NOT be blocked by "folder"
        val folder = File(tempDir, "folder")
        folder.mkdirs()

        val clipboard = Clipboard(
            items = listOf(createFileItem(path = folder.absolutePath, isDirectory = true)),
            mode = ClipboardMode.COPY,
            sourceParent = tempDir.absolutePath
        )
        assertTrue(clipboard.canPasteInto(File(tempDir, "folder2").absolutePath))
    }

    private fun createFileItem(
        path: String = "/storage/emulated/0/test.txt",
        name: String = "test.txt",
        isDirectory: Boolean = false
    ) = FileItem(
        path = path,
        name = name,
        isDirectory = isDirectory,
        size = 1024,
        lastModified = System.currentTimeMillis(),
        mimeType = "text/plain",
        childCount = null
    )
}
