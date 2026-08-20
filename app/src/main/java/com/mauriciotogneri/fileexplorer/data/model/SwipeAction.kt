package com.mauriciotogneri.fileexplorer.data.model

/**
 * What the button revealed by swiping a folder row does.
 *
 * [NONE] switches the direction off: the row does not follow the finger that way and no button is
 * revealed, which is how a user who keeps triggering a swipe by accident turns it off.
 *
 * Every other entry applies to any row, folder or file, readable or not, so what a direction reveals
 * never changes with what is under it. That is what keeps compress out: the actions bottom sheet
 * offers uncompress instead on an archive, and a swipe whose meaning depends on the row cannot be
 * described by a settings label.
 */
enum class SwipeAction {
    NONE,
    RENAME,
    DELETE,
    MOVE_TO,
    COPY_TO,
    INFO
}
