package com.mauriciotogneri.fileexplorer.data.model

/**
 * The way a folder row was dragged to open it: [RIGHT] is towards the right edge of the screen in
 * every language, the same physical meaning the swipe settings give a direction
 * ([com.mauriciotogneri.fileexplorer.data.source.PreferencesSource.swipeLeftAction]).
 *
 * The lowercased names are analytics vocabulary — the `side` of the folder swipe events — so
 * renaming an entry breaks continuity with the data already collected.
 */
enum class SwipeSide {
    LEFT,
    RIGHT
}
