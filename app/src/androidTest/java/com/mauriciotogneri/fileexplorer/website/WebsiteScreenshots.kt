package com.mauriciotogneri.fileexplorer.website

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.activities.SettingsScreen
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.model.FileSecondLine
import com.mauriciotogneri.fileexplorer.data.model.FolderSecondLine
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.model.SearchFileType
import com.mauriciotogneri.fileexplorer.data.model.StartupScreen
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.StorageType
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.repository.AnalyzerRepository
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.ScanProgress
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.source.AndroidPdfDocumentOpener
import com.mauriciotogneri.fileexplorer.data.source.CachedSizeResult
import com.mauriciotogneri.fileexplorer.data.source.LocationsCacheSource
import com.mauriciotogneri.fileexplorer.data.source.PreferencesSource
import com.mauriciotogneri.fileexplorer.data.source.StorageSource
import com.mauriciotogneri.fileexplorer.testutil.FolderScreenRobot
import com.mauriciotogneri.fileexplorer.testutil.InMemoryPreferencesSource
import com.mauriciotogneri.fileexplorer.testutil.NoExternalChanges
import com.mauriciotogneri.fileexplorer.testutil.SeededFavorites
import com.mauriciotogneri.fileexplorer.testutil.SeededRecentFiles
import com.mauriciotogneri.fileexplorer.ui.screens.analyzer.AnalyzerScreen
import com.mauriciotogneri.fileexplorer.ui.screens.analyzer.AnalyzerViewModel
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderScreen
import com.mauriciotogneri.fileexplorer.ui.screens.folder.FolderViewModel
import com.mauriciotogneri.fileexplorer.ui.screens.home.HomeScreen
import com.mauriciotogneri.fileexplorer.ui.screens.home.HomeViewModel
import com.mauriciotogneri.fileexplorer.ui.screens.pdfviewer.PdfViewerScreen
import com.mauriciotogneri.fileexplorer.ui.screens.pdfviewer.PdfViewerViewModel
import com.mauriciotogneri.fileexplorer.ui.screens.search.SearchScreen
import com.mauriciotogneri.fileexplorer.ui.screens.search.SearchViewModel
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import com.mauriciotogneri.fileexplorer.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.io.RandomAccessFile
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * Captures the website's screenshots: eight views of the app's real screens, fed the same made-up
 * files and drive figures in every language, each saved as a full-screen PNG under the app's
 * external files directory (`website-screenshots/<language>/<screen>-<theme>.png`).
 *
 * Not a test of the app. `scripts/website-screenshots.sh` runs it once per theme, after switching
 * the emulator's dark mode and turning on the status bar's demo mode, and converts what it saves
 * into `website/images/`. It is meant to be deleted once the screenshots are done; `SCREENSHOTS.md`
 * at the repository's root shows how to restore it to capture a screen again.
 *
 * Each language runs as its own set of tests, with the app's language switched through Android's
 * per-app language setting before the activity starts, so view models, number formats and the
 * layout direction follow it exactly as they do for a user who picked it.
 */
// Android's per-app language setting arrived in API 33; the project's emulator runs 36.
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
@RunWith(Parameterized::class)
class WebsiteScreenshots(private val language: String) {

    private val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AppLanguage(language)).around(composeTestRule)

    private val application: Application = ApplicationProvider.getApplicationContext()

    // ==================== Screens ====================

    @Test
    fun home() {
        showHome(Fixtures.favorites())
        capture("home")
    }

    /** The home screen again, with no favorites: only the drives and the quick-access locations. */
    @Test
    fun drives() {
        showHome(favorites = emptyList())
        capture("drives")
    }

    @Test
    fun select() {
        val robot = showAlbum()

        robot.longClick(Fixtures.photoNames[0])
            .click(Fixtures.photoNames[2])
            .click(Fixtures.photoNames[3])
        capture("select")
    }

    @Test
    fun actions() {
        val robot = showAlbum()

        robot.openRowActions(Fixtures.photoNames[1])
        capture("actions")
    }

    @Test
    fun search() {
        val preferences = PreferencesRepository(ScreenshotPreferences)
        val viewModel = SearchViewModel(
            application = application,
            fileRepository = FileRepository(),
            storageRepository = StorageRepository(ScreenshotStorages),
            preferencesRepository = preferences,
            favoritesRepository = FavoritesRepository(SeededFavorites(Fixtures.favorites()))
        )

        show { SearchScreen(onBackClick = {}, viewModel = viewModel) }
        composeTestRule.onNode(hasSetTextAction()).performTextInput(SEARCH_QUERY)
        composeTestRule.onNode(hasSetTextAction()).performImeAction()
        closeSoftKeyboard()
        waitForText(Fixtures.photoNames[0])
        capture("search")
    }

    @Test
    fun pdf() {
        val viewModel = PdfViewerViewModel(
            filePath = Fixtures.pdf.absolutePath,
            source = "website",
            application = application,
            fileRepository = FileRepository(),
            opener = AndroidPdfDocumentOpener()
        )

        show { PdfViewerScreen(viewModel = viewModel, onBackClick = {}, onFinish = {}) }
        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule.onAllNodesWithContentDescription(string(R.string.pdf_viewer_search))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription(string(R.string.pdf_viewer_search)).performClick()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(PDF_QUERY)
        composeTestRule.onNode(hasSetTextAction()).performImeAction()
        closeSoftKeyboard()
        composeTestRule.waitUntil(TIMEOUT_MS) { viewModel.state.value.search.matches.isNotEmpty() }
        capture("pdf")
    }

    @Test
    fun analyzer() {
        val internal = ScreenshotStorages.internal()
        val scan = ScanProgress(
            currentFolder = internal.path,
            scannedBytes = ANALYZED_SIZES.values.sum(),
            fileCount = 18_437,
            sizesByType = SearchFileType.entries.associateWith { ANALYZED_SIZES[it] ?: 0L },
            isComplete = true
        )
        val viewModel = AnalyzerViewModel(
            storageRepository = StorageRepository(ScreenshotStorages),
            analyzerRepository = object : AnalyzerRepository() {
                override fun analyze(rootPath: String): Flow<ScanProgress> = flowOf(scan)
            }
        )

        show { AnalyzerScreen(viewModel = viewModel, onCloseClick = {}) }
        // With more than one drive, nothing is selected until the user picks one.
        waitForText(internal.displayName)
        composeTestRule.onNodeWithText(internal.displayName).performClick()
        composeTestRule.onNodeWithText(string(R.string.analyzer_analyze)).performClick()
        waitForText(string(R.string.location_images))
        capture("analyzer")
    }

    @Test
    fun settings() {
        show {
            SettingsScreen(
                themeMode = ThemeMode.SYSTEM,
                onThemeModeChange = {},
                startupScreen = StartupScreen.HOME,
                startupFolderName = null,
                onStartupHomeSelected = {},
                onStartupFolderSelected = {},
                enabledLocations = LocationType.entries.toSet(),
                availableLocationTypes = LocationType.entries,
                isLoadingLocations = false,
                onEnabledLocationsSave = {},
                showHidden = false,
                onShowHiddenChange = {},
                folderSecondLine = FolderSecondLine.ITEM_COUNT,
                onFolderSecondLineChange = {},
                fileSecondLine = FileSecondLine.SIZE,
                onFileSecondLineChange = {},
                swipeLeftAction = SwipeAction.RENAME,
                onSwipeLeftActionChange = {},
                swipeRightAction = SwipeAction.DELETE,
                onSwipeRightActionChange = {},
                recentFilesEnabled = true,
                hasRecentFiles = true,
                onRecentFilesEnabledChange = {},
                onClearRecentFiles = {},
                hasFavorites = true,
                onClearFavorites = {},
                homeSectionOrder = HomeSection.DEFAULT_ORDER,
                onHomeSectionOrderSave = {},
                onBackClick = {}
            )
        }
        capture("settings")
    }

    // ==================== Rendering ====================

    /** Shows the home screen with [favorites]; an empty list hides that section. */
    private fun showHome(favorites: List<Favorite>) {
        val preferences = PreferencesRepository(ScreenshotPreferences)
        val viewModel = HomeViewModel(
            application = application,
            recentFilesRepository = RecentFilesRepository(SeededRecentFiles(Fixtures.recentFiles())),
            favoritesRepository = FavoritesRepository(SeededFavorites(favorites)),
            locationsRepository = LocationsRepository(PlausibleLocationSizes, preferences),
            storageRepository = StorageRepository(ScreenshotStorages),
            preferencesRepository = preferences,
            fileRepository = FileRepository(),
            mediaChangeSource = NoExternalChanges,
            storageVolumeChangeSource = NoExternalChanges
        )

        show { HomeScreen(viewModel = viewModel) }
        waitForText(string(R.string.storage_usb_drive))
    }

    private fun showAlbum(): FolderScreenRobot {
        val preferences = PreferencesRepository(ScreenshotPreferences)
        val internal = ScreenshotStorages.internal()
        val viewModel = FolderViewModel(
            application = application,
            initialPath = Fixtures.album.absolutePath,
            initialTitle = Fixtures.album.name,
            fileRepository = FileRepository(),
            preferencesRepository = preferences,
            storageRepository = StorageRepository(ScreenshotStorages),
            favoritesRepository = FavoritesRepository(SeededFavorites(Fixtures.favorites())),
            recentFilesRepository = RecentFilesRepository(SeededRecentFiles(Fixtures.recentFiles()))
        )

        show {
            FolderScreen(
                path = Fixtures.album.absolutePath,
                title = Fixtures.album.name,
                rootPath = internal.path,
                rootDisplayName = internal.displayName,
                onNavigateToFolder = {},
                onNavigateBack = {},
                viewModel = viewModel
            )
        }
        return FolderScreenRobot(composeTestRule, Fixtures.album).waitForText(Fixtures.photoNames[0])
    }

    /**
     * Draws [content] edge to edge, as every activity of the app does, under the app's theme. In
     * touch mode, as a phone in someone's hand is: the test's clicks are not touches, and out of it
     * the bottom sheet's drag handle takes focus and shows its tooltip.
     */
    private fun show(content: @Composable () -> Unit) {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
        composeTestRule.runOnUiThread { composeTestRule.activity.enableEdgeToEdge() }
        composeTestRule.setContent { FileExplorerTheme { content() } }
        composeTestRule.waitForIdle()
    }

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Saves the whole screen, status bar included. Thumbnails decode off the composition, where
     * waiting for idle cannot see them, so the screen gets a moment to finish drawing first.
     */
    private fun capture(screen: String) {
        composeTestRule.waitForIdle()
        SystemClock.sleep(SETTLE_MS)

        val theme = if (application.resources.configuration.isNightModeActive) "dark" else "light"
        val bitmap: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val output = File(outputRoot(), "$language/$screen-$theme.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun string(@StringRes id: Int): String = application.getString(id)

    // ==================== Language ====================

    /**
     * Switches the app to [language] before the activity starts, and waits until the process has
     * taken it: the setting reaches the app as a configuration change, after the call returns.
     */
    private class AppLanguage(private val language: String) : ExternalResource() {
        override fun before() {
            val wanted = Locale.forLanguageTag(ANDROID_TAGS.getValue(language))
            localeManager().applicationLocales = LocaleList(wanted)

            val resources = ApplicationProvider.getApplicationContext<Context>().resources
            val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
            while (resources.configuration.locales[0].language != wanted.language &&
                SystemClock.uptimeMillis() < deadline
            ) {
                SystemClock.sleep(50)
            }
            assertEquals(
                "The app never switched to $language",
                wanted.language,
                resources.configuration.locales[0].language
            )
        }
    }

    // ==================== Made-up data ====================

    /**
     * The app's defaults, with every feature badge already seen: dots would point at nothing. The
     * home screen leads with favorites, then the drives, so one screen shows a favorite, every drive
     * and the quick-access locations together.
     */
    private object ScreenshotPreferences : PreferencesSource by InMemoryPreferencesSource() {
        override fun dismissedBadgeVersion(badgeId: String): Flow<Int> = flowOf(Int.MAX_VALUE)

        override val recentFilesEnabled: Flow<Boolean> = flowOf(false)

        override val homeSectionOrder: Flow<List<HomeSection>> = flowOf(
            listOf(HomeSection.FAVORITES, HomeSection.STORAGE, HomeSection.LOCATIONS, HomeSection.RECENT)
        )
    }

    /** A phone's worth of sizes for the home screen's locations, instead of the emulator's. */
    private object PlausibleLocationSizes : LocationsCacheSource {
        private val sizes = mapOf(
            LocationType.DOWNLOADS to 2_458_000_000L,
            LocationType.CAMERA to 9_826_000_000L,
            LocationType.IMAGES to 1_342_000_000L,
            LocationType.SCREENSHOTS to 186_000_000L,
            LocationType.VIDEOS to 6_517_000_000L,
            LocationType.DOCUMENTS to 412_000_000L,
            LocationType.AUDIO to 3_085_000_000L,
            LocationType.PODCASTS to 734_000_000L
        )

        override suspend fun getCachedSize(type: LocationType): CachedSizeResult =
            CachedSizeResult(size = sizes[type], isValid = true)

        override suspend fun generation(): Long = 0L

        override suspend fun updateCache(sizes: Map<LocationType, Long>, generation: Long) = Unit

        override suspend fun clearCache(): Boolean = true
    }

    /**
     * Internal storage, an SD card and a USB drive, rooted in the fixture folders and named as
     * the app names them in the current language.
     */
    private object ScreenshotStorages : StorageSource {
        private val context: Context get() = ApplicationProvider.getApplicationContext()

        fun internal() = StorageDevice(
            path = Fixtures.internalRoot.absolutePath,
            displayName = context.getString(R.string.storage_internal),
            totalBytes = 128 * GIB,
            availableBytes = 79 * GIB + 900 * MIB,
            type = StorageType.INTERNAL
        )

        override suspend fun getStorages(): List<StorageDevice> = listOf(
            internal(),
            StorageDevice(
                path = Fixtures.sdCardRoot.absolutePath,
                displayName = context.getString(R.string.storage_sd_card),
                totalBytes = 64 * GIB,
                availableBytes = 41 * GIB + 200 * MIB,
                type = StorageType.SD_CARD
            ),
            StorageDevice(
                path = Fixtures.usbRoot.absolutePath,
                displayName = context.getString(R.string.storage_usb_drive),
                totalBytes = 32 * GIB,
                availableBytes = 27 * GIB + 500 * MIB,
                type = StorageType.USB_DRIVE
            )
        )
    }

    /**
     * The files every screenshot shows, copied out of the test APK's `website/` assets once per
     * run. Photos are padded to a camera's file sizes with bytes past the end of the image, which
     * every decoder ignores, so the repository keeps small copies.
     */
    private object Fixtures {
        private val root: File by lazy {
            val context = ApplicationProvider.getApplicationContext<Context>()
            File(context.getExternalFilesDir(null), "website-fixtures").apply {
                deleteRecursively()
                prepare(this)
            }
        }

        val internalRoot: File get() = File(root, "internal")
        val sdCardRoot: File get() = File(root, "sdcard")
        val usbRoot: File get() = File(root, "usb")
        val album: File get() = File(internalRoot, "Pictures/$ALBUM_NAME")
        val pdf: File get() = File(internalRoot, "Documents/$PDF_NAME")

        val photoNames: List<String> by lazy { album.list().orEmpty().sorted() }

        fun recentFiles(): List<RecentFile> {
            val now = System.currentTimeMillis()
            val files = listOf(photo(9), pdf, photo(7), photo(4))
            return files.mapIndexed { index, file ->
                RecentFile(
                    path = file.absolutePath,
                    name = file.name,
                    mimeType = if (file == pdf) "application/pdf" else "image/jpeg",
                    lastOpenedTimestamp = now - (index + 1) * HOUR_MS
                )
            }
        }

        fun favorites(): List<Favorite> {
            val now = System.currentTimeMillis()
            val sunset = photo(9)
            val dog = photo(4)
            return listOf(
                Favorite(sunset.absolutePath, sunset.name, false, "image/jpeg", now - 1 * DAY_MS),
                Favorite(pdf.absolutePath, pdf.name, false, "application/pdf", now - 3 * DAY_MS),
                Favorite(dog.absolutePath, dog.name, false, "image/jpeg", now - 5 * DAY_MS),
                Favorite(album.absolutePath, album.name, true, "", now - 9 * DAY_MS)
            )
        }

        private fun photo(index: Int): File = File(album, photoNames[index])

        private fun prepare(root: File) {
            val assets = InstrumentationRegistry.getInstrumentation().context.assets
            val internal = File(root, "internal")
            listOf("Download", "DCIM/Camera", "Movies", "Music").forEach { File(internal, it).mkdirs() }
            File(root, "sdcard/DCIM").mkdirs()
            File(root, "usb").mkdirs()

            val album = File(internal, "Pictures/$ALBUM_NAME").apply { mkdirs() }
            assets.list("website/photos").orEmpty().sorted().forEachIndexed { index, name ->
                val photo = File(album, name)
                assets.open("website/photos/$name").use { input -> photo.outputStream().use { input.copyTo(it) } }
                RandomAccessFile(photo, "rw").use { it.setLength(maxOf(it.length(), PHOTO_SIZES[index % PHOTO_SIZES.size])) }
                // Taken on the day its name starts with, in the evening.
                photo.setLastModified(
                    LocalDate.parse(name.take(10)).atTime(18, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
                )
            }

            val pdf = File(internal, "Documents/$PDF_NAME").apply { parentFile?.mkdirs() }
            assets.open("website/$PDF_NAME").use { input -> pdf.outputStream().use { input.copyTo(it) } }
        }
    }

    companion object {
        /** The site's language codes, each with the locale its pages are written in. */
        private val ANDROID_TAGS = mapOf(
            "en" to "en-US",
            "ar" to "ar",
            "bn" to "bn",
            "ca" to "ca",
            "de" to "de",
            "el" to "el",
            "es" to "es",
            "fr" to "fr",
            "hi" to "hi",
            "id" to "id",
            "it" to "it",
            "ja" to "ja",
            "nl" to "nl",
            "pt" to "pt-BR",
            "ro" to "ro",
            "ru" to "ru",
            "tr" to "tr",
            "ur" to "ur",
            "vi" to "vi",
            "zh" to "zh-CN"
        )

        private const val ALBUM_NAME = "Summer 2026"
        private const val SEARCH_QUERY = "2026"
        private const val PDF_NAME = "Summer Days at Mount Shasta.pdf"
        private const val PDF_QUERY = "Shasta"

        private const val TIMEOUT_MS = 10_000L
        private const val SETTLE_MS = 1_500L
        private const val HOUR_MS = 3_600_000L
        private const val DAY_MS = 24 * HOUR_MS
        private const val MIB = 1024L * 1024L
        private const val GIB = 1024L * MIB

        private val PHOTO_SIZES = listOf(
            2_412_000L, 3_187_000L, 1_934_000L, 4_206_000L, 2_745_000L,
            3_618_000L, 2_291_000L, 3_904_000L, 2_873_000L, 1_768_000L
        )

        private val ANALYZED_SIZES = mapOf(
            SearchFileType.IMAGES to 11 * GIB + 400 * MIB,
            SearchFileType.VIDEOS to 17 * GIB + 800 * MIB,
            SearchFileType.AUDIO to 3 * GIB + 900 * MIB,
            SearchFileType.DOCUMENTS to 1 * GIB + 200 * MIB,
            SearchFileType.OTHER to 4 * GIB + 600 * MIB
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun languages(): List<String> = ANDROID_TAGS.keys.toList()

        @JvmStatic
        @AfterClass
        fun restoreLanguage() {
            localeManager().applicationLocales = LocaleList.getEmptyLocaleList()
        }

        private fun localeManager(): LocaleManager =
            ApplicationProvider.getApplicationContext<Context>().getSystemService(LocaleManager::class.java)

        private fun outputRoot(): File =
            File(ApplicationProvider.getApplicationContext<Context>().getExternalFilesDir(null), "website-screenshots")
    }
}
