package com.mauriciotogneri.fileexplorer.ui.screens.iteminfo

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.ApkMetadata
import com.mauriciotogneri.fileexplorer.data.model.AudioMetadata
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.ImageMetadata
import com.mauriciotogneri.fileexplorer.data.model.VideoMetadata
import com.mauriciotogneri.fileexplorer.data.model.ZipMetadata
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter
import com.mauriciotogneri.fileexplorer.testutil.MetadataFixtures
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.DateFormat
import java.util.Date

/**
 * Base rows and the image / audio / video / apk / zip sections of the real [ItemInfoContent].
 * PDF, Office, EPUB, SQLite, VCard, iCalendar and CSV are covered by [ItemInfoMetadataTest].
 *
 * This file used to render a private `TestItemInfoContent` replica. Besides omitting seven metadata
 * sections outright, its `TestInfoRow` used `clickable { }` where production copies the row's value
 * to the clipboard — so tapping a row, the screen's one real interaction, had no coverage at all.
 *
 * Rows live in a plain `Column` + `verticalScroll`, so every row composes regardless of scroll
 * position; assertions use `assertExists()` so they hold on short screens.
 */
@RunWith(AndroidJUnit4::class)
class ItemInfoScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val testFile = FileItem(
        path = "/storage/emulated/0/Download/document.pdf",
        name = "document.pdf",
        isDirectory = false,
        size = 2048L,
        lastModified = 1_700_000_000_000L,
        createdTime = 1_600_000_000_000L,
        mimeType = "application/pdf",
        childCount = null
    )

    private val testFolder = FileItem(
        path = "/storage/emulated/0/Download/Documents",
        name = "Documents",
        isDirectory = true,
        size = 0L,
        lastModified = 1_700_000_000_000L,
        createdTime = 1_600_000_000_000L,
        mimeType = "",
        childCount = 12
    )

    private fun string(resId: Int): String = composeTestRule.activity.getString(resId)

    private fun plural(resId: Int, quantity: Int, vararg args: Any): String =
        composeTestRule.activity.resources.getQuantityString(resId, quantity, *args)

    /** Mirrors the screen's own `formatDate`, so assertions follow the device locale/timezone. */
    private fun formatDate(timestamp: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

    private fun renderInfoContent(
        file: FileItem = testFile,
        folderSize: Long? = null,
        imageMetadata: ImageMetadata? = null,
        audioMetadata: AudioMetadata? = null,
        videoMetadata: VideoMetadata? = null,
        apkMetadata: ApkMetadata? = null,
        zipMetadata: ZipMetadata? = null,
        onOpenFile: () -> Unit = {},
        onCloseClick: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FileExplorerTheme {
                ItemInfoContent(
                    file = file,
                    folderSize = folderSize,
                    imageMetadata = imageMetadata,
                    audioMetadata = audioMetadata,
                    videoMetadata = videoMetadata,
                    pdfMetadata = null,
                    apkMetadata = apkMetadata,
                    zipMetadata = zipMetadata,
                    officeMetadata = null,
                    epubMetadata = null,
                    sqliteMetadata = null,
                    vcardMetadata = null,
                    icalendarMetadata = null,
                    csvMetadata = null,
                    onOpenFile = onOpenFile,
                    onCloseClick = onCloseClick
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    // ==================== Base rows ====================

    @Test
    fun itemInfo_displaysFileName() {
        renderInfoContent()

        composeTestRule.onNodeWithText(string(R.string.info_name)).assertExists()
        composeTestRule.onNodeWithText("document.pdf").assertExists()
    }

    @Test
    fun itemInfo_displaysLocation() {
        renderInfoContent()

        composeTestRule.onNodeWithText(string(R.string.info_location)).assertExists()
        composeTestRule.onNodeWithText(testFile.parentPath).assertExists()
    }

    @Test
    fun itemInfo_displaysCreatedDate() {
        renderInfoContent()

        composeTestRule.onNodeWithText(string(R.string.info_created)).assertExists()
        composeTestRule.onNodeWithText(formatDate(testFile.createdTime)).assertExists()
    }

    @Test
    fun itemInfo_displaysModifiedDate() {
        renderInfoContent()

        composeTestRule.onNodeWithText(string(R.string.info_modified)).assertExists()
        composeTestRule.onNodeWithText(formatDate(testFile.lastModified)).assertExists()
    }

    @Test
    fun itemInfo_displaysFileSize() {
        renderInfoContent()

        composeTestRule.onNodeWithText(string(R.string.info_size)).assertExists()
        composeTestRule.onNodeWithText(testFile.formattedSize).assertExists()
    }

    @Test
    fun itemInfo_displaysMimeType() {
        renderInfoContent()

        composeTestRule.onNodeWithText(string(R.string.info_type)).assertExists()
        composeTestRule.onNodeWithText("application/pdf").assertExists()
    }

    /** A folder has no byte size of its own, so the row must not appear without `folderSize`. */
    @Test
    fun itemInfo_folder_hidesSizeUntilComputed() {
        renderInfoContent(file = testFolder, folderSize = null)

        composeTestRule.onNodeWithText(string(R.string.info_size)).assertDoesNotExist()
    }

    @Test
    fun itemInfo_displaysFolderItemCount() {
        renderInfoContent(file = testFolder)

        composeTestRule.onNodeWithText(string(R.string.info_items)).assertExists()
        composeTestRule.onNodeWithText(plural(R.plurals.item_amount, 12, 12)).assertExists()
    }

    @Test
    fun itemInfo_displaysFolderSize() {
        renderInfoContent(file = testFolder, folderSize = 1024L * 1024L * 5)

        composeTestRule.onNodeWithText(string(R.string.info_size)).assertExists()
        composeTestRule.onNodeWithText(FileSizeFormatter.format(1024L * 1024L * 5)).assertExists()
    }

    @Test
    fun itemInfo_folder_hidesMimeTypeRow() {
        renderInfoContent(file = testFolder)

        composeTestRule.onNodeWithText(string(R.string.info_type)).assertDoesNotExist()
    }

    // ==================== Interactions ====================

    @Test
    fun itemInfo_closeButton_dismisses() {
        var closed = false
        renderInfoContent(onCloseClick = { closed = true })

        composeTestRule.onNodeWithContentDescription(string(R.string.info_close)).performClick()

        assertTrue("Close should invoke onCloseClick", closed)
    }

    /**
     * Tapping any info row copies its value. The replica this file used to assert against wired an
     * empty `clickable { }`, so this — the screen's only real interaction — was untested.
     */
    @Test
    fun itemInfo_tappingRow_copiesValueToClipboard() {
        renderInfoContent()

        composeTestRule.onNodeWithText("document.pdf").performClick()
        composeTestRule.waitForIdle()

        val clipboard = composeTestRule.activity
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipped = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals("Tapping the name row should copy the file name", "document.pdf", clipped)
    }

    @Test
    fun itemInfo_tappingIcon_opensFile() {
        var opened = false
        renderInfoContent(onOpenFile = { opened = true })

        composeTestRule.onNodeWithContentDescription(string(R.string.action_open)).performClick()

        assertTrue("Tapping the file icon should open the file", opened)
    }

    /** A folder has nothing to open, so its icon must not be clickable. */
    @Test
    fun itemInfo_folderIcon_doesNotOpen() {
        var opened = false
        renderInfoContent(file = testFolder, onOpenFile = { opened = true })

        composeTestRule.onNodeWithContentDescription("Documents").performClick()

        org.junit.Assert.assertFalse("A folder icon must not trigger open", opened)
    }

    // ==================== Image metadata ====================

    @Test
    fun imageInfo_displaysDimensions() {
        renderInfoContent(imageMetadata = MetadataFixtures.image(width = 1920, height = 1080))

        composeTestRule.onNodeWithText(string(R.string.info_dimensions)).assertExists()
        composeTestRule.onNodeWithText("1920 × 1080 px").assertExists()
    }

    @Test
    fun imageInfo_partialDimensions_hidesRow() {
        renderInfoContent(imageMetadata = MetadataFixtures.image(width = 1920, height = null))

        composeTestRule.onNodeWithText(string(R.string.info_dimensions)).assertDoesNotExist()
    }

    @Test
    fun imageInfo_displaysCameraInfo() {
        renderInfoContent(
            imageMetadata = MetadataFixtures.image(cameraMake = "Canon", cameraModel = "EOS R5")
        )

        composeTestRule.onNodeWithText(string(R.string.info_camera_make)).assertExists()
        composeTestRule.onNodeWithText("Canon").assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_camera_model)).assertExists()
        composeTestRule.onNodeWithText("EOS R5").assertExists()
    }

    @Test
    fun imageInfo_displaysIso() {
        renderInfoContent(imageMetadata = MetadataFixtures.image(iso = 400))

        composeTestRule.onNodeWithText(string(R.string.info_iso)).assertExists()
        composeTestRule.onNodeWithText("ISO 400").assertExists()
    }

    @Test
    fun imageInfo_displaysGpsCoordinates() {
        renderInfoContent(
            imageMetadata = MetadataFixtures.image(latitude = 37.774929, longitude = -122.419418)
        )

        composeTestRule.onNodeWithText(string(R.string.info_gps_coordinates)).assertExists()
        composeTestRule.onNodeWithText("37.774929, -122.419418").assertExists()
    }

    @Test
    fun imageInfo_gpsMapButton_isDisplayed() {
        renderInfoContent(
            imageMetadata = MetadataFixtures.image(latitude = 37.774929, longitude = -122.419418)
        )

        composeTestRule.onNodeWithContentDescription(string(R.string.info_open_map)).assertExists()
    }

    /** Without coordinates there is nothing to open, so the map button must not be offered. */
    @Test
    fun imageInfo_withoutGps_hidesMapButton() {
        renderInfoContent(imageMetadata = MetadataFixtures.image(width = 100, height = 100))

        composeTestRule.onNodeWithContentDescription(string(R.string.info_open_map)).assertDoesNotExist()
    }

    // ==================== Audio metadata ====================

    @Test
    fun audioInfo_displaysDuration() {
        // 3m 45s
        renderInfoContent(audioMetadata = MetadataFixtures.audio(duration = 225_000L))

        composeTestRule.onNodeWithText(string(R.string.info_duration)).assertExists()
        composeTestRule.onNodeWithText("3:45").assertExists()
    }

    /** Past an hour the format grows an hours component rather than overflowing minutes. */
    @Test
    fun audioInfo_longDuration_includesHours() {
        // 1h 02m 03s
        renderInfoContent(audioMetadata = MetadataFixtures.audio(duration = 3_723_000L))

        composeTestRule.onNodeWithText("1:02:03").assertExists()
    }

    @Test
    fun audioInfo_displaysArtistAndAlbum() {
        renderInfoContent(
            audioMetadata = MetadataFixtures.audio(artist = "Test Artist", album = "Test Album")
        )

        composeTestRule.onNodeWithText(string(R.string.info_artist)).assertExists()
        composeTestRule.onNodeWithText("Test Artist").assertExists()
        composeTestRule.onNodeWithText(string(R.string.info_album)).assertExists()
        composeTestRule.onNodeWithText("Test Album").assertExists()
    }

    @Test
    fun audioInfo_displaysBitrate() {
        renderInfoContent(audioMetadata = MetadataFixtures.audio(bitrate = 320))

        composeTestRule.onNodeWithText(string(R.string.info_bitrate)).assertExists()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.format_kbps, 320))
            .assertExists()
    }

    // ==================== Video metadata ====================

    @Test
    fun videoInfo_displaysDuration() {
        renderInfoContent(videoMetadata = MetadataFixtures.video(duration = 225_000L))

        composeTestRule.onNodeWithText(string(R.string.info_duration)).assertExists()
        composeTestRule.onNodeWithText("3:45").assertExists()
    }

    @Test
    fun videoInfo_displaysResolution() {
        renderInfoContent(videoMetadata = MetadataFixtures.video(width = 3840, height = 2160))

        composeTestRule.onNodeWithText(string(R.string.info_video_resolution)).assertExists()
        composeTestRule.onNodeWithText("3840 × 2160").assertExists()
    }

    @Test
    fun videoInfo_displaysFrameRate() {
        renderInfoContent(videoMetadata = MetadataFixtures.video(frameRate = 59.94f))

        composeTestRule.onNodeWithText(string(R.string.info_frame_rate)).assertExists()
        composeTestRule.onNodeWithText("59.94 fps").assertExists()
    }

    /** A zero rotation is the norm and would be noise, so the row only appears when non-zero. */
    @Test
    fun videoInfo_zeroRotation_hidesRow() {
        renderInfoContent(videoMetadata = MetadataFixtures.video(rotation = 0))

        composeTestRule.onNodeWithText(string(R.string.info_rotation)).assertDoesNotExist()
    }

    @Test
    fun videoInfo_nonZeroRotation_showsRow() {
        renderInfoContent(videoMetadata = MetadataFixtures.video(rotation = 90))

        composeTestRule.onNodeWithText(string(R.string.info_rotation)).assertExists()
    }

    // ==================== APK metadata ====================

    @Test
    fun apkInfo_displaysPackageName() {
        renderInfoContent(apkMetadata = MetadataFixtures.apk(packageName = "com.example.app"))

        composeTestRule.onNodeWithText(string(R.string.info_package_name)).assertExists()
        composeTestRule.onNodeWithText("com.example.app").assertExists()
    }

    @Test
    fun apkInfo_displaysPermissionCount() {
        val permissions = listOf("android.permission.INTERNET", "android.permission.CAMERA")
        renderInfoContent(apkMetadata = MetadataFixtures.apk(permissions = permissions))

        composeTestRule.onNodeWithText(string(R.string.info_permissions)).assertExists()
        composeTestRule.onNodeWithText(plural(R.plurals.permission_count, 2, 2)).assertExists()
    }

    @Test
    fun apkInfo_displaysMinSdk() {
        renderInfoContent(apkMetadata = MetadataFixtures.apk(minSdk = 23))

        composeTestRule.onNodeWithText(string(R.string.info_min_sdk)).assertExists()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.format_api_level, 23))
            .assertExists()
    }

    // ==================== ZIP metadata ====================

    @Test
    fun zipInfo_displaysEntryCount() {
        renderInfoContent(zipMetadata = MetadataFixtures.zip(entryCount = 42))

        composeTestRule.onNodeWithText(string(R.string.info_entries)).assertExists()
        composeTestRule.onNodeWithText(plural(R.plurals.entry_count, 42, 42)).assertExists()
    }

    @Test
    fun zipInfo_displaysUncompressedSize() {
        val uncompressed = 1024L * 1024L * 100L
        renderInfoContent(zipMetadata = MetadataFixtures.zip(uncompressedSize = uncompressed))

        composeTestRule.onNodeWithText(string(R.string.info_uncompressed_size)).assertExists()
        composeTestRule.onNodeWithText(FileSizeFormatter.format(uncompressed)).assertExists()
    }

    /**
     * The compression ratio is derived, not stored: 25 MB out of 100 MB is a 75% saving. The old
     * replica omitted this row entirely, so the arithmetic had no coverage.
     */
    @Test
    fun zipInfo_displaysCompressionRatio() {
        renderInfoContent(
            zipMetadata = MetadataFixtures.zip(
                compressedSize = 25L * 1024 * 1024,
                uncompressedSize = 100L * 1024 * 1024
            )
        )

        composeTestRule.onNodeWithText(string(R.string.info_compression_ratio)).assertExists()
        composeTestRule.onNodeWithText("75.0%").assertExists()
    }

    /** An archive that grew has a negative ratio, which is not worth showing. */
    @Test
    fun zipInfo_negativeRatio_hidesRow() {
        renderInfoContent(
            zipMetadata = MetadataFixtures.zip(
                compressedSize = 120L * 1024 * 1024,
                uncompressedSize = 100L * 1024 * 1024
            )
        )

        composeTestRule.onNodeWithText(string(R.string.info_compression_ratio)).assertDoesNotExist()
    }

    /** Dividing by a zero uncompressed size must be guarded rather than producing NaN/∞. */
    @Test
    fun zipInfo_zeroUncompressedSize_hidesRatioRow() {
        renderInfoContent(
            zipMetadata = MetadataFixtures.zip(compressedSize = 10L, uncompressedSize = 0L)
        )

        composeTestRule.onNodeWithText(string(R.string.info_compression_ratio)).assertDoesNotExist()
    }
}
