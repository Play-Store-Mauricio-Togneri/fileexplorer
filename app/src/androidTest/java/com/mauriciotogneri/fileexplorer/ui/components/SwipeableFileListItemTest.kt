package com.mauriciotogneri.fileexplorer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwipeableFileListItemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(id: Int): String = context.getString(id)

    private fun createTestFile(
        name: String = "document.pdf",
        isDirectory: Boolean = false,
        size: Long = 1024L,
        mimeType: String = "application/pdf",
        childCount: Int? = null
    ) = FileItem(
        path = "/storage/emulated/0/Documents/$name",
        name = name,
        isDirectory = isDirectory,
        size = size,
        lastModified = System.currentTimeMillis(),
        createdTime = System.currentTimeMillis(),
        mimeType = mimeType,
        childCount = childCount
    )

    @Test
    fun swipeRight_revealsDeleteAction() {
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = {},
                    isSelected = false,
                    isSelectionMode = false
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertIsDisplayed()
    }

    @Test
    fun swipeLeft_revealsRenameAction() {
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = {},
                    isSelected = false,
                    isSelectionMode = false
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeLeft(startX = centerX, endX = left)
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(string(R.string.action_rename)).assertIsDisplayed()
    }

    /** Each direction reveals what it is configured with, not the pair the app happens to ship. */
    @Test
    fun eachDirection_revealsTheActionItIsConfiguredWith() {
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = {},
                    isSelected = false,
                    isSelectionMode = false,
                    leftAction = SwipeAction.INFO,
                    rightAction = SwipeAction.MOVE_TO
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(string(R.string.action_move_to)).assertIsDisplayed()

        composeTestRule.onNodeWithText("document.pdf").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeLeft(startX = centerX, endX = left)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(string(R.string.action_info)).assertIsDisplayed()
    }

    /** Both directions may hold the same action; nothing about one depends on the other. */
    @Test
    fun theSameAction_canBeConfiguredOnBothDirections() {
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = {},
                    isSelected = false,
                    isSelectionMode = false,
                    leftAction = SwipeAction.COPY_TO,
                    rightAction = SwipeAction.COPY_TO
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeLeft(startX = centerX, endX = left)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(string(R.string.action_copy_to)).assertIsDisplayed()

        composeTestRule.onNodeWithText("document.pdf").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(string(R.string.action_copy_to)).assertIsDisplayed()
    }

    /**
     * A direction set to NONE is off, not merely empty: the row must not follow the finger that way,
     * which is the whole point for a user who keeps triggering the gesture by accident. The other
     * direction is unaffected.
     */
    @Test
    fun aDirectionSetToNone_doesNotMoveTheRowAndRevealsNothing() {
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = {},
                    isSelected = false,
                    isSelectionMode = false,
                    leftAction = SwipeAction.RENAME,
                    rightAction = SwipeAction.NONE
                )
            }
        }
        composeTestRule.waitForIdle()

        val restingLeft = composeTestRule.onNodeWithText("document.pdf")
            .getUnclippedBoundsInRoot().left

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertDoesNotExist()
        composeTestRule.onNodeWithText("document.pdf")
            .assertLeftPositionInRootIsEqualTo(restingLeft)

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeLeft(startX = centerX, endX = left)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(string(R.string.action_rename)).assertIsDisplayed()
    }

    @Test
    fun bothDirectionsSetToNone_leaveTheRowInPlace() {
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = {},
                    isSelected = false,
                    isSelectionMode = false,
                    leftAction = SwipeAction.NONE,
                    rightAction = SwipeAction.NONE
                )
            }
        }
        composeTestRule.waitForIdle()

        val restingLeft = composeTestRule.onNodeWithText("document.pdf")
            .getUnclippedBoundsInRoot().left

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeLeft(startX = centerX, endX = left)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf")
            .assertLeftPositionInRootIsEqualTo(restingLeft)

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("document.pdf")
            .assertLeftPositionInRootIsEqualTo(restingLeft)
    }

    @Test
    fun tappingTheRevealedButton_reportsTheActionItShows() {
        var reported: SwipeAction? = null
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = { action -> reported = action },
                    isSelected = false,
                    isSelectionMode = false,
                    leftAction = SwipeAction.NONE,
                    rightAction = SwipeAction.MOVE_TO
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()

        // The button node fills the row so its background paints the whole width as the row
        // slides, but only the 80dp strip the row has moved off is actually uncovered. The node's
        // centre is still underneath the row, where a tap lands on the row instead.
        composeTestRule.onNodeWithContentDescription(string(R.string.action_move_to)).performTouchInput {
            click(Offset(x = 40.dp.toPx(), y = centerY))
        }
        composeTestRule.waitUntil { reported != null }

        assertEquals(SwipeAction.MOVE_TO, reported)
    }

    /**
     * Settings is a separate screen, so a direction can be switched off while a row sits open behind
     * it. The row has to close itself: the drag that would close it is clamped away with the
     * direction.
     */
    @Test
    fun switchingADirectionOff_collapsesARevealedRow() {
        val testFile = createTestFile()
        var rightAction by mutableStateOf(SwipeAction.DELETE)

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = {},
                    isSelected = false,
                    isSelectionMode = false,
                    rightAction = rightAction
                )
            }
        }
        composeTestRule.waitForIdle()

        val restingLeft = composeTestRule.onNodeWithText("document.pdf")
            .getUnclippedBoundsInRoot().left

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertIsDisplayed()

        rightAction = SwipeAction.NONE
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertDoesNotExist()
        composeTestRule.onNodeWithText("document.pdf")
            .assertLeftPositionInRootIsEqualTo(restingLeft)
    }

    /**
     * The directions are physical, so a right-to-left locale gets the same gesture, not its mirror.
     * The row previously moved away from the finger here: the offset modifier mirrored under RTL
     * while the drag, reported in raw screen pixels, did not.
     */
    @Test
    fun rtl_theRowFollowsTheFingerAndRevealsTheSameAction() {
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SwipeableFileListItem(
                        file = testFile,
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        onSwipeAction = {},
                        isSelected = false,
                        isSelectionMode = false
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val restingLeft = composeTestRule.onNodeWithText("document.pdf")
            .getUnclippedBoundsInRoot().left

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertIsDisplayed()

        val revealedLeft = composeTestRule.onNodeWithText("document.pdf")
            .getUnclippedBoundsInRoot().left
        assertTrue(
            "Dragging right moved the row from $restingLeft to $revealedLeft, away from the finger",
            revealedLeft > restingLeft
        )
    }

    @Test
    fun partialSwipe_doesNotRevealAction() {
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = {},
                    isSelected = false,
                    isSelectionMode = false
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = centerX + 20f)
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertDoesNotExist()
    }

    @Test
    fun swipeInSelectionMode_disabled() {
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = {},
                    isSelected = true,
                    isSelectionMode = true
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertDoesNotExist()
    }

    @Test
    fun tapWhileRevealed_collapsesAction() {
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = {},
                    isSelected = false,
                    isSelectionMode = false
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertIsDisplayed()

        composeTestRule.onNodeWithText("document.pdf").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertDoesNotExist()
    }

    @Test
    fun swipeOneItem_othersUnaffected() {
        val file1 = createTestFile(name = "file1.pdf")
        val file2 = createTestFile(name = "file2.pdf")
        val file3 = createTestFile(name = "file3.pdf")

        composeTestRule.setContent {
            FileExplorerTheme {
                Column {
                    SwipeableFileListItem(
                        file = file1,
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        onSwipeAction = {},
                        isSelected = false,
                        isSelectionMode = false
                    )
                    SwipeableFileListItem(
                        file = file2,
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        onSwipeAction = {},
                        isSelected = false,
                        isSelectionMode = false
                    )
                    SwipeableFileListItem(
                        file = file3,
                        onClick = {},
                        onLongClick = {},
                        onMenuClick = {},
                        onSwipeAction = {},
                        isSelected = false,
                        isSelectionMode = false
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("file1.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("file2.pdf").assertIsDisplayed()
        composeTestRule.onNodeWithText("file3.pdf").assertIsDisplayed()
    }

    @Test
    fun folder_swipeActionsWork() {
        val testFolder = createTestFile(
            name = "MyFolder",
            isDirectory = true,
            size = 0L,
            mimeType = "",
            childCount = 5
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFolder,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = {},
                    isSelected = false,
                    isSelectionMode = false
                )
            }
        }

        composeTestRule.onNodeWithText("MyFolder").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertIsDisplayed()
    }

    @Test
    fun folder_swipeLeftShowsRename() {
        val testFolder = createTestFile(
            name = "MyFolder",
            isDirectory = true,
            size = 0L,
            mimeType = "",
            childCount = 5
        )

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFolder,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = {},
                    isSelected = false,
                    isSelectionMode = false
                )
            }
        }

        composeTestRule.onNodeWithText("MyFolder").performTouchInput {
            swipeLeft(startX = centerX, endX = left)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(string(R.string.action_rename)).assertIsDisplayed()
    }

    @Test
    fun tapWhileRevealed_doesNotTriggerOnClick() {
        var clickTriggered = false
        val testFile = createTestFile()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = { clickTriggered = true },
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = {},
                    isSelected = false,
                    isSelectionMode = false
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("document.pdf").performClick()
        composeTestRule.waitForIdle()

        assertFalse(clickTriggered)
    }

    @Test
    fun enteringSelectionMode_collapsesRevealedRowAndHidesAction() {
        val testFile = createTestFile()
        // This row is never selected itself; selection mode is entered by selecting another row.
        var selectionMode by mutableStateOf(false)

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = {},
                    isSelected = false,
                    isSelectionMode = selectionMode
                )
            }
        }
        composeTestRule.waitForIdle()

        val restingLeft = composeTestRule.onNodeWithText("document.pdf")
            .getUnclippedBoundsInRoot().left

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertIsDisplayed()

        // Entering selection mode (e.g. long-pressing a different row) must collapse this one.
        selectionMode = true
        composeTestRule.waitForIdle()

        // The destructive action is gone and the row has slid back to its resting position.
        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertDoesNotExist()
        composeTestRule.onNodeWithText("document.pdf")
            .assertLeftPositionInRootIsEqualTo(restingLeft)
    }
}
