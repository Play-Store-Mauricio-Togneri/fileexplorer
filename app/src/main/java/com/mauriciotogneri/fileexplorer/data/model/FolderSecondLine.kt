package com.mauriciotogneri.fileexplorer.data.model

/**
 * What a folder row shows under its name.
 *
 * [NONE] removes the line rather than blanking it, so the name is centered against the row's icon.
 * Row height does not change with it: the icon and the overflow menu are both taller than the two
 * lines of text beside them.
 *
 * A folder the app cannot read ignores this and shows "Restricted" under all three options, since it
 * has neither a count nor a date worth reporting.
 */
enum class FolderSecondLine {
    NONE,
    ITEM_COUNT,
    LAST_MODIFIED
}
