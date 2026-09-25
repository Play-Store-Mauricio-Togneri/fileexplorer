package com.mauriciotogneri.fileexplorer.ui.screens.home

import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.data.model.HomeSection
import com.mauriciotogneri.fileexplorer.data.model.Location
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.RecentFile
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.StorageType
import com.mauriciotogneri.fileexplorer.data.repository.FavoritesRepository
import com.mauriciotogneri.fileexplorer.data.repository.FileRepository
import com.mauriciotogneri.fileexplorer.data.repository.LocationsRepository
import com.mauriciotogneri.fileexplorer.data.repository.PreferencesRepository
import com.mauriciotogneri.fileexplorer.data.repository.RecentFilesRepository
import com.mauriciotogneri.fileexplorer.data.repository.StorageRepository
import com.mauriciotogneri.fileexplorer.data.repository.preferencesDataStore
import com.mauriciotogneri.fileexplorer.data.source.DataStorePreferencesSource
import com.mauriciotogneri.fileexplorer.data.source.FavoriteFilesSource
import com.mauriciotogneri.fileexplorer.data.source.RecentFilesSource
import com.mauriciotogneri.fileexplorer.testutil.FakeStorageSource
import com.mauriciotogneri.fileexplorer.testutil.NoExternalChanges
import com.mauriciotogneri.fileexplorer.testutil.WarmLocationSizes
import com.mauriciotogneri.fileexplorer.testutil.FileFixtures
import com.mauriciotogneri.fileexplorer.ui.components.LOCATION_SIZE_PLACEHOLDER_TEST_TAG
import com.mauriciotogneri.fileexplorer.ui.components.LocationsSection
import com.mauriciotogneri.fileexplorer.ui.components.StoragesSection
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val application = context.applicationContext as Application

    private val preferencesRepository =
        PreferencesRepository(DataStorePreferencesSource(application.preferencesDataStore))

    // Storage root the whole-screen tests report, and where their recents/favorites fixtures live.
    private val testDir = File(context.cacheDir, "test_home_${System.currentTimeMillis()}")

    /**
     * Puts the preferences the whole-screen tests write back to what a fresh install holds, so a
     * failure cannot leave the device — or the next test — on a rearranged home screen. Runs for the
     * isolated-section tests too, which write none of them: three writes of the shipped defaults
     * cost less than tracking which test seeded what.
     */
    @After
    fun restoreDefaults() {
        runBlocking {
            preferencesRepository.setHomeSectionOrder(HomeSection.DEFAULT_ORDER)
            preferencesRepository.setRecentFilesEnabled(true)
            preferencesRepository.setEnabledLocations(LocationType.entries.toSet())
        }
        testDir.deleteRecursively()
    }

    // Test data
    private val testLocations = listOf(
        Location(
            type = LocationType.DOWNLOADS,
            path = "/storage/emulated/0/Download",
            totalSizeBytes = 1024L * 1024 * 500 // 500 MB
        ),
        Location(
            type = LocationType.IMAGES,
            path = "/storage/emulated/0/Pictures",
            totalSizeBytes = 1024L * 1024 * 1024 * 2 // 2 GB
        ),
        Location(
            type = LocationType.VIDEOS,
            path = "/storage/emulated/0/Movies",
            totalSizeBytes = 1024L * 1024 * 1024 * 5 // 5 GB
        ),
        Location(
            type = LocationType.CAMERA,
            path = "/storage/emulated/0/DCIM",
            totalSizeBytes = 1024L * 1024 * 1024 * 3 // 3 GB
        ),
        Location(
            type = LocationType.AUDIO,
            path = "/storage/emulated/0/Music",
            totalSizeBytes = 1024L * 1024 * 800 // 800 MB
        )
    )

    private val internalStorage = StorageDevice(
        path = "/storage/emulated/0",
        displayName = INTERNAL_NAME,
        totalBytes = 1024L * 1024 * 1024 * 64, // 64 GB
        availableBytes = 1024L * 1024 * 1024 * 20, // 20 GB available
        type = StorageType.INTERNAL
    )

    private val sdCardStorage = StorageDevice(
        path = "/storage/sdcard1",
        displayName = CARD_NAME,
        totalBytes = 1024L * 1024 * 1024 * 32, // 32 GB
        availableBytes = 1024L * 1024 * 1024 * 25, // 25 GB available
        type = StorageType.SD_CARD
    )

    // ==================== Locations Section Display Tests ====================

    @Test
    fun locationsSection_displaysSectionTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = testLocations,
                    onLocationClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.section_locations))
            .assertIsDisplayed()
    }

    @Test
    fun locationsSection_displaysDownloadsCard() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = listOf(testLocations[0]),
                    onLocationClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.location_downloads))
            .assertIsDisplayed()
    }

    @Test
    fun locationsSection_displaysImagesCard() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = listOf(testLocations[1]),
                    onLocationClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.location_images))
            .assertIsDisplayed()
    }

    @Test
    fun locationsSection_displaysVideosCard() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = listOf(testLocations[2]),
                    onLocationClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.location_videos))
            .assertIsDisplayed()
    }

    @Test
    fun locationsSection_displaysCameraCard() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = listOf(testLocations[3]),
                    onLocationClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.location_camera))
            .assertIsDisplayed()
    }

    @Test
    fun locationsSection_displaysAudioCard() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = listOf(testLocations[4]),
                    onLocationClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.location_audio))
            .assertIsDisplayed()
    }

    @Test
    fun locationsSection_displaysAllLocationCards() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = testLocations,
                    onLocationClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.location_downloads)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.location_images)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.location_videos)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.location_camera)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.location_audio)).assertIsDisplayed()
    }

    // ==================== Locations Section Click Tests ====================

    @Test
    fun locationsSection_showsPlaceholderForUnmeasuredLocation() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = listOf(testLocations[0].copy(totalSizeBytes = null)),
                    onLocationClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(LOCATION_SIZE_PLACEHOLDER_TEST_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.location_downloads)).assertIsDisplayed()
    }

    @Test
    fun locationsSection_showsSizeInsteadOfPlaceholderOnceMeasured() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = listOf(testLocations[0]),
                    onLocationClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(testLocations[0].formattedSize!!).assertIsDisplayed()
        composeTestRule.onNodeWithTag(LOCATION_SIZE_PLACEHOLDER_TEST_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun locationsSection_downloadsClick_triggersCallback() {
        var clickedLocation: Location? = null
        var clickedTitle: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = listOf(testLocations[0]),
                    onLocationClick = { location, title ->
                        clickedLocation = location
                        clickedTitle = title
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.location_downloads))
            .performClick()

        assertEquals(testLocations[0], clickedLocation)
        assertEquals(context.getString(R.string.location_downloads), clickedTitle)
    }

    @Test
    fun locationsSection_imagesClick_triggersCallback() {
        var clickedLocation: Location? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = listOf(testLocations[1]),
                    onLocationClick = { location, _ -> clickedLocation = location }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.location_images))
            .performClick()

        assertEquals(testLocations[1], clickedLocation)
    }

    @Test
    fun locationsSection_videosClick_triggersCallback() {
        var clickedLocation: Location? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = listOf(testLocations[2]),
                    onLocationClick = { location, _ -> clickedLocation = location }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.location_videos))
            .performClick()

        assertEquals(testLocations[2], clickedLocation)
    }

    @Test
    fun locationsSection_cameraClick_triggersCallback() {
        var clickedLocation: Location? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = listOf(testLocations[3]),
                    onLocationClick = { location, _ -> clickedLocation = location }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.location_camera))
            .performClick()

        assertEquals(testLocations[3], clickedLocation)
    }

    @Test
    fun locationsSection_audioClick_triggersCallback() {
        var clickedLocation: Location? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = listOf(testLocations[4]),
                    onLocationClick = { location, _ -> clickedLocation = location }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.location_audio))
            .performClick()

        assertEquals(testLocations[4], clickedLocation)
    }

    @Test
    fun locationsSection_eachLocationClick_triggersCorrectCallback() {
        val clickedLocations = mutableListOf<LocationType>()

        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = testLocations,
                    onLocationClick = { location, _ -> clickedLocations.add(location.type) }
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(context.getString(R.string.location_downloads)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.location_images)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.location_videos)).performClick()

        assertEquals(3, clickedLocations.size)
        assertTrue(clickedLocations.contains(LocationType.DOWNLOADS))
        assertTrue(clickedLocations.contains(LocationType.IMAGES))
        assertTrue(clickedLocations.contains(LocationType.VIDEOS))
    }

    // ==================== Storages Section Display Tests ====================

    @Test
    fun storagesSection_displaysSectionTitle() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StoragesSection(
                    storages = listOf(internalStorage),
                    onStorageClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.section_storage))
            .assertIsDisplayed()
    }

    @Test
    fun storagesSection_displaysInternalStorageCard() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StoragesSection(
                    storages = listOf(internalStorage),
                    onStorageClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(INTERNAL_NAME)
            .assertIsDisplayed()
    }

    @Test
    fun storagesSection_displaysSDCardCard() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StoragesSection(
                    storages = listOf(sdCardStorage),
                    onStorageClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(CARD_NAME)
            .assertIsDisplayed()
    }

    @Test
    fun storagesSection_displaysStorageCapacity() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StoragesSection(
                    storages = listOf(internalStorage),
                    onStorageClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Check that capacity info is displayed (format: "X available of Y")
        composeTestRule.onNodeWithText(
            context.getString(
                R.string.storage_capacity_format,
                internalStorage.formattedAvailable,
                internalStorage.formattedTotal
            )
        ).assertIsDisplayed()
    }

    @Test
    fun storagesSection_displaysMultipleStorages() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StoragesSection(
                    storages = listOf(internalStorage, sdCardStorage),
                    onStorageClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(INTERNAL_NAME).assertIsDisplayed()
        composeTestRule.onNodeWithText(CARD_NAME).assertIsDisplayed()
    }

    // ==================== Storages Section Click Tests ====================

    @Test
    fun storagesSection_internalStorageClick_triggersCallback() {
        var clickedStorage: StorageDevice? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                StoragesSection(
                    storages = listOf(internalStorage),
                    onStorageClick = { clickedStorage = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(INTERNAL_NAME)
            .performClick()

        assertEquals(internalStorage, clickedStorage)
    }

    @Test
    fun storagesSection_sdCardClick_triggersCallback() {
        var clickedStorage: StorageDevice? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                StoragesSection(
                    storages = listOf(sdCardStorage),
                    onStorageClick = { clickedStorage = it }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(CARD_NAME)
            .performClick()

        assertEquals(sdCardStorage, clickedStorage)
    }

    @Test
    fun storagesSection_eachStorageClick_triggersCorrectCallback() {
        val clickedStorages = mutableListOf<String>()

        composeTestRule.setContent {
            FileExplorerTheme {
                StoragesSection(
                    storages = listOf(internalStorage, sdCardStorage),
                    onStorageClick = { clickedStorages.add(it.displayName) }
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(INTERNAL_NAME).performClick()
        composeTestRule.onNodeWithText(CARD_NAME).performClick()

        assertEquals(2, clickedStorages.size)
        assertTrue(clickedStorages.contains(INTERNAL_NAME))
        assertTrue(clickedStorages.contains(CARD_NAME))
    }

    // ==================== Empty State Tests ====================

    @Test
    fun locationsSection_emptyList_displaysNothing() {
        composeTestRule.setContent {
            FileExplorerTheme {
                LocationsSection(
                    locations = emptyList(),
                    onLocationClick = { _, _ -> }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.section_locations))
            .assertDoesNotExist()
    }

    @Test
    fun storagesSection_emptyList_displaysNothing() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StoragesSection(
                    storages = emptyList(),
                    onStorageClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.section_storage))
            .assertDoesNotExist()
    }

    // ==================== Whole-screen Section Tests ====================

    // Everything above renders a section composable on its own, so nothing covered the dispatch in
    // HomeScreen itself: deleting its `HomeSection.LOCATIONS ->` arm, or swapping two arms so the
    // arrangement the user chose is silently ignored, made a whole home section disappear with every
    // test in this file still green. The three tests below render the real screen.
    //
    // Seeded in the shape NavigationDrawerTest established — the real HomeViewModel and the real
    // repositories, with only their sources replaced: FakeStorageSource for the volume enumeration,
    // WarmLocationSizes so getLocations() reports a cache hit instead of walking the device's
    // storage, and a fixed recents/favorites list each, since the app's own stores hold whatever the
    // device happens to have.
    //
    // The one section a test cannot seed is LOCATIONS: HomeViewModel takes LocationsRepository
    // itself, and that resolves its paths through Environment, so *which* locations exist is the
    // device's answer rather than the test's. It is still asserted outright rather than skipped — a
    // device with no public media directory at all fails here instead of quietly dropping the arm
    // from coverage, which is the hole these tests exist to close.

    @Test
    fun homeScreen_rendersEverySectionItIsArrangedToShow() {
        arrangeSections(HomeSection.DEFAULT_ORDER)

        renderHomeScreen()

        // Waited for one at a time so a `when (section)` arm that stopped rendering names itself.
        HomeSection.entries.forEach { section -> awaitSection(section) }
    }

    @Test
    fun homeScreen_rendersSectionsInTheArrangedOrder() {
        // The reverse of the default, so an arrangement that is read but not applied — and any two
        // arms swapped over — puts a header somewhere this test can see.
        val order = HomeSection.DEFAULT_ORDER.reversed()
        arrangeSections(order)

        renderHomeScreen()
        order.forEach { section -> awaitSection(section) }

        // The arrangement reaches uiState through a DataStore read of its own, which can land after
        // the frame the sections were first drawn in, so the order is waited for rather than
        // sampled. A wait that never settles is left to the assertions below, which name the pair
        // that was out of place.
        runCatching {
            composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                order.zipWithNext().all { (upper, lower) -> topOf(upper) < topOf(lower) }
            }
        }

        order.zipWithNext().forEach { (upper, lower) ->
            val upperTop = topOf(upper)
            val lowerTop = topOf(lower)
            assertTrue(
                "${upper.name} is arranged above ${lower.name}, but its header sits at $upperTop " +
                    "and ${lower.name}'s at $lowerTop",
                upperTop < lowerTop
            )
        }
    }

    @Test
    fun homeScreen_omitsTheRecentSection_whenRecentFilesAreTurnedOff() {
        // Recents are still seeded: the section must be gone because the preference is off, not
        // because there was nothing to show.
        arrangeSections(HomeSection.DEFAULT_ORDER, recentFilesEnabled = false)

        renderHomeScreen()

        // Awaited first, so RECENT's absence is the preference rather than a frame yet to arrive.
        HomeSection.entries.filter { it != HomeSection.RECENT }.forEach { awaitSection(it) }
        composeTestRule.onNodeWithText(sectionTitle(HomeSection.RECENT)).assertDoesNotExist()
    }

    // ==================== Whole-screen Helpers ====================

    /**
     * Writes the home arrangement the test expects to read back. The app's own preferences store,
     * because that is what [HomeViewModel] observes and there is no seam to inject;
     * [restoreDefaults] puts every value back.
     *
     * Every location is enabled as well, even though no test varies it: the settings tests toggle
     * that same stored set, and a run that left them all off would empty the LOCATIONS section for
     * a reason that has nothing to do with what is asserted here.
     */
    private fun arrangeSections(order: List<HomeSection>, recentFilesEnabled: Boolean = true) {
        runBlocking {
            preferencesRepository.setHomeSectionOrder(order)
            preferencesRepository.setRecentFilesEnabled(recentFilesEnabled)
            preferencesRepository.setEnabledLocations(LocationType.entries.toSet())
        }
    }

    private fun renderHomeScreen() {
        // Real files: both repositories re-stat every stored entry and drop the ones whose file is
        // gone, so a fixture that does not exist on disk never reaches the screen.
        val recentFixture = FileFixtures.createTextFile(testDir, "recent_fixture.txt")
        val favoriteFixture = FileFixtures.createTextFile(testDir, "favorite_fixture.txt")
        val recentFiles = listOf(
            RecentFile(
                path = recentFixture.absolutePath,
                name = recentFixture.name,
                mimeType = TEXT_MIME_TYPE,
                lastOpenedTimestamp = System.currentTimeMillis()
            )
        )
        val favorites = listOf(
            Favorite(
                path = favoriteFixture.absolutePath,
                name = favoriteFixture.name,
                isDirectory = false,
                mimeType = TEXT_MIME_TYPE,
                favoritedTimestamp = System.currentTimeMillis()
            )
        )

        composeTestRule.setContent {
            val viewModel = remember { buildHomeViewModel(recentFiles, favorites) }
            FileExplorerTheme {
                HomeScreen(viewModel = viewModel)
            }
        }
    }

    private fun buildHomeViewModel(
        recentFiles: List<RecentFile>,
        favorites: List<Favorite>
    ): HomeViewModel = HomeViewModel(
        application = application,
        recentFilesRepository = RecentFilesRepository(SeededRecentFiles(recentFiles)),
        favoritesRepository = FavoritesRepository(SeededFavorites(favorites)),
        locationsRepository = LocationsRepository(WarmLocationSizes, preferencesRepository),
        storageRepository = StorageRepository(FakeStorageSource(testDir)),
        preferencesRepository = preferencesRepository,
        fileRepository = FileRepository(),
        mediaChangeSource = NoExternalChanges,
        storageVolumeChangeSource = NoExternalChanges
    )

    private fun sectionTitle(section: HomeSection): String = context.getString(section.titleResId)

    /** Waits for [section]'s header, naming the section when it never arrives. */
    private fun awaitSection(section: HomeSection) {
        try {
            composeTestRule.waitUntil(timeoutMillis = TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(sectionTitle(section))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: ComposeTimeoutException) {
            fail(
                "HomeScreen never rendered the ${section.name} section, so its when(section) arm " +
                    "draws nothing (${timeout.message})"
            )
        }
    }

    /**
     * The top edge of [section]'s header, with the clip left out. Every section sits in one
     * `verticalScroll` column, so the ones below the fold are laid out and carry semantics but are
     * clipped away to an empty rectangle — `boundsInRoot` would read 0 for each of them and the
     * comparisons would hold whatever the order was.
     */
    private fun topOf(section: HomeSection): Dp =
        composeTestRule.onNodeWithText(sectionTitle(section)).getUnclippedBoundsInRoot().top

    /**
     * Recents exactly as the test seeded them. Seeding through the app's own store would both depend
     * on the device's existing recents and outlive the test.
     */
    private class SeededRecentFiles(private val files: List<RecentFile>) : RecentFilesSource {
        override val recentFilesFlow: Flow<List<RecentFile>> = flowOf(files)

        override suspend fun getRecentFiles(): List<RecentFile> = files

        override suspend fun updateRecentFiles(transform: (List<RecentFile>) -> List<RecentFile>) = Unit

        override suspend fun clearRecentFiles() = Unit
    }

    /** Favorites exactly as the test seeded them. See [SeededRecentFiles]. */
    private class SeededFavorites(private val favorites: List<Favorite>) : FavoriteFilesSource {
        override val favoritesFlow: Flow<List<Favorite>> = flowOf(favorites)

        override suspend fun getFavorites(): List<Favorite> = favorites

        override suspend fun updateFavorites(transform: (List<Favorite>) -> List<Favorite>) = Unit

        override suspend fun clearFavorites() = Unit
    }

    /**
     * Answers every location with a valid cached size, which is what keeps `getLocations()` from
     * walking a tree: it measures only on a cache miss. Nothing is written back, so the app's own
     * cache store is left exactly as the test found it.
     */

    /**
     * Neither a media notification nor a volume broadcast during these tests, so nothing reloads
     * underneath the assertions. One object for both interfaces: they declare the same member.
     */

    private companion object {
        /**
         * Deliberately not "Internal Storage"/"SD Card". `StoragesSection` must render the
         * `StorageDevice.displayName` it is handed, and those two literals are also what
         * R.string.storage_internal / storage_sd_card resolve to in English — so a row that
         * ignored `displayName` and printed the resource instead satisfied every matcher in this
         * file. Names no resource defines make the assertions prove the wiring, and keep them
         * locale-independent: a fixture name the test owns is never translated, so it is
         * correctly written inline.
         */
        const val INTERNAL_NAME = "Fixture Internal"
        const val CARD_NAME = "Fixture Card"

        // A ceiling on composition and the preference store's first read, not on a directory walk:
        // WarmLocationSizes removes the scan a real home load waits for.
        const val TIMEOUT_MS = 10_000L

        // Fixture mime type, chosen because it has no thumbnail support: the cards then draw an icon
        // instead of starting an image load the assertions would have to wait behind.
        const val TEXT_MIME_TYPE = "text/plain"
    }
}
