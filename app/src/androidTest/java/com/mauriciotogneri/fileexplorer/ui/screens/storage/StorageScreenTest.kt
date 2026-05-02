package com.mauriciotogneri.fileexplorer.ui.screens.storage

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.data.model.StorageDevice
import com.mauriciotogneri.fileexplorer.ui.components.StorageListItem
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun storageListItem_displaysStorageInfo() {
        val storage = StorageDevice(
            path = "/storage/emulated/0",
            displayName = "Internal Storage",
            totalBytes = 64_000_000_000L,
            availableBytes = 32_000_000_000L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                StorageListItem(
                    storage = storage,
                    onClick = {}
                )
            }
        }

        // Verify display name is shown
        composeTestRule.onNodeWithText("Internal Storage").assertIsDisplayed()

        // Verify available space is shown (32 GB)
        composeTestRule.onNodeWithText("Available", substring = true).assertIsDisplayed()

        // Verify total space is shown (64 GB)
        composeTestRule.onNodeWithText("Total", substring = true).assertIsDisplayed()
    }

    @Test
    fun storageListItem_clickTriggersCallback() {
        var clicked = false
        val storage = StorageDevice(
            path = "/storage/emulated/0",
            displayName = "Internal Storage",
            totalBytes = 64_000_000_000L,
            availableBytes = 32_000_000_000L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                StorageListItem(
                    storage = storage,
                    onClick = { clicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Internal Storage").performClick()

        assertTrue(clicked)
    }

    @Test
    fun storageListItem_displaysSDCardName() {
        val storage = StorageDevice(
            path = "/storage/sdcard1",
            displayName = "SD Card",
            totalBytes = 32_000_000_000L,
            availableBytes = 16_000_000_000L
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                StorageListItem(
                    storage = storage,
                    onClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("SD Card").assertIsDisplayed()
    }
}
