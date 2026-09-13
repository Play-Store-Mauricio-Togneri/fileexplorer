package com.mauriciotogneri.fileexplorer.ui.screens.picker

import androidx.annotation.StringRes
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
 * Storage display names go through `getString`, not written out as "Internal Storage"/"SD Card".
 * Production derives both from resources (`AndroidStorageSource` falls back to `storage_internal` /
 * `storage_sd_card`), so a literal is locale-dependent: on a German device the resource reads
 * "Interner Speicher" and every matcher here silently stops matching — which makes the
 * `assertDoesNotExist` cases below pass whether the row is on screen or not.
 */
@RunWith(AndroidJUnit4::class)
class StorageSelectorContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(@StringRes id: Int): String = context.getString(id)

    private val internalStorage = StorageDevice(
        path = "/storage/emulated/0",
        displayName = string(R.string.storage_internal),
        totalBytes = 64_000_000_000L,
        availableBytes = 32_000_000_000L,
        type = StorageType.INTERNAL
    )

    private val sdCard = StorageDevice(
        path = "/storage/sdcard1",
        displayName = string(R.string.storage_sd_card),
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

        composeTestRule.onNodeWithText(string(R.string.storage_internal)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.storage_sd_card)).assertIsDisplayed()
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

        composeTestRule.onNodeWithText(string(R.string.storage_sd_card)).performClick()

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

        composeTestRule.onNodeWithText(string(R.string.storage_internal)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.storage_sd_card)).assertDoesNotExist()
    }
}
