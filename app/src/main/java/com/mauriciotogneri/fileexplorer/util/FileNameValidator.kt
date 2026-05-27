package com.mauriciotogneri.fileexplorer.util

/**
 * Characters that are invalid in file and folder names.
 * This includes path separators and characters prohibited by various file systems.
 */
val INVALID_FILENAME_CHARS = setOf('/', '\\', '*', '?', '"', '<', '>', '|', ':')

/**
 * Returns true if the given name contains any invalid filename characters.
 * The name should be pre-trimmed by the caller.
 */
fun hasInvalidFileNameCharacters(name: String): Boolean =
    name.any { it in INVALID_FILENAME_CHARS }

/**
 * Returns true if the given name is a valid file or folder name.
 * The name should be pre-trimmed by the caller.
 */
fun isValidFileName(name: String): Boolean =
    name.isNotBlank() &&
        name != "." &&
        name != ".." &&
        !hasInvalidFileNameCharacters(name)
