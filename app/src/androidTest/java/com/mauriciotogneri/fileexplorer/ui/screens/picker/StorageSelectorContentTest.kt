package com.mauriciotogneri.fileexplorer.ui.screens.picker

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @Test
    fun storageAvailableSpace_isDisplayed() {
        composeTestRule.setContent {
            FileExplorerTheme {
                StorageSelectorContent(
                    storages = listOf(internalStorage),
                    onStorageClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("29.8 GB available").assertIsDisplayed()
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
