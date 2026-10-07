package com.mauriciotogneri.fileexplorer.ui.screens.iteminfo

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasDataString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.ApkMetadata
import com.mauriciotogneri.fileexplorer.data.model.AudioChannels
import com.mauriciotogneri.fileexplorer.data.model.AudioMetadata
import com.mauriciotogneri.fileexplorer.data.model.ColorSpace
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.FlashMode
import com.mauriciotogneri.fileexplorer.data.model.ImageOrientation
import com.mauriciotogneri.fileexplorer.data.model.MeteringMode
import com.mauriciotogneri.fileexplorer.data.model.SceneCaptureType
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.StorageType
import com.mauriciotogneri.fileexplorer.data.model.ImageMetadata
import com.mauriciotogneri.fileexplorer.data.model.VideoColorStandard
import com.mauriciotogneri.fileexplorer.data.model.VideoColorTransfer
import com.mauriciotogneri.fileexplorer.data.model.VideoMetadata
import com.mauriciotogneri.fileexplorer.data.model.WhiteBalanceMode
import com.mauriciotogneri.fileexplorer.data.model.ZipMetadata
import com.mauriciotogneri.fileexplorer.data.util.FileSizeFormatter
import com.mauriciotogneri.fileexplorer.testutil.MetadataFixtures
import com.mauriciotogneri.fileexplorer.testutil.hasClickLabel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.DateFormat
import java.util.Calendar
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
 * position; assertions use `assertExists()` so they hold on short screens. A tap needs the target on
 * screen, so the map-button tests scroll to it first.
 *
 * Espresso-Intents stubs every outgoing intent, so the GPS map button never launches a real maps app.
 */
@RunWith(AndroidJUnit4::class)
class ItemInfoScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // The parent directory the location row names and copies, as a literal this test owns.
    // `itemInfo_displaysLocation` used to assert `testFile.parentPath` — the same getter the screen
    // renders — so a `parentPath` returning the whole path stayed green, and that getter also picks
    // the uncompress target in Search / Home / AnalyzerCategory.
    private val testFileParent = "/storage/emulated/0/Download"

    // The two timestamps are years apart, so the created and modified rows can never read alike.
    private val testFile = FileItem(
        path = "$testFileParent/document.pdf",
        name = "document.pdf",
        isDirectory = false,
        size = 2048L,
        lastModified = 1_700_000_000_000L,
        createdTime = 1_600_000_000_000L,
        mimeType = "application/pdf",
        childCount = null
    )

    // Named "Documents" until that collided with the `location_documents` resource value: the icon
    // describes itself by the on-disk name, so the literal is right in kind, but a matcher written
    // as a literal a translation also owns is locale-dependent. "Ledgers" is in no <string> value.
    private val testFolder = FileItem(
        path = "$testFileParent/Ledgers",
        name = "Ledgers",
        isDirectory = true,
        size = 0L,
        lastModified = 1_700_000_000_000L,
        createdTime = 1_600_000_000_000L,
        mimeType = "",
        childCount = 12
    )

    @Before
    fun setUp() {
        Intents.init()
        // Stub every outgoing intent so a tap on the map button never launches a real maps app.
        intending(anyIntent()).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    private fun string(resId: Int): String = composeTestRule.activity.getString(resId)

    private fun plural(resId: Int, quantity: Int, vararg args: Any): String =
        composeTestRule.activity.resources.getQuantityString(resId, quantity, *args)

    /** Mirrors the screen's own `formatDate`, so assertions follow the device locale/timezone. */
    private fun formatDate(timestamp: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

    /**
     * The screen's output format applied to a wall-clock instant the test builds from its fields —
     * never to the input string — so a date row asserts the *parsed and formatted* value rather
     * than echoing back what was passed in. Local, because the screen parses a string carrying no
     * UTC offset in the device's own timezone.
     */
    private fun localDate(year: Int, month: Int, day: Int, hour: Int, minute: Int): String {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }
        return formatDate(calendar.timeInMillis)
    }

    /**
     * Asserts [label] and [value] belong to the SAME row.
     *
     * `InfoRow` is clickable, which merges its label and value into one semantics node, so this
     * pins the pairing that two independent `onNodeWithText` calls cannot: with those, swapping the
     * `value =` arguments of two neighbouring rows — created/modified, camera make/model — leaves
     * both strings on screen and both assertions green.
     */
    private fun assertInfoRow(label: String, value: String, message: String? = null) {
        composeTestRule.onNode(hasText(label) and hasText(value)).assertExists(message)
    }

    /** The metadata bags this file renders; the remaining sections live in [ItemInfoMetadataTest]. */
    private data class Sections(
        val image: ImageMetadata? = null,
        val audio: AudioMetadata? = null,
        val video: VideoMetadata? = null,
        val apk: ApkMetadata? = null,
        val zip: ZipMetadata? = null
    )

    private fun renderInfoContent(
        file: FileItem = testFile,
        folderSize: Long? = null,
        imageMetadata: ImageMetadata? = null,
        audioMetadata: AudioMetadata? = null,
        videoMetadata: VideoMetadata? = null,
        apkMetadata: ApkMetadata? = null,
        zipMetadata: ZipMetadata? = null,
        onOpenFile: () -> Unit = {},
        onCloseClick: () -> Unit = {},
        storage: StorageDevice? = null
    ) {
        val sections = Sections(imageMetadata, audioMetadata, videoMetadata, apkMetadata, zipMetadata)
        renderSections(file, folderSize, onOpenFile, onCloseClick, storage) { sections }
        composeTestRule.waitForIdle()
    }

    /**
     * Every case in [cases] rendered in turn through ONE composition — a test may only call
     * `setContent` once — and each checked against the string resource it must show under [label].
     *
     * Walking every constant is the point: a single sample leaves two swapped branches of a
     * conversion green, and an enum constant this table does not list fails the test, so a new one
     * cannot ship without its row being pinned too.
     */
    private fun <T : Enum<T>> assertEachConstantRendersAs(
        label: String,
        cases: Map<T, Int>,
        constants: List<T>,
        sectionsOf: (T) -> Sections
    ) {
        assertEquals("Every constant must have an expected row", constants.toSet(), cases.keys)

        var current by mutableStateOf(constants.first())
        renderSections { sectionsOf(current) }

        cases.forEach { (constant, valueRes) ->
            composeTestRule.runOnIdle { current = constant }
            composeTestRule.waitForIdle()
            assertInfoRow(label, string(valueRes), "$constant should read '${string(valueRes)}'")
        }
    }

    private fun renderSections(
        file: FileItem = testFile,
        folderSize: Long? = null,
        onOpenFile: () -> Unit = {},
        onCloseClick: () -> Unit = {},
        storage: StorageDevice? = null,
        sectionsOf: () -> Sections
    ) {
        composeTestRule.setContent {
            val sections = sectionsOf()
            FileExplorerTheme {
                ItemInfoContent(
                    file = file,
                    folderSize = folderSize,
                    imageMetadata = sections.image,
                    audioMetadata = sections.audio,
                    videoMetadata = sections.video,
                    pdfMetadata = null,
                    apkMetadata = sections.apk,
                    zipMetadata = sections.zip,
                    officeMetadata = null,
                    epubMetadata = null,
                    sqliteMetadata = null,
                    vcardMetadata = null,
                    icalendarMetadata = null,
                    csvMetadata = null,
                    onOpenFile = onOpenFile,
                    onCloseClick = onCloseClick,
                    storage = storage
                )
            }
        }
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

        assertInfoRow(string(R.string.info_location), "${string(R.string.storage_internal)} › Download")
    }

    @Test
    fun itemInfo_displaysLocationOnSdCardByStorageName() {
        val sdCard = StorageDevice(
            path = "/storage/1234-5678",
            displayName = "SD card",
            totalBytes = 2_000L,
            availableBytes = 1_000L,
            type = StorageType.SD_CARD
        )
        val file = testFile.copy(path = "/storage/1234-5678/Music/Albums/song.mp3", name = "song.mp3")

        renderInfoContent(file = file, storage = sdCard)

        assertInfoRow(string(R.string.info_location), "SD card › Music › Albums")
    }

    /** The row shows friendly names, but what a user pastes elsewhere has to be the real path. */
    @Test
    fun itemInfo_tappingLocation_copiesRawPath() {
        renderInfoContent()

        composeTestRule.onNodeWithText("${string(R.string.storage_internal)} › Download").performClick()
        composeTestRule.waitForIdle()

        val clipboard = composeTestRule.activity
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipped = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals(testFileParent, clipped)
    }

    /**
     * The value has to be in the created row, not merely on screen: both timestamps are rendered
     * either way, so swapping the two `value =` arguments passed the label-and-value-apart shape
     * this and [itemInfo_displaysModifiedDate] used to have.
     */
    @Test
    fun itemInfo_displaysCreatedDate() {
        renderInfoContent()

        assertInfoRow(string(R.string.info_created), formatDate(testFile.createdTime))
    }

    @Test
    fun itemInfo_displaysModifiedDate() {
        renderInfoContent()

        assertInfoRow(string(R.string.info_modified), formatDate(testFile.lastModified))
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

    /**
     * `action_open` is the icon's click label, not its description: the icon describes itself by the
     * file name, and for a thumbnail-capable type such as this PDF the screen renders an async image
     * whose description depends on whether the load finished. The click label is on the clickable
     * itself in both branches, so it addresses the target whatever the image is doing.
     */
    @Test
    fun itemInfo_tappingIcon_opensFile() {
        var opened = false
        renderInfoContent(onOpenFile = { opened = true })

        composeTestRule.onNode(hasClickLabel(string(R.string.action_open))).performClick()

        assertTrue("Tapping the file icon should open the file", opened)
    }

    /** A folder has nothing to open, so its icon must not be clickable. */
    @Test
    fun itemInfo_folderIcon_doesNotOpen() {
        var opened = false
        renderInfoContent(file = testFolder, onOpenFile = { opened = true })

        composeTestRule.onNodeWithContentDescription("Ledgers").performClick()

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

    /** Make and model are adjacent rows fed by adjacent fields, so each value's row is the point. */
    @Test
    fun imageInfo_displaysCameraInfo() {
        renderInfoContent(
            imageMetadata = MetadataFixtures.image(cameraMake = "Canon", cameraModel = "EOS R5")
        )

        assertInfoRow(string(R.string.info_camera_make), "Canon")
        assertInfoRow(string(R.string.info_camera_model), "EOS R5")
    }

    /**
     * The EXIF timestamp reaches the screen as "yyyy:MM:dd HH:mm:ss" and is rendered through its
     * `parseAndFormatDate`. Every fixture left `dateTaken` null, so this call site — and eight
     * others — only ever reached the parser's fall-through: replacing its whole body with
     * `return dateString` was green across the suite.
     */
    @Test
    fun imageInfo_displaysDateTaken_formatted() {
        renderInfoContent(imageMetadata = MetadataFixtures.image(dateTaken = "2021:07:04 13:45:30"))

        assertInfoRow(string(R.string.info_date_taken), localDate(2021, 7, 4, 13, 45))
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

    /**
     * The button's job is the tap, not its presence: emptying its `onClick` leaves the icon rendered
     * and an existence assertion green while the coordinates open nothing.
     */
    @Test
    fun imageInfo_gpsMapButton_opensCoordinatesInAMapApp() {
        renderInfoContent(
            imageMetadata = MetadataFixtures.image(latitude = 37.774929, longitude = -122.419418)
        )

        composeTestRule.onNodeWithContentDescription(string(R.string.info_open_map))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        // The zoom parameter the screen appends is deliberately not pinned; the coordinates are.
        intended(
            allOf(
                hasAction(Intent.ACTION_VIEW),
                hasDataString(startsWith("geo:37.774929,-122.419418"))
            )
        )
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
        composeTestRule.onNodeWithText("03:45").assertExists()
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
        composeTestRule.onNodeWithText("03:45").assertExists()
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

    /**
     * The video section has its own `parseAndFormatDate` call, and its date arrives in the compact
     * `yyyyMMdd'T'HHmmss` shape the extractor reads off the container — a different branch of the
     * parser's format list than the image section's EXIF timestamp.
     */
    @Test
    fun videoInfo_displaysDateRecorded_formatted() {
        renderInfoContent(videoMetadata = MetadataFixtures.video(dateRecorded = "20180226T081500"))

        assertInfoRow(string(R.string.info_date_recorded), localDate(2018, 2, 26, 8, 15))
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

        assertInfoRow(
            string(R.string.info_rotation),
            composeTestRule.activity.getString(R.string.format_degrees, 90)
        )
    }

    /**
     * The video section carries its own copy of the GPS row and its own map button, so the image test
     * does not cover it: this `onClick` can go dead on its own.
     */
    @Test
    fun videoInfo_gpsMapButton_opensCoordinatesInAMapApp() {
        renderInfoContent(
            videoMetadata = MetadataFixtures.video(latitude = 48.858844, longitude = 2.294351)
        )

        composeTestRule.onNodeWithContentDescription(string(R.string.info_open_map))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        intended(
            allOf(
                hasAction(Intent.ACTION_VIEW),
                hasDataString(startsWith("geo:48.858844,2.294351"))
            )
        )
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

    // ==================== Value conversions ====================

    // Split between the two flash tests below. Every other constant lands in the "fired" test's
    // coverage check, so a new mode that belongs in neither list fails it.
    private val firedFlashModes = mapOf(
        FlashMode.FIRED to R.string.flash_on,
        FlashMode.ON_FIRED to R.string.flash_on,
        FlashMode.OFF_FIRED to R.string.flash_on,
        FlashMode.AUTO_FIRED to R.string.flash_auto
    )

    private val notFiredFlashModes = listOf(
        FlashMode.DID_NOT_FIRE,
        FlashMode.ON_DID_NOT_FIRE,
        FlashMode.OFF_DID_NOT_FIRE,
        FlashMode.AUTO_DID_NOT_FIRE
    )

    /**
     * Only a flash that fired gets a row, and the auto mode says so. OFF_FIRED reading "On" is
     * pinned as production has it: EXIF records the flash firing despite the mode being off, and
     * the row reports that it fired.
     */
    @Test
    fun imageInfo_flashThatFired_showsItsMode() {
        assertEachConstantRendersAs(
            label = string(R.string.info_flash),
            cases = firedFlashModes,
            constants = FlashMode.entries - notFiredFlashModes.toSet()
        ) { Sections(image = MetadataFixtures.image(flash = it)) }
    }

    /** A flash that did not fire is the norm, so its row would be noise in every mode. */
    @Test
    fun imageInfo_flashThatDidNotFire_hidesRow() {
        var current by mutableStateOf(notFiredFlashModes.first())
        renderSections { Sections(image = MetadataFixtures.image(flash = current, iso = 100)) }

        notFiredFlashModes.forEach { mode ->
            composeTestRule.runOnIdle { current = mode }
            composeTestRule.waitForIdle()
            // The ISO row proves the section rendered, so the flash row's absence is not vacuous.
            assertInfoRow(string(R.string.info_iso), "ISO 100", "$mode: image section not rendered")
            composeTestRule.onNodeWithText(string(R.string.info_flash)).assertDoesNotExist()
        }
    }

    @Test
    fun imageInfo_whiteBalance_namesEveryMode() {
        assertEachConstantRendersAs(
            label = string(R.string.info_white_balance),
            cases = mapOf(
                WhiteBalanceMode.AUTO to R.string.white_balance_auto,
                WhiteBalanceMode.MANUAL to R.string.white_balance_manual
            ),
            constants = WhiteBalanceMode.entries
        ) { Sections(image = MetadataFixtures.image(whiteBalance = it)) }
    }

    @Test
    fun imageInfo_meteringMode_namesEveryMode() {
        assertEachConstantRendersAs(
            label = string(R.string.info_metering_mode),
            cases = mapOf(
                MeteringMode.AVERAGE to R.string.metering_average,
                MeteringMode.CENTER_WEIGHTED to R.string.metering_center_weighted,
                MeteringMode.SPOT to R.string.metering_spot,
                MeteringMode.MULTI_SPOT to R.string.metering_multi_spot,
                MeteringMode.PATTERN to R.string.metering_pattern,
                MeteringMode.PARTIAL to R.string.metering_partial
            ),
            constants = MeteringMode.entries
        ) { Sections(image = MetadataFixtures.image(meteringMode = it)) }
    }

    @Test
    fun imageInfo_sceneType_namesEveryType() {
        assertEachConstantRendersAs(
            label = string(R.string.info_scene_type),
            cases = mapOf(
                SceneCaptureType.STANDARD to R.string.scene_standard,
                SceneCaptureType.LANDSCAPE to R.string.scene_landscape,
                SceneCaptureType.PORTRAIT to R.string.scene_portrait,
                SceneCaptureType.NIGHT to R.string.scene_night
            ),
            constants = SceneCaptureType.entries
        ) { Sections(image = MetadataFixtures.image(sceneCaptureType = it)) }
    }

    @Test
    fun imageInfo_orientation_namesEveryOrientation() {
        assertEachConstantRendersAs(
            label = string(R.string.info_orientation),
            cases = mapOf(
                ImageOrientation.NORMAL to R.string.orientation_normal,
                ImageOrientation.FLIP_HORIZONTAL to R.string.orientation_flip_horizontal,
                ImageOrientation.ROTATE_180 to R.string.orientation_rotate_180,
                ImageOrientation.FLIP_VERTICAL to R.string.orientation_flip_vertical,
                ImageOrientation.TRANSPOSE to R.string.orientation_transpose,
                ImageOrientation.ROTATE_90_CW to R.string.orientation_rotate_90_cw,
                ImageOrientation.TRANSVERSE to R.string.orientation_transverse,
                ImageOrientation.ROTATE_270_CW to R.string.orientation_rotate_270_cw
            ),
            constants = ImageOrientation.entries
        ) { Sections(image = MetadataFixtures.image(orientation = it)) }
    }

    @Test
    fun imageInfo_colorSpace_namesEverySpace() {
        assertEachConstantRendersAs(
            label = string(R.string.info_color_space),
            cases = mapOf(
                ColorSpace.SRGB to R.string.color_space_srgb,
                ColorSpace.ADOBE_RGB to R.string.color_space_adobe_rgb,
                ColorSpace.UNCALIBRATED to R.string.color_space_uncalibrated
            ),
            constants = ColorSpace.entries
        ) { Sections(image = MetadataFixtures.image(colorSpace = it)) }
    }

    @Test
    fun videoInfo_colorStandard_namesEveryStandard() {
        assertEachConstantRendersAs(
            label = string(R.string.info_color_standard),
            cases = mapOf(
                VideoColorStandard.BT601 to R.string.color_standard_bt601,
                VideoColorStandard.BT709 to R.string.color_standard_bt709,
                VideoColorStandard.BT2020 to R.string.color_standard_bt2020
            ),
            constants = VideoColorStandard.entries
        ) { Sections(video = MetadataFixtures.video(colorStandard = it)) }
    }

    @Test
    fun videoInfo_colorTransfer_namesEveryTransfer() {
        assertEachConstantRendersAs(
            label = string(R.string.info_color_transfer),
            cases = mapOf(
                VideoColorTransfer.SDR to R.string.color_transfer_sdr,
                VideoColorTransfer.ST2084 to R.string.color_transfer_st2084,
                VideoColorTransfer.HLG to R.string.color_transfer_hlg
            ),
            constants = VideoColorTransfer.entries
        ) { Sections(video = MetadataFixtures.video(colorTransfer = it)) }
    }

    @Test
    fun audioInfo_channels_namesEveryLayout() {
        assertEachConstantRendersAs(
            label = string(R.string.info_channels),
            cases = mapOf(
                AudioChannels.MONO to R.string.channels_mono,
                AudioChannels.STEREO to R.string.channels_stereo
            ),
            constants = AudioChannels.entries
        ) { Sections(audio = MetadataFixtures.audio(channels = it)) }
    }

    // ==================== Formatted rows ====================

    /** Each value distinct from its neighbours, so a row reading another field's value fails. */
    @Test
    fun imageInfo_displaysOpticsAndCaptureRows() {
        renderInfoContent(
            imageMetadata = MetadataFixtures.image(
                megapixels = 12.3,
                aperture = 2.8,
                focalLength = 50.0,
                exposureTime = "1/250",
                lensMake = "Lensmaker",
                lensModel = "Lens 50",
                software = "Editor 4",
                copyright = "Owner 2024",
                altitude = 123.4,
                digitalZoom = 2.5,
                resolutionX = 300.0,
                resolutionY = 72.0
            )
        )

        assertInfoRow(string(R.string.info_megapixels), "12.3 MP")
        assertInfoRow(string(R.string.info_aperture), "f/2.8")
        assertInfoRow(string(R.string.info_focal_length), "50.0 mm")
        assertInfoRow(string(R.string.info_exposure_time), "1/250")
        assertInfoRow(string(R.string.info_lens_make), "Lensmaker")
        assertInfoRow(string(R.string.info_lens_model), "Lens 50")
        assertInfoRow(string(R.string.info_software), "Editor 4")
        assertInfoRow(string(R.string.info_copyright), "Owner 2024")
        assertInfoRow(string(R.string.info_altitude), "123.4 m")
        assertInfoRow(string(R.string.info_digital_zoom), "2.5x")
        assertInfoRow(string(R.string.info_resolution), "300 × 72 DPI")
    }

    @Test
    fun audioInfo_displaysSampleRateAndBitDepth() {
        renderInfoContent(audioMetadata = MetadataFixtures.audio(sampleRate = 44_100, bitDepth = 24))

        assertInfoRow(
            string(R.string.info_sample_rate),
            composeTestRule.activity.getString(R.string.format_hz, 44_100)
        )
        assertInfoRow(
            string(R.string.info_bit_depth),
            composeTestRule.activity.getString(R.string.format_bit_depth, 24)
        )
    }

    @Test
    fun audioInfo_displaysCreditAndTrackRows() {
        renderInfoContent(
            audioMetadata = MetadataFixtures.audio(
                albumArtist = "Band Name",
                trackNumber = "3/12",
                discNumber = "1/2",
                genre = "Fixture Genre",
                year = "1999",
                composer = "Composer Name",
                writer = "Writer Name"
            )
        )

        assertInfoRow(string(R.string.info_album_artist), "Band Name")
        assertInfoRow(string(R.string.info_track_number), "3/12")
        assertInfoRow(string(R.string.info_disc_number), "1/2")
        assertInfoRow(string(R.string.info_genre), "Fixture Genre")
        assertInfoRow(string(R.string.info_year), "1999")
        assertInfoRow(string(R.string.info_composer), "Composer Name")
        assertInfoRow(string(R.string.info_writer), "Writer Name")
    }

    /** A compilation flag only earns a row when set; "not a compilation" is the norm. */
    @Test
    fun audioInfo_compilation_showsOnlyWhenSet() {
        var compilation by mutableStateOf(true)
        renderSections {
            Sections(audio = MetadataFixtures.audio(isCompilation = compilation, year = "1999"))
        }
        composeTestRule.waitForIdle()

        assertInfoRow(string(R.string.info_compilation), string(R.string.yes))

        composeTestRule.runOnIdle { compilation = false }
        composeTestRule.waitForIdle()

        assertInfoRow(string(R.string.info_year), "1999", "audio section not rendered")
        composeTestRule.onNodeWithText(string(R.string.info_compilation)).assertDoesNotExist()
    }

    @Test
    fun videoInfo_displaysAudioTrackAndAuthorRows() {
        renderInfoContent(
            videoMetadata = MetadataFixtures.video(
                audioSampleRate = 48_000,
                audioBitDepth = 16,
                author = "Author Name"
            )
        )

        assertInfoRow(
            string(R.string.info_audio_sample_rate),
            composeTestRule.activity.getString(R.string.format_hz, 48_000)
        )
        assertInfoRow(
            string(R.string.info_audio_bit_depth),
            composeTestRule.activity.getString(R.string.format_bit_depth, 16)
        )
        assertInfoRow(string(R.string.info_author), "Author Name")
    }

    /** Min and target SDK share a format, so only the pairing tells a swap apart. */
    @Test
    fun apkInfo_displaysVersionAndTargetRows() {
        renderInfoContent(
            apkMetadata = MetadataFixtures.apk(
                appName = "Fixture App",
                versionName = "2.7.0",
                versionCode = 270L,
                minSdk = 24,
                targetSdk = 35
            )
        )

        assertInfoRow(string(R.string.info_app_name), "Fixture App")
        assertInfoRow(string(R.string.info_version_name), "2.7.0")
        assertInfoRow(string(R.string.info_version_code), "270")
        assertInfoRow(
            string(R.string.info_min_sdk),
            composeTestRule.activity.getString(R.string.format_api_level, 24)
        )
        assertInfoRow(
            string(R.string.info_target_sdk),
            composeTestRule.activity.getString(R.string.format_api_level, 35)
        )
    }
}
