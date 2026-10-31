package com.mauriciotogneri.fileexplorer.data.model

/**
 * Why a swiped-open folder row closed without its button being tapped.
 *
 * Each reason is reported by the one place that sees it happen, so no close is counted twice:
 * - the row itself: [ROW_TAP], [DRAG_BACK], [CANCELLED], [SETTING_OFF];
 * - the folder screen: [SCROLL], [OVERLAY], [LEFT_SCREEN];
 * - the folder ViewModel: [SELECTION];
 * - the tracker that pairs openings with their ends: [OTHER_SIDE], from a row reporting an opening
 *   on the other side while it was already open, and [OTHER_ROW], from a drag starting on another
 *   row (which the screen passes on) or another row reporting an opening.
 *
 * The lowercased names are analytics vocabulary — the `reason` of `folder_swipe_closed` — so
 * renaming an entry breaks continuity with the data already collected.
 */
enum class SwipeCloseReason {
    /** The open row's body was tapped, which closes it instead of opening the file. */
    ROW_TAP,

    /** The open row was dragged back under the reveal threshold. */
    DRAG_BACK,

    /** The system cancelled a drag on the open row. */
    CANCELLED,

    /** The open side was switched off in Settings while the row stayed open. */
    SETTING_OFF,

    /** Another row started being dragged. */
    OTHER_ROW,

    /** The list started scrolling. */
    SCROLL,

    /** The top-bar menu, the sort sheet, the actions sheet or a dialog opened over the list. */
    OVERLAY,

    /** The folder screen left composition: back, into another folder, or the activity destroyed. */
    LEFT_SCREEN,

    /** Selection mode started. */
    SELECTION,

    /** The open row was dragged across and settled open on its other side. */
    OTHER_SIDE
}
