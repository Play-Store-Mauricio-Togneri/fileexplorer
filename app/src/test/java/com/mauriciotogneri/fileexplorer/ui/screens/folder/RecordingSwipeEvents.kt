package com.mauriciotogneri.fileexplorer.ui.screens.folder

import com.mauriciotogneri.fileexplorer.data.model.SwipeAction
import com.mauriciotogneri.fileexplorer.data.model.SwipeCloseReason
import com.mauriciotogneri.fileexplorer.data.model.SwipeSide

/** One call the folder screen's swipe tracking made, as the sink received it. */
internal sealed interface SwipeCall {
    data class Opened(val side: SwipeSide, val action: SwipeAction) : SwipeCall
    data class Tapped(val side: SwipeSide, val action: SwipeAction) : SwipeCall
    data class Closed(
        val side: SwipeSide,
        val action: SwipeAction,
        val reason: SwipeCloseReason,
        val openFor: String
    ) : SwipeCall
}

/** A [SwipeEvents] sink that keeps every call, in order, instead of sending it. */
internal class RecordingSwipeEvents : SwipeEvents {
    val calls = mutableListOf<SwipeCall>()

    /**
     * The calls without their `open_for` bucket, for tests that run on the real clock: the bucket
     * depends on how long the test took, so only the tracker's own tests, with a fake clock, read it.
     */
    fun untimed(): List<String> = calls.map { call ->
        when (call) {
            is SwipeCall.Opened -> "opened ${call.side} ${call.action}"
            is SwipeCall.Tapped -> "tapped ${call.side} ${call.action}"
            is SwipeCall.Closed -> "closed ${call.side} ${call.action} ${call.reason}"
        }
    }

    override fun opened(side: SwipeSide, action: SwipeAction) {
        calls += SwipeCall.Opened(side, action)
    }

    override fun tapped(side: SwipeSide, action: SwipeAction) {
        calls += SwipeCall.Tapped(side, action)
    }

    override fun closed(side: SwipeSide, action: SwipeAction, reason: SwipeCloseReason, openFor: String) {
        calls += SwipeCall.Closed(side, action, reason, openFor)
    }
}
