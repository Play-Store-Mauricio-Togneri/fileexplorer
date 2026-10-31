package com.mauriciotogneri.fileexplorer.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
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
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.mauriciotogneri.fileexplorer.R
import com.mauriciotogneri.fileexplorer.data.model.FileItem
import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.model.SwipeCloseReason
import com.mauriciotogneri.fileexplorer.data.model.SwipeSide
import com.mauriciotogneri.fileexplorer.ui.theme.FileExplorerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max

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

    private val openedRightDelete = SwipeRowEvent.Opened(SwipeSide.RIGHT, SwipeAction.DELETE)

    /**
     * Renders one row that records what it reports into [events], and counts its reveal callbacks
     * in [revealCalls]: a drag reports one on start and one on release, which is what shows a drag
     * that reported nothing really took place.
     */
    private fun renderRecordingRow(
        events: MutableList<SwipeRowEvent>,
        revealCalls: AtomicInteger = AtomicInteger(),
        leftAction: SwipeAction = SwipeAction.RENAME,
        rightAction: SwipeAction = SwipeAction.DELETE,
        onSwipeAction: (SwipeAction) -> Unit = {}
    ) {
        val testFile = createTestFile()
        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = onSwipeAction,
                    isSelected = false,
                    isSelectionMode = false,
                    leftAction = leftAction,
                    rightAction = rightAction,
                    onRevealedChange = { revealCalls.incrementAndGet() },
                    onSwipeEvent = { events += it }
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun swipeRowFullyRight() {
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()
    }

    /**
     * Dragging an open row again and letting it settle open on the same side is the same opening.
     * The re-drag moves the row 12dp back (20dp less the 8dp touch slop), which leaves it past the
     * 40dp threshold.
     */
    @Test
    fun reDraggingAnOpenRow_thatStaysOpen_reportsOneOpening() {
        val events = mutableListOf<SwipeRowEvent>()
        val revealCalls = AtomicInteger()
        renderRecordingRow(events, revealCalls)

        swipeRowFullyRight()
        val callsAfterOpening = revealCalls.get()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeLeft(startX = centerX, endX = centerX - 20.dp.toPx())
        }
        composeTestRule.waitForIdle()

        assertTrue("The re-drag never started", revealCalls.get() > callsAfterOpening)
        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertIsDisplayed()
        assertEquals(listOf(openedRightDelete), events)
    }

    /** A row already at its limit cannot move further, but the drag still ends; it is no new opening. */
    @Test
    fun pushingAnOpenRowAgainstItsLimit_reportsOneOpening() {
        val events = mutableListOf<SwipeRowEvent>()
        val revealCalls = AtomicInteger()
        renderRecordingRow(events, revealCalls)

        swipeRowFullyRight()
        val callsAfterOpening = revealCalls.get()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = centerX + 60.dp.toPx())
        }
        composeTestRule.waitForIdle()

        assertTrue("The push never started", revealCalls.get() > callsAfterOpening)
        assertEquals(listOf(openedRightDelete), events)
    }

    /**
     * Dragged across in one go, the row ends up open on the other side: a new opening with that
     * side's action. The tracker turns the second one into a close of the first.
     */
    @Test
    fun draggingAnOpenRowAcross_reportsAnOpeningOnTheOtherSide() {
        val events = mutableListOf<SwipeRowEvent>()
        renderRecordingRow(events)

        swipeRowFullyRight()
        // From the open row's centre to its left edge is half the row's width, well past the 160dp
        // from one limit to the other.
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeLeft(startX = centerX, endX = left)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(string(R.string.action_rename)).assertIsDisplayed()
        assertEquals(
            listOf(openedRightDelete, SwipeRowEvent.Opened(SwipeSide.LEFT, SwipeAction.RENAME)),
            events
        )
    }

    @Test
    fun closingThenReopening_reportsTwoOpenings() {
        val events = mutableListOf<SwipeRowEvent>()
        renderRecordingRow(events)

        swipeRowFullyRight()
        composeTestRule.onNodeWithText("document.pdf").performClick()
        composeTestRule.waitForIdle()
        swipeRowFullyRight()

        assertEquals(
            listOf(openedRightDelete, SwipeRowEvent.Closed(SwipeCloseReason.ROW_TAP), openedRightDelete),
            events
        )
    }

    /** 80dp back, less the 8dp touch slop, leaves the row 8dp open: under the 40dp threshold. */
    @Test
    fun draggingAnOpenRowBackShut_reportsADragBack() {
        val events = mutableListOf<SwipeRowEvent>()
        renderRecordingRow(events)
        val restingLeft = composeTestRule.onNodeWithText("document.pdf")
            .getUnclippedBoundsInRoot().left

        swipeRowFullyRight()
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeLeft(startX = centerX, endX = centerX - 80.dp.toPx())
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("document.pdf")
            .assertLeftPositionInRootIsEqualTo(restingLeft)
        assertEquals(listOf(openedRightDelete, SwipeRowEvent.Closed(SwipeCloseReason.DRAG_BACK)), events)
    }

    /**
     * With the same action on both sides, only the side tells the two buttons apart. The left
     * button's side is pinned by [tappingTheLeftRevealedButton_reportsTheLeftAction].
     */
    @Test
    fun withTheSameActionOnBothSides_aTapReportsTheSideItWasOn() {
        val events = mutableListOf<SwipeRowEvent>()
        var reported: SwipeAction? = null
        renderRecordingRow(
            events,
            leftAction = SwipeAction.DELETE,
            rightAction = SwipeAction.DELETE,
            onSwipeAction = { reported = it }
        )

        swipeRowFullyRight()
        // Only the 80dp strip the row moved off is uncovered; see tappingTheRevealedButton_reportsTheActionItShows.
        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).performTouchInput {
            click(Offset(x = 40.dp.toPx(), y = centerY))
        }
        composeTestRule.waitUntil { reported != null }

        assertEquals(
            listOf(openedRightDelete, SwipeRowEvent.Tapped(SwipeSide.RIGHT, SwipeAction.DELETE)),
            events
        )
    }

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
     * The left swipe's button reports its own action, not the right swipe's. Only the right-hand
     * button's tap was driven, so a left button wired to the other direction's action — tapping
     * Rename deleting the file — passed.
     */
    @Test
    fun tappingTheLeftRevealedButton_reportsTheLeftAction() {
        var reported: SwipeAction? = null
        val events = mutableListOf<SwipeRowEvent>()
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
                    onSwipeEvent = { events += it }
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeLeft(startX = centerX, endX = left)
        }
        composeTestRule.waitForIdle()

        // As in the right-hand tap above, only the strip the row has moved off is uncovered — here
        // the one at the right edge.
        composeTestRule.onNodeWithContentDescription(string(R.string.action_rename)).performTouchInput {
            click(Offset(x = right - 40.dp.toPx(), y = centerY))
        }
        composeTestRule.waitUntil { reported != null }

        assertEquals(SwipeAction.RENAME, reported)
        assertEquals(
            listOf(
                SwipeRowEvent.Opened(SwipeSide.LEFT, SwipeAction.RENAME),
                SwipeRowEvent.Tapped(SwipeSide.LEFT, SwipeAction.RENAME)
            ),
            events
        )
    }

    /**
     * The button a left swipe reveals draws its icon and label in the strip that swipe uncovers. The
     * button's node fills the row, so finding or tapping it holds however its content is aligned:
     * content aligned to the other edge is drawn under the row, and the strip shows a bare fill.
     */
    // The SdkSuppress is captureToImage()'s: it is annotated @RequiresApi(O), and minSdk is 24.
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun swipeLeft_drawsItsButtonInTheUncoveredStrip() {
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

        assertButtonDrawsInTheUncoveredStrip(string(R.string.action_rename), rowName = "document.pdf")
    }

    /** The mirror of [swipeLeft_drawsItsButtonInTheUncoveredStrip], for the button a right swipe reveals. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun swipeRight_drawsItsButtonInTheUncoveredStrip() {
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

        assertButtonDrawsInTheUncoveredStrip(string(R.string.action_delete), rowName = "document.pdf")
    }

    /**
     * Fails unless something other than [label]'s button fill is drawn in the strip the row named
     * [rowName] has slid off.
     *
     * The strip is read from the two nodes' bounds rather than from the button width, so it follows
     * whatever distance the row actually moved. Its corner farthest from the row stands for the
     * fill — clear of the centred icon and label — and a pixel more than 0.25 off it in any channel
     * is the icon or the label: the fill/content pairs differ far more than that in every mode, and
     * an antialiased edge against the row is kept out by staying 4dp clear of it.
     */
    private fun assertButtonDrawsInTheUncoveredStrip(label: String, rowName: String) {
        val button = composeTestRule.onNodeWithContentDescription(label)
        val buttonBounds = button.getUnclippedBoundsInRoot()
        val rowBounds = composeTestRule.onNodeWithText(rowName).getUnclippedBoundsInRoot()
        val pixels = button.captureToImage().toPixelMap()
        val density = composeTestRule.density
        val margin = with(density) { 4.dp.roundToPx() }

        // In the captured button's own pixels: the row slid right uncovers its left end, and the
        // other way round.
        val slidRight = rowBounds.left > buttonBounds.left
        val stripStart: Int
        val stripEnd: Int
        if (slidRight) {
            stripStart = 0
            stripEnd = with(density) { (rowBounds.left - buttonBounds.left).roundToPx() }
        } else {
            stripStart = with(density) { (rowBounds.right - buttonBounds.left).roundToPx() }
            stripEnd = pixels.width
        }
        assertTrue("The row uncovered no strip: $stripStart..$stripEnd", stripEnd - stripStart > 2 * margin)

        val fill = pixels[if (slidRight) stripStart + margin else stripEnd - 1 - margin, margin]
        var furthest = 0f
        for (x in stripStart + margin until stripEnd - margin) {
            for (y in 0 until pixels.height) {
                val pixel = pixels[x, y]
                val distance = max(
                    abs(pixel.red - fill.red),
                    max(abs(pixel.green - fill.green), abs(pixel.blue - fill.blue))
                )
                furthest = maxOf(furthest, distance)
            }
        }

        assertTrue(
            "The '$label' button draws nothing but its fill in the strip the row uncovered (furthest $furthest)",
            furthest > 0.25f
        )
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
        val events = mutableListOf<SwipeRowEvent>()

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
                    rightAction = rightAction,
                    onSwipeEvent = { events += it }
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
        assertEquals(listOf(openedRightDelete, SwipeRowEvent.Closed(SwipeCloseReason.SETTING_OFF)), events)
    }

    /**
     * The directions are physical, so a right-to-left locale gets the same gesture, not its mirror.
     * The row previously moved away from the finger here: the offset modifier mirrored under RTL
     * while the drag, reported in raw screen pixels, did not.
     */
    @Test
    fun rtl_theRowFollowsTheFingerAndRevealsTheSameAction() {
        val testFile = createTestFile()
        val events = mutableListOf<SwipeRowEvent>()

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
                        isSelectionMode = false,
                        onSwipeEvent = { events += it }
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
        // The side is the physical one too, so analytics reads a right drag as right in Arabic.
        assertEquals(listOf(openedRightDelete), events)
    }

    @Test
    fun partialSwipe_doesNotRevealAction() {
        val testFile = createTestFile()
        val events = mutableListOf<SwipeRowEvent>()

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
                    onSwipeEvent = { events += it }
                )
            }
        }

        composeTestRule.waitForIdle()

        val restingLeft = composeTestRule.onNodeWithText("document.pdf")
            .getUnclippedBoundsInRoot().left

        // 36dp clears the 8dp touch slop, so the drag really starts, yet stays under the 40dp
        // reveal threshold. A distance in pixels would fall inside the slop on dense screens, the
        // drag would never begin and the threshold would go untested.
        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = centerX + 36.dp.toPx())
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertDoesNotExist()
        composeTestRule.onNodeWithText("document.pdf")
            .assertLeftPositionInRootIsEqualTo(restingLeft)
        // A row that never opened was no opening, and closing it is nothing to report either.
        assertEquals(emptyList<SwipeRowEvent>(), events)
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
        composeTestRule.waitForIdle()

        val restingLeft = composeTestRule.onNodeWithText("document.pdf")
            .getUnclippedBoundsInRoot().left

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }

        composeTestRule.waitForIdle()
        // Selection mode never draws the action buttons, so their absence alone holds however the
        // drag behaves. The row staying put is what shows the drag itself is disabled.
        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertDoesNotExist()
        composeTestRule.onNodeWithText("document.pdf")
            .assertLeftPositionInRootIsEqualTo(restingLeft)
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
        val events = mutableListOf<SwipeRowEvent>()

        composeTestRule.setContent {
            FileExplorerTheme {
                SwipeableFileListItem(
                    file = testFile,
                    onClick = {},
                    onLongClick = {},
                    onMenuClick = {},
                    onSwipeAction = {},
                    isSelected = false,
                    isSelectionMode = selectionMode,
                    onSwipeEvent = { events += it }
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
        // Whoever started selection mode reports that close, not the row.
        assertEquals(listOf(openedRightDelete), events)

        // The row forgot it was open, so swiping it again after selection is a new opening.
        selectionMode = false
        composeTestRule.waitForIdle()
        swipeRowFullyRight()
        assertEquals(listOf(openedRightDelete, openedRightDelete), events)
    }

    @Test
    fun swipe_reportsRevealed_andCollapsesWhenCallerTakesItAway() {
        val testFile = createTestFile()
        var revealed by mutableStateOf(false)
        val events = mutableListOf<SwipeRowEvent>()

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
                    isRevealed = revealed,
                    onRevealedChange = { revealed = it },
                    onSwipeEvent = { events += it }
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
        assertTrue(revealed)
        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertIsDisplayed()

        // Another row opening, a dialog or a scroll: the caller hands the open row elsewhere.
        revealed = false
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription(string(R.string.action_delete)).assertDoesNotExist()
        composeTestRule.onNodeWithText("document.pdf")
            .assertLeftPositionInRootIsEqualTo(restingLeft)
        // The caller knows why it took the row away and reports that itself.
        assertEquals(listOf(openedRightDelete), events)

        // The row forgot it was open, so the next swipe is a new opening.
        swipeRowFullyRight()
        assertEquals(listOf(openedRightDelete, openedRightDelete), events)
    }

    @Test
    fun tapToClose_reportsNotRevealed() {
        val testFile = createTestFile()
        var revealed by mutableStateOf(false)

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
                    isRevealed = revealed,
                    onRevealedChange = { revealed = it }
                )
            }
        }

        composeTestRule.onNodeWithText("document.pdf").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        composeTestRule.waitForIdle()
        assertTrue(revealed)

        composeTestRule.onNodeWithText("document.pdf").performClick()
        composeTestRule.waitForIdle()

        assertFalse(revealed)
    }
}
