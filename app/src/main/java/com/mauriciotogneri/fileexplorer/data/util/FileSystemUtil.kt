package com.mauriciotogneri.fileexplorer.data.util

import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Whether this entry is a symbolic link. Every recursive walk in the app skips them: a link back up
 * its own tree turns a walk into an unbounded one, and a link to a file already counted elsewhere
 * would have its bytes tallied twice.
 *
 * Lives here rather than beside any one walker because more than one of them needs it, and the two
 * subtleties below are not ones a second copy would be likely to keep.
 */
internal fun File.isSymlink(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // A name that cannot be represented as a Path is reported as a regular file rather than
        // guessed at: the canonical-path comparison below re-encodes the name lossily, so it
        // would answer true for a plain file, and callers treat symlinks as entries to skip —
        // copy, compress and search would drop it and still report success. Every java.io call
        // on such a name fails, so callers surface a real error instead.
        val path = toPathOrNull() ?: return false
        return Files.isSymbolicLink(path)
    }

    // Pre-O, compare the canonical path against the parent's canonical path plus this entry's
    // name.
    return try {
        parentFile?.let { parent ->
            canonicalPath != File(parent.canonicalFile, name).path
        } ?: false
    } catch (_: IOException) {
        false
    }
}

/**
 * Returns this file as a [Path], or null when its name cannot be represented as one. [File.toPath]
 * re-encodes the name with the platform charset and rejects names whose bytes are not valid UTF-8 —
 * common in downloaded files whose names were truncated mid-character, which surface as unpaired
 * surrogates. Callers must degrade to the `java.io` API, which tolerates them, instead of
 * propagating the unchecked [InvalidPathException].
 */
@RequiresApi(Build.VERSION_CODES.O)
internal fun File.toPathOrNull(): Path? = try {
    toPath()
} catch (_: InvalidPathException) {
    null
}
