package com.mauriciotogneri.fileexplorer.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.VideoFile
import com.mauriciotogneri.fileexplorer.data.model.FileTypeInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class FileIconUtilTest {

    private data class TestFileTypeInfo(
        override val isDirectory: Boolean = false,
        override val isImage: Boolean = false,
        override val isSvg: Boolean = false,
        override val isPdf: Boolean = false,
        override val isAudio: Boolean = false,
        override val isVideo: Boolean = false,
        override val isApk: Boolean = false,
        override val isZip: Boolean = false,
        override val isArchive: Boolean = false,
        override val isOfficeDocument: Boolean = false,
        override val isEpub: Boolean = false,
        override val isFont: Boolean = false,
        override val isSqlite: Boolean = false,
        override val isVCard: Boolean = false,
        override val isICalendar: Boolean = false,
        override val isCsv: Boolean = false,
        override val isText: Boolean = false
    ) : FileTypeInfo

    @Test
    fun `getFileIcon returns Folder icon for directories`() {
        val file = TestFileTypeInfo(isDirectory = true)
        assertEquals(Icons.Outlined.Folder, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns Image icon for images`() {
        val file = TestFileTypeInfo(isImage = true)
        assertEquals(Icons.Outlined.Image, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns Image icon for SVG files`() {
        val file = TestFileTypeInfo(isSvg = true)
        assertEquals(Icons.Outlined.Image, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns PictureAsPdf icon for PDF files`() {
        val file = TestFileTypeInfo(isPdf = true)
        assertEquals(Icons.Outlined.PictureAsPdf, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns AudioFile icon for audio files`() {
        val file = TestFileTypeInfo(isAudio = true)
        assertEquals(Icons.Outlined.AudioFile, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns VideoFile icon for video files`() {
        val file = TestFileTypeInfo(isVideo = true)
        assertEquals(Icons.Outlined.VideoFile, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns Android icon for APK files`() {
        val file = TestFileTypeInfo(isApk = true)
        assertEquals(Icons.Outlined.Android, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns FolderZip icon for archive files`() {
        val file = TestFileTypeInfo(isArchive = true)
        assertEquals(Icons.Outlined.FolderZip, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns Description icon for Office documents`() {
        val file = TestFileTypeInfo(isOfficeDocument = true)
        assertEquals(Icons.Outlined.Description, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns Book icon for EPUB files`() {
        val file = TestFileTypeInfo(isEpub = true)
        assertEquals(Icons.Outlined.Book, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns FontDownload icon for font files`() {
        val file = TestFileTypeInfo(isFont = true)
        assertEquals(Icons.Outlined.FontDownload, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns Storage icon for SQLite files`() {
        val file = TestFileTypeInfo(isSqlite = true)
        assertEquals(Icons.Outlined.Storage, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns Group icon for vCard files`() {
        val file = TestFileTypeInfo(isVCard = true)
        assertEquals(Icons.Outlined.Group, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns CalendarMonth icon for iCalendar files`() {
        val file = TestFileTypeInfo(isICalendar = true)
        assertEquals(Icons.Outlined.CalendarMonth, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns TableChart icon for CSV files`() {
        val file = TestFileTypeInfo(isCsv = true)
        assertEquals(Icons.Outlined.TableChart, getFileIcon(file))
    }

    @Test
    fun `getFileIcon returns InsertDriveFile icon for unknown files`() {
        val file = TestFileTypeInfo()
        assertEquals(Icons.AutoMirrored.Outlined.InsertDriveFile, getFileIcon(file))
    }
}
