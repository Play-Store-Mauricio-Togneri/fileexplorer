package com.mauriciotogneri.fileexplorer.data.source

/**
 * Keeps Android's media index — what gallery, music and other file apps read — in step with what a
 * file operation did, given only the top-level paths it created and took away.
 */
interface MediaIndexSync {
    /** Called once per operation, after it ends however it ends. Never throws, never blocks. */
    fun afterOperation(created: List<String>, removed: List<String>)
}
