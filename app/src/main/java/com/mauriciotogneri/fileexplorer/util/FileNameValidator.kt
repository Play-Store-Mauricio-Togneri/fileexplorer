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

/**
 * The part of [name] a rename or a numbered copy edits, leaving the extension to be put back
 * afterwards. Only an interior dot separates one: a leading dot belongs to a dotfile's own name
 * (".gitignore" is a name, not an extension) and a trailing dot has nothing after it to be one.
 *
 * A different question from the extension the type of a file is looked up by, which is the last
 * dot-separated token whatever its position — for ".gitignore" that is "gitignore", and answering
 * it here would number a copy of it " (1).gitignore".
 */
fun fileNameStem(name: String): String {
    val dotIndex = name.lastIndexOf('.')
    return if (dotIndex > 0 && dotIndex < name.length - 1) name.substring(0, dotIndex) else name
}
