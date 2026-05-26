package com.mauriciotogneri.fileexplorer.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector
import com.mauriciotogneri.fileexplorer.data.model.FileTypeInfo

fun getFileIcon(file: FileTypeInfo): ImageVector {
    return when {
        file.isDirectory -> Icons.Outlined.Folder
        file.isImage || file.isSvg -> Icons.Outlined.Image
        file.isPdf -> Icons.Outlined.PictureAsPdf
        file.isAudio -> Icons.Outlined.AudioFile
        file.isVideo -> Icons.Outlined.VideoFile
        file.isApk -> Icons.Outlined.Android
        file.isArchive -> Icons.Outlined.FolderZip
        file.isOfficeDocument -> Icons.Outlined.Description
        file.isEpub -> Icons.Outlined.Book
        file.isFont -> Icons.Outlined.FontDownload
        file.isSqlite -> Icons.Outlined.Storage
        file.isVCard -> Icons.Outlined.Group
        file.isICalendar -> Icons.Outlined.CalendarMonth
        file.isCsv -> Icons.Outlined.TableChart
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}
