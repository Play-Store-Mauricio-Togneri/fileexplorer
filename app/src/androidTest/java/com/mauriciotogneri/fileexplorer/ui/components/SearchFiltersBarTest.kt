package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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

    /**
     * Folders have no file type, so the type chip is off while the kind is Folders. Only the
     * default kind was ever rendered, so a chip left enabled — opening a type sheet that filters
     * nothing — passed.
     */
    @Test
    fun typeChipIsDisabledWhileKindIsFolders() {
        composeTestRule.setContent {
            FileExplorerTheme {
                SearchFiltersBar(
                    filters = SearchFilters(itemKind = SearchItemKind.FOLDERS),
                    onItemKindSelected = {},
                    onIncludeHiddenChanged = {},
                    onTypeToggled = {},
                    onAllTypesSelected = {}
                )
            }
        }

        val typeChip = composeTestRule.onNodeWithText(context.getString(R.string.search_filter_type_all))
        typeChip.assertIsNotEnabled()

        typeChip.performClick()
        composeTestRule.waitForIdle()

        // The type sheet lists every type; none of its rows may appear.
        composeTestRule.onNodeWithText(context.getString(R.string.location_images)).assertDoesNotExist()
    }

    /**
     * Each option of the hidden sheet reports its own value, and choosing one closes the sheet. Both
     * this and [hideHiddenOptionReportsFalse] start from hidden items excluded, so a sheet whose rows
     * only flip the current value fails the second.
     */
    @Test
    fun showHiddenOptionReportsTrue() {
        var reported: Boolean? = null
        composeTestRule.setContent {
            FileExplorerTheme {
                SearchFiltersBar(
                    filters = SearchFilters(includeHidden = false),
                    onItemKindSelected = {},
                    onIncludeHiddenChanged = { reported = it },
                    onTypeToggled = {},
                    onAllTypesSelected = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.search_filter_hidden)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.show_hidden_items)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(true, reported)
        composeTestRule.onNodeWithText(context.getString(R.string.show_hidden_items)).assertDoesNotExist()
    }

    /** See [showHiddenOptionReportsTrue]: re-choosing the current value must report it, not flip it. */
    @Test
    fun hideHiddenOptionReportsFalse() {
        var reported: Boolean? = null
        composeTestRule.setContent {
            FileExplorerTheme {
                SearchFiltersBar(
                    filters = SearchFilters(includeHidden = false),
                    onItemKindSelected = {},
                    onIncludeHiddenChanged = { reported = it },
                    onTypeToggled = {},
                    onAllTypesSelected = {}
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.search_filter_hidden)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.hide_hidden_items)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(false, reported)
        composeTestRule.onNodeWithText(context.getString(R.string.hide_hidden_items)).assertDoesNotExist()
    }

    /**
     * Several types read as the first in the sheet's order plus how many more. The set is built in a
     * different order on purpose: a label taken from the set's own first element shows "Videos"
     * instead, and one that counts every type shows "+3".
     */
    @Test
    fun typeChipSummarisesFirstTypePlusCount() {
        composeTestRule.setContent {
            FileExplorerTheme {
                SearchFiltersBar(
                    filters = SearchFilters(
                        selectedTypes = linkedSetOf(SearchFileType.VIDEOS, SearchFileType.IMAGES, SearchFileType.AUDIO)
                    ),
                    onItemKindSelected = {},
                    onIncludeHiddenChanged = {},
                    onTypeToggled = {},
                    onAllTypesSelected = {}
                )
            }
        }

        val summary = context.getString(
            R.string.search_filter_type_summary,
            context.getString(R.string.location_images),
            2
        )
        composeTestRule.onNodeWithText(summary).assertIsDisplayed()
    }
}
