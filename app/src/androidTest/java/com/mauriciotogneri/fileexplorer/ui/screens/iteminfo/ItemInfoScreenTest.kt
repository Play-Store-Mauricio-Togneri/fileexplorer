package com.mauriciotogneri.fileexplorer.ui.screens.iteminfo

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.ApkMetadata
import com.mauriciotogneri.fileexplorer.data.model.AudioMetadata
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.ImageMetadata
import com.mauriciotogneri.fileexplorer.data.model.VideoMetadata
import com.mauriciotogneri.fileexplorer.data.model.ZipMetadata
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.util.getFileIcon
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ItemInfoScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val testFile = FileItem(
        path = "/storage/emulated/0/Documents/test_document.txt",
        name = "test_document.txt",
        isDirectory = false,
        size = 1024L,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis() - 86400000L,
        mimeType = "text/plain",
        childCount = null
    )

    private val testFolder = FileItem(
        path = "/storage/emulated/0/Documents/TestFolder",
        name = "TestFolder",
        isDirectory = true,
        size = 0L,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis() - 86400000L,
        mimeType = "",
        childCount = 15
    )

    private fun formatDate(timestamp: Long): String {
        if (timestamp <= 0) return "-"
        val date = Date(timestamp)
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        return dateFormat.format(date)
    }

    // ==================== Basic Info Tests ====================

    @Test
    fun itemInfo_displaysFileName() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = testFile,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("test_document.txt").assertIsDisplayed()
    }

    @Test
    fun itemInfo_displaysLocation() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = testFile,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("/storage/emulated/0/Documents").assertIsDisplayed()
    }

    @Test
    fun itemInfo_displaysCreatedDate() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = testFile,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val createdLabel = composeTestRule.activity.getString(R.string.info_created)
        composeTestRule.onNodeWithText(createdLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(formatDate(testFile.createdTime)).assertIsDisplayed()
    }

    @Test
    fun itemInfo_displaysModifiedDate() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = testFile,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val modifiedLabel = composeTestRule.activity.getString(R.string.info_modified)
        composeTestRule.onNodeWithText(modifiedLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(formatDate(testFile.lastModified)).assertIsDisplayed()
    }

    @Test
    fun itemInfo_displaysFileSize() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = testFile,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val sizeLabel = composeTestRule.activity.getString(R.string.info_size)
        composeTestRule.onNodeWithText(sizeLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(testFile.formattedSize).assertIsDisplayed()
    }

    @Test
    fun itemInfo_displaysFolderItemCount() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = testFolder,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val itemsLabel = composeTestRule.activity.getString(R.string.info_items)
        composeTestRule.onNodeWithText(itemsLabel).assertIsDisplayed()
        val itemCountText = composeTestRule.activity.resources.getQuantityString(
            R.plurals.item_amount,
            15,
            15
        )
        composeTestRule.onNodeWithText(itemCountText).assertIsDisplayed()
    }

    @Test
    fun itemInfo_displaysFolderSize() {
        val folderSize = 1024L * 1024L * 50L

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = testFolder,
                    folderSize = folderSize,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(FileSizeFormatter.format(folderSize)).assertIsDisplayed()
    }

    @Test
    fun itemInfo_displaysMimeType() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = testFile,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val typeLabel = composeTestRule.activity.getString(R.string.info_type)
        composeTestRule.onNodeWithText(typeLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText("text/plain").assertIsDisplayed()
    }

    @Test
    fun itemInfo_closeButton_dismisses() {
        var closeCalled = false

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = testFile,
                    onCloseClick = { closeCalled = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.info_close)
        ).performClick()

        assertTrue("Close callback should be triggered", closeCalled)
    }

    @Test
    fun itemInfo_loading_showsSpinner() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoLoading()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("loading_indicator").assertIsDisplayed()
    }

    @Test
    fun itemInfo_error_showsErrorMessage() {
        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoError()
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.info_error))
            .assertIsDisplayed()
    }

    // ==================== Image Metadata Tests ====================

    @Test
    fun imageInfo_displaysDimensions() {
        val imageFile = testFile.copy(
            name = "photo.jpg",
            path = "/storage/emulated/0/DCIM/photo.jpg",
            mimeType = "image/jpeg"
        )
        val imageMetadata = ImageMetadata(
            width = 1920,
            height = 1080,
            megapixels = null,
            dateTaken = null,
            cameraMake = null,
            cameraModel = null,
            lensMake = null,
            lensModel = null,
            iso = null,
            aperture = null,
            focalLength = null,
            exposureTime = null,
            flash = null,
            whiteBalance = null,
            meteringMode = null,
            sceneCaptureType = null,
            orientation = null,
            colorSpace = null,
            software = null,
            artist = null,
            copyright = null,
            latitude = null,
            longitude = null,
            altitude = null,
            digitalZoom = null,
            resolutionX = null,
            resolutionY = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = imageFile,
                    imageMetadata = imageMetadata,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val dimensionsLabel = composeTestRule.activity.getString(R.string.info_dimensions)
        composeTestRule.onNodeWithText(dimensionsLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText("1920 × 1080 px").assertIsDisplayed()
    }

    @Test
    fun imageInfo_displaysCameraInfo() {
        val imageFile = testFile.copy(
            name = "photo.jpg",
            path = "/storage/emulated/0/DCIM/photo.jpg",
            mimeType = "image/jpeg"
        )
        val imageMetadata = ImageMetadata(
            width = null,
            height = null,
            megapixels = null,
            dateTaken = null,
            cameraMake = "Canon",
            cameraModel = "EOS R5",
            lensMake = null,
            lensModel = null,
            iso = null,
            aperture = null,
            focalLength = null,
            exposureTime = null,
            flash = null,
            whiteBalance = null,
            meteringMode = null,
            sceneCaptureType = null,
            orientation = null,
            colorSpace = null,
            software = null,
            artist = null,
            copyright = null,
            latitude = null,
            longitude = null,
            altitude = null,
            digitalZoom = null,
            resolutionX = null,
            resolutionY = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = imageFile,
                    imageMetadata = imageMetadata,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Canon").assertIsDisplayed()
        composeTestRule.onNodeWithText("EOS R5").assertIsDisplayed()
    }

    @Test
    fun imageInfo_displaysGpsCoordinates() {
        val imageFile = testFile.copy(
            name = "photo.jpg",
            path = "/storage/emulated/0/DCIM/photo.jpg",
            mimeType = "image/jpeg"
        )
        val imageMetadata = ImageMetadata(
            width = null,
            height = null,
            megapixels = null,
            dateTaken = null,
            cameraMake = null,
            cameraModel = null,
            lensMake = null,
            lensModel = null,
            iso = null,
            aperture = null,
            focalLength = null,
            exposureTime = null,
            flash = null,
            whiteBalance = null,
            meteringMode = null,
            sceneCaptureType = null,
            orientation = null,
            colorSpace = null,
            software = null,
            artist = null,
            copyright = null,
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = null,
            digitalZoom = null,
            resolutionX = null,
            resolutionY = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = imageFile,
                    imageMetadata = imageMetadata,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val gpsLabel = composeTestRule.activity.getString(R.string.info_gps_coordinates)
        composeTestRule.onNodeWithText(gpsLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(String.format(Locale.US, "%.6f, %.6f", 37.7749, -122.4194))
            .assertIsDisplayed()
    }

    @Test
    fun imageInfo_gpsMapButton_isDisplayed() {
        val imageFile = testFile.copy(
            name = "photo.jpg",
            path = "/storage/emulated/0/DCIM/photo.jpg",
            mimeType = "image/jpeg"
        )
        val imageMetadata = ImageMetadata(
            width = null,
            height = null,
            megapixels = null,
            dateTaken = null,
            cameraMake = null,
            cameraModel = null,
            lensMake = null,
            lensModel = null,
            iso = null,
            aperture = null,
            focalLength = null,
            exposureTime = null,
            flash = null,
            whiteBalance = null,
            meteringMode = null,
            sceneCaptureType = null,
            orientation = null,
            colorSpace = null,
            software = null,
            artist = null,
            copyright = null,
            latitude = 37.7749,
            longitude = -122.4194,
            altitude = null,
            digitalZoom = null,
            resolutionX = null,
            resolutionY = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = imageFile,
                    imageMetadata = imageMetadata,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(
            composeTestRule.activity.getString(R.string.info_open_map)
        ).assertIsDisplayed()
    }

    // ==================== Audio Metadata Tests ====================

    @Test
    fun audioInfo_displaysDuration() {
        val audioFile = testFile.copy(
            name = "song.mp3",
            path = "/storage/emulated/0/Music/song.mp3",
            mimeType = "audio/mpeg"
        )
        val audioMetadata = AudioMetadata(
            duration = 215000L,
            artist = null,
            album = null,
            title = null,
            genre = null,
            year = null,
            bitrate = null,
            trackNumber = null,
            discNumber = null,
            composer = null,
            albumArtist = null,
            writer = null,
            sampleRate = null,
            bitDepth = null,
            channels = null,
            isCompilation = null,
            recordingDate = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = audioFile,
                    audioMetadata = audioMetadata,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val durationLabel = composeTestRule.activity.getString(R.string.info_duration)
        composeTestRule.onNodeWithText(durationLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText("3:35").assertIsDisplayed()
    }

    @Test
    fun audioInfo_displaysArtist() {
        val audioFile = testFile.copy(
            name = "song.mp3",
            path = "/storage/emulated/0/Music/song.mp3",
            mimeType = "audio/mpeg"
        )
        val audioMetadata = AudioMetadata(
            duration = null,
            artist = "Test Artist",
            album = null,
            title = null,
            genre = null,
            year = null,
            bitrate = null,
            trackNumber = null,
            discNumber = null,
            composer = null,
            albumArtist = null,
            writer = null,
            sampleRate = null,
            bitDepth = null,
            channels = null,
            isCompilation = null,
            recordingDate = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = audioFile,
                    audioMetadata = audioMetadata,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val artistLabel = composeTestRule.activity.getString(R.string.info_artist)
        composeTestRule.onNodeWithText(artistLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Artist").assertIsDisplayed()
    }

    @Test
    fun audioInfo_displaysAlbum() {
        val audioFile = testFile.copy(
            name = "song.mp3",
            path = "/storage/emulated/0/Music/song.mp3",
            mimeType = "audio/mpeg"
        )
        val audioMetadata = AudioMetadata(
            duration = null,
            artist = null,
            album = "Test Album",
            title = null,
            genre = null,
            year = null,
            bitrate = null,
            trackNumber = null,
            discNumber = null,
            composer = null,
            albumArtist = null,
            writer = null,
            sampleRate = null,
            bitDepth = null,
            channels = null,
            isCompilation = null,
            recordingDate = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = audioFile,
                    audioMetadata = audioMetadata,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val albumLabel = composeTestRule.activity.getString(R.string.info_album)
        composeTestRule.onNodeWithText(albumLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Album").assertIsDisplayed()
    }

    @Test
    fun audioInfo_displaysBitrate() {
        val audioFile = testFile.copy(
            name = "song.mp3",
            path = "/storage/emulated/0/Music/song.mp3",
            mimeType = "audio/mpeg"
        )
        val audioMetadata = AudioMetadata(
            duration = null,
            artist = null,
            album = null,
            title = null,
            genre = null,
            year = null,
            bitrate = 320,
            trackNumber = null,
            discNumber = null,
            composer = null,
            albumArtist = null,
            writer = null,
            sampleRate = null,
            bitDepth = null,
            channels = null,
            isCompilation = null,
            recordingDate = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = audioFile,
                    audioMetadata = audioMetadata,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val bitrateLabel = composeTestRule.activity.getString(R.string.info_bitrate)
        composeTestRule.onNodeWithText(bitrateLabel).assertIsDisplayed()
        val bitrateValue = composeTestRule.activity.getString(R.string.format_kbps, 320)
        composeTestRule.onNodeWithText(bitrateValue).assertIsDisplayed()
    }

    // ==================== Video Metadata Tests ====================

    @Test
    fun videoInfo_displaysDuration() {
        val videoFile = testFile.copy(
            name = "video.mp4",
            path = "/storage/emulated/0/Videos/video.mp4",
            mimeType = "video/mp4"
        )
        val videoMetadata = VideoMetadata(
            duration = 7265000L,
            width = null,
            height = null,
            frameRate = null,
            bitrate = null,
            rotation = null,
            colorStandard = null,
            colorTransfer = null,
            audioSampleRate = null,
            audioBitDepth = null,
            title = null,
            dateRecorded = null,
            latitude = null,
            longitude = null,
            author = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = videoFile,
                    videoMetadata = videoMetadata,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val durationLabel = composeTestRule.activity.getString(R.string.info_duration)
        composeTestRule.onNodeWithText(durationLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText("2:01:05").assertIsDisplayed()
    }

    @Test
    fun videoInfo_displaysResolution() {
        val videoFile = testFile.copy(
            name = "video.mp4",
            path = "/storage/emulated/0/Videos/video.mp4",
            mimeType = "video/mp4"
        )
        val videoMetadata = VideoMetadata(
            duration = null,
            width = 3840,
            height = 2160,
            frameRate = null,
            bitrate = null,
            rotation = null,
            colorStandard = null,
            colorTransfer = null,
            audioSampleRate = null,
            audioBitDepth = null,
            title = null,
            dateRecorded = null,
            latitude = null,
            longitude = null,
            author = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = videoFile,
                    videoMetadata = videoMetadata,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val resolutionLabel = composeTestRule.activity.getString(R.string.info_video_resolution)
        composeTestRule.onNodeWithText(resolutionLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText("3840 × 2160").assertIsDisplayed()
    }

    @Test
    fun videoInfo_displaysFrameRate() {
        val videoFile = testFile.copy(
            name = "video.mp4",
            path = "/storage/emulated/0/Videos/video.mp4",
            mimeType = "video/mp4"
        )
        val videoMetadata = VideoMetadata(
            duration = null,
            width = null,
            height = null,
            frameRate = 59.94f,
            bitrate = null,
            rotation = null,
            colorStandard = null,
            colorTransfer = null,
            audioSampleRate = null,
            audioBitDepth = null,
            title = null,
            dateRecorded = null,
            latitude = null,
            longitude = null,
            author = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = videoFile,
                    videoMetadata = videoMetadata,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val frameRateLabel = composeTestRule.activity.getString(R.string.info_frame_rate)
        composeTestRule.onNodeWithText(frameRateLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(String.format(Locale.US, "%.2f fps", 59.94f))
            .assertIsDisplayed()
    }

    // ==================== APK Metadata Tests ====================

    @Test
    fun apkInfo_displaysPackageName() {
        val apkFile = testFile.copy(
            name = "app.apk",
            path = "/storage/emulated/0/Download/app.apk",
            mimeType = "application/vnd.android.package-archive"
        )
        val apkMetadata = ApkMetadata(
            packageName = "com.example.testapp",
            appName = null,
            versionName = null,
            versionCode = null,
            minSdk = null,
            targetSdk = null,
            permissions = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = apkFile,
                    apkMetadata = apkMetadata,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val packageLabel = composeTestRule.activity.getString(R.string.info_package_name)
        composeTestRule.onNodeWithText(packageLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText("com.example.testapp").assertIsDisplayed()
    }

    @Test
    fun apkInfo_displaysPermissionCount() {
        val apkFile = testFile.copy(
            name = "app.apk",
            path = "/storage/emulated/0/Download/app.apk",
            mimeType = "application/vnd.android.package-archive"
        )
        val apkMetadata = ApkMetadata(
            packageName = null,
            appName = null,
            versionName = null,
            versionCode = null,
            minSdk = null,
            targetSdk = null,
            permissions = listOf("INTERNET", "CAMERA", "LOCATION")
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = apkFile,
                    apkMetadata = apkMetadata,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val permissionsLabel = composeTestRule.activity.getString(R.string.info_permissions)
        composeTestRule.onNodeWithText(permissionsLabel).assertIsDisplayed()
        val permissionCountText = composeTestRule.activity.resources.getQuantityString(
            R.plurals.permission_count,
            3,
            3
        )
        composeTestRule.onNodeWithText(permissionCountText).assertIsDisplayed()
    }

    // ==================== ZIP Metadata Tests ====================

    @Test
    fun zipInfo_displaysEntryCount() {
        val zipFile = testFile.copy(
            name = "archive.zip",
            path = "/storage/emulated/0/Download/archive.zip",
            mimeType = "application/zip"
        )
        val zipMetadata = ZipMetadata(
            entryCount = 42,
            compressedSize = null,
            uncompressedSize = null
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = zipFile,
                    zipMetadata = zipMetadata,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val entriesLabel = composeTestRule.activity.getString(R.string.info_entries)
        composeTestRule.onNodeWithText(entriesLabel).assertIsDisplayed()
        val entryCountText = composeTestRule.activity.resources.getQuantityString(
            R.plurals.entry_count,
            42,
            42
        )
        composeTestRule.onNodeWithText(entryCountText).assertIsDisplayed()
    }

    @Test
    fun zipInfo_displaysUncompressedSize() {
        val zipFile = testFile.copy(
            name = "archive.zip",
            path = "/storage/emulated/0/Download/archive.zip",
            mimeType = "application/zip"
        )
        val uncompressedSize = 1024L * 1024L * 100L
        val zipMetadata = ZipMetadata(
            entryCount = null,
            compressedSize = null,
            uncompressedSize = uncompressedSize
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                TestItemInfoContent(
                    file = zipFile,
                    zipMetadata = zipMetadata,
                    onCloseClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        val uncompressedLabel = composeTestRule.activity.getString(R.string.info_uncompressed_size)
        composeTestRule.onNodeWithText(uncompressedLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(FileSizeFormatter.format(uncompressedSize)).assertIsDisplayed()
    }

    // ==================== Test Composables ====================

    @Composable
    private fun TestItemInfoContent(
        file: FileItem,
        folderSize: Long? = null,
        imageMetadata: ImageMetadata? = null,
        audioMetadata: AudioMetadata? = null,
        videoMetadata: VideoMetadata? = null,
        apkMetadata: ApkMetadata? = null,
        zipMetadata: ZipMetadata? = null,
        onCloseClick: () -> Unit
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    IconButton(
                        onClick = onCloseClick,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.info_close),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getFileIcon(file),
                        contentDescription = file.name,
                        modifier = Modifier
                            .padding(16.dp)
                            .size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))

                TestInfoRow(
                    label = stringResource(R.string.info_name),
                    value = file.name
                )

                TestInfoRow(
                    label = stringResource(R.string.info_location),
                    value = file.parentPath
                )

                TestInfoRow(
                    label = stringResource(R.string.info_created),
                    value = formatDate(file.createdTime)
                )

                TestInfoRow(
                    label = stringResource(R.string.info_modified),
                    value = formatDate(file.lastModified)
                )

                if (!file.isDirectory) {
                    TestInfoRow(
                        label = stringResource(R.string.info_size),
                        value = file.formattedSize
                    )
                }

                if (file.isDirectory && file.childCount != null) {
                    TestInfoRow(
                        label = stringResource(R.string.info_items),
                        value = pluralStringResource(
                            R.plurals.item_amount,
                            file.childCount,
                            file.childCount
                        )
                    )
                }

                if (file.isDirectory && folderSize != null) {
                    TestInfoRow(
                        label = stringResource(R.string.info_size),
                        value = FileSizeFormatter.format(folderSize)
                    )
                }

                if (!file.isDirectory && file.mimeType.isNotBlank()) {
                    TestInfoRow(
                        label = stringResource(R.string.info_type),
                        value = file.mimeType
                    )
                }

                imageMetadata?.let { metadata ->
                    if (metadata.width != null && metadata.height != null) {
                        TestInfoRow(
                            label = stringResource(R.string.info_dimensions),
                            value = "${metadata.width} × ${metadata.height} px"
                        )
                    }
                    metadata.cameraMake?.let {
                        TestInfoRow(
                            label = stringResource(R.string.info_camera_make),
                            value = it
                        )
                    }
                    metadata.cameraModel?.let {
                        TestInfoRow(
                            label = stringResource(R.string.info_camera_model),
                            value = it
                        )
                    }
                    if (metadata.latitude != null && metadata.longitude != null) {
                        TestInfoRowWithMapButton(
                            label = stringResource(R.string.info_gps_coordinates),
                            value = String.format(Locale.US, "%.6f, %.6f", metadata.latitude, metadata.longitude)
                        )
                    }
                }

                audioMetadata?.let { metadata ->
                    metadata.duration?.let {
                        TestInfoRow(
                            label = stringResource(R.string.info_duration),
                            value = formatDuration(it)
                        )
                    }
                    metadata.artist?.let {
                        TestInfoRow(
                            label = stringResource(R.string.info_artist),
                            value = it
                        )
                    }
                    metadata.album?.let {
                        TestInfoRow(
                            label = stringResource(R.string.info_album),
                            value = it
                        )
                    }
                    metadata.bitrate?.let {
                        TestInfoRow(
                            label = stringResource(R.string.info_bitrate),
                            value = stringResource(R.string.format_kbps, it)
                        )
                    }
                }

                videoMetadata?.let { metadata ->
                    metadata.duration?.let {
                        TestInfoRow(
                            label = stringResource(R.string.info_duration),
                            value = formatDuration(it)
                        )
                    }
                    if (metadata.width != null && metadata.height != null) {
                        TestInfoRow(
                            label = stringResource(R.string.info_video_resolution),
                            value = "${metadata.width} × ${metadata.height}"
                        )
                    }
                    metadata.frameRate?.let {
                        TestInfoRow(
                            label = stringResource(R.string.info_frame_rate),
                            value = String.format(Locale.US, "%.2f fps", it)
                        )
                    }
                }

                apkMetadata?.let { metadata ->
                    metadata.packageName?.let {
                        TestInfoRow(
                            label = stringResource(R.string.info_package_name),
                            value = it
                        )
                    }
                    metadata.permissions?.let { permissions ->
                        TestInfoRow(
                            label = stringResource(R.string.info_permissions),
                            value = pluralStringResource(R.plurals.permission_count, permissions.size, permissions.size)
                        )
                    }
                }

                zipMetadata?.let { metadata ->
                    metadata.entryCount?.let {
                        TestInfoRow(
                            label = stringResource(R.string.info_entries),
                            value = pluralStringResource(R.plurals.entry_count, it, it)
                        )
                    }
                    metadata.uncompressedSize?.let {
                        TestInfoRow(
                            label = stringResource(R.string.info_uncompressed_size),
                            value = FileSizeFormatter.format(it)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun TestInfoRow(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { }
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
        }
    }

    @Composable
    private fun TestInfoRowWithMapButton(label: String, value: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { }
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
            IconButton(onClick = { }) {
                Icon(
                    imageVector = Icons.Outlined.Map,
                    contentDescription = stringResource(R.string.info_open_map),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    @Composable
    private fun TestItemInfoLoading() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .testTag("loading_indicator")
                )
            }
        }
    }

    @Composable
    private fun TestItemInfoError() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(R.string.info_error),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}
