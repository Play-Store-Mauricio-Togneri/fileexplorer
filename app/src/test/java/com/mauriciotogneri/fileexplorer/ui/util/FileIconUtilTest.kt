package com.mauriciotogneri.fileexplorer.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.VideoFile
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import org.junit.Assert.assertEquals
import org.junit.Test

class FileIconUtilTest {

    @Test
    fun `getFileIcon returns Folder icon for directories`() {
        val file = createFileItem(isDirectory = true)
        assertEquals(Icons.Outlined.Folder, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns Image icon for images`() {
        val file = createFileItem(mimeType = "image/jpeg")
        assertEquals(Icons.Outlined.Image, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns Image icon for SVG files`() {
        val file = createFileItem(name = "image.svg", mimeType = "image/svg+xml")
        assertEquals(Icons.Outlined.Image, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns PictureAsPdf icon for PDF files`() {
        val file = createFileItem(mimeType = "application/pdf")
        assertEquals(Icons.Outlined.PictureAsPdf, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns AudioFile icon for audio files`() {
        val file = createFileItem(mimeType = "audio/mpeg")
        assertEquals(Icons.Outlined.AudioFile, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns VideoFile icon for video files`() {
        val file = createFileItem(mimeType = "video/mp4")
        assertEquals(Icons.Outlined.VideoFile, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns Android icon for APK files`() {
        val file = createFileItem(mimeType = "application/vnd.android.package-archive")
        assertEquals(Icons.Outlined.Android, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns FolderZip icon for ZIP files`() {
        val file = createFileItem(mimeType = "application/zip")
        assertEquals(Icons.Outlined.FolderZip, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns Description icon for Office documents`() {
        val file = createFileItem(mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        assertEquals(Icons.Outlined.Description, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns Book icon for EPUB files`() {
        val file = createFileItem(mimeType = "application/epub+zip")
        assertEquals(Icons.Outlined.Book, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns Storage icon for SQLite files`() {
        val file = createFileItem(name = "database.db", mimeType = "application/x-sqlite3")
        assertEquals(Icons.Outlined.Storage, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns Contacts icon for vCard files`() {
        val file = createFileItem(name = "contacts.vcf", mimeType = "text/vcard")
        assertEquals(Icons.Outlined.Contacts, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns CalendarMonth icon for iCalendar files`() {
        val file = createFileItem(name = "calendar.ics", mimeType = "text/calendar")
        assertEquals(Icons.Outlined.CalendarMonth, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns TableChart icon for CSV files`() {
        val file = createFileItem(name = "data.csv", mimeType = "text/csv")
        assertEquals(Icons.Outlined.TableChart, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns InsertDriveFile icon for unknown files`() {
        val file = createFileItem(mimeType = "application/octet-stream")
        assertEquals(Icons.AutoMirrored.Outlined.InsertDriveFile, getFileIcon(file))
    }

    private fun createFileItem(
        name: String = "test.txt",
        isDirectory: Boolean = false,
        mimeType: String = "text/plain"
    ): FileItem = FileItem(
        path = "/test/$name",
        name = name,
        isDirectory = isDirectory,
        size = 1024L,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = mimeType,
        childCount = if (isDirectory) 0 else null
    )
}
