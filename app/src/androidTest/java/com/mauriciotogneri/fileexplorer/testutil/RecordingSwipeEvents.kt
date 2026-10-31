package com.mauriciotogneri.fileexplorer.testutil

import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.model.SwipeCloseReason
import com.mauriciotogneri.fileexplorer.data.model.SwipeSide
import com.mauriciotogneri.fileexplorer.ui.screens.folder.SwipeEvents
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [SwipeEvents] sink that keeps every call the folder screen's swipe tracking makes, instead of
 * sending it. The screen calls it on the main thread while the test reads it from its own, hence the
 * concurrent lists.
 */
class RecordingSwipeEvents : SwipeEvents {
    private val calls = CopyOnWriteArrayList<String>()
    private val openFors = CopyOnWriteArrayList<String>()

    /**
     * Every call in order, as "opened RIGHT DELETE", "tapped RIGHT DELETE" or
     * "closed RIGHT DELETE SCROLL". The `open_for` bucket is left out, since it depends on how long
     * the test took; [openFors] has it.
     */
    fun calls(): List<String> = calls.toList()

    /** The `open_for` bucket of each close, in order. */
    fun openFors(): List<String> = openFors.toList()

    override fun opened(side: SwipeSide, action: SwipeAction) {
        calls += "opened $side $action"
    }

    override fun tapped(side: SwipeSide, action: SwipeAction) {
        calls += "tapped $side $action"
    }

    override fun closed(side: SwipeSide, action: SwipeAction, reason: SwipeCloseReason, openFor: String) {
        calls += "closed $side $action $reason"
        openFors += openFor
    }
}
