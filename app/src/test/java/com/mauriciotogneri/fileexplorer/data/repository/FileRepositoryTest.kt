package com.mauriciotogneri.fileexplorer.data.repository

import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SortMode
import org.junit.Assert.assertEquals
import org.junit.Test

class FileRepositoryTest {

    private val repository = FileRepository()

    @Test
    fun `sortFiles places folders before files`() {
        val files = listOf(
            createFileItem(name = "file.txt", isDirectory = false),
            createFileItem(name = "folder", isDirectory = true)
        )

        val sorted = repository.sortFiles(files, SortMode.NAME_ASC)

        assertEquals("folder", sorted[0].name)
        assertEquals("file.txt", sorted[1].name)
    }

    @Test
    fun `sortFiles NAME_ASC sorts alphabetically ascending`() {
        val files = listOf(
            createFileItem(name = "zebra.txt"),
            createFileItem(name = "apple.txt"),
            createFileItem(name = "Banana.txt")
        )

        val sorted = repository.sortFiles(files, SortMode.NAME_ASC)

        assertEquals("apple.txt", sorted[0].name)
        assertEquals("Banana.txt", sorted[1].name)
        assertEquals("zebra.txt", sorted[2].name)
    }

    @Test
    fun `sortFiles NAME_DESC sorts alphabetically descending`() {
        val files = listOf(
            createFileItem(name = "apple.txt"),
            createFileItem(name = "zebra.txt"),
            createFileItem(name = "Banana.txt")
        )

        val sorted = repository.sortFiles(files, SortMode.NAME_DESC)

        assertEquals("zebra.txt", sorted[0].name)
        assertEquals("Banana.txt", sorted[1].name)
        assertEquals("apple.txt", sorted[2].name)
    }

    @Test
    fun `sortFiles SIZE_ASC sorts by size ascending`() {
        val files = listOf(
            createFileItem(name = "large.txt", size = 1000),
            createFileItem(name = "small.txt", size = 100),
            createFileItem(name = "medium.txt", size = 500)
        )

        val sorted = repository.sortFiles(files, SortMode.SIZE_ASC)

        assertEquals("small.txt", sorted[0].name)
        assertEquals("medium.txt", sorted[1].name)
        assertEquals("large.txt", sorted[2].name)
    }

    @Test
    fun `sortFiles SIZE_DESC sorts by size descending`() {
        val files = listOf(
            createFileItem(name = "small.txt", size = 100),
            createFileItem(name = "large.txt", size = 1000),
            createFileItem(name = "medium.txt", size = 500)
        )

        val sorted = repository.sortFiles(files, SortMode.SIZE_DESC)

        assertEquals("large.txt", sorted[0].name)
        assertEquals("medium.txt", sorted[1].name)
        assertEquals("small.txt", sorted[2].name)
    }

    @Test
    fun `sortFiles DATE_ASC sorts by date ascending`() {
        val files = listOf(
            createFileItem(name = "newest.txt", lastModified = 3000),
            createFileItem(name = "oldest.txt", lastModified = 1000),
            createFileItem(name = "middle.txt", lastModified = 2000)
        )

        val sorted = repository.sortFiles(files, SortMode.DATE_ASC)

        assertEquals("oldest.txt", sorted[0].name)
        assertEquals("middle.txt", sorted[1].name)
        assertEquals("newest.txt", sorted[2].name)
    }

    @Test
    fun `sortFiles DATE_DESC sorts by date descending`() {
        val files = listOf(
            createFileItem(name = "oldest.txt", lastModified = 1000),
            createFileItem(name = "newest.txt", lastModified = 3000),
            createFileItem(name = "middle.txt", lastModified = 2000)
        )

        val sorted = repository.sortFiles(files, SortMode.DATE_DESC)

        assertEquals("newest.txt", sorted[0].name)
        assertEquals("middle.txt", sorted[1].name)
        assertEquals("oldest.txt", sorted[2].name)
    }

    @Test
    fun `sortFiles maintains folders first with any sort mode`() {
        val files = listOf(
            createFileItem(name = "z_file.txt", isDirectory = false, size = 100),
            createFileItem(name = "a_folder", isDirectory = true, size = 0),
            createFileItem(name = "b_folder", isDirectory = true, size = 0),
            createFileItem(name = "a_file.txt", isDirectory = false, size = 1000)
        )

        val sortedBySize = repository.sortFiles(files, SortMode.SIZE_DESC)

        // Folders should come first (sorted by size within folders group)
        assertEquals(true, sortedBySize[0].isDirectory)
        assertEquals(true, sortedBySize[1].isDirectory)
        // Files should come after (sorted by size within files group)
        assertEquals(false, sortedBySize[2].isDirectory)
        assertEquals(false, sortedBySize[3].isDirectory)
        assertEquals("a_file.txt", sortedBySize[2].name) // larger file first
        assertEquals("z_file.txt", sortedBySize[3].name)
    }

    @Test
    fun `sortFiles handles empty list`() {
        val sorted = repository.sortFiles(emptyList(), SortMode.NAME_ASC)
        assertEquals(emptyList<FileItem>(), sorted)
    }

    @Test
    fun `sortFiles handles single item`() {
        val files = listOf(createFileItem(name = "only.txt"))
        val sorted = repository.sortFiles(files, SortMode.NAME_ASC)
        assertEquals(1, sorted.size)
        assertEquals("only.txt", sorted[0].name)
    }

    private fun createFileItem(
        name: String = "test.txt",
        isDirectory: Boolean = false,
        size: Long = 1024,
        lastModified: Long = System.currentTimeMillis()
    ) = FileItem(
        path = "/storage/emulated/0/$name",
        name = name,
        isDirectory = isDirectory,
        size = size,
        lastModified = lastModified,
        mimeType = if (isDirectory) "" else "text/plain",
        childCount = if (isDirectory) 0 else null
    )
}
