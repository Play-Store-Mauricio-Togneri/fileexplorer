package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable
import java.util.UUID

/**
 * What the destination picker was opened for.
 *
 * [mode] is null when the picker is only choosing a folder — the startup screen setting — rather
 * than performing a file operation on [items]. A null mode is deliberately not a third
 * [OperationMode]: that enum reaches the copy and delete paths in FileRepository, where every
 * `MOVE`-or-else branch would silently treat a third value as a copy.
 */
@Immutable
data class PickerRequest(
    val items: List<FileItem>,
    val mode: OperationMode?,
    val id: String = UUID.randomUUID().toString()
)
