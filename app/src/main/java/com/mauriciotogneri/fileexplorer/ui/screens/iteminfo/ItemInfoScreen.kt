package com.mauriciotogneri.fileexplorer.ui.screens.iteminfo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Map
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
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.util.ErrorReporter
import com.mauriciotogneri.fileexplorer.data.util.toDisplayLanguage
import com.mauriciotogneri.fileexplorer.data.model.ApkMetadata
import com.mauriciotogneri.fileexplorer.data.model.AudioChannels
import com.mauriciotogneri.fileexplorer.data.model.AudioMetadata
import com.mauriciotogneri.fileexplorer.data.model.ColorSpace
import com.mauriciotogneri.fileexplorer.data.model.CsvMetadata
import com.mauriciotogneri.fileexplorer.data.model.EpubMetadata
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.FlashMode
import com.mauriciotogneri.fileexplorer.data.model.ICalendarMetadata
import com.mauriciotogneri.fileexplorer.data.model.ImageMetadata
import com.mauriciotogneri.fileexplorer.data.model.ImageOrientation
import com.mauriciotogneri.fileexplorer.data.model.MeteringMode
import com.mauriciotogneri.fileexplorer.data.model.OfficeMetadata
import com.mauriciotogneri.fileexplorer.data.model.PdfMetadata
import com.mauriciotogneri.fileexplorer.data.model.SceneCaptureType
import com.mauriciotogneri.fileexplorer.data.model.SqliteMetadata
import com.mauriciotogneri.fileexplorer.data.model.VCardMetadata
import com.mauriciotogneri.fileexplorer.data.model.VideoColorStandard
import com.mauriciotogneri.fileexplorer.data.model.VideoColorTransfer
import com.mauriciotogneri.fileexplorer.data.model.VideoMetadata
import com.mauriciotogneri.fileexplorer.data.model.WhiteBalanceMode
import com.mauriciotogneri.fileexplorer.data.model.ZipMetadata
import com.mauriciotogneri.fileexplorer.data.util.AppImageLoader
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter
import com.mauriciotogneri.fileexplorer.ui.components.UncompressDialog
import com.mauriciotogneri.fileexplorer.ui.components.UncompressProgressDialog
import com.mauriciotogneri.fileexplorer.ui.util.getFileIcon
import com.mauriciotogneri.fileexplorer.util.IntentUtil
import com.mauriciotogneri.fileexplorer.util.OpenFileResult
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

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ItemInfoUiEvent.OpenFile -> {
                    when (val result = IntentUtil.openFile(context, event.file)) {
                        is OpenFileResult.Handled -> { }
                        is OpenFileResult.RequiresUncompress -> {
                            viewModel.showUncompressDialog(result.file)
                        }
                    }
                }
                is ItemInfoUiEvent.ShowToast -> {
                    Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
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
                            videoMetadata = state.videoMetadata,
                            pdfMetadata = state.pdfMetadata,
                            apkMetadata = state.apkMetadata,
                            zipMetadata = state.zipMetadata,
                            officeMetadata = state.officeMetadata,
                            epubMetadata = state.epubMetadata,
                            sqliteMetadata = state.sqliteMetadata,
                            vcardMetadata = state.vcardMetadata,
                            icalendarMetadata = state.icalendarMetadata,
                            csvMetadata = state.csvMetadata,
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

    state.itemToUncompress?.let {
        UncompressDialog(
            entryCount = state.uncompressEntryCount,
            onDismiss = { viewModel.dismissUncompressDialog() },
            onExtract = { viewModel.confirmUncompress() }
        )
    }

    state.uncompressProgress?.let { progress ->
        UncompressProgressDialog(
            progress = progress,
            onCancel = { viewModel.cancelUncompression() }
        )
    }
}

@Composable
private fun ItemInfoContent(
    file: FileItem,
    imageMetadata: ImageMetadata?,
    audioMetadata: AudioMetadata?,
    videoMetadata: VideoMetadata?,
    pdfMetadata: PdfMetadata?,
    apkMetadata: ApkMetadata?,
    zipMetadata: ZipMetadata?,
    officeMetadata: OfficeMetadata?,
    epubMetadata: EpubMetadata?,
    sqliteMetadata: SqliteMetadata?,
    vcardMetadata: VCardMetadata?,
    icalendarMetadata: ICalendarMetadata?,
    csvMetadata: CsvMetadata?,
    onOpenFile: () -> Unit
) {
    val context = LocalContext.current
    val openLabel = stringResource(R.string.action_open)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 64.dp, bottom = 40.dp)
    ) {
        if (!file.isDirectory && file.hasThumbnailSupport) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(file.path))
                    .size(400)
                    .crossfade(true)
                    .build(),
                imageLoader = AppImageLoader.get(context),
                contentDescription = openLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable(onClickLabel = openLabel, onClick = onOpenFile),
                success = {
                    SubcomposeAsyncImageContent(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentScale = ContentScale.Fit
                    )
                },
                loading = {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp))
                },
                error = {
                    Icon(
                        imageVector = getFileIcon(file),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable(
                        enabled = !file.isDirectory,
                        onClickLabel = openLabel,
                        onClick = onOpenFile
                    ),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = getFileIcon(file),
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
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

        if (videoMetadata != null) {
            VideoMetadataSection(videoMetadata)
        }

        if (pdfMetadata != null) {
            PdfMetadataSection(pdfMetadata)
        }

        if (apkMetadata != null) {
            ApkMetadataSection(apkMetadata)
        }

        if (zipMetadata != null) {
            ZipMetadataSection(zipMetadata)
        }

        if (officeMetadata != null) {
            OfficeMetadataSection(officeMetadata)
        }

        if (epubMetadata != null) {
            EpubMetadataSection(epubMetadata)
        }

        if (sqliteMetadata != null) {
            SqliteMetadataSection(sqliteMetadata)
        }

        if (vcardMetadata != null) {
            VCardMetadataSection(vcardMetadata)
        }

        if (icalendarMetadata != null) {
            ICalendarMetadataSection(icalendarMetadata)
        }

        if (csvMetadata != null) {
            CsvMetadataSection(csvMetadata)
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

    metadata.flash?.let { flash ->
        if (flash.fired()) {
            InfoRow(
                label = stringResource(R.string.info_flash),
                value = stringResource(flash.toStringRes())
            )
        }
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
        val context = LocalContext.current
        val openMapLabel = stringResource(R.string.info_open_map)
        val noMapAppMessage = stringResource(R.string.info_no_map_app)
        InfoRow(
            label = stringResource(R.string.info_gps_coordinates),
            value = String.format("%.6f, %.6f", metadata.latitude, metadata.longitude),
            trailingIcon = {
                IconButton(onClick = { openGeoUri(context, metadata.latitude, metadata.longitude, noMapAppMessage) }) {
                    Icon(
                        imageVector = Icons.Outlined.Map,
                        contentDescription = openMapLabel,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
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
    value: String,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.info_copied)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                copyToClipboard(context, value, copiedMessage)
            }
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailingIcon?.invoke()
    }
}

private fun copyToClipboard(context: Context, text: String, copiedMessage: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("", text)
    clipboard.setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
    }
}

private fun openGeoUri(context: android.content.Context, latitude: Double, longitude: Double, errorMessage: String) {
    try {
        val geoUri = Uri.parse("geo:$latitude,$longitude?z=18")
        val intent = Intent(Intent.ACTION_VIEW, geoUri)
        context.startActivity(intent)
    } catch (e: Exception) {
        ErrorReporter.error(e, "open_geo_uri")
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
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

private fun FlashMode.fired(): Boolean = when (this) {
    FlashMode.FIRED, FlashMode.ON_FIRED, FlashMode.OFF_FIRED, FlashMode.AUTO_FIRED -> true
    else -> false
}

private fun FlashMode.toStringRes(): Int = when (this) {
    FlashMode.AUTO_FIRED -> R.string.flash_auto
    else -> R.string.flash_on
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

@Composable
private fun VideoMetadataSection(metadata: VideoMetadata) {
    metadata.duration?.let {
        InfoRow(
            label = stringResource(R.string.info_duration),
            value = formatDuration(it)
        )
    }

    if (metadata.width != null && metadata.height != null) {
        InfoRow(
            label = stringResource(R.string.info_video_resolution),
            value = "${metadata.width} × ${metadata.height}"
        )
    }

    metadata.frameRate?.let {
        InfoRow(
            label = stringResource(R.string.info_frame_rate),
            value = String.format("%.2f fps", it)
        )
    }

    metadata.bitrate?.let {
        InfoRow(
            label = stringResource(R.string.info_bitrate),
            value = stringResource(R.string.format_kbps, it)
        )
    }

    metadata.rotation?.let {
        if (it != 0) {
            InfoRow(
                label = stringResource(R.string.info_rotation),
                value = stringResource(R.string.format_degrees, it)
            )
        }
    }

    metadata.colorStandard?.let {
        InfoRow(
            label = stringResource(R.string.info_color_standard),
            value = stringResource(it.toStringRes())
        )
    }

    metadata.colorTransfer?.let {
        InfoRow(
            label = stringResource(R.string.info_color_transfer),
            value = stringResource(it.toStringRes())
        )
    }

    metadata.audioSampleRate?.let {
        InfoRow(
            label = stringResource(R.string.info_audio_sample_rate),
            value = stringResource(R.string.format_hz, it)
        )
    }

    metadata.audioBitDepth?.let {
        InfoRow(
            label = stringResource(R.string.info_audio_bit_depth),
            value = stringResource(R.string.format_bit_depth, it)
        )
    }

    metadata.title?.let {
        InfoRow(
            label = stringResource(R.string.info_title),
            value = it
        )
    }

    metadata.dateRecorded?.let {
        InfoRow(
            label = stringResource(R.string.info_date_recorded),
            value = parseAndFormatDate(it)
        )
    }

    if (metadata.latitude != null && metadata.longitude != null) {
        val context = LocalContext.current
        val openMapLabel = stringResource(R.string.info_open_map)
        val noMapAppMessage = stringResource(R.string.info_no_map_app)
        InfoRow(
            label = stringResource(R.string.info_gps_coordinates),
            value = String.format("%.6f, %.6f", metadata.latitude, metadata.longitude),
            trailingIcon = {
                IconButton(onClick = { openGeoUri(context, metadata.latitude, metadata.longitude, noMapAppMessage) }) {
                    Icon(
                        imageVector = Icons.Outlined.Map,
                        contentDescription = openMapLabel,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }

    metadata.author?.let {
        InfoRow(
            label = stringResource(R.string.info_author),
            value = it
        )
    }
}

private fun VideoColorStandard.toStringRes(): Int = when (this) {
    VideoColorStandard.BT601 -> R.string.color_standard_bt601
    VideoColorStandard.BT709 -> R.string.color_standard_bt709
    VideoColorStandard.BT2020 -> R.string.color_standard_bt2020
}

private fun VideoColorTransfer.toStringRes(): Int = when (this) {
    VideoColorTransfer.SDR -> R.string.color_transfer_sdr
    VideoColorTransfer.ST2084 -> R.string.color_transfer_st2084
    VideoColorTransfer.HLG -> R.string.color_transfer_hlg
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

@Composable
private fun PdfMetadataSection(metadata: PdfMetadata) {
    metadata.pageCount?.let {
        InfoRow(
            label = stringResource(R.string.info_pages),
            value = pluralStringResource(R.plurals.page_count, it, it)
        )
    }
}

@Composable
private fun ApkMetadataSection(metadata: ApkMetadata) {
    metadata.appName?.let {
        InfoRow(
            label = stringResource(R.string.info_app_name),
            value = it
        )
    }

    metadata.packageName?.let {
        InfoRow(
            label = stringResource(R.string.info_package_name),
            value = it
        )
    }

    metadata.versionName?.let {
        InfoRow(
            label = stringResource(R.string.info_version_name),
            value = it
        )
    }

    metadata.versionCode?.let {
        InfoRow(
            label = stringResource(R.string.info_version_code),
            value = it.toString()
        )
    }

    metadata.minSdk?.let {
        InfoRow(
            label = stringResource(R.string.info_min_sdk),
            value = stringResource(R.string.format_api_level, it)
        )
    }

    metadata.targetSdk?.let {
        InfoRow(
            label = stringResource(R.string.info_target_sdk),
            value = stringResource(R.string.format_api_level, it)
        )
    }

    metadata.permissions?.let { permissions ->
        InfoRow(
            label = stringResource(R.string.info_permissions),
            value = pluralStringResource(R.plurals.permission_count, permissions.size, permissions.size)
        )
    }
}

@Composable
private fun ZipMetadataSection(metadata: ZipMetadata) {
    metadata.entryCount?.let {
        InfoRow(
            label = stringResource(R.string.info_entries),
            value = pluralStringResource(R.plurals.entry_count, it, it)
        )
    }

    metadata.uncompressedSize?.let {
        InfoRow(
            label = stringResource(R.string.info_uncompressed_size),
            value = FileSizeFormatter.format(it)
        )
    }

    if (metadata.compressedSize != null && metadata.uncompressedSize != null && metadata.uncompressedSize > 0) {
        val ratio = (1.0 - metadata.compressedSize.toDouble() / metadata.uncompressedSize.toDouble()) * 100
        if (ratio > 0) {
            InfoRow(
                label = stringResource(R.string.info_compression_ratio),
                value = String.format("%.1f%%", ratio)
            )
        }
    }
}

@Composable
private fun OfficeMetadataSection(metadata: OfficeMetadata) {
    metadata.title?.let {
        InfoRow(
            label = stringResource(R.string.info_title),
            value = it
        )
    }

    metadata.creator?.let {
        InfoRow(
            label = stringResource(R.string.info_creator),
            value = it
        )
    }

    metadata.subject?.let {
        InfoRow(
            label = stringResource(R.string.info_subject),
            value = it
        )
    }

    metadata.keywords?.let {
        InfoRow(
            label = stringResource(R.string.info_keywords),
            value = it
        )
    }

    metadata.createdDate?.let {
        InfoRow(
            label = stringResource(R.string.info_created),
            value = parseAndFormatDate(it)
        )
    }

    metadata.modifiedDate?.let {
        InfoRow(
            label = stringResource(R.string.info_modified),
            value = parseAndFormatDate(it)
        )
    }
}

@Composable
private fun EpubMetadataSection(metadata: EpubMetadata) {
    metadata.title?.let {
        InfoRow(
            label = stringResource(R.string.info_title),
            value = it
        )
    }

    metadata.creator?.let {
        InfoRow(
            label = stringResource(R.string.info_creator),
            value = it
        )
    }

    metadata.publisher?.let {
        InfoRow(
            label = stringResource(R.string.info_publisher),
            value = it
        )
    }

    metadata.language?.let {
        InfoRow(
            label = stringResource(R.string.info_language),
            value = it.toDisplayLanguage()
        )
    }

    metadata.date?.let {
        InfoRow(
            label = stringResource(R.string.info_date),
            value = parseAndFormatDate(it)
        )
    }

    metadata.description?.let {
        InfoRow(
            label = stringResource(R.string.info_description),
            value = it
        )
    }
}

@Composable
private fun SqliteMetadataSection(metadata: SqliteMetadata) {
    metadata.tableCount?.let {
        InfoRow(
            label = stringResource(R.string.info_tables),
            value = pluralStringResource(R.plurals.table_count, it, it)
        )
    }

    metadata.tableNames?.let { tables ->
        InfoRow(
            label = stringResource(R.string.info_table_names),
            value = tables.joinToString(", ")
        )
    }

    metadata.totalRowCount?.let {
        val safeCount = it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        InfoRow(
            label = stringResource(R.string.info_total_rows),
            value = pluralStringResource(R.plurals.row_count, safeCount, it)
        )
    }
}

@Composable
private fun VCardMetadataSection(metadata: VCardMetadata) {
    metadata.contactCount?.let {
        InfoRow(
            label = stringResource(R.string.info_contacts),
            value = pluralStringResource(R.plurals.contact_count, it, it)
        )
    }

    if (metadata.hasPhoneNumbers == true) {
        InfoRow(
            label = stringResource(R.string.info_has_phone_numbers),
            value = stringResource(R.string.yes)
        )
    }

    if (metadata.hasEmails == true) {
        InfoRow(
            label = stringResource(R.string.info_has_emails),
            value = stringResource(R.string.yes)
        )
    }

    if (metadata.hasPhotos == true) {
        InfoRow(
            label = stringResource(R.string.info_has_photos),
            value = stringResource(R.string.yes)
        )
    }
}

@Composable
private fun ICalendarMetadataSection(metadata: ICalendarMetadata) {
    metadata.eventCount?.let {
        InfoRow(
            label = stringResource(R.string.info_events),
            value = pluralStringResource(R.plurals.event_count, it, it)
        )
    }

    metadata.todoCount?.let {
        InfoRow(
            label = stringResource(R.string.info_todos),
            value = pluralStringResource(R.plurals.todo_count, it, it)
        )
    }

    metadata.earliestDate?.let {
        InfoRow(
            label = stringResource(R.string.info_earliest_date),
            value = parseAndFormatDate(it)
        )
    }

    metadata.latestDate?.let {
        InfoRow(
            label = stringResource(R.string.info_latest_date),
            value = parseAndFormatDate(it)
        )
    }
}

@Composable
private fun CsvMetadataSection(metadata: CsvMetadata) {
    metadata.rowCount?.let {
        InfoRow(
            label = stringResource(R.string.info_rows),
            value = pluralStringResource(R.plurals.row_count, it, it)
        )
    }

    metadata.columnCount?.let {
        InfoRow(
            label = stringResource(R.string.info_columns),
            value = pluralStringResource(R.plurals.column_count, it, it)
        )
    }
}

