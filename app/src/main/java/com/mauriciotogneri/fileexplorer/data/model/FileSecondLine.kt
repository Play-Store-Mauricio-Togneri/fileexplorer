package com.mauriciotogneri.fileexplorer.data.model

/**
 * What a file row shows under its name. [NONE] removes the line and centers the name, for the reason
 * given in [FolderSecondLine].
 */
enum class FileSecondLine {
    NONE,
    SIZE,
    LAST_MODIFIED
}
