package com.mauriciotogneri.fileexplorer.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoredEntryDestinationResolverTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `resolves a path nothing occupies as missing`() {
        val path = File(temporaryFolder.root, "gone.pdf").absolutePath

        val destination = StoredEntryDestinationResolver.resolve(path, "gone.pdf", "application/pdf")

        assertEquals(StoredEntryDestination.Missing, destination)
    }

    @Test
    fun `resolves an existing file as open`() {
        val file = temporaryFolder.newFile("report.pdf")

        val destination = StoredEntryDestinationResolver.resolve(file.absolutePath, "report.pdf", "application/pdf")

        val open = destination as StoredEntryDestination.Open
        assertEquals(file.absolutePath, open.file.path)
        assertEquals("report.pdf", open.file.name)
        assertFalse(open.file.isDirectory)
        assertEquals("application/pdf", open.file.mimeType)
    }

    // The case the resolver exists for: a stored entry recorded as a file, whose path a directory
    // now occupies. Routed by the extension it would reach IntentUtil.openFile as an APK.
    @Test
    fun `resolves a directory stored under an apk name as a folder`() {
        val folder = temporaryFolder.newFolder("archive.apk")

        val destination = StoredEntryDestinationResolver.resolve(
            folder.absolutePath,
            "archive.apk",
            "application/vnd.android.package-archive"
        )

        assertEquals(StoredEntryDestination.Folder(folder.absolutePath, "archive.apk"), destination)
    }

    @Test
    fun `resolves a directory stored under a document name as a folder`() {
        val folder = temporaryFolder.newFolder("notes.md")

        val destination = StoredEntryDestinationResolver.resolve(folder.absolutePath, "notes.md", "text/markdown")

        assertEquals(StoredEntryDestination.Folder(folder.absolutePath, "notes.md"), destination)
    }

    // A favorited directory is stored with an empty mimeType, so an entry that drifted from
    // directory to file has nothing to classify it. Without the refill isZip stays false and the
    // uncompress branch is skipped.
    @Test
    fun `refills an empty mime type from the file on disk`() {
        val file = temporaryFolder.newFile("backup.zip")

        val destination = StoredEntryDestinationResolver.resolve(file.absolutePath, "backup.zip", "")

        val open = destination as StoredEntryDestination.Open
        assertEquals("application/zip", open.file.mimeType)
        assertTrue(open.file.isZip)
    }

    @Test
    fun `preserves a stored mime type instead of re-deriving it`() {
        val file = temporaryFolder.newFile("backup.zip")

        val destination = StoredEntryDestinationResolver.resolve(file.absolutePath, "backup.zip", "application/x-zip-compressed")

        val open = destination as StoredEntryDestination.Open
        assertEquals("application/x-zip-compressed", open.file.mimeType)
    }
}
