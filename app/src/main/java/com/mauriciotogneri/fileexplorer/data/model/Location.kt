package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter

enum class LocationType(
    val icon: ImageVector,
    val titleResId: Int
) {
    DOWNLOADS(Icons.Outlined.FileDownload, com.mauriciotogneri.fileexplorer.R.string.location_downloads),
    CAMERA(Icons.Outlined.PhotoCamera, com.mauriciotogneri.fileexplorer.R.string.location_camera),
    IMAGES(Icons.Outlined.Image, com.mauriciotogneri.fileexplorer.R.string.location_images),
    SCREENSHOTS(Icons.Outlined.Fullscreen, com.mauriciotogneri.fileexplorer.R.string.location_screenshots),
    VIDEOS(Icons.Outlined.PlayCircle, com.mauriciotogneri.fileexplorer.R.string.location_videos),
    DOCUMENTS(Icons.Outlined.Description, com.mauriciotogneri.fileexplorer.R.string.location_documents),
    AUDIO(Icons.Outlined.MusicNote, com.mauriciotogneri.fileexplorer.R.string.location_audio),
    PODCASTS(Icons.Outlined.Mic, com.mauriciotogneri.fileexplorer.R.string.location_podcasts)
}

@Immutable
data class Location(
    val type: LocationType,
    val path: String,
    val totalSizeBytes: Long
) {
    val formattedSize: String get() = FileSizeFormatter.format(totalSizeBytes)
}
