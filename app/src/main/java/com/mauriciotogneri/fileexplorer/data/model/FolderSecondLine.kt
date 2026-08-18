package com.mauriciotogneri.fileexplorer.data.model

/**
 * What a folder row shows under its name.
 *
 * [NONE] blanks the line rather than removing it: every row keeps the same height whichever option
 * is chosen, so a list mixing folders and files never shows two row heights at once.
 *
 * A folder the app cannot read ignores this and shows "Restricted" under all three options, since
 * it has neither a count nor a date worth reporting.
 */
enum class FolderSecondLine {
    NONE,
    ITEM_COUNT,
    LAST_MODIFIED
}
