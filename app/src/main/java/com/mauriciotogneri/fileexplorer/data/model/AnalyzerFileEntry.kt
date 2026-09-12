package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable

/**
 * One file the storage analyzer counted, as little of it as the category listing needs.
 *
 * Deliberately not a [FileItem]: the scan visits every file on a volume, and it retains these for
 * the whole time the results are on screen, so each field is one that would otherwise cost either
 * a syscall during the walk or bytes of heap for as long as the results live. The name, MIME type
 * and timestamps a row displays are read back from disk one page at a time instead — see
 * `AnalyzerCategoryViewModel`.
 */
@Immutable
data class AnalyzerFileEntry(
    val path: String,
    val size: Long
)
