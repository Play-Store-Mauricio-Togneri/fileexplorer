package com.mauriciotogneri.fileexplorer.ui.screens.picker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.data.model.StorageType
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fixture volume names are ones no string resource defines. `StoragePickerItem` must render the
 * `StorageDevice.displayName` it is handed — vendor volume labels arrive that way — and a fixture
 * named `storage_internal` / `storage_sd_card` is exactly what a row printing its type's label
 * instead would show, so every matcher here would pass with that wiring broken. A name the test
 * owns is never translated, so it is also correctly written inline on every locale.
 */
@RunWith(AndroidJUnit4::class)
class StorageSelectorContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val internalStorage = StorageDevice(
        path = "/storage/emulated/0",
        displayName = INTERNAL_NAME,
        totalBytes = 64_000_000_000L,
        availableBytes = 32_000_000_000L,
        type = StorageType.INTERNAL
    )

    private val sdCard = StorageDevice(
        path = "/storage/sdcard1",
        displayName = CARD_NAME,
        totalBytes = 32_000_000_000L,
        availableBytes = 16_000_000_000L,
        type = StorageType.SD_CARD
    )

    @Test
    fun storageNames_areDisplayed() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StorageSelectorContent(
                    storages = listOf(internalStorage, sdCard),
                    onStorageClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText(INTERNAL_NAME).assertIsDisplayed()
        composeTestRule.onNodeWithText(CARD_NAME).assertIsDisplayed()
    }

    /**
     * Built through `getString` rather than written out as "29.8 GB available": both halves of that
     * literal are locale-dependent — `storage_available` is "%s verfügbar" in German, and
     * `formattedAvailable` goes through a `DecimalFormat` that renders the same bytes as "29,8".
     * The literal form failed on every non-English, non-US-decimal device.
     */
    @Test
    fun storageAvailableSpace_isDisplayed() {
        val expected = context.getString(
            R.string.storage_available,
            internalStorage.formattedAvailable
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                StorageSelectorContent(
                    storages = listOf(internalStorage),
                    onStorageClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText(expected).assertIsDisplayed()
    }

    @Test
    fun storageTap_triggersCallback() {
        var clickedStorage: StorageDevice? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                StorageSelectorContent(
                    storages = listOf(internalStorage, sdCard),
                    onStorageClick = { clickedStorage = it }
                )
            }
        }

        composeTestRule.onNodeWithText(CARD_NAME).performClick()

        assertEquals(sdCard, clickedStorage)
    }

    @Test
    fun emptyStorageList_showsNothing() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StorageSelectorContent(
                    storages = emptyList(),
                    onStorageClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText(INTERNAL_NAME).assertDoesNotExist()
        composeTestRule.onNodeWithText(CARD_NAME).assertDoesNotExist()
    }

    private companion object {
        const val INTERNAL_NAME = "Fixture Internal"
        const val CARD_NAME = "Fixture Card"
    }
}
