package com.mauriciotogneri.fileexplorer.ui.screens.picker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageSelectorContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val internalStorage = StorageDevice(
        path = "/storage/emulated/0",
        displayName = "Internal Storage",
        totalBytes = 64_000_000_000L,
        availableBytes = 32_000_000_000L
    )

    private val sdCard = StorageDevice(
        path = "/storage/sdcard1",
        displayName = "SD Card",
        totalBytes = 32_000_000_000L,
        availableBytes = 16_000_000_000L
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

        composeTestRule.onNodeWithText("Internal Storage").assertIsDisplayed()
        composeTestRule.onNodeWithText("SD Card").assertIsDisplayed()
    }

    /**
     * Built through `getString` rather than written out as "29.8 GB available": both halves of that
     * literal are locale-dependent — `storage_available` is "%s verfügbar" in German, and
     * `formattedAvailable` goes through a `DecimalFormat` that renders the same bytes as "29,8".
     * The literal form failed on every non-English, non-US-decimal device.
     */
    @Test
    fun storageAvailableSpace_isDisplayed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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

        composeTestRule.onNodeWithText("SD Card").performClick()

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

        composeTestRule.onNodeWithText("Internal Storage").assertDoesNotExist()
        composeTestRule.onNodeWithText("SD Card").assertDoesNotExist()
    }
}
