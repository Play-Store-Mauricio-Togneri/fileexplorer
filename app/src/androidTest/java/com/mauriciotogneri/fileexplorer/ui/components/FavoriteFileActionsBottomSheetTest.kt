package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.Favorite
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the favorites per-item action sheet ([FavoriteFileActionsBottomSheet]).
 *
 * Renders the real composable directly. The sheet's `AnalyticsTracker` calls are null-safe no-ops
 * under test, so no Firebase init is required.
 *
 * Directory favorites hide Open with, Share, and Open folder, so those actions apply to files only
 * — mirroring the search action sheet.
 */
@RunWith(AndroidJUnit4::class)
class FavoriteFileActionsBottomSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun createTestFavorite(
        name: String = "document.txt",
        isDirectory: Boolean = false,
        mimeType: String = "text/plain"
    ) = Favorite(
        path = "/storage/emulated/0/Documents/$name",
        name = name,
        isDirectory = isDirectory,
        mimeType = mimeType,
        favoritedTimestamp = System.currentTimeMillis()
    )

    private fun setSheet(favorite: Favorite) {
        composeTestRule.setContent {
            FileExplorerTheme {
                // Production always opens this sheet in "icon" mode (long press handles selection).
                FavoriteFileActionsBottomSheet(
                    favorite = favorite,
                    mode = "icon",
                    onAction = {},
                    onDismiss = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun assertActionDisplayed(resId: Int) {
        composeTestRule.onNodeWithText(context.getString(resId)).assertIsDisplayed()
    }

    private fun assertActionDoesNotExist(resId: Int) {
        composeTestRule.onNodeWithText(context.getString(resId)).assertDoesNotExist()
    }

    @Test
    fun file_showsAllActions() {
        setSheet(createTestFavorite())

        assertActionDisplayed(R.string.action_open_with)
        assertActionDisplayed(R.string.action_share)
        assertActionDisplayed(R.string.action_open_folder)
        assertActionDisplayed(R.string.action_remove_from_favorites)
        assertActionDisplayed(R.string.action_delete)
        assertActionDisplayed(R.string.action_info)
    }

    @Test
    fun directory_hidesOpenWithShareAndOpenFolder() {
        setSheet(
            createTestFavorite(
                name = "MyFolder",
                isDirectory = true,
                mimeType = "inode/directory"
            )
        )

        assertActionDoesNotExist(R.string.action_open_with)
        assertActionDoesNotExist(R.string.action_share)
        assertActionDoesNotExist(R.string.action_open_folder)

        assertActionDisplayed(R.string.action_remove_from_favorites)
        assertActionDisplayed(R.string.action_delete)
        assertActionDisplayed(R.string.action_info)
    }
}
