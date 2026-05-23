package com.mauriciotogneri.fileexplorer.util

/**
 * Characters that are invalid in file and folder names.
 * This includes path separators and characters prohibited by various file systems.
 */
val INVALID_FILENAME_CHARS = setOf('/', '\\', '*', '?', '"', '<', '>', '|', ':')
