package com.mauriciotogneri.fileexplorer.ui.screens.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.Location
import com.mauriciotogneri.fileexplorer.data.model.LocationType
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.ui.components.LocationsSection
import com.mauriciotogneri.fileexplorer.ui.components.StoragesSection
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

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
        displayName = "Internal Storage",
        totalBytes = 1024L * 1024 * 1024 * 64, // 64 GB
        availableBytes = 1024L * 1024 * 1024 * 20 // 20 GB available
    )

    private val sdCardStorage = StorageDevice(
        path = "/storage/sdcard1",
        displayName = "SD Card",
        totalBytes = 1024L * 1024 * 1024 * 32, // 32 GB
        availableBytes = 1024L * 1024 * 1024 * 25 // 25 GB available
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
        composeTestRule.onNodeWithText("Internal Storage")
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
        composeTestRule.onNodeWithText("SD Card")
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
        composeTestRule.onNodeWithText("Internal Storage").assertIsDisplayed()
        composeTestRule.onNodeWithText("SD Card").assertIsDisplayed()
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
        composeTestRule.onNodeWithText("Internal Storage")
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
        composeTestRule.onNodeWithText("SD Card")
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

        composeTestRule.onNodeWithText("Internal Storage").performClick()
        composeTestRule.onNodeWithText("SD Card").performClick()

        assertEquals(2, clickedStorages.size)
        assertTrue(clickedStorages.contains("Internal Storage"))
        assertTrue(clickedStorages.contains("SD Card"))
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
}
