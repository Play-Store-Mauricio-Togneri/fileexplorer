package com.mauriciotogneri.fileexplorer.data.model

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class PickerRequest(
    val items: List<FileItem>,
    val mode: OperationMode,
    val id: String = UUID.randomUUID().toString()
)
