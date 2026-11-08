package com.mauriciotogneri.fileexplorer.ui.screens.folder

import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.model.SwipeCloseReason
import com.mauriciotogneri.fileexplorer.data.model.SwipeSide
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.ui.components.SwipeRowEvent
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The tracker is what keeps the swipe analytics countable: one `opened` per opening, then exactly
 * one `tapped` or `closed` for it, so opened = tapped + closed + the few lost to a dying process.
 */
class SwipeRevealTrackerTest {

    private val events = RecordingSwipeEvents()
    private var clockNanos = 0L
    private val tracker = SwipeRevealTracker(events) { clockNanos }

    private val rowA = "/storage/emulated/0/Documents/a.txt"
    private val rowB = "/storage/emulated/0/Documents/b.txt"

    private val openedRightDelete = SwipeRowEvent.Opened(SwipeSide.RIGHT, SwipeAction.DELETE)
    private val openedLeftRename = SwipeRowEvent.Opened(SwipeSide.LEFT, SwipeAction.RENAME)

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun advanceMillis(millis: Long) {
        clockNanos += millis * 1_000_000L
    }

    private fun closed(reason: SwipeCloseReason, openFor: String = "under_1s") =
        SwipeCall.Closed(SwipeSide.RIGHT, SwipeAction.DELETE, reason, openFor)

    @Test
    fun `an opening is sent once even when reported twice`() {
        tracker.onRowEvent(rowA, openedRightDelete)
        tracker.onRowEvent(rowA, openedRightDelete)

        assertEquals(listOf(SwipeCall.Opened(SwipeSide.RIGHT, SwipeAction.DELETE)), events.calls)
    }

    @Test
    fun `opening the other side of the same row closes the first opening as other_side`() {
        tracker.onRowEvent(rowA, openedRightDelete)
        tracker.onRowEvent(rowA, openedLeftRename)

        assertEquals(
            listOf(
                SwipeCall.Opened(SwipeSide.RIGHT, SwipeAction.DELETE),
                closed(SwipeCloseReason.OTHER_SIDE),
                SwipeCall.Opened(SwipeSide.LEFT, SwipeAction.RENAME)
            ),
            events.calls
        )
    }

    @Test
    fun `another row opening closes the first opening as other_row`() {
        tracker.onRowEvent(rowA, openedRightDelete)
        tracker.onRowEvent(rowB, openedLeftRename)

        assertEquals(
            listOf(
                SwipeCall.Opened(SwipeSide.RIGHT, SwipeAction.DELETE),
                closed(SwipeCloseReason.OTHER_ROW),
                SwipeCall.Opened(SwipeSide.LEFT, SwipeAction.RENAME)
            ),
            events.calls
        )
    }

    @Test
    fun `a drag starting on another row closes the open one as other_row`() {
        tracker.onRowEvent(rowA, openedRightDelete)
        tracker.rowClaimed(rowB)

        assertEquals(
            listOf(SwipeCall.Opened(SwipeSide.RIGHT, SwipeAction.DELETE), closed(SwipeCloseReason.OTHER_ROW)),
            events.calls
        )
    }

    /**
     * A row claims again once it settles open. With two fingers, the screen may already have moved
     * its open row elsewhere by then, and that claim must not end the opening the row just reported.
     */
    @Test
    fun `a row claiming itself keeps its own opening`() {
        tracker.onRowEvent(rowA, openedRightDelete)
        tracker.rowClaimed(rowA)

        assertEquals(listOf(SwipeCall.Opened(SwipeSide.RIGHT, SwipeAction.DELETE)), events.calls)
    }

    @Test
    fun `a tap ends the opening, so a later close sends nothing`() {
        tracker.onRowEvent(rowA, openedRightDelete)
        tracker.onRowEvent(rowA, SwipeRowEvent.Tapped(SwipeSide.RIGHT, SwipeAction.DELETE))
        tracker.closeOpen(SwipeCloseReason.SCROLL)

        assertEquals(
            listOf(
                SwipeCall.Opened(SwipeSide.RIGHT, SwipeAction.DELETE),
                SwipeCall.Tapped(SwipeSide.RIGHT, SwipeAction.DELETE)
            ),
            events.calls
        )
    }

    @Test
    fun `a tap with no opening held is still sent`() {
        tracker.onRowEvent(rowA, SwipeRowEvent.Tapped(SwipeSide.LEFT, SwipeAction.RENAME))

        assertEquals(listOf(SwipeCall.Tapped(SwipeSide.LEFT, SwipeAction.RENAME)), events.calls)
    }

    @Test
    fun `a close reported by a row that is not open sends nothing and keeps the opening`() {
        tracker.onRowEvent(rowA, openedRightDelete)
        tracker.onRowEvent(rowB, SwipeRowEvent.Closed(SwipeCloseReason.ROW_TAP))
        tracker.closeOpen(SwipeCloseReason.SCROLL)

        assertEquals(
            listOf(SwipeCall.Opened(SwipeSide.RIGHT, SwipeAction.DELETE), closed(SwipeCloseReason.SCROLL)),
            events.calls
        )
    }

    @Test
    fun `closing with nothing open sends nothing`() {
        tracker.closeOpen(SwipeCloseReason.SCROLL)
        tracker.rowClaimed(rowA)
        tracker.onRowEvent(rowA, SwipeRowEvent.Closed(SwipeCloseReason.DRAG_BACK))

        assertEquals(emptyList<SwipeCall>(), events.calls)
    }

    @Test
    fun `every close reason reaches the sink unchanged, from a row or from the screen`() {
        SwipeCloseReason.entries.forEach { reason ->
            tracker.onRowEvent(rowA, openedRightDelete)
            tracker.onRowEvent(rowA, SwipeRowEvent.Closed(reason))
        }
        SwipeCloseReason.entries.forEach { reason ->
            tracker.onRowEvent(rowA, openedRightDelete)
            tracker.closeOpen(reason)
        }

        val reasons = events.calls.filterIsInstance<SwipeCall.Closed>().map { it.reason }
        assertEquals(SwipeCloseReason.entries + SwipeCloseReason.entries, reasons)
    }

    @Test
    fun `open_for buckets how long the row stayed open`() {
        listOf(
            999L to "under_1s",
            1_000L to "1_to_5s",
            4_999L to "1_to_5s",
            5_000L to "over_5s"
        ).forEach { (millis, bucket) ->
            tracker.onRowEvent(rowA, openedRightDelete)
            advanceMillis(millis)
            tracker.closeOpen(SwipeCloseReason.SCROLL)

            val close = events.calls.last() as SwipeCall.Closed
            assertEquals("open for $millis ms", bucket, close.openFor)
        }
    }

    /**
     * The values analytics receives are the ones `AnalyticsTracker` documents, and the analysis
     * reads. Renaming an enum entry renames a value, which would split the data in two.
     */
    @Test
    fun `the analytics sink sends only the documented values`() {
        mockkObject(AnalyticsTracker)
        val sides = mutableListOf<String>()
        val actions = mutableListOf<String>()
        val reasons = mutableListOf<String>()
        every { AnalyticsTracker.trackFolderSwipeOpened(capture(sides), capture(actions)) } just Runs
        every { AnalyticsTracker.trackFolderSwipeActionTapped(capture(actions), capture(sides)) } just Runs
        every {
            AnalyticsTracker.trackFolderSwipeClosed(capture(sides), capture(actions), capture(reasons), any())
        } just Runs

        val revealable = SwipeAction.entries - SwipeAction.NONE
        SwipeSide.entries.forEach { side ->
            revealable.forEach { action ->
                AnalyticsSwipeEvents.opened(side, action)
                AnalyticsSwipeEvents.tapped(side, action)
            }
        }
        SwipeCloseReason.entries.forEach { reason ->
            AnalyticsSwipeEvents.closed(SwipeSide.RIGHT, SwipeAction.DELETE, reason, "under_1s")
        }

        assertEquals(setOf("left", "right"), sides.toSet())
        assertEquals(setOf("rename", "delete", "move_to", "copy_to", "info"), actions.toSet())
        assertEquals(
            setOf(
                "row_tap", "drag_back", "cancelled", "setting_off", "other_row",
                "scroll", "overlay", "left_screen", "selection", "other_side"
            ),
            reasons.toSet()
        )
    }
}
