package com.mauriciotogneri.fileexplorer.data.model

/**
 * What a file row shows under its name. [NONE] blanks the line without removing it, for the reason
 * given in [FolderSecondLine].
 */
enum class FileSecondLine {
    NONE,
    SIZE,
    LAST_MODIFIED
}
