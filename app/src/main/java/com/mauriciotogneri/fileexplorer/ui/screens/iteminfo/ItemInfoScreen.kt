package com.mauriciotogneri.fileexplorer.ui.screens.iteminfo

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.AudioChannels
import com.mauriciotogneri.fileexplorer.data.model.AudioMetadata
import com.mauriciotogneri.fileexplorer.data.model.ColorSpace
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.FlashMode
import com.mauriciotogneri.fileexplorer.data.model.ImageMetadata
import com.mauriciotogneri.fileexplorer.data.model.ImageOrientation
import com.mauriciotogneri.fileexplorer.data.model.MeteringMode
import com.mauriciotogneri.fileexplorer.data.model.SceneCaptureType
import com.mauriciotogneri.fileexplorer.data.model.WhiteBalanceMode
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ItemInfoScreen(
    viewModel: ItemInfoViewModel,
    onCloseClick: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val openUnableMessage = stringResource(R.string.open_unable)

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ItemInfoUiEvent.OpenFile -> {
                    val opened = IntentUtil.openFile(context, event.file)
                    if (!opened) {
                        Toast.makeText(context, openUnableMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.error -> {
                    Text(
                        text = stringResource(R.string.info_error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    state.file?.let { file ->
                        ItemInfoContent(
                            file = file,
                            imageMetadata = state.imageMetadata,
                            audioMetadata = state.audioMetadata,
                            onOpenFile = { viewModel.onOpenFile() }
                        )
                    }
                }
            }

            IconButton(
                onClick = onCloseClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.info_close),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun ItemInfoContent(
    file: FileItem,
    imageMetadata: ImageMetadata?,
    audioMetadata: AudioMetadata?,
    onOpenFile: () -> Unit
) {
    val context = LocalContext.current
    val openLabel = stringResource(R.string.action_open)

    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(top = 48.dp)
    ) {
        if (!file.isDirectory && file.isImage) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(file.path))
                    .size(400)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = openLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable(onClickLabel = openLabel, onClick = onOpenFile),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = !file.isDirectory,
                        onClickLabel = openLabel,
                        onClick = onOpenFile
                    ),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when {
                        file.isDirectory -> Icons.Outlined.Folder
                        file.isPdf -> Icons.Outlined.PictureAsPdf
                        file.isAudio -> Icons.Outlined.AudioFile
                        file.isVideo -> Icons.Outlined.VideoFile
                        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                    },
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = if (file.isDirectory) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        InfoRow(
            label = stringResource(R.string.info_name),
            value = file.name
        )

        InfoRow(
            label = stringResource(R.string.info_location),
            value = file.parentPath
        )

        InfoRow(
            label = stringResource(R.string.info_created),
            value = formatDate(file.createdTime)
        )

        InfoRow(
            label = stringResource(R.string.info_modified),
            value = formatDate(file.lastModified)
        )

        if (!file.isDirectory) {
            InfoRow(
                label = stringResource(R.string.info_size),
                value = file.formattedSize
            )
        }

        if (file.isDirectory && file.childCount != null) {
            InfoRow(
                label = stringResource(R.string.info_size),
                value = pluralStringResource(
                    R.plurals.item_amount,
                    file.childCount,
                    file.childCount
                )
            )
        }

        if (!file.isDirectory && file.mimeType.isNotBlank()) {
            InfoRow(
                label = stringResource(R.string.info_type),
                value = file.mimeType
            )
        }

        if (imageMetadata != null) {
            ImageMetadataSection(imageMetadata)
        }

        if (audioMetadata != null) {
            AudioMetadataSection(audioMetadata)
        }
    }
}

@Composable
private fun ImageMetadataSection(metadata: ImageMetadata) {
    if (metadata.width != null && metadata.height != null) {
        InfoRow(
            label = stringResource(R.string.info_dimensions),
            value = "${metadata.width} × ${metadata.height} px"
        )
    }

    metadata.megapixels?.let {
        InfoRow(
            label = stringResource(R.string.info_megapixels),
            value = String.format("%.1f MP", it)
        )
    }

    metadata.dateTaken?.let {
        InfoRow(
            label = stringResource(R.string.info_date_taken),
            value = parseAndFormatDate(it)
        )
    }

    metadata.cameraMake?.let {
        InfoRow(
            label = stringResource(R.string.info_camera_make),
            value = it
        )
    }

    metadata.cameraModel?.let {
        InfoRow(
            label = stringResource(R.string.info_camera_model),
            value = it
        )
    }

    metadata.lensMake?.let {
        InfoRow(
            label = stringResource(R.string.info_lens_make),
            value = it
        )
    }

    metadata.lensModel?.let {
        InfoRow(
            label = stringResource(R.string.info_lens_model),
            value = it
        )
    }

    metadata.iso?.let {
        InfoRow(
            label = stringResource(R.string.info_iso),
            value = "ISO $it"
        )
    }

    metadata.aperture?.let {
        InfoRow(
            label = stringResource(R.string.info_aperture),
            value = String.format("f/%.1f", it)
        )
    }

    metadata.focalLength?.let {
        InfoRow(
            label = stringResource(R.string.info_focal_length),
            value = String.format("%.1f mm", it)
        )
    }

    metadata.exposureTime?.let {
        InfoRow(
            label = stringResource(R.string.info_exposure_time),
            value = it
        )
    }

    metadata.flash?.let {
        InfoRow(
            label = stringResource(R.string.info_flash),
            value = stringResource(it.toStringRes())
        )
    }

    metadata.whiteBalance?.let {
        InfoRow(
            label = stringResource(R.string.info_white_balance),
            value = stringResource(it.toStringRes())
        )
    }

    metadata.meteringMode?.let {
        InfoRow(
            label = stringResource(R.string.info_metering_mode),
            value = stringResource(it.toStringRes())
        )
    }

    metadata.sceneCaptureType?.let {
        InfoRow(
            label = stringResource(R.string.info_scene_type),
            value = stringResource(it.toStringRes())
        )
    }

    metadata.orientation?.let {
        InfoRow(
            label = stringResource(R.string.info_orientation),
            value = stringResource(it.toStringRes())
        )
    }

    metadata.colorSpace?.let {
        InfoRow(
            label = stringResource(R.string.info_color_space),
            value = stringResource(it.toStringRes())
        )
    }

    metadata.software?.let {
        InfoRow(
            label = stringResource(R.string.info_software),
            value = it
        )
    }

    metadata.artist?.let {
        InfoRow(
            label = stringResource(R.string.info_artist),
            value = it
        )
    }

    metadata.copyright?.let {
        InfoRow(
            label = stringResource(R.string.info_copyright),
            value = it
        )
    }

    if (metadata.latitude != null && metadata.longitude != null) {
        InfoRow(
            label = stringResource(R.string.info_gps_coordinates),
            value = String.format("%.6f, %.6f", metadata.latitude, metadata.longitude)
        )
    }

    metadata.altitude?.let {
        InfoRow(
            label = stringResource(R.string.info_altitude),
            value = String.format("%.1f m", it)
        )
    }

    metadata.digitalZoom?.let {
        InfoRow(
            label = stringResource(R.string.info_digital_zoom),
            value = String.format("%.1fx", it)
        )
    }

    if (metadata.resolutionX != null && metadata.resolutionY != null) {
        InfoRow(
            label = stringResource(R.string.info_resolution),
            value = String.format("%.0f × %.0f DPI", metadata.resolutionX, metadata.resolutionY)
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "-"
    val date = Date(timestamp)
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    return dateFormat.format(date)
}

private fun parseAndFormatDate(dateString: String): String {
    val inputFormats = listOf(
        "yyyy:MM:dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
        "yyyyMMdd'T'HHmmss",
        "yyyyMMdd",
        "yyyy"
    )

    for (format in inputFormats) {
        try {
            val parser = SimpleDateFormat(format, Locale.US)
            val date = parser.parse(dateString) ?: continue
            val outputFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            return outputFormat.format(date)
        } catch (e: Exception) {
            continue
        }
    }

    return dateString
}

private fun FlashMode.toStringRes(): Int = when (this) {
    FlashMode.FIRED -> R.string.flash_fired
    FlashMode.DID_NOT_FIRE -> R.string.flash_did_not_fire
    FlashMode.ON_FIRED -> R.string.flash_on_fired
    FlashMode.ON_DID_NOT_FIRE -> R.string.flash_on_did_not_fire
    FlashMode.OFF_FIRED -> R.string.flash_off_fired
    FlashMode.OFF_DID_NOT_FIRE -> R.string.flash_off_did_not_fire
    FlashMode.AUTO_FIRED -> R.string.flash_auto_fired
    FlashMode.AUTO_DID_NOT_FIRE -> R.string.flash_auto_did_not_fire
}

private fun WhiteBalanceMode.toStringRes(): Int = when (this) {
    WhiteBalanceMode.AUTO -> R.string.white_balance_auto
    WhiteBalanceMode.MANUAL -> R.string.white_balance_manual
}

private fun MeteringMode.toStringRes(): Int = when (this) {
    MeteringMode.AVERAGE -> R.string.metering_average
    MeteringMode.CENTER_WEIGHTED -> R.string.metering_center_weighted
    MeteringMode.SPOT -> R.string.metering_spot
    MeteringMode.MULTI_SPOT -> R.string.metering_multi_spot
    MeteringMode.PATTERN -> R.string.metering_pattern
    MeteringMode.PARTIAL -> R.string.metering_partial
}

private fun SceneCaptureType.toStringRes(): Int = when (this) {
    SceneCaptureType.STANDARD -> R.string.scene_standard
    SceneCaptureType.LANDSCAPE -> R.string.scene_landscape
    SceneCaptureType.PORTRAIT -> R.string.scene_portrait
    SceneCaptureType.NIGHT -> R.string.scene_night
}

private fun ImageOrientation.toStringRes(): Int = when (this) {
    ImageOrientation.NORMAL -> R.string.orientation_normal
    ImageOrientation.FLIP_HORIZONTAL -> R.string.orientation_flip_horizontal
    ImageOrientation.ROTATE_180 -> R.string.orientation_rotate_180
    ImageOrientation.FLIP_VERTICAL -> R.string.orientation_flip_vertical
    ImageOrientation.TRANSPOSE -> R.string.orientation_transpose
    ImageOrientation.ROTATE_90_CW -> R.string.orientation_rotate_90_cw
    ImageOrientation.TRANSVERSE -> R.string.orientation_transverse
    ImageOrientation.ROTATE_270_CW -> R.string.orientation_rotate_270_cw
}

private fun ColorSpace.toStringRes(): Int = when (this) {
    ColorSpace.SRGB -> R.string.color_space_srgb
    ColorSpace.ADOBE_RGB -> R.string.color_space_adobe_rgb
    ColorSpace.UNCALIBRATED -> R.string.color_space_uncalibrated
}

@Composable
private fun AudioMetadataSection(metadata: AudioMetadata) {
    metadata.duration?.let {
        InfoRow(
            label = stringResource(R.string.info_duration),
            value = formatDuration(it)
        )
    }

    metadata.title?.let {
        InfoRow(
            label = stringResource(R.string.info_title),
            value = it
        )
    }

    metadata.artist?.let {
        InfoRow(
            label = stringResource(R.string.info_artist),
            value = it
        )
    }

    metadata.albumArtist?.let {
        InfoRow(
            label = stringResource(R.string.info_album_artist),
            value = it
        )
    }

    metadata.album?.let {
        InfoRow(
            label = stringResource(R.string.info_album),
            value = it
        )
    }

    metadata.trackNumber?.let {
        InfoRow(
            label = stringResource(R.string.info_track_number),
            value = it
        )
    }

    metadata.discNumber?.let {
        InfoRow(
            label = stringResource(R.string.info_disc_number),
            value = it
        )
    }

    metadata.genre?.let {
        InfoRow(
            label = stringResource(R.string.info_genre),
            value = it
        )
    }

    metadata.year?.let {
        InfoRow(
            label = stringResource(R.string.info_year),
            value = it
        )
    }

    metadata.composer?.let {
        InfoRow(
            label = stringResource(R.string.info_composer),
            value = it
        )
    }

    metadata.writer?.let {
        InfoRow(
            label = stringResource(R.string.info_writer),
            value = it
        )
    }

    metadata.bitrate?.let {
        InfoRow(
            label = stringResource(R.string.info_bitrate),
            value = stringResource(R.string.format_kbps, it)
        )
    }

    metadata.sampleRate?.let {
        InfoRow(
            label = stringResource(R.string.info_sample_rate),
            value = stringResource(R.string.format_hz, it)
        )
    }

    metadata.bitDepth?.let {
        InfoRow(
            label = stringResource(R.string.info_bit_depth),
            value = stringResource(R.string.format_bit_depth, it)
        )
    }

    metadata.channels?.let {
        InfoRow(
            label = stringResource(R.string.info_channels),
            value = stringResource(it.toStringRes())
        )
    }

    metadata.isCompilation?.let {
        if (it) {
            InfoRow(
                label = stringResource(R.string.info_compilation),
                value = stringResource(R.string.yes)
            )
        }
    }

    metadata.recordingDate?.let {
        InfoRow(
            label = stringResource(R.string.info_recording_date),
            value = parseAndFormatDate(it)
        )
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun AudioChannels.toStringRes(): Int = when (this) {
    AudioChannels.MONO -> R.string.channels_mono
    AudioChannels.STEREO -> R.string.channels_stereo
}
