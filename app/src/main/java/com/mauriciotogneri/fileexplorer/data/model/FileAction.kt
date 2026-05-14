package com.mauriciotogneri.fileexplorer.data.model

/**
 * Represents user actions available in the folder view action bar.
 */
sealed interface FileAction {
    data object Cut : FileAction
    data object Copy : FileAction
    data object Paste : FileAction
    data object SelectAll : FileAction
    data object Rename : FileAction
    data object Compress : FileAction
    data object Share : FileAction
    data object Delete : FileAction
    data object CreateFolder : FileAction
}
