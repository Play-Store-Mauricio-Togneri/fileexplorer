package com.mauriciotogneri.fileexplorer.ui.components

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The root segment's label goes through `getString`, not written out as "Internal Storage".
 * `Breadcrumbs` resolves it itself — `stringResource(R.string.storage_internal)` at Breadcrumbs.kt:47
 * — so it is translated UI chrome, not a path segment the filesystem supplies. As a literal these
 * matchers stop matching on every non-English device, which turns the two `assertDoesNotExist`
 * cases below into guaranteed passes: production could prepend the internal-storage root even when
 * `rootDisplayName` is given, or render it for an empty path, and neither would fail off-locale.
 */
@RunWith(AndroidJUnit4::class)
class BreadcrumbsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(@StringRes id: Int): String = context.getString(id)

    @Test
    fun breadcrumbs_displaysAllSegments() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Ledgers/Work",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.storage_internal)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Ledgers").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_rootSegment_showsStorageName() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Parcels",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.storage_internal)).assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_segmentTap_triggersNavigation() {
        var navigatedPath: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Ledgers/Work",
                    onNavigateToPath = { navigatedPath = it },
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText("Ledgers").performClick()

        assertEquals("/storage/emulated/0/Ledgers", navigatedPath)
    }

    @Test
    fun breadcrumbs_currentSegment_notClickable() {
        var navigatedPath: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Ledgers/Work",
                    onNavigateToPath = { navigatedPath = it },
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText("Work").performClick()

        assertNull(navigatedPath)
    }

    @Test
    fun breadcrumbs_horizontalScroll_works() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Level1/Level2/Level3/Level4/Level5/Level6/Level7/Level8",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNode(hasScrollAction()).assertExists()
        composeTestRule.onNode(hasScrollAction()).performScrollToIndex(0)
        composeTestRule.onNodeWithText(string(R.string.storage_internal)).assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_autoScrollsToEnd() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Level1/Level2/Level3/Level4/Level5/Level6/Level7/DeepFolder",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("DeepFolder").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_singleSegment_displaysCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.storage_internal)).assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_deepPath_displaysAllSegments() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/L1/L2/L3/L4/L5/L6/L7/L8",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNode(hasScrollAction()).performScrollToIndex(0)
        composeTestRule.onNodeWithText(string(R.string.storage_internal)).assertIsDisplayed()

        composeTestRule.onNode(hasScrollAction()).performScrollToIndex(8)
        composeTestRule.onNodeWithText("L8").assertIsDisplayed()
    }

    /**
     * A separator follows every segment except the last, so a three-segment trail draws exactly two
     * — a count that fails both if the chevrons disappear and if one trails the current folder.
     *
     * The chevron is decorative (`contentDescription = null`), so it emits no semantics of its own,
     * and [BREADCRUMB_SEPARATOR_TEST_TAG] is the only thing a test can observe it by. This test
     * used to assert the three segment labels instead — byte-identical to
     * [breadcrumbs_displaysAllSegments] — which left it green with the whole `if (!isLast)` branch
     * deleted from `BreadcrumbSegment`.
     */
    @Test
    fun breadcrumbs_separatorIcons_drawnBetweenSegmentsOnly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Ledgers/Work",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        // Internal Storage / Ledgers / Work: three segments, so two separators.
        composeTestRule.onAllNodesWithTag(BREADCRUMB_SEPARATOR_TEST_TAG).assertCountEquals(2)
    }

    @Test
    fun breadcrumbs_sdCardRoot_showsSdCardPath() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/1234-5678/DCIM",
                    onNavigateToPath = {},
                    rootPath = "/storage/1234-5678",
                    rootDisplayName = string(R.string.storage_sd_card)
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.storage_sd_card)).assertIsDisplayed()
        composeTestRule.onNodeWithText("DCIM").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_specialCharactersInPath_displayCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/My Files & Documents",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText("My Files & Documents").assertIsDisplayed()
    }

    @Test
    fun breadcrumbs_customRootDisplayName_displaysCorrectly() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Parcels/Work",
                    onNavigateToPath = {},
                    rootPath = "/storage/emulated/0/Parcels",
                    rootDisplayName = "Parcels"
                )
            }
        }

        composeTestRule.onNodeWithText("Parcels").assertIsDisplayed()
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.storage_internal)).assertDoesNotExist()
    }

    @Test
    fun breadcrumbs_rootSegmentTap_triggersNavigation() {
        var navigatedPath: String? = null

        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "/storage/emulated/0/Ledgers/Work",
                    onNavigateToPath = { navigatedPath = it },
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.storage_internal)).performClick()

        assertEquals("/storage/emulated/0", navigatedPath)
    }

    @Test
    fun breadcrumbs_emptyPath_displaysNothing() {
        composeTestRule.setContent {
            FileExplorerTheme {
                Breadcrumbs(
                    currentPath = "",
                    onNavigateToPath = {},
                    rootPath = null,
                    rootDisplayName = null
                )
            }
        }

        composeTestRule.onNodeWithText(string(R.string.storage_internal)).assertDoesNotExist()
    }
}
