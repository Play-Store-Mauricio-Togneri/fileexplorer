package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.ClipboardMode
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClipboardManagerTest {

    @Before
    fun setUp() {
        ClipboardManager.clear()
    }

    @Test
    fun `clipboard starts empty`() {
        assertTrue(ClipboardManager.clipboard.value.isEmpty())
        assertEquals(ClipboardMode.NONE, ClipboardManager.clipboard.value.mode)
    }

    @Test
    fun `cut sets items with CUT mode`() {
        val items = listOf(createFileItem())
        ClipboardManager.cut(items, "/storage/emulated/0")

        val clipboard = ClipboardManager.clipboard.value
        assertEquals(items, clipboard.items)
        assertEquals(ClipboardMode.CUT, clipboard.mode)
        assertEquals("/storage/emulated/0", clipboard.sourceParent)
    }

    @Test
    fun `copy sets items with COPY mode`() {
        val items = listOf(createFileItem())
        ClipboardManager.copy(items, "/storage/emulated/0")

        val clipboard = ClipboardManager.clipboard.value
        assertEquals(items, clipboard.items)
        assertEquals(ClipboardMode.COPY, clipboard.mode)
        assertEquals("/storage/emulated/0", clipboard.sourceParent)
    }

    @Test
    fun `clear resets clipboard to empty`() {
        ClipboardManager.copy(listOf(createFileItem()), "/storage/emulated/0")
        ClipboardManager.clear()

        assertTrue(ClipboardManager.clipboard.value.isEmpty())
        assertEquals(ClipboardMode.NONE, ClipboardManager.clipboard.value.mode)
    }

    @Test
    fun `cut replaces previous clipboard content`() {
        ClipboardManager.copy(listOf(createFileItem(name = "first.txt")), "/storage/emulated/0")

        val newItems = listOf(createFileItem(name = "second.txt"))
        ClipboardManager.cut(newItems, "/storage/emulated/0/folder")

        val clipboard = ClipboardManager.clipboard.value
        assertEquals(1, clipboard.items.size)
        assertEquals("second.txt", clipboard.items[0].name)
        assertEquals(ClipboardMode.CUT, clipboard.mode)
    }

    private fun createFileItem(
        path: String = "/storage/emulated/0/test.txt",
        name: String = "test.txt"
    ) = FileItem(
        path = path,
        name = name,
        isDirectory = false,
        size = 1024,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = "text/plain",
        childCount = null
    )
}
