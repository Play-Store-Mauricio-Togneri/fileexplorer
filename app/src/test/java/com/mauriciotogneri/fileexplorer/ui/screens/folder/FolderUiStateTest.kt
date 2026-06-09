package com.mauriciotogneri.fileexplorer.ui.screens.folder

import com.mauriciotogneri.fileexplorer.data.model.FileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderUiStateTest {

    private fun fileItem(path: String, isDirectory: Boolean = false) = FileItem(
        path = path,
        name = path.substringAfterLast('/'),
        isDirectory = isDirectory,
        size = if (isDirectory) 0L else 1024L,
        lastModified = 1000L,
        createdTime = 1000L,
        mimeType = if (isDirectory) "" else "text/plain",
        childCount = if (isDirectory) 0 else null
    )

    private val folder = fileItem("/root/Folder1", isDirectory = true)
    private val fileA = fileItem("/root/a.txt")
    private val fileB = fileItem("/root/b.txt")

    @Test
    fun `selectedFiles returns items matching selected paths`() {
        val state = FolderUiState(
            files = listOf(folder, fileA, fileB),
            selectedPaths = setOf(fileA.path, fileB.path)
        )

        assertEquals(setOf(fileA, fileB), state.selectedFiles.toSet())
    }

    @Test
    fun `selectedFiles ignores paths not present in files`() {
        val state = FolderUiState(
            files = listOf(fileA),
            selectedPaths = setOf(fileA.path, "/root/missing.txt")
        )

        assertEquals(listOf(fileA), state.selectedFiles)
    }

    @Test
    fun `allSelectedAreFiles is true when only files are selected`() {
        val state = FolderUiState(
            files = listOf(folder, fileA, fileB),
            selectedPaths = setOf(fileA.path, fileB.path)
        )

        assertTrue(state.allSelectedAreFiles)
    }

    @Test
    fun `allSelectedAreFiles is false when a directory is selected`() {
        val state = FolderUiState(
            files = listOf(folder, fileA),
            selectedPaths = setOf(folder.path, fileA.path)
        )

        assertFalse(state.allSelectedAreFiles)
    }

    @Test
    fun `allSelectedAreFiles is false when nothing is selected`() {
        val state = FolderUiState(files = listOf(fileA))

        assertFalse(state.allSelectedAreFiles)
    }

    @Test
    fun `singleSelectedFile returns item only when exactly one is selected`() {
        val base = FolderUiState(files = listOf(folder, fileA, fileB))

        assertNull(base.singleSelectedFile)
        assertEquals(fileA, base.copy(selectedPaths = setOf(fileA.path)).singleSelectedFile)
        assertNull(base.copy(selectedPaths = setOf(fileA.path, fileB.path)).singleSelectedFile)
    }

    @Test
    fun `selection getters resolve against new files after copy`() {
        val state = FolderUiState(
            files = listOf(fileA),
            selectedPaths = setOf(fileA.path)
        )
        // Force the lazily cached path lookup to initialize on the original instance.
        assertEquals(listOf(fileA), state.selectedFiles)

        val renamedFileA = fileItem("/root/a-renamed.txt")
        val copied = state.copy(
            files = listOf(renamedFileA),
            selectedPaths = setOf(renamedFileA.path)
        )

        assertEquals(listOf(renamedFileA), copied.selectedFiles)
        assertTrue(copied.allSelectedAreFiles)
    }
}
