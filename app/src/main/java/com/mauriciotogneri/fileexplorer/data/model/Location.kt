package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter

enum class LocationType(
    val icon: ImageVector,
    val titleResId: Int
) {
    DOWNLOADS(Icons.Default.Download, com.mauriciotogneri.fileexplorer.R.string.location_downloads),
    CAMERA(Icons.Default.PhotoCamera, com.mauriciotogneri.fileexplorer.R.string.location_camera),
    IMAGES(Icons.Default.Image, com.mauriciotogneri.fileexplorer.R.string.location_images),
    SCREENSHOTS(Icons.Default.Screenshot, com.mauriciotogneri.fileexplorer.R.string.location_screenshots),
    VIDEOS(Icons.Default.VideoFile, com.mauriciotogneri.fileexplorer.R.string.location_videos),
    DOCUMENTS(Icons.Default.Description, com.mauriciotogneri.fileexplorer.R.string.location_documents),
    AUDIO(Icons.Default.AudioFile, com.mauriciotogneri.fileexplorer.R.string.location_audio),
    PODCASTS(Icons.Default.Podcasts, com.mauriciotogneri.fileexplorer.R.string.location_podcasts)
}

@Immutable
data class Location(
    val type: LocationType,
    val path: String,
    val totalSizeBytes: Long
) {
    val formattedSize: String get() = FileSizeFormatter.format(totalSizeBytes)
}
