package com.mauriciotogneri.fileexplorer.data.util

import coil3.Uri
import coil3.filePath
import java.io.File

/**
 * The local file this [Uri] addresses, or null when it addresses something else.
 *
 * Coil maps a [File] handed to a request into a `file://` [Uri] before any fetcher is consulted, so
 * a fetcher that wants the file back has to undo that. Defined once because all five thumbnail
 * fetchers need it and must agree on what counts as a local file: anything else a request can carry
 * — `content://`, `android.resource://`, a Uri with no path at all — is left to Coil's own fetchers
 * rather than guessed at.
 */
internal fun Uri.toFileOrNull(): File? {
    if (scheme != FILE_SCHEME) {
        return null
    }

    return filePath?.let(::File)
}

private const val FILE_SCHEME = "file"
