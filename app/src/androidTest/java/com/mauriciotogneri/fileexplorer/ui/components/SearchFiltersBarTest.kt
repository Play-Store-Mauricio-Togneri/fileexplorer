package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.SearchFileType
import com.mauriciotogneri.fileexplorer.data.model.SearchFilters
import com.mauriciotogneri.fileexplorer.data.model.SearchItemKind
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchFiltersBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun chipsShowDefaultLabels() {
        composeTestRule.setContent {
            FileExplorerTheme {
                SearchFiltersBar(
                    filters = SearchFilters(),
                    onItemKindSelected = {},
                    onIncludeHiddenChanged = {},
                    onTypeToggled = {},
                    onAllTypesSelected = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.search_filter_kind_files)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.search_filter_hidden)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.search_filter_type_all)).assertIsDisplayed()
    }

    @Test
    fun selectingFoldersInvokesCallback() {
        var selected: SearchItemKind? = null
        composeTestRule.setContent {
            FileExplorerTheme {
                SearchFiltersBar(
                    filters = SearchFilters(),
                    onItemKindSelected = { selected = it },
                    onIncludeHiddenChanged = {},
                    onTypeToggled = {},
                    onAllTypesSelected = {}
                )
            }
        }

        // Open the kind sheet, then pick "Folders" (unique to the sheet row).
        composeTestRule.onNodeWithText(context.getString(R.string.search_filter_kind_files)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.search_filter_kind_folders)).performClick()

        assertEquals(SearchItemKind.FOLDERS, selected)
    }

    @Test
    fun togglingImagesInvokesCallback() {
        var toggled: SearchFileType? = null
        composeTestRule.setContent {
            FileExplorerTheme {
                SearchFiltersBar(
                    filters = SearchFilters(),
                    onItemKindSelected = {},
                    onIncludeHiddenChanged = {},
                    onTypeToggled = { toggled = it },
                    onAllTypesSelected = {}
                )
            }
        }

        // Open the type sheet, then toggle "Images".
        composeTestRule.onNodeWithText(context.getString(R.string.search_filter_type_all)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.location_images)).performClick()

        assertEquals(SearchFileType.IMAGES, toggled)
    }
}
