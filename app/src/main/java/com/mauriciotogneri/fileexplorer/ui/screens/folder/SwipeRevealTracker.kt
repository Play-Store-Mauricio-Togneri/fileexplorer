package com.mauriciotogneri.fileexplorer.ui.screens.folder

import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.model.SwipeCloseReason
import com.mauriciotogneri.fileexplorer.data.model.SwipeSide
import com.mauriciotogneri.fileexplorer.data.util.AnalyticsTracker
import com.mauriciotogneri.fileexplorer.ui.components.SwipeRowEvent

/**
 * Where the folder screen's swipe events go. Every value is one of the app's own options, never a
 * path, a name or anything else about the row.
 */
interface SwipeEvents {
    fun opened(side: SwipeSide, action: SwipeAction)

    fun tapped(side: SwipeSide, action: SwipeAction)

    /** [openFor] is how long the row stayed open: "under_1s", "1_to_5s" or "over_5s". */
    fun closed(side: SwipeSide, action: SwipeAction, reason: SwipeCloseReason, openFor: String)
}

/** The production sink: [AnalyticsTracker]'s folder swipe events, with the enum names lowercased. */
object AnalyticsSwipeEvents : SwipeEvents {
    override fun opened(side: SwipeSide, action: SwipeAction) {
        AnalyticsTracker.trackFolderSwipeOpened(side.value(), action.value())
    }

    override fun tapped(side: SwipeSide, action: SwipeAction) {
        AnalyticsTracker.trackFolderSwipeActionTapped(action.value(), side.value())
    }

    override fun closed(side: SwipeSide, action: SwipeAction, reason: SwipeCloseReason, openFor: String) {
        AnalyticsTracker.trackFolderSwipeClosed(side.value(), action.value(), reason.value(), openFor)
    }

    private fun Enum<*>.value(): String = name.lowercase()
}

/**
 * Pairs each swipe opening on the folder screen with the one way it ended, so [events] receives one
 * `opened` and then exactly one `tapped` or `closed` for it.
 *
 * The row reports its own transitions; the screen and the ViewModel report the closes only they
 * see. At most one row is open at a time, so one opening is held. Its path is kept in memory only,
 * to match a close to its row, and is never sent.
 *
 * [now] is in nanoseconds. [System.nanoTime] rather than `SystemClock` keeps the JVM tests free of
 * Android stubs.
 */
internal class SwipeRevealTracker(
    private val events: SwipeEvents,
    private val now: () -> Long = System::nanoTime
) {
    private class Opening(
        val path: String,
        val side: SwipeSide,
        val action: SwipeAction,
        val since: Long
    )

    private var open: Opening? = null

    fun onRowEvent(path: String, event: SwipeRowEvent) {
        when (event) {
            is SwipeRowEvent.Opened -> opened(path, event.side, event.action)
            is SwipeRowEvent.Tapped -> {
                // Sent even with no opening held: the row already reports a tap only on a row it
                // saw settle open, once.
                if (open?.path == path) open = null
                events.tapped(event.side, event.action)
            }
            is SwipeRowEvent.Closed -> if (open?.path == path) close(event.reason)
        }
    }

    /**
     * Ends whatever opening is held: a scroll, an overlay, selection mode, the screen going away.
     * Does nothing when no row is open, so calling it on every scroll start costs nothing.
     */
    fun closeOpen(reason: SwipeCloseReason) {
        close(reason)
    }

    /**
     * A drag started on [path]'s row, which takes the open row away from any other.
     *
     * The opening of [path] itself is kept rather than read against the screen's open row: a row
     * claims again once it settles open, and a second finger may have moved that elsewhere meanwhile,
     * so the claim of the row that just opened must not end its own opening.
     */
    fun rowClaimed(path: String) {
        if (open?.path != path) close(SwipeCloseReason.OTHER_ROW)
    }

    private fun opened(path: String, side: SwipeSide, action: SwipeAction) {
        val current = open
        if (current != null) {
            // The row reports one opening per side already; this only guards against a duplicate.
            if (current.path == path && current.side == side) return
            // Another row normally closes this one at its drag start, through rowClaimed.
            close(if (current.path == path) SwipeCloseReason.OTHER_SIDE else SwipeCloseReason.OTHER_ROW)
        }
        open = Opening(path, side, action, now())
        events.opened(side, action)
    }

    private fun close(reason: SwipeCloseReason) {
        val opening = open ?: return
        open = null
        events.closed(opening.side, opening.action, reason, openFor(now() - opening.since))
    }

    private fun openFor(elapsedNanos: Long): String {
        val millis = elapsedNanos / NANOS_PER_MILLI
        return when {
            millis < 1_000 -> "under_1s"
            millis < 5_000 -> "1_to_5s"
            else -> "over_5s"
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
