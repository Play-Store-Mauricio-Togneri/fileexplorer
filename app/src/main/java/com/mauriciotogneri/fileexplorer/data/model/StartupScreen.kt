package com.mauriciotogneri.fileexplorer.data.model

/**
 * What the app opens on a cold start.
 *
 * [FOLDER] is a shortcut layered on top of [HOME], not a replacement for it: the folder is launched
 * over the home screen, so pressing back once returns there exactly as it does for a folder opened
 * by hand.
 */
enum class StartupScreen {
    HOME,
    FOLDER
}
