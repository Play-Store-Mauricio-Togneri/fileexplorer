package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stage 9 (Point 9) — BadgeDot component. Verifies the new-feature dot renders only when
 * `showBadge` is true and that wrapped content is always shown and remains interactive.
 *
 * The dot is a bare [androidx.compose.foundation.layout.Box] with no text/contentDescription, so it
 * carries a [BADGE_DOT_TEST_TAG] (a behavior-neutral test seam) to be matchable. The drawer/menu
 * badge dismiss LOGIC is covered by the existing `HomeViewModelBadgeTest` unit test.
 */
@RunWith(AndroidJUnit4::class)
class BadgeDotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun badgeDot_showBadgeTrue_dotDisplayed() {
        composeTestRule.setContent {
            FileExplorerTheme {
                BadgeDot(showBadge = true) { Text("content") }
            }
        }

        composeTestRule.onNodeWithTag(BADGE_DOT_TEST_TAG).assertExists()
        composeTestRule.onNodeWithText("content").assertIsDisplayed()
    }

    @Test
    fun badgeDot_showBadgeFalse_dotHidden() {
        composeTestRule.setContent {
            FileExplorerTheme {
                BadgeDot(showBadge = false) { Text("content") }
            }
        }

        composeTestRule.onNodeWithTag(BADGE_DOT_TEST_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText("content").assertIsDisplayed()
    }

    @Test
    fun badgeDot_wrapsContent_contentStillClickable() {
        var clicked = false
        composeTestRule.setContent {
            FileExplorerTheme {
                BadgeDot(showBadge = true) {
                    Text("content", modifier = Modifier.clickable { clicked = true })
                }
            }
        }

        composeTestRule.onNodeWithText("content").performClick()
        assertTrue("Wrapped content should remain clickable", clicked)
    }
}
